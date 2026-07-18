package com.inktone.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.inktone.data.database.entity.PronunciationRule
import kotlinx.coroutines.flow.Flow

@Dao
interface PronunciationRuleDao {

    @Query("SELECT * FROM pronunciation_rules ORDER BY createdAt DESC")
    fun getAllRulesFlow(): Flow<List<PronunciationRule>>

    @Insert
    suspend fun insertRule(rule: PronunciationRule)

    @Update
    suspend fun updateRule(rule: PronunciationRule)

    @Delete
    suspend fun deleteRule(rule: PronunciationRule)

    @Query("UPDATE pronunciation_rules SET isActive = :isActive WHERE id = :id")
    suspend fun toggleRuleActive(id: Int, isActive: Boolean)

    @Query("SELECT * FROM pronunciation_rules WHERE isActive = 1")
    suspend fun getActiveRules(): List<PronunciationRule>

    @Query("SELECT * FROM pronunciation_rules")
    suspend fun getAllSync(): List<PronunciationRule>
}
