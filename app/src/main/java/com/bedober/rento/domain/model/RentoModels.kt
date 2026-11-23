package com.bedober.rento.domain.model

data class RentoUser(
    val uid: String = "", val name: String = "", val phone: String = "",
    val email: String = "", val role: String = "TENANT", val profilePhoto: String? = null,
    val nationalId: String? = null, val createdAt: Long = 0L, val kycStatus: String = "PENDING"
)
data class Tenant(val id: String = "", val name: String = "", val phone: String = "", val email: String = "", val unitId: String = "", val landlordId: String = "")
data class Wallet(val userId: String = "", val balanceUgx: Long = 0L, val currency: String = "UGX")
data class RentBill(
    val id: String = "", val tenantId: String = "", val landlordId: String = "", val unitId: String = "",
    val period: String = "", val amountUgx: Long = 0L, val lateFeeUgx: Long = 0L,
    val dueAt: Long = 0L, val status: String = "PENDING"
)
data class Maintenance(
    val id: String = "", val propertyId: String = "", val unitId: String = "", val tenantId: String = "",
    val description: String = "", val priority: String = "LOW", val status: String = "OPEN",
    val mediaUrls: List<String> = emptyList(), val createdAt: Long = 0L
)
data class AppConfig(
    val appName: String = "Rento", val googleLoginEnabled: Boolean = false,
    val facebookLoginEnabled: Boolean = false, val phoneLoginEnabled: Boolean = true,
    val emailLoginEnabled: Boolean = true, val pesapalEnv: String = "sandbox",
    val pesapalFeePercent: Double = 0.0, val lateFeePercent: Double = 0.0,
    val lateFeeAfterDays: Int = 3, val caretakerCommission: Double = 0.0,
    val withdrawalDailyLimit: Long = 5000000L, val minTopUp: Long = 1000L,
    val maxTopUp: Long = 50000000L, val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "", val supportPhone: String = ""
)
