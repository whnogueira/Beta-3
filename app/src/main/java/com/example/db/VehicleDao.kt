package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY createdAtMs ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY createdAtMs ASC")
    suspend fun getAllVehiclesList(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicleById(id: Long): VehicleEntity?

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun getVehicleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity): Long

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicleById(id: Long)

    @Query("DELETE FROM vehicles")
    suspend fun clearAllVehicles()
}
