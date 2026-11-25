package com.ascend.fitness.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Sex { MALE, FEMALE }

data class UserSettings(
    val theme: String = "Ember",
    val bodyweightKg: Double = 75.0,
    val sex: Sex = Sex.MALE,
    val birthYear: Int = 2000,
    val weeklyGoal: Int = 4,
    val restSeconds: Int = 90,
    val geminiModel: String = "gemini-3.5-flash-lite",
    val hasGeminiKey: Boolean = false,
    val onboarded: Boolean = false,
)

/** Plain settings in normal prefs; the Gemini key only in Keystore-backed encrypted prefs. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<UserSettings> = _state

    private fun read() = UserSettings(
        theme = prefs.getString("theme", "Ember") ?: "Ember",
        bodyweightKg = prefs.getFloat("bw", 75f).toDouble(),
        sex = if (prefs.getString("sex", "MALE") == "FEMALE") Sex.FEMALE else Sex.MALE,
        birthYear = prefs.getInt("birthYear", 2000),
        weeklyGoal = prefs.getInt("weeklyGoal", 4),
        restSeconds = prefs.getInt("rest", 90),
        // The old default was retired by Google for new keys, so it moves to the current one.
        geminiModel = (prefs.getString("model", null) ?: "gemini-3.5-flash-lite").let { if (it == "gemini-2.5-flash") "gemini-3.5-flash-lite" else it },
        hasGeminiKey = !secure.getString("gemini_key", null).isNullOrBlank(),
        onboarded = prefs.getBoolean("onboarded", false),
    )

    fun update(block: (UserSettings) -> UserSettings) {
        val n = block(_state.value)
        prefs.edit()
            .putString("theme", n.theme)
            .putFloat("bw", n.bodyweightKg.toFloat())
            .putString("sex", n.sex.name)
            .putInt("birthYear", n.birthYear)
            .putInt("weeklyGoal", n.weeklyGoal)
            .putInt("rest", n.restSeconds)
            .putString("model", n.geminiModel)
            .putBoolean("onboarded", n.onboarded)
            .apply()
        _state.value = n.copy(hasGeminiKey = _state.value.hasGeminiKey)
    }

    fun catalogVersion(): Int = prefs.getInt("catalogVersion", 0)
    fun setCatalogVersion(v: Int) = prefs.edit().putInt("catalogVersion", v).apply()

    fun geminiKey(): String? = secure.getString("gemini_key", null)?.takeIf { it.isNotBlank() }

    fun setGeminiKey(key: String?) {
        secure.edit().apply { if (key.isNullOrBlank()) remove("gemini_key") else putString("gemini_key", key.trim()) }.apply()
        _state.value = _state.value.copy(hasGeminiKey = !key.isNullOrBlank())
    }
}
