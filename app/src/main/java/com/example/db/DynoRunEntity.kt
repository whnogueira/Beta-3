package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dyno_runs")
data class DynoRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val vehicleName: String,
    val selectedGear: Int,
    val maxSpeedKmh: Double,
    val peakPowerCv: Double,
    val peakPowerRpm: Int,
    val peakTorqueKgfm: Double,
    val peakTorqueRpm: Int,
    val durationSeconds: Double,
    val pointsJson: String,
    val dataSource: String
)
