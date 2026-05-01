package com.stylemirror.core.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SharedPrefsSecureKeyStore] running under Robolectric with a
 * plain [android.content.SharedPreferences] (Robolectric's in-memory shadow).
 *
 * Why plain prefs and not [androidx.security.crypto.EncryptedSharedPreferences]?
 * The latter delegates to Tink + the Android Keystore, which Robolectric does
 * not always emulate cleanly. Encryption correctness is the AndroidX library's
 * own concern; our wrapper only has to honour the [SecureKeyStore] contract.
 * A device-backed integration test verifies the encrypted factory in M3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SharedPrefsSecureKeyStoreTest {
    private lateinit var store: SharedPrefsSecureKeyStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        store = SharedPrefsSecureKeyStore(prefs, ioDispatcher = Dispatchers.Unconfined)
    }

    @Test
    fun `get returns null for unknown name`() =
        runTest {
            store.get("missing") shouldBe null
        }

    @Test
    fun `put then get round-trips the value`() =
        runTest {
            store.put("deepseek_api_key", "sk-test-123")
            store.get("deepseek_api_key") shouldBe "sk-test-123"
        }

    @Test
    fun `put twice with same name overwrites the prior value`() =
        runTest {
            store.put("rotating_token", "v1")
            store.put("rotating_token", "v2")
            store.get("rotating_token") shouldBe "v2"
        }

    @Test
    fun `remove erases an existing entry`() =
        runTest {
            store.put("ephemeral", "x")
            store.remove("ephemeral")
            store.get("ephemeral") shouldBe null
        }

    @Test
    fun `remove on a missing name is a no-op`() =
        runTest {
            store.remove("never-existed")
            store.get("never-existed") shouldBe null
        }

    @Test
    fun `multiple names coexist without interference`() =
        runTest {
            store.put("a", "1")
            store.put("b", "2")
            store.put("c", "3")

            store.get("a") shouldBe "1"
            store.get("b") shouldBe "2"
            store.get("c") shouldBe "3"

            store.remove("b")
            store.get("a") shouldBe "1"
            store.get("b") shouldBe null
            store.get("c") shouldBe "3"
        }

    @Test
    fun `empty string is a legitimate stored value`() =
        runTest {
            store.put("blank", "")
            store.get("blank") shouldBe ""
        }

    @Test
    fun `clearAll wipes every entry`() =
        runTest {
            store.put("a", "1")
            store.put("b", "2")
            store.put("c", "3")

            store.clearAll()

            store.get("a") shouldBe null
            store.get("b") shouldBe null
            store.get("c") shouldBe null
        }

    @Test
    fun `separate prefs files yield isolated namespaces`() =
        runTest {
            val context: Context = ApplicationProvider.getApplicationContext()
            val storeA =
                SharedPrefsSecureKeyStore(
                    context.getSharedPreferences("ns_a", Context.MODE_PRIVATE),
                    ioDispatcher = Dispatchers.Unconfined,
                )
            val storeB =
                SharedPrefsSecureKeyStore(
                    context.getSharedPreferences("ns_b", Context.MODE_PRIVATE),
                    ioDispatcher = Dispatchers.Unconfined,
                )

            storeA.put("shared_name", "fromA")

            storeB.get("shared_name") shouldBe null
            storeA.get("shared_name") shouldBe "fromA"
        }
}
