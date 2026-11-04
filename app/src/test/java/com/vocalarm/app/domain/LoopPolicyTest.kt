package com.vocalarm.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopPolicyTest {

    @Test
    fun onceStopsAfterOneRound() {
        assertFalse(LoopPolicy.shouldContinue(LoopMode.Once, 3, 1))
        assertEquals(1, LoopPolicy.plannedRounds(LoopMode.Once, 5))
    }

    @Test
    fun timesContinuesUntilCountReached() {
        assertTrue(LoopPolicy.shouldContinue(LoopMode.Times, 3, 0))
        assertTrue(LoopPolicy.shouldContinue(LoopMode.Times, 3, 1))
        assertTrue(LoopPolicy.shouldContinue(LoopMode.Times, 3, 2))
        assertFalse(LoopPolicy.shouldContinue(LoopMode.Times, 3, 3))
        assertFalse(LoopPolicy.shouldContinue(LoopMode.Times, 3, 4))
        assertEquals(3, LoopPolicy.plannedRounds(LoopMode.Times, 3))
    }

    @Test
    fun continuousNeverStops() {
        assertTrue(LoopPolicy.shouldContinue(LoopMode.Continuous, 1, 0))
        assertTrue(LoopPolicy.shouldContinue(LoopMode.Continuous, 1, 99))
        assertNull(LoopPolicy.plannedRounds(LoopMode.Continuous, 1))
    }

    @Test
    fun loopCountIsClampedToValidRange() {
        assertFalse(LoopPolicy.shouldContinue(LoopMode.Times, 0, 1))
        assertTrue(LoopPolicy.shouldContinue(LoopMode.Times, 0, 0))
        assertEquals(Alarm.MAX_LOOP_COUNT, LoopPolicy.plannedRounds(LoopMode.Times, 999))
    }
}

class FadeGainTest {

    @Test
    fun fadeStartsSilentAndReachesTarget() {
        assertEquals(0f, FadeGain.gainAt(elapsedMillis = 0, fadeMillis = 10_000, target = 0.8f), 0.001f)
        assertEquals(0.4f, FadeGain.gainAt(elapsedMillis = 5_000, fadeMillis = 10_000, target = 0.8f), 0.001f)
        assertEquals(0.8f, FadeGain.gainAt(elapsedMillis = 10_000, fadeMillis = 10_000, target = 0.8f), 0.001f)
        assertEquals(0.8f, FadeGain.gainAt(elapsedMillis = 20_000, fadeMillis = 10_000, target = 0.8f), 0.001f)
    }

    @Test
    fun disabledFadeReturnsTargetImmediately() {
        assertEquals(0.7f, FadeGain.gainAt(elapsedMillis = 0, fadeMillis = 0, target = 0.7f), 0.001f)
    }

    @Test
    fun negativeElapsedIsSilent() {
        assertEquals(0f, FadeGain.gainAt(elapsedMillis = -10, fadeMillis = 1_000, target = 1f), 0.001f)
    }

    @Test
    fun fadeMillisHonoursLimit() {
        assertEquals(10_000L, FadeGain.fadeMillis(10))
        assertEquals(0L, FadeGain.fadeMillis(-5))
        assertEquals(Alarm.MAX_FADE_SECONDS * 1000L, FadeGain.fadeMillis(999))
    }
}
