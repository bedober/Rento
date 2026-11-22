package com.bedober.rento.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedPropertyEntity::class, CachedUnitEntity::class, CachedRentEntity::class],
    version = 1,
    exportSchema = true
)
abstract class RentoDatabase : RoomDatabase() {
    abstract fun rentoDao(): RentoDao
}
