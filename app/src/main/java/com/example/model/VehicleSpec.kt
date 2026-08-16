package com.example.model

import kotlin.math.PI

data class VehicleSpec(
    val id: Long = 0L,
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val year: Int = 0,
    val engine: String = "",
    val displacementCc: Int = 0,
    val cylinders: Int = 4,
    val weightKg: Double = 0.0,
    val testWeightKg: Double = 0.0,
    val drive: String = "Dianteira (FWD)",
    val transmissionName: String = "",
    val tireWidthMm: Int = 185,
    val tireAspect: Int = 70,
    val rimInches: Int = 14,
    val gearRatios: List<Double> = listOf(3.55, 1.95, 1.28, 0.89, 0.71),
    val finalDrive: Double = 3.74,
    val isExampleVehicle: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val isConfigured: Boolean
        get() = name.isNotBlank() && (weightKg > 0.0 || testWeightKg > 0.0)

    /**
     * Calculated tire outer diameter in millimeters.
     * Diameter = Rim(in) * 25.4 + 2 * (Width(mm) * Aspect / 100)
     * For 185/70 R14: 14 * 25.4 + 2 * (185 * 0.70) = 355.6 + 259.0 = 614.6 mm
     */
    val tireDiameterMm: Double
        get() {
            val rimMm = rimInches * 25.4
            val sidewallMm = tireWidthMm * (tireAspect / 100.0)
            return rimMm + (2.0 * sidewallMm)
        }

    /**
     * Calculated tire circumference in meters.
     * For 185/70 R14: (614.6 / 1000) * PI ≈ 1.9308... m ≈ 1.931 m
     */
    val tireCircumferenceMeters: Double
        get() = (tireDiameterMm / 1000.0) * PI

    /**
     * Dynamic wheel rolling radius in meters.
     */
    val tireRadiusMeters: Double
        get() = tireDiameterMm / 2000.0

    /**
     * Total effective mass for dyno calculations (car mass + driver/fluids or preset test weight).
     */
    val effectiveTestMassKg: Double
        get() = when {
            testWeightKg > 0.0 -> testWeightKg
            weightKg > 0.0 -> weightKg + 100.0
            else -> 0.0
        }

    fun getGearRatio(gearIndex: Int): Double {
        return if (gearIndex in gearRatios.indices) gearRatios[gearIndex] else gearRatios.getOrElse(2) { 1.28 }
    }

    /**
     * Total transmission reduction ratio for given gear index (0 = 1ª, 1 = 2ª, etc.)
     */
    fun totalRatio(gearIndex: Int): Double {
        return getGearRatio(gearIndex) * finalDrive
    }

    /**
     * Convert speed (m/s) to engine RPM for a selected gear index.
     * Speed = (RPM / 60) * 2 * PI * r / totalRatio
     * => RPM = (Speed * totalRatio * 60) / (2 * PI * r)
     */
    fun calculateRpm(speedMs: Double, gearIndex: Int): Double {
        val totalRatio = totalRatio(gearIndex)
        val radius = tireRadiusMeters
        if (radius <= 0.0 || totalRatio <= 0.0) return 0.0
        val wheelRps = speedMs / (2 * PI * radius)
        return wheelRps * 60.0 * totalRatio
    }

    companion object {
        /**
         * Pre-registered example vehicle: Chevrolet Vectra 2.2 8V 1999
         */
        val VECTRA_EXAMPLE = VehicleSpec(
            id = 0L,
            name = "Vectra 2.2 8V 1999 — Exemplo",
            brand = "Chevrolet",
            model = "Vectra",
            year = 1999,
            engine = "2.2 8V",
            displacementCc = 2200,
            cylinders = 4,
            weightKg = 1359.0,
            testWeightKg = 1380.0,
            drive = "Dianteira (FWD)",
            transmissionName = "F17 CCW",
            tireWidthMm = 185,
            tireAspect = 70,
            rimInches = 14,
            gearRatios = listOf(3.55, 1.95, 1.28, 0.89, 0.71),
            finalDrive = 3.74,
            isExampleVehicle = true
        )
    }
}
