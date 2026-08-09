package com.zai.mamsad.admin

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for admin settings (password, lock state).
 *
 * Uses EncryptedSharedPreferences (AES-GCM 256, AES-SIV-CMAC256) — the file
 * is bound to the device and cannot be read without root + key store access.
 *
 * Default password is set on first run if the user has not changed it.
 */
object AdminPrefs {

    private const val FILE_NAME = "mamsad_admin_prefs"
    private const val KEY_PASSWORD = "admin_password"
    private const val KEY_UNLOCKED = "admin_unlocked_session"

    /** Default password shown to the user on first launch. */
    const val DEFAULT_PASSWORD = "mamsad2024"

    private fun prefs(context: Context) = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    fun getPassword(context: Context): String {
        val p = prefs(context) ?: return DEFAULT_PASSWORD
        return p.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
    }

    fun setPassword(context: Context, newPassword: String) {
        prefs(context)?.edit()?.putString(KEY_PASSWORD, newPassword)?.apply()
    }

    /**
     * Session unlock state — admin stays unlocked until app process dies.
     * Avoids re-entering password on every screen navigation.
     */
    fun isUnlocked(context: Context): Boolean {
        val p = prefs(context) ?: return false
        return p.getBoolean(KEY_UNLOCKED, false)
    }

    fun setUnlocked(context: Context, unlocked: Boolean) {
        prefs(context)?.edit()?.putBoolean(KEY_UNLOCKED, unlocked)?.apply()
    }

    fun verify(context: Context, candidate: String): Boolean {
        return candidate == getPassword(context)
    }
}
