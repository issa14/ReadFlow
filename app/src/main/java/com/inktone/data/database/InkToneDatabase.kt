package com.inktone.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inktone.data.database.entity.AnnotationEntity
import com.inktone.data.database.entity.BookEntity
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import com.inktone.data.database.entity.ProgressEntity
import com.inktone.data.database.entity.PronunciationRule
import com.inktone.data.database.entity.ReadingProgress
import com.inktone.data.database.entity.ReadingSessionEntity
import com.inktone.data.database.entity.SentenceCacheEntity
import com.inktone.data.database.entity.SentenceFts

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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sentence_cache ADD COLUMN chapterTitle TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS book_progress")
        db.execSQL("DROP TABLE IF EXISTS recent_books")
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
    version = 12,
    exportSchema = false
)
abstract class InkToneDatabase : RoomDatabase() {
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
