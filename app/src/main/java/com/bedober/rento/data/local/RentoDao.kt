package com.bedober.rento.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RentoDao {
    @Query("SELECT * FROM cached_properties WHERE ownerUid = :uid ORDER BY name")
    fun observeProperties(uid: String): Flow<List<CachedPropertyEntity>>

    @Query("SELECT * FROM cached_units WHERE propertyId = :propertyId ORDER BY number")
    fun observeUnits(propertyId: String): Flow<List<CachedUnitEntity>>

    @Query("SELECT * FROM cached_rent_items WHERE tenantUid = :uid ORDER BY dueAt DESC")
    fun observeTenantRent(uid: String): Flow<List<CachedRentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProperties(items: List<CachedPropertyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnits(items: List<CachedUnitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRent(items: List<CachedRentEntity>)

    @Query("SELECT * FROM cached_properties WHERE pendingSync = 1")
    suspend fun pendingProperties(): List<CachedPropertyEntity>

    @Query("SELECT * FROM cached_units WHERE pendingSync = 1")
    suspend fun pendingUnits(): List<CachedUnitEntity>

    @Query("UPDATE cached_properties SET pendingSync = 0 WHERE id = :id")
    suspend fun markPropertySynced(id: String)
}
