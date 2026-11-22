package com.bedober.rento.domain.model

enum class WalletTransactionType { TOPUP, RENT_PAYMENT, CARETAKER_COMMISSION, WITHDRAWAL, REFUND }
enum class WalletTransactionStatus { PENDING, PROCESSING, COMPLETED, FAILED, REVERSED }

data class WalletTransaction(
    val id: String,
    val userId: String,
    val type: WalletTransactionType,
    val amountUgx: Long,
    val feeUgx: Long = 0,
    val status: WalletTransactionStatus,
    val providerTrackingId: String? = null,
    val createdAtEpochMillis: Long
)
