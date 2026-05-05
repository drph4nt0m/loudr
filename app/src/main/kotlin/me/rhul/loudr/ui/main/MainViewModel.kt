package me.rhul.loudr.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.rhul.loudr.data.PreferencesRepository
import me.rhul.loudr.engine.AudioEngineRepository
import me.rhul.loudr.engine.AudioStream
import me.rhul.loudr.engine.DynamicsProcessorEngine
import me.rhul.loudr.safety.SafetyLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    // Boost engine state
    val boostLevel:           Float            = 0f,
    val isActive:             Boolean          = false,
    val enabledStreams:        Set<AudioStream> = AudioStream.DEFAULT_ENABLED,
    val safetyEvent:          me.rhul.loudr.safety.SafetyEvent? = null,
    // Safety / audio settings
    val safetyLimiterEnabled: Boolean          = true,
    val bassBoostEnabled:     Boolean          = false,
    val autoBoostOnHeadphone: Boolean          = false,
    // Appearance
    val theme:                String           = "dynamic",
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val engine:      AudioEngineRepository,
    private val bassEngine:  DynamicsProcessorEngine,
    private val prefs:       PreferencesRepository,
    private val safety:      SafetyLimiter,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = combine(
        // Group 1: engine + safety flows (max 5 in one combine)
        combine(
            engine.boostLevel,
            engine.isActive,
            engine.enabledStreams,
            safety.safetyEvents,
            safety.limiterEnabled,
        ) { boostLevel, isActive, streams, safetyEvent, limiterEnabled ->
            MainUiState(
                boostLevel           = boostLevel,
                isActive             = isActive,
                enabledStreams        = streams,
                safetyEvent          = safetyEvent,
                safetyLimiterEnabled = limiterEnabled,
            )
        },
        // Group 2: prefs-based settings
        prefs.bassBoostEnabled,
        prefs.autoBoostOnHeadphone,
        prefs.theme,
    ) { partial, bassEnabled, autoBoost, theme ->
        partial.copy(
            bassBoostEnabled     = bassEnabled,
            autoBoostOnHeadphone = autoBoost,
            theme                = theme,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    // -------------------------------------------------------------------------
    // Restore persisted settings into engine on first launch
    // -------------------------------------------------------------------------

    init {
        viewModelScope.launch {
            val level        = prefs.boostLevel.first()
            val enabled      = prefs.isEnabled.first()
            val mediaEnabled = prefs.streamMediaEnabled.first()
            val callEnabled  = prefs.streamCallEnabled.first()
            val notifEnabled = prefs.streamNotificationEnabled.first()
            val alarmEnabled = prefs.streamAlarmEnabled.first()
            val limiterOn    = prefs.safetyLimiterEnabled.first()
            val bassEnabled  = prefs.bassBoostEnabled.first()
            val bassLevel    = prefs.bassBoostLevel.first()

            safety.setLimiterEnabled(limiterOn)

            engine.setStreamEnabled(AudioStream.MEDIA,        mediaEnabled)
            engine.setStreamEnabled(AudioStream.CALL,         callEnabled)
            engine.setStreamEnabled(AudioStream.NOTIFICATION, notifEnabled)
            engine.setStreamEnabled(AudioStream.ALARM,        alarmEnabled)

            engine.setBoost(level)
            if (enabled) engine.enable()

            if (bassEnabled) {
                bassEngine.attachToSession(engine.currentSessionId.value)
                bassEngine.setEnabled(true)
                bassEngine.setBassBoost(bassLevel)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Boost actions
    // -------------------------------------------------------------------------

    fun setBoost(level: Float) {
        viewModelScope.launch {
            engine.setBoost(level)
            prefs.setBoostLevel(level)
            safety.recordExposureTick(level)
        }
    }

    fun toggleActive() {
        viewModelScope.launch {
            if (engine.isActive.value) {
                engine.disable()
                prefs.setEnabled(false)
            } else {
                engine.enable()
                prefs.setEnabled(true)
            }
        }
    }

    fun toggleStream(stream: AudioStream, enabled: Boolean) {
        viewModelScope.launch {
            engine.setStreamEnabled(stream, enabled)
            when (stream) {
                AudioStream.MEDIA        -> prefs.setStreamMediaEnabled(enabled)
                AudioStream.CALL         -> prefs.setStreamCallEnabled(enabled)
                AudioStream.NOTIFICATION -> prefs.setStreamNotificationEnabled(enabled)
                AudioStream.ALARM        -> prefs.setStreamAlarmEnabled(enabled)
            }
        }
    }

    fun dismissSafetyEvent() {
        safety.clearEvent()
    }

    fun enableExpertMode() {
        safety.enableExpertMode()
    }

    // -------------------------------------------------------------------------
    // Settings actions (formerly in SettingsViewModel)
    // -------------------------------------------------------------------------

    fun setSafetyLimiterEnabled(enabled: Boolean) {
        safety.setLimiterEnabled(enabled)
        viewModelScope.launch {
            prefs.setSafetyLimiterEnabled(enabled)
            // Re-clamp boost level to new ceiling immediately
            val clamped = engine.boostLevel.value
            engine.setBoost(clamped)
            prefs.setBoostLevel(engine.boostLevel.value)
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBassBoostEnabled(enabled)
            if (enabled) {
                bassEngine.attachToSession(engine.currentSessionId.value)
                bassEngine.setEnabled(true)
                bassEngine.setBassBoost(prefs.bassBoostLevel.first())
            } else {
                bassEngine.setEnabled(false)
                bassEngine.detachFromSession()
            }
        }
    }

    fun setAutoBoostOnHeadphone(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoBoostOnHeadphone(enabled) }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { prefs.setTheme(theme) }
    }
}
