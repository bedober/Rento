package com.bedober.rento.domain.model

data class Property(
    val id: String,
    val name: String,
    val location: String,
    val type: PropertyType,
    val description: String = "",
    val unitIds: List<String> = emptyList(),
    val archived: Boolean = false
)

enum class PropertyType { APARTMENT, HOUSE, HOSTEL, COMMERCIAL, SINGLE_ROOM }

enum class UnitStatus { VACANT, OCCUPIED, UNDER_MAINTENANCE }

data class RentalUnit(
    val id: String,
    val propertyId: String,
    val number: String,
    val bedrooms: Int,
    val bathrooms: Int,
    val monthlyRentUgx: Long,
    val depositUgx: Long,
    val status: UnitStatus
)
