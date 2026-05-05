package me.rhul.loudr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.rhul.loudr.data.PreferencesRepository
import me.rhul.loudr.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Thin ViewModel scoped to the Activity that exposes the persisted [AppTheme]
 * so [MainActivity] can apply it before the first frame is drawn.
 */
@HiltViewModel
class AppViewModel @Inject constructor(prefs: PreferencesRepository) : ViewModel() {

    val appTheme: StateFlow<AppTheme> = prefs.theme
        .map { raw ->
            when (raw) {
                "dark"   -> AppTheme.DARK
                "amoled" -> AppTheme.AMOLED
                else     -> AppTheme.DYNAMIC
            }
        }
        .stateIn(
            scope   = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppTheme.DYNAMIC,
        )
}
