package com.example.db

import android.content.Context
import com.example.model.DynoPoint
import com.example.model.DynoResult
import com.example.model.TestDataSource
import com.example.model.VehicleSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class DynoRepository(
    private val runDao: DynoRunDao,
    private val vehicleDao: VehicleDao
) {

    val last10Runs: Flow<List<DynoRunEntity>> = runDao.getLast10Runs()

    val allVehicles: Flow<List<VehicleSpec>> = vehicleDao.getAllVehicles().map { entities ->
        entities.map { it.toSpec() }
    }

    /**
     * Seeds the pre-registered example vehicle (Vectra 2.2 8V 1999 — Exemplo) on fresh install.
     * Prevents duplicate insertion on subsequent app launches and respects user deletion.
     */
    suspend fun ensureExampleVehicleCreated(context: Context) {
        val prefs = context.getSharedPreferences("dyno_prefs", Context.MODE_PRIVATE)
        val isSeeded = prefs.getBoolean("example_vehicle_seeded_v1", false)

        if (!isSeeded) {
            prefs.edit().putBoolean("example_vehicle_seeded_v1", true).apply()

            val count = vehicleDao.getVehicleCount()
            if (count == 0) {
                val newId = vehicleDao.insertVehicle(
                    VehicleEntity.fromSpec(VehicleSpec.VECTRA_EXAMPLE)
                )
                if (newId > 0) {
                    prefs.edit().putLong("active_vehicle_id", newId).apply()
                }
            }
        }
    }

    fun getActiveVehicleId(context: Context): Long {
        val prefs = context.getSharedPreferences("dyno_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("active_vehicle_id", 0L)
    }

    fun setActiveVehicleId(context: Context, vehicleId: Long) {
        val prefs = context.getSharedPreferences("dyno_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("active_vehicle_id", vehicleId).apply()
    }

    suspend fun saveVehicle(spec: VehicleSpec): Long {
        return if (spec.id > 0L) {
            vehicleDao.updateVehicle(VehicleEntity.fromSpec(spec))
            spec.id
        } else {
            vehicleDao.insertVehicle(VehicleEntity.fromSpec(spec))
        }
    }

    suspend fun deleteVehicle(id: Long) {
        vehicleDao.deleteVehicleById(id)
    }

    suspend fun getAllVehiclesList(): List<VehicleSpec> {
        return vehicleDao.getAllVehiclesList().map { it.toSpec() }
    }

    suspend fun saveRun(result: DynoResult): Long {
        val jsonArray = JSONArray()
        for (p in result.points) {
            val obj = JSONObject().apply {
                put("rpm", p.rpm)
                put("cv", p.powerCv)
                put("kgfm", p.torqueKgfm)
                put("kmh", p.speedKmh)
                put("tSec", p.timeSeconds)
                put("force", p.forceN)
                put("accel", p.accelMps2)
            }
            jsonArray.put(obj)
        }

        val duration = result.points.lastOrNull()?.timeSeconds ?: 0.0

        val entity = DynoRunEntity(
            timestampMs = System.currentTimeMillis(),
            vehicleName = result.vehicleSpec.name,
            selectedGear = result.selectedGear,
            maxSpeedKmh = result.maxSpeedKmh,
            peakPowerCv = result.peakPowerCv,
            peakPowerRpm = result.peakPowerRpm,
            peakTorqueKgfm = result.peakTorqueKgfm,
            peakTorqueRpm = result.peakTorqueRpm,
            durationSeconds = duration,
            pointsJson = jsonArray.toString(),
            dataSource = if (result.dataSource == TestDataSource.REAL_TEST_DATA) "REAL_TEST_DATA" else "DEMO_DATA"
        )

        return runDao.insertRun(entity)
    }

    suspend fun getRunAsDynoResult(runId: Long, spec: VehicleSpec): DynoResult? {
        val entity = runDao.getRunById(runId) ?: return null
        val points = mutableListOf<DynoPoint>()

        try {
            val array = JSONArray(entity.pointsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                points.add(
                    DynoPoint(
                        rpm = obj.optInt("rpm"),
                        powerCv = obj.optDouble("cv"),
                        torqueKgfm = obj.optDouble("kgfm"),
                        speedKmh = obj.optDouble("kmh"),
                        timeSeconds = obj.optDouble("tSec"),
                        forceN = obj.optDouble("force"),
                        accelMps2 = obj.optDouble("accel")
                    )
                )
            }
        } catch (e: Exception) {
            // fallback
        }

        val source = if (entity.dataSource == "REAL_TEST_DATA") TestDataSource.REAL_TEST_DATA else TestDataSource.DEMO_DATA

        val firstP = points.firstOrNull() ?: DynoPoint(0, 0.0, 0.0, 0.0, 0.0)
        val lastP = points.lastOrNull() ?: DynoPoint(0, 0.0, 0.0, 0.0, 0.0)
        val maxAccel = points.maxByOrNull { it.accelMps2 }?.accelMps2 ?: 0.0
        val maxForce = points.maxByOrNull { it.forceN }?.forceN ?: 0.0

        return DynoResult(
            peakPowerCv = entity.peakPowerCv,
            peakPowerRpm = entity.peakPowerRpm,
            peakTorqueKgfm = entity.peakTorqueKgfm,
            peakTorqueRpm = entity.peakTorqueRpm,
            maxSpeedKmh = entity.maxSpeedKmh,
            selectedGear = entity.selectedGear,
            vehicleSpec = spec.copy(name = entity.vehicleName),
            points = points,
            dataSource = source,
            initialSpeedKmh = (firstP.speedKmh * 10).toInt() / 10.0,
            finalSpeedKmh = (lastP.speedKmh * 10).toInt() / 10.0,
            initialRpm = firstP.rpm,
            finalRpm = lastP.rpm,
            maxAccelMps2 = (maxAccel * 100).toInt() / 100.0,
            maxForceN = (maxForce * 10).toInt() / 10.0,
            totalSampleCount = points.size,
            avgSensorFrequencyHz = if (entity.durationSeconds > 0) points.size / entity.durationSeconds else 10.0
        )
    }
}
