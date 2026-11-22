package com.bedober.rento.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_properties", indices = [Index("ownerUid")])
data class CachedPropertyEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val name: String,
    val location: String,
    val type: String,
    val updatedAt: Long,
    val pendingSync: Boolean = false
)

@Entity(tableName = "cached_units", indices = [Index("propertyId")])
data class CachedUnitEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val number: String,
    val monthlyRentUgx: Long,
    val depositUgx: Long,
    val status: String,
    val updatedAt: Long,
    val pendingSync: Boolean = false
)

@Entity(tableName = "cached_rent_items", indices = [Index("tenantUid"), Index("status")])
data class CachedRentEntity(
    @PrimaryKey val id: String,
    val tenantUid: String,
    val landlordUid: String,
    val unitId: String,
    val period: String,
    val amountUgx: Long,
    val status: String,
    val dueAt: Long,
    val updatedAt: Long,
    val pendingSync: Boolean = false
)
