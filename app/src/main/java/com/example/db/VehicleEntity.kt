package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.VehicleSpec
import org.json.JSONArray

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String = "",
    val model: String = "",
    val year: Int = 0,
    val engine: String = "",
    val displacementCc: Int = 0,
    val cylinders: Int = 4,
    val weightKg: Double,
    val testWeightKg: Double = 0.0,
    val drive: String = "Dianteira (FWD)",
    val transmissionName: String = "",
    val tireWidthMm: Int,
    val tireAspect: Int,
    val rimInches: Int,
    val gearRatiosJson: String,
    val finalDrive: Double,
    val isExampleVehicle: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun toSpec(): VehicleSpec {
        val ratios = mutableListOf<Double>()
        try {
            val arr = JSONArray(gearRatiosJson)
            for (i in 0 until arr.length()) {
                ratios.add(arr.getDouble(i))
            }
        } catch (e: Exception) {
            ratios.addAll(listOf(3.55, 1.95, 1.28, 0.89, 0.71))
        }

        return VehicleSpec(
            id = id,
            name = name,
            brand = brand,
            model = model,
            year = year,
            engine = engine,
            displacementCc = displacementCc,
            cylinders = cylinders,
            weightKg = weightKg,
            testWeightKg = testWeightKg,
            drive = drive,
            transmissionName = transmissionName,
            tireWidthMm = tireWidthMm,
            tireAspect = tireAspect,
            rimInches = rimInches,
            gearRatios = ratios,
            finalDrive = finalDrive,
            isExampleVehicle = isExampleVehicle,
            createdAtMs = createdAtMs
        )
    }

    companion object {
        fun fromSpec(spec: VehicleSpec): VehicleEntity {
            val jsonArr = JSONArray()
            for (r in spec.gearRatios) {
                jsonArr.put(r)
            }

            return VehicleEntity(
                id = spec.id,
                name = spec.name,
                brand = spec.brand,
                model = spec.model,
                year = spec.year,
                engine = spec.engine,
                displacementCc = spec.displacementCc,
                cylinders = spec.cylinders,
                weightKg = spec.weightKg,
                testWeightKg = spec.testWeightKg,
                drive = spec.drive,
                transmissionName = spec.transmissionName,
                tireWidthMm = spec.tireWidthMm,
                tireAspect = spec.tireAspect,
                rimInches = spec.rimInches,
                gearRatiosJson = jsonArr.toString(),
                finalDrive = spec.finalDrive,
                isExampleVehicle = spec.isExampleVehicle,
                createdAtMs = if (spec.createdAtMs > 0) spec.createdAtMs else System.currentTimeMillis()
            )
        }
    }
}
