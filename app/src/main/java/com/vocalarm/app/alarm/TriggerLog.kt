package com.vocalarm.app.alarm

import com.vocalarm.app.data.JsonStore
import java.io.File

/**
 * Tiny on-disk record of the alarms the system actually delivered to this app.
 *
 * It exists so the in-app self check can tell two very different failures apart: "the OS never
 * called us" (force stop, vendor background kill) versus "we were called but the ring failed".
 * Atomic writes preserve the evidence across process death and subsequent launcher recovery.
 */
class TriggerLog(private val file: File) {

    enum class DeliverySource { Broadcast, Service }

    data class Snapshot(
        val lastDeliveredAt: Long = 0L,
        val deliveredCount: Int = 0,
        val lastRingingAt: Long = 0L,
        val lastScreenShownAt: Long = 0L,
        val lastScheduledAt: Long = 0L,
        val lastDeliveryToken: String = "",
        val lastDeliverySource: DeliverySource? = null,
        val ringingCount: Int = 0,
        val lastRecoveryAt: Long = 0L,
        val lastRecoveredScheduledAt: Long = 0L,
        val lastRecoverySource: String = "",
        val lastRecoveredToken: String = "",
        val lastAppOpenedAt: Long = 0L,
        val lastTaskRemovedAt: Long = 0L,
        val lastFailureAt: Long = 0L,
        val lastFailure: String = "",
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
            lastScheduledAt = values[KEY_SCHEDULED_AT]?.toLongOrNull() ?: 0L,
            lastDeliveryToken = values[KEY_DELIVERY_TOKEN].orEmpty(),
            lastDeliverySource = DeliverySource.entries.firstOrNull { it.name == values[KEY_DELIVERY_SOURCE] },
            ringingCount = values[KEY_RINGING_COUNT]?.toIntOrNull() ?: 0,
            lastRecoveryAt = values[KEY_RECOVERY_AT]?.toLongOrNull() ?: 0L,
            lastRecoveredScheduledAt = values[KEY_RECOVERED_SCHEDULED_AT]?.toLongOrNull() ?: 0L,
            lastRecoverySource = values[KEY_RECOVERY_SOURCE].orEmpty(),
            lastRecoveredToken = values[KEY_RECOVERED_TOKEN].orEmpty(),
            lastAppOpenedAt = values[KEY_APP_OPENED_AT]?.toLongOrNull() ?: 0L,
            lastTaskRemovedAt = values[KEY_TASK_REMOVED_AT]?.toLongOrNull() ?: 0L,
            lastFailureAt = values[KEY_FAILURE_AT]?.toLongOrNull() ?: 0L,
            lastFailure = values[KEY_FAILURE].orEmpty(),
        )
    }

    /** Both entry points report delivery; the same token keeps the first arrival and counts once. */
    fun recordDelivered(
        now: Long = System.currentTimeMillis(),
        token: String = "",
        scheduledAt: Long = 0L,
        source: DeliverySource? = null,
    ) {
        val current = read()
        if (token.isNotBlank() && token == current.lastDeliveryToken) return
        write(
            current.copy(
                lastDeliveredAt = now,
                deliveredCount = current.deliveredCount + 1,
                lastScheduledAt = scheduledAt,
                lastDeliveryToken = token,
                lastDeliverySource = source,
            ),
        )
    }

    /** Called once the ringing session is actually running. */
    fun recordRinging(now: Long = System.currentTimeMillis()) {
        val current = read()
        write(current.copy(lastRingingAt = now, ringingCount = current.ringingCount + 1))
    }

    /** Only the ringing activity with window focus may confirm that the full screen was shown. */
    fun recordScreenShown(now: Long = System.currentTimeMillis()) {
        write(read().copy(lastScreenShownAt = now))
    }

    /** Only records overdue occurrences; a recovery must not be mistaken for on-time delivery. */
    fun recordRecovery(scheduledAt: Long, source: String, now: Long = System.currentTimeMillis(), token: String = "") {
        if (scheduledAt >= now) return
        val current = read()
        // A job/boot callback may restore it again before delivery. Keep the original cause.
        if (token.isNotBlank() && token == current.lastRecoveredToken) return
        write(current.copy(
            lastRecoveryAt = now,
            lastRecoveredScheduledAt = scheduledAt,
            lastRecoverySource = source,
            lastRecoveredToken = token,
        ))
    }

    fun recordAppOpened(now: Long = System.currentTimeMillis()) {
        write(read().copy(lastAppOpenedAt = now))
    }

    fun recordTaskRemoved(now: Long = System.currentTimeMillis()) {
        write(read().copy(lastTaskRemovedAt = now))
    }

    fun recordFailure(stage: String, error: Throwable, now: Long = System.currentTimeMillis()) {
        val message = "$stage: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
            .replace('\n', ' ').replace('\r', ' ').take(600)
        write(read().copy(lastFailureAt = now, lastFailure = message))
    }

    private fun write(snapshot: Snapshot) {
        runCatching {
            JsonStore(file).write(
                buildString {
                    appendLine("$KEY_DELIVERED_AT=${snapshot.lastDeliveredAt}")
                    appendLine("$KEY_DELIVERED_COUNT=${snapshot.deliveredCount}")
                    appendLine("$KEY_RINGING_AT=${snapshot.lastRingingAt}")
                    appendLine("$KEY_SCREEN_SHOWN_AT=${snapshot.lastScreenShownAt}")
                    appendLine("$KEY_SCHEDULED_AT=${snapshot.lastScheduledAt}")
                    appendLine("$KEY_DELIVERY_TOKEN=${snapshot.lastDeliveryToken}")
                    appendLine("$KEY_DELIVERY_SOURCE=${snapshot.lastDeliverySource?.name.orEmpty()}")
                    appendLine("$KEY_RINGING_COUNT=${snapshot.ringingCount}")
                    appendLine("$KEY_RECOVERY_AT=${snapshot.lastRecoveryAt}")
                    appendLine("$KEY_RECOVERED_SCHEDULED_AT=${snapshot.lastRecoveredScheduledAt}")
                    appendLine("$KEY_RECOVERY_SOURCE=${snapshot.lastRecoverySource}")
                    appendLine("$KEY_RECOVERED_TOKEN=${snapshot.lastRecoveredToken}")
                    appendLine("$KEY_APP_OPENED_AT=${snapshot.lastAppOpenedAt}")
                    appendLine("$KEY_TASK_REMOVED_AT=${snapshot.lastTaskRemovedAt}")
                    appendLine("$KEY_FAILURE_AT=${snapshot.lastFailureAt}")
                    appendLine("$KEY_FAILURE=${snapshot.lastFailure}")
                },
            )
        }
    }

    private companion object {
        const val KEY_DELIVERED_AT = "deliveredAt"
        const val KEY_DELIVERED_COUNT = "deliveredCount"
        const val KEY_RINGING_AT = "ringingAt"
        const val KEY_SCREEN_SHOWN_AT = "screenShownAt"
        const val KEY_SCHEDULED_AT = "scheduledAt"
        const val KEY_DELIVERY_TOKEN = "deliveryToken"
        const val KEY_DELIVERY_SOURCE = "deliverySource"
        const val KEY_RINGING_COUNT = "ringingCount"
        const val KEY_RECOVERY_AT = "recoveryAt"
        const val KEY_RECOVERED_SCHEDULED_AT = "recoveredScheduledAt"
        const val KEY_RECOVERY_SOURCE = "recoverySource"
        const val KEY_RECOVERED_TOKEN = "recoveredToken"
        const val KEY_APP_OPENED_AT = "appOpenedAt"
        const val KEY_TASK_REMOVED_AT = "taskRemovedAt"
        const val KEY_FAILURE_AT = "failureAt"
        const val KEY_FAILURE = "failure"
    }
}
