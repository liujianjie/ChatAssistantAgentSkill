package com.stylemirror.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.stylemirror.core.data.db.dao.CorpusSampleDao
import com.stylemirror.core.data.db.dao.FeedbackSignalDao
import com.stylemirror.core.data.db.dao.ImportSessionDao
import com.stylemirror.core.data.db.dao.MessageDao
import com.stylemirror.core.data.db.dao.StyleFingerprintDao
import com.stylemirror.core.data.db.entity.CorpusSampleEntity
import com.stylemirror.core.data.db.entity.FeedbackSignalEntity
import com.stylemirror.core.data.db.entity.ImportSessionEntity
import com.stylemirror.core.data.db.entity.MessageEntity
import com.stylemirror.core.data.db.entity.StyleFingerprintEntity
import net.sqlcipher.database.SupportFactory

/**
 * Single Room database for the app. Version 1 is the initial schema (migration 0→1).
 *
 * **Encryption**: backed by SQLCipher via [SupportFactory]. The passphrase is
 * a 32-byte random value, hex-encoded, stored in [SecureKeyStore] under
 * [DB_PASSPHRASE_KEY]. On first open [DatabasePassphraseProvider.getOrCreate]
 * generates and persists the passphrase; subsequent opens read the same key.
 *
 * **Privacy red line**: field names must never reference wechat_id, phone,
 * or any real-identity column — see entity definitions.
 *
 * @see StyleMirrorMigrations for the migration set to pass to the builder.
 */
@Database(
    entities = [
        MessageEntity::class,
        StyleFingerprintEntity::class,
        FeedbackSignalEntity::class,
        ImportSessionEntity::class,
        CorpusSampleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class StyleMirrorDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    abstract fun styleFingerprintDao(): StyleFingerprintDao

    abstract fun feedbackSignalDao(): FeedbackSignalDao

    abstract fun importSessionDao(): ImportSessionDao

    abstract fun corpusSampleDao(): CorpusSampleDao

    companion object {
        const val DB_NAME: String = "style_mirror.db"
        const val DB_PASSPHRASE_KEY: String = "db.passphrase"

        /**
         * Builds an encrypted production [StyleMirrorDatabase].
         * Call once and keep the instance as a singleton (Hilt @Singleton).
         *
         * [passphrase] must be the raw 32-byte passphrase (not hex-encoded)
         * as SQLCipher expects a char array.
         */
        fun create(
            context: Context,
            passphrase: ByteArray,
        ): StyleMirrorDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                StyleMirrorDatabase::class.java,
                DB_NAME,
            )
                .openHelperFactory(factory)
                .apply { StyleMirrorMigrations.ALL.forEach { addMigrations(it) } }
                .build()
        }

        /**
         * In-memory unencrypted database for unit tests. SQLCipher is not
         * available under Robolectric — this variant skips the SupportFactory.
         */
        fun createInMemory(context: Context): StyleMirrorDatabase =
            Room.inMemoryDatabaseBuilder(context, StyleMirrorDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
