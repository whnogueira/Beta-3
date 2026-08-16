package com.example.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class VehicleDaoMock : VehicleDao {
    private val vehicles = mutableListOf<VehicleEntity>()
    private val flow = MutableStateFlow<List<VehicleEntity>>(emptyList())

    override fun getAllVehicles(): Flow<List<VehicleEntity>> = flow

    override suspend fun getAllVehiclesList(): List<VehicleEntity> = vehicles.toList()

    override suspend fun getVehicleById(id: Long): VehicleEntity? {
        return vehicles.find { it.id == id }
    }

    override suspend fun getVehicleCount(): Int = vehicles.size

    override suspend fun insertVehicle(vehicle: VehicleEntity): Long {
        val id = if (vehicle.id > 0) vehicle.id else (vehicles.size + 1).toLong()
        val saved = vehicle.copy(id = id)
        vehicles.add(saved)
        flow.value = vehicles.toList()
        return id
    }

    override suspend fun updateVehicle(vehicle: VehicleEntity) {
        val index = vehicles.indexOfFirst { it.id == vehicle.id }
        if (index >= 0) {
            vehicles[index] = vehicle
            flow.value = vehicles.toList()
        }
    }

    override suspend fun deleteVehicleById(id: Long) {
        vehicles.removeAll { it.id == id }
        flow.value = vehicles.toList()
    }

    override suspend fun clearAllVehicles() {
        vehicles.clear()
        flow.value = emptyList()
    }
}
