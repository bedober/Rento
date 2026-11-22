package com.bedober.rento.domain.model

enum class UserRole { LANDLORD, TENANT, CARETAKER }

data class UserProfile(
    val uid: String,
    val name: String,
    val phoneE164: String,
    val email: String?,
    val role: UserRole,
    val profilePhotoUrl: String? = null,
    val kycVerified: Boolean = false
)
