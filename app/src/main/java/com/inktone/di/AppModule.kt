package com.inktone.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.inktone.data.database.AnnotationDao
import com.inktone.data.database.BookDao
import com.inktone.data.database.BookProgressDao
import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.MIGRATION_1_2
import com.inktone.data.database.MIGRATION_2_3
import com.inktone.data.database.MIGRATION_3_4
import com.inktone.data.database.MIGRATION_4_5
import com.inktone.data.database.ProgressDao
import com.inktone.data.database.PronunciationRuleDao
import com.inktone.data.database.InkToneDatabase
import com.inktone.data.database.ReadingProgressDao
import com.inktone.data.database.ReadingSessionDao
import com.inktone.data.database.RecentBookDao
import com.inktone.data.database.SearchDao
import com.inktone.data.database.SentenceCacheDao
import com.inktone.data.repository.BookRepositoryImpl
import com.inktone.data.repository.TtsRepositoryImpl
import com.inktone.data.settings.SettingsRepository
import com.inktone.domain.provider.EdgeTtsProvider
import com.inktone.domain.provider.PiperTtsProvider
import com.inktone.domain.provider.TtsProvider
import com.inktone.domain.repository.BookRepository
import com.inktone.domain.repository.TtsRepository
import com.inktone.domain.service.AudioServiceLauncher
import com.inktone.service.audio.AudioServiceLauncherImpl
import com.inktone.service.edge.EdgeTtsClient
import com.inktone.service.edge.Mp3Decoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.multibindings.IntoSet
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InkToneDatabase {
        return Room.databaseBuilder(
            context,
            InkToneDatabase::class.java,
            "inktone.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
         .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
         .fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideBookDao(db: InkToneDatabase): BookDao = db.bookDao()

    @Provides
    fun provideBookmarkDao(db: InkToneDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideSearchDao(db: InkToneDatabase): SearchDao = db.searchDao()

    @Provides
    fun provideProgressDao(db: InkToneDatabase): ProgressDao = db.progressDao()

    @Provides
    fun provideReadingProgressDao(db: InkToneDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideSentenceCacheDao(db: InkToneDatabase): SentenceCacheDao = db.sentenceCacheDao()

    @Provides
    fun providePronunciationRuleDao(db: InkToneDatabase): PronunciationRuleDao = db.pronunciationRuleDao()

    @Provides
    fun provideAnnotationDao(db: InkToneDatabase): AnnotationDao = db.annotationDao()

    @Provides
    fun provideHighlightDao(db: InkToneDatabase): HighlightDao = db.highlightDao()

    @Provides
    fun provideReadingSessionDao(db: InkToneDatabase): ReadingSessionDao = db.readingSessionDao()

    @Provides
    fun provideRecentBookDao(db: InkToneDatabase): RecentBookDao = db.recentBookDao()

    @Provides
    fun provideBookProgressDao(db: InkToneDatabase): BookProgressDao = db.bookProgressDao()

    @Provides
    @Singleton
    fun provideBookRepository(impl: BookRepositoryImpl): BookRepository = impl

    // ── TTS Providers ──────────────────────────────────────

    @Provides
    @Singleton
    @IntoSet
    fun providePiperTtsProvider(impl: PiperTtsProvider): TtsProvider = impl

    @Provides
    @Singleton
    @IntoSet
    fun provideEdgeTtsProvider(impl: EdgeTtsProvider): TtsProvider = impl

    @Provides
    @Singleton
    fun provideTtsRepository(
        impl: TtsRepositoryImpl
    ): TtsRepository = impl

    @Provides
    @Singleton
    fun provideAudioServiceLauncher(impl: AudioServiceLauncherImpl): AudioServiceLauncher = impl
}
