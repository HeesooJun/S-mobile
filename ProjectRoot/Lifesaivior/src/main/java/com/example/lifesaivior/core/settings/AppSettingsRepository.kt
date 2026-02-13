package com.example.lifesaivior.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DEFAULT_DEMO_LEVEL = 100

data class AppSettingsState(
    val isVoiceDetectionEnabled: Boolean = false,
    val isShockDetectionEnabled: Boolean = false,
    val isDemoModeEnabled: Boolean = false,
    val demoBeepLevel: Int = DEFAULT_DEMO_LEVEL,
    val demoHighToneLevel: Int = DEFAULT_DEMO_LEVEL,
    val demoVibrateLevel: Int = DEFAULT_DEMO_LEVEL,
    val demoEasLevel: Int = DEFAULT_DEMO_LEVEL,
    val isSosBackgroundSuspended: Boolean = false,
    val sosBackupVoiceDetection: Boolean = false,
    val sosBackupShockDetection: Boolean = false,
    val sosBackupDemoMode: Boolean = false
)

object AppSettingsRepository {
    private const val PREFS_NAME = "app_settings"

    private val lock = Any()
    @Volatile private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(AppSettingsState())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, _ ->
        updateState(sharedPrefs)
    }

    fun init(context: Context) {
        ensurePrefs(context)
    }

    fun snapshot(context: Context): AppSettingsState {
        val sharedPrefs = ensurePrefs(context)
        ensureDemoConsistency(sharedPrefs)
        updateState(sharedPrefs)
        return _state.value
    }

    fun setVoiceDetection(context: Context, enabled: Boolean) {
        val sharedPrefs = ensurePrefs(context)
        sharedPrefs.edit().putBoolean(KEY_VOICE_DETECTION, enabled).apply()
        updateState(sharedPrefs)
    }

    fun setShockDetection(context: Context, enabled: Boolean) {
        val sharedPrefs = ensurePrefs(context)
        sharedPrefs.edit().putBoolean(KEY_SHOCK_DETECTION, enabled).apply()
        updateState(sharedPrefs)
    }

    fun setDemoMode(context: Context, enabled: Boolean) {
        val sharedPrefs = ensurePrefs(context)
        sharedPrefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        updateState(sharedPrefs)
    }

    fun setDemoBeepLevel(context: Context, level: Int) {
        val sharedPrefs = ensurePrefs(context)
        sharedPrefs.edit()
            .putInt(KEY_DEMO_BEEP_LEVEL, level.coerceIn(0, 100))
            .apply()
        updateState(sharedPrefs)
    }

    fun setDemoHighToneLevel(context: Context, level: Int) {
        val sharedPrefs = ensurePrefs(context)
        sharedPrefs.edit()
            .putInt(KEY_DEMO_HIGH_TONE_LEVEL, level.coerceIn(0, 100))
            .apply()
        updateState(sharedPrefs)
    }

    fun setDemoVibrateLevel(context: Context, level: Int) {
        val sharedPrefs = ensurePrefs(context)
        sharedPrefs.edit()
            .putInt(KEY_DEMO_VIBRATE_LEVEL, level.coerceIn(0, 100))
            .apply()
        updateState(sharedPrefs)
    }

    fun setDemoEasLevel(context: Context, level: Int) {
        val sharedPrefs = ensurePrefs(context)
        sharedPrefs.edit()
            .putInt(KEY_DEMO_EAS_LEVEL, level.coerceIn(0, 100))
            .apply()
        updateState(sharedPrefs)
    }

    fun setSosBackgroundSuspended(
        context: Context,
        suspended: Boolean,
        backupVoice: Boolean? = null,
        backupShock: Boolean? = null,
        backupDemo: Boolean? = null
    ) {
        val sharedPrefs = ensurePrefs(context)
        val editor = sharedPrefs.edit().putBoolean(KEY_SOS_BG_SUSPENDED, suspended)
        backupVoice?.let { editor.putBoolean(KEY_SOS_BACKUP_VOICE, it) }
        backupShock?.let { editor.putBoolean(KEY_SOS_BACKUP_SHOCK, it) }
        backupDemo?.let { editor.putBoolean(KEY_SOS_BACKUP_DEMO, it) }
        editor.apply()
        updateState(sharedPrefs)
    }

    fun clearSosBackup(context: Context) {
        val sharedPrefs = ensurePrefs(context)
        sharedPrefs.edit()
            .remove(KEY_SOS_BACKUP_VOICE)
            .remove(KEY_SOS_BACKUP_SHOCK)
            .remove(KEY_SOS_BACKUP_DEMO)
            .apply()
        updateState(sharedPrefs)
    }

    private fun ensurePrefs(context: Context): SharedPreferences {
        val existing = prefs
        if (existing != null) return existing
        synchronized(lock) {
            val again = prefs
            if (again != null) return again
            val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = sharedPrefs
            ensureDemoConsistency(sharedPrefs)
            updateState(sharedPrefs)
            sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
            return sharedPrefs
        }
    }

    private fun ensureDemoConsistency(sharedPrefs: SharedPreferences) {
        val demoEnabled = sharedPrefs.getBoolean(KEY_DEMO_MODE, false)
        if (!demoEnabled) return
        val voiceEnabled = sharedPrefs.getBoolean(KEY_VOICE_DETECTION, false)
        val shockEnabled = sharedPrefs.getBoolean(KEY_SHOCK_DETECTION, false)
        if (voiceEnabled && shockEnabled) return
        sharedPrefs.edit()
            .putBoolean(KEY_VOICE_DETECTION, true)
            .putBoolean(KEY_SHOCK_DETECTION, true)
            .apply()
    }

    private fun updateState(sharedPrefs: SharedPreferences) {
        _state.value = AppSettingsState(
            isVoiceDetectionEnabled = sharedPrefs.getBoolean(KEY_VOICE_DETECTION, false),
            isShockDetectionEnabled = sharedPrefs.getBoolean(KEY_SHOCK_DETECTION, false),
            isDemoModeEnabled = sharedPrefs.getBoolean(KEY_DEMO_MODE, false),
            demoBeepLevel = sharedPrefs.getInt(KEY_DEMO_BEEP_LEVEL, DEFAULT_DEMO_LEVEL).coerceIn(0, 100),
            demoHighToneLevel = sharedPrefs.getInt(KEY_DEMO_HIGH_TONE_LEVEL, DEFAULT_DEMO_LEVEL).coerceIn(0, 100),
            demoVibrateLevel = sharedPrefs.getInt(KEY_DEMO_VIBRATE_LEVEL, DEFAULT_DEMO_LEVEL).coerceIn(0, 100),
            demoEasLevel = sharedPrefs.getInt(KEY_DEMO_EAS_LEVEL, DEFAULT_DEMO_LEVEL).coerceIn(0, 100),
            isSosBackgroundSuspended = sharedPrefs.getBoolean(KEY_SOS_BG_SUSPENDED, false),
            sosBackupVoiceDetection = sharedPrefs.getBoolean(KEY_SOS_BACKUP_VOICE, false),
            sosBackupShockDetection = sharedPrefs.getBoolean(KEY_SOS_BACKUP_SHOCK, false),
            sosBackupDemoMode = sharedPrefs.getBoolean(KEY_SOS_BACKUP_DEMO, false)
        )
    }

    const val KEY_VOICE_DETECTION = "voice_detection"
    const val KEY_SHOCK_DETECTION = "shock_detection"
    const val KEY_DEMO_MODE = "demo_mode"
    const val KEY_DEMO_BEEP_LEVEL = "demo_beep_level"
    const val KEY_DEMO_HIGH_TONE_LEVEL = "demo_high_tone_level"
    const val KEY_DEMO_VIBRATE_LEVEL = "demo_vibrate_level"
    const val KEY_DEMO_EAS_LEVEL = "demo_eas_level"
    const val KEY_SOS_BG_SUSPENDED = "sos_background_suspended"
    const val KEY_SOS_BACKUP_VOICE = "sos_backup_voice_detection"
    const val KEY_SOS_BACKUP_SHOCK = "sos_backup_shock_detection"
    const val KEY_SOS_BACKUP_DEMO = "sos_backup_demo_mode"
}
