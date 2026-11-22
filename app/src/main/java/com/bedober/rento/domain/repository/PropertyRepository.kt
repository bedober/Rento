package com.bedober.rento.domain.repository

import com.bedober.rento.domain.model.Property
import com.bedober.rento.domain.model.RentalUnit
import kotlinx.coroutines.flow.Flow

interface PropertyRepository {
    fun observeProperties(ownerUid: String): Flow<List<Property>>
    suspend fun saveProperty(property: Property)
    suspend fun archiveProperty(propertyId: String)
    fun observeUnits(propertyId: String): Flow<List<RentalUnit>>
    suspend fun saveUnit(unit: RentalUnit)
}
