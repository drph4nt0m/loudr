package me.rhul.loudr.safety

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SafetyLimiterTest {

    private lateinit var limiter: SafetyLimiter

    @BeforeEach
    fun setUp() {
        limiter = SafetyLimiter()
    }

    @Test
    fun `default ceiling is 0_5`() {
        assertEquals(SafetyLimiter.DEFAULT_CEILING, limiter.ceiling.value, 0.001f)
    }

    @Test
    fun `clamp returns value when below ceiling`() {
        val result = limiter.clamp(0.5f)
        assertEquals(0.5f, result, 0.001f)
    }

    @Test
    fun `clamp caps value at default ceiling`() {
        val result = limiter.clamp(1.0f)
        assertEquals(SafetyLimiter.DEFAULT_CEILING, result, 0.001f)
    }

    @Test
    fun `clamp returns 0 for negative input`() {
        val result = limiter.clamp(-0.1f)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `expert mode raises ceiling to EXPERT_CEILING`() {
        limiter.enableExpertMode()
        assertEquals(SafetyLimiter.EXPERT_CEILING, limiter.ceiling.value, 0.001f)
    }

    @Test
    fun `expert mode allows values up to 1_0`() {
        limiter.enableExpertMode()
        val result = limiter.clamp(1.0f)
        assertEquals(1.0f, result, 0.001f)
    }

    @Test
    fun `disableExpertMode restores default ceiling`() {
        limiter.enableExpertMode()
        limiter.disableExpertMode()
        assertEquals(SafetyLimiter.DEFAULT_CEILING, limiter.ceiling.value, 0.001f)
    }

    @Test
    fun `setCeiling is ignored when not in expert mode`() {
        limiter.setCeiling(0.5f)
        // Should still be the default
        assertEquals(SafetyLimiter.DEFAULT_CEILING, limiter.ceiling.value, 0.001f)
    }

    @Test
    fun `setCeiling works in expert mode`() {
        limiter.enableExpertMode()
        limiter.setCeiling(0.95f)
        assertEquals(0.95f, limiter.ceiling.value, 0.001f)
    }

    @Test
    fun `no exposure warning emitted on first tick`() {
        limiter.recordExposureTick(0.8f)
        assertNull(limiter.safetyEvents.value)
    }

    @Test
    fun `no exposure warning when level below threshold`() {
        // LOUD_THRESHOLD = 0.3f and the condition is >=, so use 0.29f (strictly below)
        repeat(100) { limiter.recordExposureTick(0.29f) }
        assertNull(limiter.safetyEvents.value)
    }

    @Test
    fun `clearEvent resets safety event to null`() {
        limiter.recordExposureTick(0.9f)
        limiter.clearEvent()
        assertNull(limiter.safetyEvents.value)
    }

    @Test
    fun `expert mode is false by default`() {
        assertFalse(limiter.expertMode.value)
    }

    @Test
    fun `expert mode is true after enable`() {
        limiter.enableExpertMode()
        assertTrue(limiter.expertMode.value)
    }

    @Test
    fun `setLimiterEnabled false raises ceiling and true restores default and resets expertMode`() {
        limiter.enableExpertMode()
        limiter.setLimiterEnabled(false)
        assertFalse(limiter.limiterEnabled.value)
        assertEquals(SafetyLimiter.EXPERT_CEILING, limiter.ceiling.value, 0.001f)
        assertEquals(1.0f, limiter.clamp(1.0f), 0.001f)

        limiter.setLimiterEnabled(true)
        assertTrue(limiter.limiterEnabled.value)
        assertFalse(limiter.expertMode.value)
        assertEquals(SafetyLimiter.DEFAULT_CEILING, limiter.ceiling.value, 0.001f)
        assertEquals(SafetyLimiter.DEFAULT_CEILING, limiter.clamp(1.0f), 0.001f)
    }

    @Test
    fun `recordExposureTick fires ExposureWarning after 30 minutes at or above threshold`() {
        var nowMs = 1_000_000L
        val timedLimiter = SafetyLimiter(monotonicClockMs = { nowMs })

        timedLimiter.recordExposureTick(SafetyLimiter.LOUD_THRESHOLD)
        assertNull(timedLimiter.safetyEvents.value)

        nowMs += 29 * 60_000L
        timedLimiter.recordExposureTick(SafetyLimiter.LOUD_THRESHOLD)
        assertNull(timedLimiter.safetyEvents.value)

        nowMs += 60_000L
        timedLimiter.recordExposureTick(SafetyLimiter.LOUD_THRESHOLD)
        val event = timedLimiter.safetyEvents.value
        assertTrue(event is SafetyEvent.ExposureWarning)
        assertEquals(30L, (event as SafetyEvent.ExposureWarning).minutes)
    }
}
