package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.RelayRole
import com.example.model.UpdateRateOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "linkgps_prefs")

data class UserPreferences(
    val selectedRole: RelayRole = RelayRole.TRANSMITTER,
    val updateRate: UpdateRateOption = UpdateRateOption.RATE_1_HZ,
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_ROLE = stringPreferencesKey("selected_role")
        val UPDATE_INTERVAL_MS = longPreferencesKey("update_interval_ms")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val role = prefs[Keys.SELECTED_ROLE]
            ?.let { runCatching { RelayRole.valueOf(it) }.getOrNull() }
            ?: RelayRole.TRANSMITTER
        val rate = prefs[Keys.UPDATE_INTERVAL_MS]
            ?.let { UpdateRateOption.fromInterval(it) }
            ?: UpdateRateOption.RATE_1_HZ
        UserPreferences(selectedRole = role, updateRate = rate)
    }

    suspend fun setSelectedRole(role: RelayRole) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_ROLE] = role.name
        }
    }

    suspend fun setUpdateRate(rate: UpdateRateOption) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UPDATE_INTERVAL_MS] = rate.intervalMs
        }
    }
}
