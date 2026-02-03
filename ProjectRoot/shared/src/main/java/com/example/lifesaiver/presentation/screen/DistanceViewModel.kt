package com.example.lifesaiver.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifesaiver.core.call.CallTransportType
import com.example.lifesaiver.core.location.DistanceTrend
import com.example.lifesaiver.core.location.DistanceMeasurementSource
import com.example.lifesaiver.core.location.HybridDistanceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

data class DistanceUiState(
    val distanceMeters: Float? = null,
    val measurementSource: DistanceMeasurementSource = DistanceMeasurementSource.NONE,
    val isPrecisionMode: Boolean = false,
    val trend: DistanceTrend = DistanceTrend.Unknown,
    val statusMessage: String = ""
)

class DistanceViewModel(
    private val hybridDistanceManager: HybridDistanceManager
) : ViewModel() {

    val uiState: StateFlow<DistanceUiState> = hybridDistanceManager.locationState
        .scan(DistanceUiState()) { previousState, newLoc ->
            val currentDist = newLoc.distanceMeters
            val previousDist = previousState.distanceMeters

            val newTrend = when {
                currentDist == null || previousDist == null -> DistanceTrend.Unknown
                currentDist < previousDist - 0.5 -> DistanceTrend.Approaching
                currentDist > previousDist + 0.5 -> DistanceTrend.Receding
                else -> DistanceTrend.Unknown
            }

            DistanceUiState(
                distanceMeters = currentDist,
                measurementSource = newLoc.measurementSource,
                isPrecisionMode = newLoc.isPrecisionMode,
                trend = newTrend,
                statusMessage = newLoc.statusMessage
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DistanceUiState()
        )

    fun setTargetPeerId(peerIdHex: String?) {
        hybridDistanceManager.setTargetPeerId(peerIdHex)
    }

    fun setActiveTransport(transport: CallTransportType) {
        hybridDistanceManager.setActiveTransport(transport)
    }

    fun setUwbCapability(localSupported: Boolean, peerSupported: Boolean) {
        hybridDistanceManager.setUwbCapability(localSupported, peerSupported)
    }

    fun start() {
        hybridDistanceManager.start()
    }

    fun stop() {
        hybridDistanceManager.stop()
    }
}
