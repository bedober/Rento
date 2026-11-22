package com.bedober.rento.presentation.wallet

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class PesapalCheckout(val trackingId: String, val redirectUrl: String, val status: String)

class WalletFunctionsDataSource(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    suspend fun createTopUp(amountUgx: Long): PesapalCheckout {
        require(amountUgx in 1_000L..50_000_000L)
        val result = functions.getHttpsCallable("createPesapalOrder")
            .call(mapOf("amount" to amountUgx)).await()
        val data = result.data as? Map<*, *> ?: error("Invalid payment response")
        return PesapalCheckout(
            trackingId = data["trackingId"] as? String ?: error("Missing tracking ID"),
            redirectUrl = data["redirectUrl"] as? String ?: error("Missing checkout URL"),
            status = data["status"] as? String ?: "PENDING"
        )
    }

    suspend fun payRent(invoiceId: String): Map<*, *> {
        val result = functions.getHttpsCallable("payRentFromWallet")
            .call(mapOf("invoiceId" to invoiceId)).await()
        return result.data as? Map<*, *> ?: error("Invalid rent payment response")
    }
}
