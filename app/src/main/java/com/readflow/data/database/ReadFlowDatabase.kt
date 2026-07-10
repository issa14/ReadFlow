package com.readflow.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.readflow.data.database.entity.AnnotationEntity
import com.readflow.data.database.entity.BookEntity
import com.readflow.data.database.entity.BookmarkEntity
import com.readflow.data.database.entity.HighlightEntity
import com.readflow.data.database.entity.ProgressEntity
import com.readflow.data.database.entity.PronunciationRule
import com.readflow.data.database.entity.ReadingProgress
import com.readflow.data.database.entity.ReadingSessionEntity
import com.readflow.data.database.entity.SentenceCacheEntity
import com.readflow.data.database.entity.SentenceFts

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                bookId TEXT NOT NULL,
                chapterIndex INTEGER NOT NULL,
                sentenceIndex INTEGER NOT NULL,
                text TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks (bookId)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS sentence_fts USING fts4(bookId, chapterIndex, sentenceIndex, text)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reading_progress (
                bookId TEXT NOT NULL PRIMARY KEY,
                chapterIndex INTEGER NOT NULL DEFAULT 0,
                sentenceIndex INTEGER NOT NULL DEFAULT 0,
                characterOffset INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}

@Database(
    entities = [
        BookEntity::class,
        ProgressEntity::class,
        BookmarkEntity::class,
        SentenceFts::class,
        ReadingProgress::class,
        SentenceCacheEntity::class,
        PronunciationRule::class,
        AnnotationEntity::class,
        HighlightEntity::class,
        ReadingSessionEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class ReadFlowDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun searchDao(): SearchDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun sentenceCacheDao(): SentenceCacheDao
    abstract fun pronunciationRuleDao(): PronunciationRuleDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun highlightDao(): HighlightDao
    abstract fun readingSessionDao(): ReadingSessionDao
}
