package com.example.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class DynoDaoMock : DynoRunDao {
    private val runs = mutableListOf<DynoRunEntity>()
    private val flow = MutableStateFlow<List<DynoRunEntity>>(emptyList())

    override suspend fun insertRun(run: DynoRunEntity): Long {
        val id = (runs.size + 1).toLong()
        val saved = run.copy(id = id)
        runs.add(0, saved)
        flow.value = runs.take(10)
        return id
    }

    override fun getLast10Runs(): Flow<List<DynoRunEntity>> = flow

    override suspend fun getRunById(id: Long): DynoRunEntity? {
        return runs.find { it.id == id }
    }

    override suspend fun deleteRunById(id: Long) {
        runs.removeAll { it.id == id }
        flow.value = runs.take(10)
    }

    override suspend fun clearAllRuns() {
        runs.clear()
        flow.value = emptyList()
    }
}
