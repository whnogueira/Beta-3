package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DynoRunDao {
    @Query("SELECT * FROM dyno_runs ORDER BY timestampMs DESC LIMIT 10")
    fun getLast10Runs(): Flow<List<DynoRunEntity>>

    @Query("SELECT * FROM dyno_runs WHERE id = :id")
    suspend fun getRunById(id: Long): DynoRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: DynoRunEntity): Long

    @Query("DELETE FROM dyno_runs WHERE id = :id")
    suspend fun deleteRunById(id: Long)

    @Query("DELETE FROM dyno_runs")
    suspend fun clearAllRuns()
}
