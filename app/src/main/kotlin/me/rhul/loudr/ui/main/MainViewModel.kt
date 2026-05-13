package me.rhul.loudr.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.rhul.loudr.data.PreferencesRepository
import me.rhul.loudr.engine.AudioEngineRepository
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
    val safetyEvent:          me.rhul.loudr.safety.SafetyEvent? = null,
    // Safety / audio settings
    val safetyLimiterEnabled: Boolean          = true,
    val bassBoostEnabled:     Boolean          = false,
    val autoBoostOnHeadphone: Boolean          = false,
    val notificationEnabled:  Boolean          = true,
    // Appearance
    val theme:                String           = "dynamic",
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val engine:      AudioEngineRepository,
    private val bassEngine:  DynamicsProcessorEngine,
    private val prefs:       PreferencesRepository,
    private val safety:      SafetyLimiter,
    private val boostController: me.rhul.loudr.engine.BoostController,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = combine(
        // Group 1: engine + safety flows (max 5 in one combine)
        combine(
            engine.boostLevel,
            engine.isActive,
            safety.safetyEvents,
            safety.limiterEnabled,
        ) { boostLevel, isActive, safetyEvent, limiterEnabled ->
            MainUiState(
                boostLevel           = boostLevel,
                isActive             = isActive,
                safetyEvent          = safetyEvent,
                safetyLimiterEnabled = limiterEnabled,
            )
        },
        // Group 2: prefs-based settings
        prefs.bassBoostEnabled,
        prefs.autoBoostOnHeadphone,
        prefs.notificationEnabled,
        prefs.theme,
    ) { partial, bassEnabled, autoBoost, notifEnabled, theme ->
        partial.copy(
            bassBoostEnabled     = bassEnabled,
            autoBoostOnHeadphone = autoBoost,
            notificationEnabled  = notifEnabled,
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
            val limiterOn    = prefs.safetyLimiterEnabled.first()
            val bassEnabled  = prefs.bassBoostEnabled.first()
            val bassLevel    = prefs.bassBoostLevel.first()

            safety.setLimiterEnabled(limiterOn)

            engine.setBoost(level)
            // H-6: If the stored level was above the active ceiling it was clamped;
            // persist the corrected value so DataStore never holds a stale boosted level.
            val stored = engine.boostLevel.value
            if (stored != level) prefs.setBoostLevel(stored)

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
            boostController.setBoost(level)
        }
    }

    fun toggleActive() {
        viewModelScope.launch {
            boostController.toggleActive()
        }
    }

    // Removed toggleStream

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
            // Re-clamp the current boost to the new ceiling and persist the result.
            // H-1: Without persisting here, a value above the re-enabled ceiling
            // would remain in DataStore and be restored incorrectly on next launch.
            engine.setBoost(engine.boostLevel.value)
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

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotificationEnabled(enabled) }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { prefs.setTheme(theme) }
    }
}
