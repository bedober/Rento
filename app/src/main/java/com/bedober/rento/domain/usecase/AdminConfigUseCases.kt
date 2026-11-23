package com.bedober.rento.domain.usecase

import com.bedober.rento.data.repository.AdminConfigRepository
import com.bedober.rento.domain.model.AppConfig
import javax.inject.Inject

class GetConfig @Inject constructor(private val repository: AdminConfigRepository) {
    operator fun invoke() = repository.observe()
}
class SaveConfig @Inject constructor(private val repository: AdminConfigRepository) {
    suspend operator fun invoke(values: Map<String, Any>) = repository.save(values)
}
class TestPesapalConnection @Inject constructor(private val repository: AdminConfigRepository) {
    suspend operator fun invoke() = repository.testPesapal()
}
