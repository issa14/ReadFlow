package com.inktone.data.database

import androidx.room.*
import com.inktone.data.database.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY chapterIndex ASC, sentenceIndex ASC")
    fun getAnnotationsForBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    suspend fun getAnnotationsForChapter(bookId: String, chapterIndex: Int): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Update
    suspend fun updateAnnotation(annotation: AnnotationEntity)

    @Delete
    suspend fun deleteAnnotation(annotation: AnnotationEntity)
}
