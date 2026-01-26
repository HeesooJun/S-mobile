package com.example.lifesaiver.presentation.screen

import androidx.lifecycle.ViewModel
import com.example.lifesaiver.presentation.sensor.SensorDefaults
import com.example.lifesaiver.presentation.sensor.SensorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StandbyStatusUiState(
    val isSensorExpanded: Boolean = false,
    val sensorStatus: Map<Int, SensorStatus> = emptyMap()
)

class StandbyStatusViewModel : ViewModel() {
    val sensorItems = SensorDefaults.probes

    private val _uiState = MutableStateFlow(StandbyStatusUiState())
    val uiState: StateFlow<StandbyStatusUiState> = _uiState.asStateFlow()

    fun setSensorExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isSensorExpanded = expanded)
    }

    fun updateSensorStatus(type: Int, status: SensorStatus) {
        val updated = _uiState.value.sensorStatus.toMutableMap()
        updated[type] = status
        _uiState.value = _uiState.value.copy(sensorStatus = updated)
    }
}
