package com.stylemirror.core.data.db

import com.stylemirror.domain.security.SecureKeyStore
import java.security.SecureRandom

/**
 * Manages the SQLCipher database passphrase via [SecureKeyStore].
 *
 * On first call [getOrCreate] generates a 32-byte random passphrase,
 * hex-encodes it, and persists it in [keyStore] under
 * [StyleMirrorDatabase.DB_PASSPHRASE_KEY]. Subsequent calls return the
 * stored value without generating a new one.
 *
 * The passphrase never appears in logs or error messages (SecureKeyStore
 * implementation guarantees are the same as for API keys).
 */
object DatabasePassphraseProvider {
    suspend fun getOrCreate(keyStore: SecureKeyStore): ByteArray {
        val existing = keyStore.get(StyleMirrorDatabase.DB_PASSPHRASE_KEY)
        if (existing != null) return hexToBytes(existing)

        val bytes = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(bytes)
        val hex = bytesToHex(bytes)
        keyStore.put(StyleMirrorDatabase.DB_PASSPHRASE_KEY, hex)
        return bytes
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private const val PASSPHRASE_BYTES = 32
}
