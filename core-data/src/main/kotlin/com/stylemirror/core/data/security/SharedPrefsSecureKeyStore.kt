package com.stylemirror.core.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.stylemirror.domain.security.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * [SecureKeyStore] backed by Android's [SharedPreferences]. Designed to be
 * paired with [EncryptedSharedPreferences] in production via [encrypted];
 * unit tests inject a plain [SharedPreferences] so they verify the wrapper
 * contract without depending on the device keystore being available under
 * Robolectric.
 *
 * I/O dispatches to [ioDispatcher] because [SharedPreferences.getString] /
 * editor commit may touch the disk on first use (initial decrypt under the
 * encrypted variant) and we don't want main-thread stalls.
 */
class SharedPrefsSecureKeyStore(
    private val prefs: SharedPreferences,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : SecureKeyStore {
    override suspend fun get(name: String): String? =
        withContext(ioDispatcher) {
            prefs.getString(name, null)
        }

    override suspend fun put(
        name: String,
        value: String,
    ) {
        withContext(ioDispatcher) {
            prefs.edit().putString(name, value).apply()
        }
    }

    override suspend fun remove(name: String) {
        withContext(ioDispatcher) {
            prefs.edit().remove(name).apply()
        }
    }

    override suspend fun clearAll() {
        withContext(ioDispatcher) {
            prefs.edit().clear().apply()
        }
    }

    companion object {
        /**
         * Filename for the encrypted prefs file. Distinct from any non-secret
         * prefs the app uses so an accidental config dump can't mix them.
         */
        const val PREFS_FILE: String = "style_mirror_secrets"

        /**
         * Builds a [SharedPrefsSecureKeyStore] backed by [EncryptedSharedPreferences]
         * with an AES256_GCM master key. The encryption layer itself is
         * exercised by AndroidX's own test suite — our unit tests cover the
         * wrapper logic against a plain [SharedPreferences].
         */
        fun encrypted(context: Context): SharedPrefsSecureKeyStore {
            val appContext = context.applicationContext
            val masterKey =
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            val prefs =
                EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            return SharedPrefsSecureKeyStore(prefs)
        }
    }
}
