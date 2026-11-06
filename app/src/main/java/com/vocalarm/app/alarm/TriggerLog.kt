package com.vocalarm.app.alarm

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
        )
    }

    /** Called from the broadcast receiver, before anything else can fail. */
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

    private fun write(snapshot: Snapshot) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    appendLine("$KEY_DELIVERED_AT=${snapshot.lastDeliveredAt}")
                    appendLine("$KEY_DELIVERED_COUNT=${snapshot.deliveredCount}")
                    appendLine("$KEY_RINGING_AT=${snapshot.lastRingingAt}")
                },
            )
        }
    }

    private companion object {
        const val KEY_DELIVERED_AT = "deliveredAt"
        const val KEY_DELIVERED_COUNT = "deliveredCount"
        const val KEY_RINGING_AT = "ringingAt"
    }
}
