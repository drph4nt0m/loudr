package me.rhul.loudr.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed preference keys — all keys in one place to avoid typos.
 */
object PreferenceKeys {
    val BOOST_LEVEL                = floatPreferencesKey("boost_level")
    val IS_ENABLED                 = booleanPreferencesKey("is_enabled")
    val SAFETY_LIMITER_ENABLED     = booleanPreferencesKey("safety_limiter_enabled")
    val SAFETY_CEILING_DB          = floatPreferencesKey("safety_ceiling_db")
    val AUTO_BOOST_ON_HEADPHONE    = booleanPreferencesKey("auto_boost_on_headphone")
    val THEME                      = stringPreferencesKey("theme")
    val BASS_BOOST_ENABLED         = booleanPreferencesKey("bass_boost_enabled")
    val BASS_BOOST_LEVEL           = floatPreferencesKey("bass_boost_level")
    val STREAM_MEDIA_ENABLED       = booleanPreferencesKey("stream_media_enabled")
    val STREAM_CALL_ENABLED        = booleanPreferencesKey("stream_call_enabled")
    val STREAM_NOTIFICATION_ENABLED = booleanPreferencesKey("stream_notification_enabled")
    val STREAM_ALARM_ENABLED       = booleanPreferencesKey("stream_alarm_enabled")
    val FLOATING_OVERLAY_ENABLED   = booleanPreferencesKey("floating_overlay_enabled")
    val VISUALIZER_ENABLED         = booleanPreferencesKey("visualizer_enabled")
}

/**
 * Repository wrapping [DataStore<Preferences>] with typed Flow reads and
 * suspend writes for all user preferences.
 *
 * Default values are defined inline — no serialiser or generated class needed.
 */
@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    val boostLevel:                Flow<Float>   = prefs.map { it[PreferenceKeys.BOOST_LEVEL] ?: 0f }
    val isEnabled:                 Flow<Boolean> = prefs.map { it[PreferenceKeys.IS_ENABLED] ?: false }
    val safetyLimiterEnabled:      Flow<Boolean> = prefs.map { it[PreferenceKeys.SAFETY_LIMITER_ENABLED] ?: true }
    val safetyCeilingDb:           Flow<Float>   = prefs.map { it[PreferenceKeys.SAFETY_CEILING_DB] ?: 6f }
    val autoBoostOnHeadphone:      Flow<Boolean> = prefs.map { it[PreferenceKeys.AUTO_BOOST_ON_HEADPHONE] ?: false }
    val theme:                     Flow<String>  = prefs.map { it[PreferenceKeys.THEME] ?: "dynamic" }
    val bassBoostEnabled:          Flow<Boolean> = prefs.map { it[PreferenceKeys.BASS_BOOST_ENABLED] ?: false }
    val bassBoostLevel:            Flow<Float>   = prefs.map { it[PreferenceKeys.BASS_BOOST_LEVEL] ?: 0f }
    val streamMediaEnabled:        Flow<Boolean> = prefs.map { it[PreferenceKeys.STREAM_MEDIA_ENABLED] ?: true }
    val streamCallEnabled:         Flow<Boolean> = prefs.map { it[PreferenceKeys.STREAM_CALL_ENABLED] ?: false }
    val streamNotificationEnabled: Flow<Boolean> = prefs.map { it[PreferenceKeys.STREAM_NOTIFICATION_ENABLED] ?: false }
    val streamAlarmEnabled:        Flow<Boolean> = prefs.map { it[PreferenceKeys.STREAM_ALARM_ENABLED] ?: false }
    val floatingOverlayEnabled:    Flow<Boolean> = prefs.map { it[PreferenceKeys.FLOATING_OVERLAY_ENABLED] ?: false }
    val visualizerEnabled:         Flow<Boolean> = prefs.map { it[PreferenceKeys.VISUALIZER_ENABLED] ?: false }

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    suspend fun setBoostLevel(level: Float)             = dataStore.edit { it[PreferenceKeys.BOOST_LEVEL] = level }
    suspend fun setEnabled(enabled: Boolean)            = dataStore.edit { it[PreferenceKeys.IS_ENABLED] = enabled }
    suspend fun setSafetyLimiterEnabled(enabled: Boolean) = dataStore.edit { it[PreferenceKeys.SAFETY_LIMITER_ENABLED] = enabled }
    suspend fun setSafetyCeilingDb(db: Float)           = dataStore.edit { it[PreferenceKeys.SAFETY_CEILING_DB] = db }
    suspend fun setAutoBoostOnHeadphone(enabled: Boolean) = dataStore.edit { it[PreferenceKeys.AUTO_BOOST_ON_HEADPHONE] = enabled }
    suspend fun setTheme(theme: String)                 = dataStore.edit { it[PreferenceKeys.THEME] = theme }
    suspend fun setBassBoostEnabled(enabled: Boolean)   = dataStore.edit { it[PreferenceKeys.BASS_BOOST_ENABLED] = enabled }
    suspend fun setBassBoostLevel(level: Float)         = dataStore.edit { it[PreferenceKeys.BASS_BOOST_LEVEL] = level }
    suspend fun setStreamMediaEnabled(enabled: Boolean) = dataStore.edit { it[PreferenceKeys.STREAM_MEDIA_ENABLED] = enabled }
    suspend fun setStreamCallEnabled(enabled: Boolean)  = dataStore.edit { it[PreferenceKeys.STREAM_CALL_ENABLED] = enabled }
    suspend fun setStreamNotificationEnabled(enabled: Boolean) = dataStore.edit { it[PreferenceKeys.STREAM_NOTIFICATION_ENABLED] = enabled }
    suspend fun setStreamAlarmEnabled(enabled: Boolean) = dataStore.edit { it[PreferenceKeys.STREAM_ALARM_ENABLED] = enabled }
    suspend fun setFloatingOverlayEnabled(enabled: Boolean) = dataStore.edit { it[PreferenceKeys.FLOATING_OVERLAY_ENABLED] = enabled }
    suspend fun setVisualizerEnabled(enabled: Boolean)  = dataStore.edit { it[PreferenceKeys.VISUALIZER_ENABLED] = enabled }
}
