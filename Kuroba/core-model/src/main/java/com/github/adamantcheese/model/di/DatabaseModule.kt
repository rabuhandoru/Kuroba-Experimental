package com.github.adamantcheese.model.di

import android.app.Application
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.di.annotation.LoggerTagPrefix
import com.github.adamantcheese.model.di.annotation.VerboseLogs
import com.github.adamantcheese.model.repository.InlinedFileInfoRepository
import com.github.adamantcheese.model.repository.MediaServiceLinkExtraContentRepository
import com.github.adamantcheese.model.repository.SeenPostRepository
import com.github.adamantcheese.model.source.cache.GenericCacheSource
import com.github.adamantcheese.model.source.local.InlinedFileInfoLocalSource
import com.github.adamantcheese.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.model.source.local.SeenPostLocalSource
import com.github.adamantcheese.model.source.remote.InlinedFileInfoRemoteSource
import com.github.adamantcheese.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
class DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(application: Application): KurobaDatabase {
        return KurobaDatabase.buildDatabase(application)
    }

    @Singleton
    @Provides
    fun provideLogger(@VerboseLogs verboseLogs: Boolean): Logger {
        return Logger(verboseLogs)
    }

    /**
     * Local sources
     * */

    @Singleton
    @Provides
    fun provideMediaServiceLinkExtraContentLocalSource(
            database: KurobaDatabase,
            @LoggerTagPrefix loggerTag: String,
            logger: Logger
    ): MediaServiceLinkExtraContentLocalSource {
        return MediaServiceLinkExtraContentLocalSource(
                database,
                loggerTag,
                logger
        )
    }

    @Singleton
    @Provides
    fun provideSeenPostLocalSource(
            database: KurobaDatabase,
            @LoggerTagPrefix loggerTag: String,
            logger: Logger
    ): SeenPostLocalSource {
        return SeenPostLocalSource(
                database,
                loggerTag,
                logger
        )
    }

    @Singleton
    @Provides
    fun provideInlinedFileInfoLocalSource(
            database: KurobaDatabase,
            @LoggerTagPrefix loggerTag: String,
            logger: Logger
    ): InlinedFileInfoLocalSource {
        return InlinedFileInfoLocalSource(
                database,
                loggerTag,
                logger
        )
    }

    /**
     * Remote sources
     * */

    @Singleton
    @Provides
    fun provideMediaServiceLinkExtraContentRemoteSource(
            logger: Logger,
            okHttpClient: OkHttpClient,
            @LoggerTagPrefix loggerTag: String
    ): MediaServiceLinkExtraContentRemoteSource {
        return MediaServiceLinkExtraContentRemoteSource(okHttpClient, loggerTag, logger)
    }

    @Singleton
    @Provides
    fun provideInlinedFileInfoRemoteSource(
            logger: Logger,
            okHttpClient: OkHttpClient,
            @LoggerTagPrefix loggerTag: String
    ): InlinedFileInfoRemoteSource {
        return InlinedFileInfoRemoteSource(okHttpClient, loggerTag, logger)
    }

    /**
     * Repositories
     * */

    @Singleton
    @Provides
    fun provideYoutubeLinkExtraContentRepository(
            logger: Logger,
            database: KurobaDatabase,
            mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource,
            mediaServiceLinkExtraContentRemoteSource: MediaServiceLinkExtraContentRemoteSource,
            @LoggerTagPrefix loggerTag: String
    ): MediaServiceLinkExtraContentRepository {
        return MediaServiceLinkExtraContentRepository(
                database,
                loggerTag,
                logger,
                GenericCacheSource(),
                mediaServiceLinkExtraContentLocalSource,
                mediaServiceLinkExtraContentRemoteSource
        )
    }

    @Singleton
    @Provides
    fun provideSeenPostRepository(
            logger: Logger,
            database: KurobaDatabase,
            seenPostLocalSource: SeenPostLocalSource,
            @LoggerTagPrefix loggerTag: String
    ): SeenPostRepository {
        return SeenPostRepository(
                database,
                loggerTag,
                logger,
                seenPostLocalSource
        )
    }

    @Singleton
    @Provides
    fun provideInlinedFileInfoRepository(
            logger: Logger,
            database: KurobaDatabase,
            inlinedFileInfoLocalSource: InlinedFileInfoLocalSource,
            inlinedFileInfoRemoteSource: InlinedFileInfoRemoteSource,
            @LoggerTagPrefix loggerTag: String
    ): InlinedFileInfoRepository {
        return InlinedFileInfoRepository(
                database,
                loggerTag,
                logger,
                GenericCacheSource(),
                inlinedFileInfoLocalSource,
                inlinedFileInfoRemoteSource
        )
    }
}