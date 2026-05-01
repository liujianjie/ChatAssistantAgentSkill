package com.stylemirror.infra.llm.testing

import com.stylemirror.domain.security.SecureKeyStore
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [SecureKeyStore] for tests. NOT a real keystore — values sit in
 * process heap with no encryption. Test source sets only.
 *
 * Kept `public` (not `internal`) because the `integrationTest` source set is
 * a separate Kotlin compilation unit for visibility purposes; both `test`
 * and `integrationTest` need it.
 */
class InMemorySecureKeyStore(initial: Map<String, String> = emptyMap()) : SecureKeyStore {
    private val map = ConcurrentHashMap<String, String>(initial)

    override suspend fun get(name: String): String? = map[name]

    override suspend fun put(
        name: String,
        value: String,
    ) {
        map[name] = value
    }

    override suspend fun remove(name: String) {
        map.remove(name)
    }

    override suspend fun clearAll() {
        map.clear()
    }
}
