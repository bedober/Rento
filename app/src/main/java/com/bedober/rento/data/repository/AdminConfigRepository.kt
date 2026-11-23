package com.bedober.rento.data.repository

import com.bedober.rento.domain.model.AppConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminConfigRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions
) {
    fun observe(): Flow<Result<AppConfig>> = callbackFlow {
        val registration = firestore.collection("app_config").document("global")
            .addSnapshotListener { snapshot, error ->
                if (error != null) trySend(Result.failure(error))
                else trySend(Result.success(snapshot?.toObject(AppConfig::class.java) ?: AppConfig()))
            }
        awaitClose { registration.remove() }
    }

    suspend fun save(values: Map<String, Any>): Result<Unit> = try {
        require(values.keys.none { it in setOf("pesapalConsumerKey", "pesapalConsumerSecret", "facebookClientToken", "atApiKey") }) {
            "Provider secrets must be configured in Firebase Secret Manager, never stored in Firestore."
        }
        functions.getHttpsCallable("updateAppConfig").call(values).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun testPesapal(): Result<Boolean> = try {
        val response = functions.getHttpsCallable("testPesapalConnection").call().await()
        Result.success((response.data as? Map<*, *>)?.get("success") == true)
    } catch (e: Exception) { Result.failure(e) }
}
