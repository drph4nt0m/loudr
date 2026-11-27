package me.rhul.loudr.engine

import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.rhul.loudr.data.PreferencesRepository
import me.rhul.loudr.safety.SafetyLimiter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoostControllerTest {

    private lateinit var safety: SafetyLimiter
    private lateinit var engine: LoudnessEnhancerEngine
    private lateinit var bassEngine: DynamicsProcessorEngine
    private lateinit var prefs: PreferencesRepository

    private val storedBoostLevel = MutableStateFlow(0f)
    private val storedEnabled = MutableStateFlow(false)
    private val storedLimiter = MutableStateFlow(true)
    private val storedAutoBoost = MutableStateFlow(false)
    private val storedBassEnabled = MutableStateFlow(false)
    private val storedBassLevel = MutableStateFlow(DynamicsProcessorEngine.DEFAULT_BASS_LEVEL)

    @BeforeEach
    fun setUp() {
        safety = SafetyLimiter(monotonicClockMs = { 0L })
        engine = LoudnessEnhancerEngine(safety)
        bassEngine = mockk(relaxed = true)
        prefs = mockk(relaxed = true)

        every { prefs.boostLevel } returns storedBoostLevel
        every { prefs.isEnabled } returns storedEnabled
        every { prefs.safetyLimiterEnabled } returns storedLimiter
        every { prefs.autoBoostOnHeadphone } returns storedAutoBoost
        every { prefs.bassBoostEnabled } returns storedBassEnabled
        every { prefs.bassBoostLevel } returns storedBassLevel

        coEvery { prefs.setBoostLevel(any()) } answers { storedBoostLevel.value = firstArg(); emptyPreferences() }
        coEvery { prefs.setEnabled(any()) } answers { storedEnabled.value = firstArg(); emptyPreferences() }
        coEvery { prefs.setSafetyLimiterEnabled(any()) } answers { storedLimiter.value = firstArg(); emptyPreferences() }
        coEvery { prefs.setBassBoostEnabled(any()) } answers { storedBassEnabled.value = firstArg(); emptyPreferences() }
    }

    @Test
    fun `setBoost persists clamped level instead of raw input`() = runTest {
        val controller = BoostController(engine, bassEngine, prefs, safety, backgroundScope)

        controller.setBoost(0.95f)

        assertEquals(SafetyLimiter.DEFAULT_CEILING, engine.boostLevel.value, 0.001f)
        coVerify { prefs.setBoostLevel(SafetyLimiter.DEFAULT_CEILING) }
    }

    @Test
    fun `toggleActive on cold start hydrates persisted boost level and bass boost`() = runTest {
        storedBoostLevel.value = 0.4f
        storedEnabled.value = false
        storedBassEnabled.value = true
        storedBassLevel.value = 0.7f

        val controller = BoostController(engine, bassEngine, prefs, safety, backgroundScope)

        controller.toggleActive()

        assertTrue(engine.isActive.value)
        assertEquals(0.4f, engine.boostLevel.value, 0.001f)
        verify { bassEngine.attachToSession(0) }
        verify { bassEngine.setBassBoost(0.7f) }
        verify { bassEngine.setEnabled(true) }
    }

    @Test
    fun `toggleActive off disables and detaches bass engine`() = runTest {
        storedBassEnabled.value = true
        val controller = BoostController(engine, bassEngine, prefs, safety, backgroundScope)

        controller.toggleActive() // on
        controller.toggleActive() // off

        assertFalse(engine.isActive.value)
        verify { bassEngine.setEnabled(false) }
        verify { bassEngine.detachFromSession() }
        coVerify { prefs.setEnabled(false) }
    }

    @Test
    fun `onAudioDeviceConnected activates boost when autoBoostOnHeadphone is enabled`() = runTest {
        storedBoostLevel.value = 0.35f
        storedAutoBoost.value = true
        val controller = BoostController(engine, bassEngine, prefs, safety, backgroundScope)

        controller.onAudioDeviceConnected()

        assertTrue(engine.isActive.value)
        assertEquals(0, engine.currentSessionId.value)
        assertEquals(0.35f, engine.boostLevel.value, 0.001f)
        coVerify { prefs.setEnabled(true) }
    }

    @Test
    fun `onAudioDeviceConnected does nothing when autoBoostOnHeadphone is disabled`() = runTest {
        storedAutoBoost.value = false
        val controller = BoostController(engine, bassEngine, prefs, safety, backgroundScope)

        controller.onAudioDeviceConnected()

        assertFalse(engine.isActive.value)
        assertEquals(-1, engine.currentSessionId.value)
    }

    @Test
    fun `onAudioDeviceDisconnected disables and detaches both engines`() = runTest {
        storedBassEnabled.value = true
        val controller = BoostController(engine, bassEngine, prefs, safety, backgroundScope)

        controller.toggleActive()
        controller.onAudioDeviceDisconnected()

        assertFalse(engine.isActive.value)
        assertEquals(-1, engine.currentSessionId.value)
        verify { bassEngine.setEnabled(false) }
        verify { bassEngine.detachFromSession() }
        coVerify { prefs.setEnabled(false) }
    }
}
