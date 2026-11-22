package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.models.AppPreferences
import com.example.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pxreminder_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_SOUND_URI = stringPreferencesKey("default_sound_uri")
        val DEFAULT_SOUND_TITLE = stringPreferencesKey("default_sound_title")
        val DEFAULT_VOLUME = floatPreferencesKey("default_volume")
        val DEFAULT_SNOOZE_MINUTES = intPreferencesKey("default_snooze_minutes")
        val DEFAULT_VIBRATION = booleanPreferencesKey("default_vibration")
        val GRADUAL_VOLUME = booleanPreferencesKey("gradual_volume")
        val SNOOZE_PLAY_SOUND = booleanPreferencesKey("snooze_play_sound")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE]),
            defaultSoundUri = prefs[Keys.DEFAULT_SOUND_URI] ?: "builtin:classic_alarm",
            defaultSoundTitle = prefs[Keys.DEFAULT_SOUND_TITLE] ?: "Classic Alarm",
            defaultAlarmVolume = prefs[Keys.DEFAULT_VOLUME] ?: 0.85f,
            defaultSnoozeMinutes = prefs[Keys.DEFAULT_SNOOZE_MINUTES] ?: 10,
            defaultVibrationEnabled = prefs[Keys.DEFAULT_VIBRATION] ?: true,
            gradualVolumeIncrease = prefs[Keys.GRADUAL_VOLUME] ?: true,
            snoozePlaySound = prefs[Keys.SNOOZE_PLAY_SOUND] ?: true,
            use24HourFormat = prefs[Keys.USE_24_HOUR] ?: false,
            firstDayOfWeek = prefs[Keys.FIRST_DAY_OF_WEEK] ?: 1
        )
    }

    suspend fun getSnapshot(): AppPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultSound(uri: String, title: String) {
        context.dataStore.edit {
            it[Keys.DEFAULT_SOUND_URI] = uri
            it[Keys.DEFAULT_SOUND_TITLE] = title
        }
    }

    suspend fun setDefaultVolume(volume: Float) {
        context.dataStore.edit { it[Keys.DEFAULT_VOLUME] = volume.coerceIn(0.1f, 1.0f) }
    }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_SNOOZE_MINUTES] = minutes.coerceIn(1, 60) }
    }

    suspend fun setDefaultVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_VIBRATION] = enabled }
    }

    suspend fun setGradualVolumeIncrease(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GRADUAL_VOLUME] = enabled }
    }

    suspend fun setSnoozePlaySound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SNOOZE_PLAY_SOUND] = enabled }
    }

    suspend fun setUse24HourFormat(use24Hour: Boolean) {
        context.dataStore.edit { it[Keys.USE_24_HOUR] = use24Hour }
    }

    suspend fun setFirstDayOfWeek(dayOfWeekValue: Int) {
        context.dataStore.edit { it[Keys.FIRST_DAY_OF_WEEK] = dayOfWeekValue }
    }

    suspend fun applyBackupPreferences(prefs: AppPreferences) {
        context.dataStore.edit {
            it[Keys.THEME_MODE] = prefs.themeMode.name
            it[Keys.DEFAULT_SOUND_URI] = prefs.defaultSoundUri
            it[Keys.DEFAULT_SOUND_TITLE] = prefs.defaultSoundTitle
            it[Keys.DEFAULT_VOLUME] = prefs.defaultAlarmVolume.coerceIn(0.1f, 1.0f)
            it[Keys.DEFAULT_SNOOZE_MINUTES] = prefs.defaultSnoozeMinutes.coerceIn(1, 60)
            it[Keys.DEFAULT_VIBRATION] = prefs.defaultVibrationEnabled
            it[Keys.GRADUAL_VOLUME] = prefs.gradualVolumeIncrease
            it[Keys.SNOOZE_PLAY_SOUND] = prefs.snoozePlaySound
            it[Keys.USE_24_HOUR] = prefs.use24HourFormat
            it[Keys.FIRST_DAY_OF_WEEK] = prefs.firstDayOfWeek
        }
    }
}
