package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ics_settings")

/**
 * Gestionnaire DataStore Preferences pour stocker l'URL .ics Flop!EDT.
 */
class IcsPreferences(private val context: Context) {
    companion object {
        val KEY_ICS_URL = stringPreferencesKey("flop_ics_url")
        const val DEFAULT_FALLBACK_URL = "https://edt.flop.org/export/schedule.ics"
    }

    val icsUrlFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_ICS_URL]
    }

    suspend fun getIcsUrl(): String? {
        return context.dataStore.data.map { it[KEY_ICS_URL] }.first()
    }

    suspend fun setIcsUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ICS_URL] = url.trim()
        }
    }

    suspend fun clearIcsUrl() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_ICS_URL)
        }
    }
}
