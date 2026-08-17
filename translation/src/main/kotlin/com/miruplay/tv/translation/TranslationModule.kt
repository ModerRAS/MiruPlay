package com.miruplay.tv.translation

import com.miruplay.tv.scraper.core.BangumiProxyAwareOkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TranslationModule {

    // 不对外绑定 OkHttpClient（app 的 AppModule 已有绑定，避免重复），只提供代理包装单例
    @Provides
    @Singleton
    fun provideProxyAwareOkHttpClient(): BangumiProxyAwareOkHttpClient =
        BangumiProxyAwareOkHttpClient(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build(),
        )

    @Provides
    @Singleton
    fun provideTranslationPreferencesRepository(
        impl: TranslationPreferencesManager,
    ): TranslationPreferencesRepository = impl
}
