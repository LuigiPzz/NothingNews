package com.nothing.news.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme_preference")
    private val filterKey = stringPreferencesKey("filter_preference")
    private val sortKey = stringPreferencesKey("sort_preference")
    private val browserKey = stringPreferencesKey("browser_preference")
    private val browserPackageKey = stringPreferencesKey("browser_package_preference")
    private val autoMarkReadDaysKey = intPreferencesKey("auto_mark_read_days")
    private val backgroundUpdateFrequencyKey = intPreferencesKey("background_update_frequency")
    private val autoBackupKey = booleanPreferencesKey("auto_backup")
    private val lastAutoBackupTimestampKey = longPreferencesKey("last_auto_backup_timestamp")
    private val remindersKey = stringPreferencesKey("reminders")
    private val geminiApiKeyKey = stringPreferencesKey("gemini_api_key")
    private val ttsLanguageKey = stringPreferencesKey("tts_language")

    val reminders: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[remindersKey] ?: "[]"
    }

    val geminiApiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[geminiApiKeyKey]
    }

    val ttsLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ttsLanguageKey] ?: "Italiano"
    }

    val themePreference: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[themeKey] ?: "System"
    }

    val filterPreference: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[filterKey] ?: "Tutti"
    }

    val sortPreference: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[sortKey] ?: "Decrescente"
    }

    val browserPreference: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[browserKey] ?: "Interno"
    }

    val browserPackagePreference: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[browserPackageKey]
    }

    val autoMarkReadDays: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[autoMarkReadDaysKey] ?: 0 // 0 means disabled
    }

    val backgroundUpdateFrequency: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[backgroundUpdateFrequencyKey] ?: 0 // 0 means disabled
    }

    val autoBackup: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[autoBackupKey] ?: false
    }

    val lastAutoBackupTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[lastAutoBackupTimestampKey] ?: 0L
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[autoBackupKey] = enabled
        }
    }

    suspend fun setLastAutoBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[lastAutoBackupTimestampKey] = timestamp
        }
    }

    suspend fun setBackgroundUpdateFrequency(hours: Int) {
        context.dataStore.edit { preferences ->
            preferences[backgroundUpdateFrequencyKey] = hours
        }
    }

    suspend fun setAutoMarkReadDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[autoMarkReadDaysKey] = days
        }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = theme
        }
    }

    suspend fun setFilter(filter: String) {
        context.dataStore.edit { preferences ->
            preferences[filterKey] = filter
        }
    }

    suspend fun setSort(sort: String) {
        context.dataStore.edit { preferences ->
            preferences[sortKey] = sort
        }
    }

    suspend fun setBrowser(browser: String) {
        context.dataStore.edit { preferences ->
            preferences[browserKey] = browser
        }
    }

    suspend fun setBrowserPackage(packageName: String?) {
        context.dataStore.edit { preferences ->
            if (packageName == null) {
                preferences.remove(browserPackageKey)
            } else {
                preferences[browserPackageKey] = packageName
            }
        }
    }

    suspend fun setReminders(remindersJson: String) {
        context.dataStore.edit { preferences ->
            preferences[remindersKey] = remindersJson
        }
    }

    suspend fun setGeminiApiKey(apiKey: String?) {
        context.dataStore.edit { preferences ->
            if (apiKey.isNullOrBlank()) {
                preferences.remove(geminiApiKeyKey)
            } else {
                preferences[geminiApiKeyKey] = apiKey
            }
        }
    }

    suspend fun setTtsLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[ttsLanguageKey] = language
        }
    }
}
