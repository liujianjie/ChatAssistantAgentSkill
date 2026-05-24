package com.stylemirror.app.di

import android.content.Context
import com.stylemirror.core.data.db.DatabasePassphraseProvider
import com.stylemirror.core.data.db.StyleMirrorDatabase
import com.stylemirror.core.data.repository.FeedbackRepository
import com.stylemirror.core.data.repository.StyleFingerprintRepository
import com.stylemirror.core.data.repository.StyleFingerprintStore
import com.stylemirror.core.data.security.SharedPrefsSecureKeyStore
import com.stylemirror.domain.security.SecureKeyStore
import com.stylemirror.feature.imports.profiling.PersonaProfiler
import com.stylemirror.feature.realtime.candidate.CandidateGenerator
import com.stylemirror.feature.realtime.input.ScreenshotInput
import com.stylemirror.feature.realtime.matching.RoomBackedStyleEngine
import com.stylemirror.feature.realtime.matching.StyleEngine
import com.stylemirror.infra.llm.LLMProvider
import com.stylemirror.infra.llm.deepseek.DeepSeekProvider
import com.stylemirror.infra.ocr.OcrProvider
import com.stylemirror.infra.ocr.mlkit.MlKitOcrProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSecureKeyStore(
        @ApplicationContext context: Context,
    ): SecureKeyStore = SharedPrefsSecureKeyStore.encrypted(context)

    @Provides
    @Singleton
    fun provideLlmProvider(keyStore: SecureKeyStore): LLMProvider = DeepSeekProvider.create(keyStore = keyStore)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyStore: SecureKeyStore,
    ): StyleMirrorDatabase {
        // Passphrase fetch is suspend; runBlocking is acceptable here because
        // it runs once at app startup before any DB access. The Tink-backed
        // SecureKeyStore I/O is fast (low millis) and dispatched on Dispatchers.IO
        // inside the implementation.
        val passphrase = runBlocking { DatabasePassphraseProvider.getOrCreate(keyStore) }
        return StyleMirrorDatabase.create(context = context, passphrase = passphrase)
    }

    @Provides
    @Singleton
    fun provideStyleFingerprintStore(db: StyleMirrorDatabase): StyleFingerprintStore =
        StyleFingerprintRepository(dao = db.styleFingerprintDao())

    @Provides
    @Singleton
    fun provideFeedbackRepository(db: StyleMirrorDatabase): FeedbackRepository =
        FeedbackRepository(dao = db.feedbackSignalDao())

    @Provides
    @Singleton
    fun provideStyleEngine(repository: StyleFingerprintStore): StyleEngine =
        RoomBackedStyleEngine(repository = repository)

    @Provides
    @Singleton
    fun providePersonaProfiler(
        llmProvider: LLMProvider,
        repository: StyleFingerprintStore,
    ): PersonaProfiler = PersonaProfiler(llmProvider = llmProvider, repository = repository)

    @Provides
    @Singleton
    fun provideCandidateGenerator(
        llmProvider: LLMProvider,
        styleEngine: StyleEngine,
    ): CandidateGenerator = CandidateGenerator(llmProvider = llmProvider, styleEngine = styleEngine)

    @Provides
    @Singleton
    fun provideOcrProvider(): OcrProvider = MlKitOcrProvider.create()

    @Provides
    @Singleton
    fun provideScreenshotInput(ocrProvider: OcrProvider): ScreenshotInput = ScreenshotInput(ocrProvider = ocrProvider)
}
