package com.example.lifesaiver.core.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "survivor_profile"
)

data class SurvivorProfile(
    val name: String = "",
    val gender: String = "",
    val birthDate: String = "",
    val notes: String = ""
) {
    val isComplete: Boolean
        get() = name.isNotBlank() && gender.isNotBlank() && birthDate.isNotBlank()
}

class ProfileStore(private val context: Context) {
    val profileFlow: Flow<SurvivorProfile> = context.profileDataStore.data.map { prefs ->
        SurvivorProfile(
            name = prefs[NAME_KEY].orEmpty(),
            gender = prefs[GENDER_KEY].orEmpty(),
            birthDate = normalizeBirthDate(prefs[BIRTH_DATE_KEY]),
            notes = prefs[NOTES_KEY].orEmpty()
        )
    }

    suspend fun saveProfile(profile: SurvivorProfile) {
        context.profileDataStore.edit { prefs ->
            prefs[NAME_KEY] = profile.name.trim()
            prefs[GENDER_KEY] = profile.gender.trim()
            prefs[BIRTH_DATE_KEY] = normalizeBirthDate(profile.birthDate)
            prefs[NOTES_KEY] = profile.notes.trim()
        }
    }
}

private val NAME_KEY = stringPreferencesKey("survivor_name")
private val GENDER_KEY = stringPreferencesKey("survivor_gender")
private val BIRTH_DATE_KEY = stringPreferencesKey("survivor_birth_date")
private val NOTES_KEY = stringPreferencesKey("survivor_notes")

private val birthDateFormatters = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ofPattern("yyyy.MM.dd"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
    DateTimeFormatter.ofPattern("yyyyMMdd")
)

private fun normalizeBirthDate(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return ""

    for (formatter in birthDateFormatters) {
        try {
            val parsed = LocalDate.parse(value, formatter)
            return DateTimeFormatter.ISO_LOCAL_DATE.format(parsed)
        } catch (_: DateTimeParseException) {
            // Try the next known format.
        }
    }
    return ""
}
