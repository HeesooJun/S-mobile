package com.example.lifesaiver.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifesaiver.presentation.sensor.SensorDefaults
import com.example.lifesaiver.presentation.sensor.SensorStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ActionType {
    Power,
    Disconnect,
    Chat,
    Count
}

data class PTTLinkUiState(
    val isSensorExpanded: Boolean = false,
    val sensorStatus: Map<Int, SensorStatus> = emptyMap(),
    val isPowerSaving: Boolean = false,
    val expandedAction: ActionType? = null,
    val showDoubleTapHint: Boolean = false
)

class PTTLinkViewModel : ViewModel() {
    val sensorItems = SensorDefaults.probes

    private val _uiState = MutableStateFlow(PTTLinkUiState())
    val uiState: StateFlow<PTTLinkUiState> = _uiState.asStateFlow()

    fun setSensorExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isSensorExpanded = expanded)
    }

    fun updateSensorStatus(type: Int, status: SensorStatus) {
        val updated = _uiState.value.sensorStatus.toMutableMap()
        updated[type] = status
        _uiState.value = _uiState.value.copy(sensorStatus = updated)
    }

    fun togglePowerSaving() {
        val next = !_uiState.value.isPowerSaving
        _uiState.value = _uiState.value.copy(isPowerSaving = next)
        onActionSelected(ActionType.Power)
    }

    fun onActionSelected(action: ActionType?) {
        _uiState.value = _uiState.value.copy(expandedAction = action)
        if (action == ActionType.Chat || action == ActionType.Disconnect || action == ActionType.Power) {
            _uiState.value = _uiState.value.copy(showDoubleTapHint = true)
            viewModelScope.launch {
                delay(3500)
                _uiState.value = _uiState.value.copy(showDoubleTapHint = false)
            }
        } else {
            _uiState.value = _uiState.value.copy(showDoubleTapHint = false)
        }
    }
}
