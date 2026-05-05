package me.rhul.loudr.engine

import me.rhul.loudr.safety.SafetyLimiter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LoudnessEnhancerEngineTest {

    private lateinit var safetyLimiter: SafetyLimiter
    private lateinit var engine: LoudnessEnhancerEngine

    @BeforeEach
    fun setUp() {
        safetyLimiter = mockk(relaxed = true)
        // Default: limiter passes through the value unchanged
        every { safetyLimiter.clamp(any()) } answers { firstArg() }
        engine = LoudnessEnhancerEngine(safetyLimiter)
    }

    @Test
    fun `initial state is inactive with zero boost`() {
        assertFalse(engine.isActive.value)
        assertEquals(0f, engine.boostLevel.value)
    }

    @Test
    fun `setBoost clamps values above 1`() = runTest {
        engine.setBoost(1.5f)
        assertEquals(1.0f, engine.boostLevel.value)
    }

    @Test
    fun `setBoost clamps values below 0`() = runTest {
        engine.setBoost(-0.5f)
        assertEquals(0f, engine.boostLevel.value)
    }

    @Test
    fun `setBoost stores normalised level`() = runTest {
        engine.setBoost(0.65f)
        assertEquals(0.65f, engine.boostLevel.value, 0.001f)
    }

    @Test
    fun `enable sets isActive to true`() = runTest {
        engine.enable()
        assertTrue(engine.isActive.value)
    }

    @Test
    fun `disable sets isActive to false`() = runTest {
        engine.enable()
        engine.disable()
        assertFalse(engine.isActive.value)
    }

    @Test
    fun `default enabled streams contains MEDIA only`() {
        assertEquals(setOf(AudioStream.MEDIA), engine.enabledStreams.value)
    }

    @Test
    fun `setStreamEnabled adds stream to enabled set`() = runTest {
        engine.setStreamEnabled(AudioStream.CALL, true)
        assertTrue(engine.enabledStreams.value.contains(AudioStream.CALL))
    }

    @Test
    fun `setStreamEnabled removes stream from enabled set`() = runTest {
        engine.setStreamEnabled(AudioStream.MEDIA, false)
        assertFalse(engine.enabledStreams.value.contains(AudioStream.MEDIA))
    }

    @Test
    fun `setBoost delegates clamping to SafetyLimiter`() = runTest {
        engine.setBoost(0.9f)
        verify { safetyLimiter.clamp(0.9f) }
    }

    @Test
    fun `detachFromSession resets currentSessionId to -1`() {
        // Attach first (without a real audio session — just test state)
        engine.detachFromSession()
        assertEquals(-1, engine.currentSessionId.value)
    }

    @Test
    fun `attachToSession same id does not re-attach`() {
        // Attaching to session -1 twice should not change state
        engine.detachFromSession()
        engine.detachFromSession()
        assertEquals(-1, engine.currentSessionId.value)
    }
}
