package com.vocalarm.app.domain

/** Loop decisions for one ringing session. */
object LoopPolicy {

    /**
     * @param completedRounds number of full play-throughs that already finished (>= 0)
     */
    fun shouldContinue(mode: LoopMode, times: Int, completedRounds: Int): Boolean {
        val completed = completedRounds.coerceAtLeast(0)
        return when (mode) {
            LoopMode.Once -> false
            LoopMode.Times -> completed < times.coerceIn(1, Alarm.MAX_LOOP_COUNT)
            LoopMode.Continuous -> true
        }
    }

    /** Null means "until dismissed". */
    fun plannedRounds(mode: LoopMode, times: Int): Int? = when (mode) {
        LoopMode.Once -> 1
        LoopMode.Times -> times.coerceIn(1, Alarm.MAX_LOOP_COUNT)
        LoopMode.Continuous -> null
    }
}

/** Linear fade-in gain. Session elapsed time is passed in, so looping never restarts the fade. */
object FadeGain {
    fun gainAt(elapsedMillis: Long, fadeMillis: Long, target: Float): Float {
        val safeTarget = target.coerceIn(0f, 1f)
        if (fadeMillis <= 0L) return safeTarget
        if (elapsedMillis <= 0L) return 0f
        if (elapsedMillis >= fadeMillis) return safeTarget
        return safeTarget * (elapsedMillis.toFloat() / fadeMillis.toFloat()).coerceIn(0f, 1f)
    }

    fun fadeMillis(seconds: Int): Long = seconds.coerceIn(0, Alarm.MAX_FADE_SECONDS) * 1000L
}
