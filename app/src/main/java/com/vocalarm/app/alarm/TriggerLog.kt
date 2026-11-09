package com.vocalarm.app.alarm

import com.vocalarm.app.data.JsonStore
import java.io.File

/**
 * Tiny on-disk record of the alarms the system actually delivered to this app.
 *
 * It exists so the in-app self check can tell two very different failures apart: "the OS never
 * called us" (force stop, vendor background kill) versus "we were called but the ring failed".
 * Writes are a few dozen bytes and happen at most a handful of times per day.
 */
class TriggerLog(private val file: File) {

    data class Snapshot(
        val lastDeliveredAt: Long = 0L,
        val deliveredCount: Int = 0,
        val lastRingingAt: Long = 0L,
        val lastScreenShownAt: Long = 0L,
    )

    fun read(): Snapshot {
        if (!file.exists()) return Snapshot()
        val values = runCatching {
            file.readLines()
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
                }
                .toMap()
        }.getOrDefault(emptyMap())
        return Snapshot(
            lastDeliveredAt = values[KEY_DELIVERED_AT]?.toLongOrNull() ?: 0L,
            deliveredCount = values[KEY_DELIVERED_COUNT]?.toIntOrNull() ?: 0,
            lastRingingAt = values[KEY_RINGING_AT]?.toLongOrNull() ?: 0L,
            lastScreenShownAt = values[KEY_SCREEN_SHOWN_AT]?.toLongOrNull() ?: 0L,
        )
    }

    /** Called as soon as the system delivers an alarm to the service (or legacy receiver). */
    fun recordDelivered(now: Long = System.currentTimeMillis()) {
        val current = read()
        write(
            current.copy(
                lastDeliveredAt = now,
                deliveredCount = current.deliveredCount + 1,
            ),
        )
    }

    /** Called once the ringing session is actually running. */
    fun recordRinging(now: Long = System.currentTimeMillis()) {
        write(read().copy(lastRingingAt = now))
    }

    /** Only the ringing activity with window focus may confirm that the full screen was shown. */
    fun recordScreenShown(now: Long = System.currentTimeMillis()) {
        write(read().copy(lastScreenShownAt = now))
    }

    private fun write(snapshot: Snapshot) {
        runCatching {
            JsonStore(file).write(
                buildString {
                    appendLine("$KEY_DELIVERED_AT=${snapshot.lastDeliveredAt}")
                    appendLine("$KEY_DELIVERED_COUNT=${snapshot.deliveredCount}")
                    appendLine("$KEY_RINGING_AT=${snapshot.lastRingingAt}")
                    appendLine("$KEY_SCREEN_SHOWN_AT=${snapshot.lastScreenShownAt}")
                },
            )
        }
    }

    private companion object {
        const val KEY_DELIVERED_AT = "deliveredAt"
        const val KEY_DELIVERED_COUNT = "deliveredCount"
        const val KEY_RINGING_AT = "ringingAt"
        const val KEY_SCREEN_SHOWN_AT = "screenShownAt"
    }
}
