package me.rhul.loudr.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rhul.loudr.data.PreferencesRepository
import me.rhul.loudr.engine.AudioEngineRepository
import me.rhul.loudr.engine.BoostController
import me.rhul.loudr.safety.SafetyEvent
import me.rhul.loudr.safety.SafetyLimiter
import javax.inject.Inject

data class MainUiState(
    // Boost engine state
    val boostLevel:           Float        = 0f,
    val isActive:             Boolean      = false,
    val safetyEvent:          SafetyEvent? = null,
    // Safety / audio settings
    val safetyLimiterEnabled: Boolean      = true,
    val bassBoostEnabled:     Boolean      = false,
    val autoBoostOnHeadphone: Boolean      = false,
    val notificationEnabled:  Boolean      = true,
    // Appearance
    val theme:                String       = "dynamic",
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val engine:          AudioEngineRepository,
    private val prefs:           PreferencesRepository,
    private val safety:          SafetyLimiter,
    private val boostController: BoostController,
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

    init {
        viewModelScope.launch {
            boostController.ensureHydrated()
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

    fun dismissSafetyEvent() {
        safety.clearEvent()
    }

    // -------------------------------------------------------------------------
    // Settings actions
    // -------------------------------------------------------------------------

    fun setSafetyLimiterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            boostController.setSafetyLimiterEnabled(enabled)
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        viewModelScope.launch {
            boostController.setBassBoostEnabled(enabled)
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
