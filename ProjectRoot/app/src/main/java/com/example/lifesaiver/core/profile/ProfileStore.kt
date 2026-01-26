package com.example.lifesaiver.core.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "survivor_profile"
)

data class SurvivorProfile(
    val name: String = "",
    val gender: String = "",
    val birthDate: String = "",
    val emergencyContact: String = "",
    val notes: String = "",
    val bloodType: String = ""
) {
    val isComplete: Boolean
        get() = name.isNotBlank() && gender.isNotBlank() && birthDate.isNotBlank()
}

class ProfileStore(private val context: Context) {
    val profileFlow: Flow<SurvivorProfile> = context.profileDataStore.data.map { prefs ->
        SurvivorProfile(
            name = prefs[NAME_KEY].orEmpty(),
            gender = prefs[GENDER_KEY].orEmpty(),
            birthDate = prefs[BIRTH_DATE_KEY].orEmpty(),
            emergencyContact = prefs[EMERGENCY_CONTACT_KEY].orEmpty(),
            notes = prefs[NOTES_KEY].orEmpty(),
            bloodType = prefs[BLOOD_TYPE_KEY].orEmpty()
        )
    }

    suspend fun saveProfile(profile: SurvivorProfile) {
        context.profileDataStore.edit { prefs ->
            prefs[NAME_KEY] = profile.name.trim()
            prefs[GENDER_KEY] = profile.gender.trim()
            prefs[BIRTH_DATE_KEY] = profile.birthDate.trim()
            prefs[EMERGENCY_CONTACT_KEY] = profile.emergencyContact.trim()
            prefs[NOTES_KEY] = profile.notes.trim()
            prefs[BLOOD_TYPE_KEY] = profile.bloodType.trim()
        }
    }
}

private val NAME_KEY = stringPreferencesKey("survivor_name")
private val GENDER_KEY = stringPreferencesKey("survivor_gender")
private val BIRTH_DATE_KEY = stringPreferencesKey("survivor_birth_date")
private val EMERGENCY_CONTACT_KEY = stringPreferencesKey("survivor_emergency_contact")
private val NOTES_KEY = stringPreferencesKey("survivor_notes")
private val BLOOD_TYPE_KEY = stringPreferencesKey("survivor_blood_type")
