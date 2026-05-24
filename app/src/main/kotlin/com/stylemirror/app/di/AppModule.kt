package com.stylemirror.app.di

import android.content.Context
import com.stylemirror.core.data.security.SharedPrefsSecureKeyStore
import com.stylemirror.domain.security.SecureKeyStore
import com.stylemirror.feature.realtime.candidate.CandidateGenerator
import com.stylemirror.feature.realtime.matching.FakeStyleEngine
import com.stylemirror.feature.realtime.matching.StyleEngine
import com.stylemirror.infra.llm.LLMProvider
import com.stylemirror.infra.llm.deepseek.DeepSeekProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideStyleEngine(): StyleEngine = FakeStyleEngine()

    @Provides
    @Singleton
    fun provideCandidateGenerator(
        llmProvider: LLMProvider,
        styleEngine: StyleEngine,
    ): CandidateGenerator = CandidateGenerator(llmProvider = llmProvider, styleEngine = styleEngine)
}
