package com.example.lifesaiver.core.ble

import kotlin.math.roundToInt

internal class RssiKalmanFilter(
    private val processNoise: Double = 0.8,
    private val measurementNoise: Double = 12.0,
    private val initialErrorCovariance: Double = 4.0
) {
    private var estimate: Double = 0.0
    private var errorCovariance: Double = initialErrorCovariance
    private var initialized: Boolean = false

    fun update(rssi: Int): Int {
        val measurement = rssi.toDouble()
        if (!initialized) {
            estimate = measurement
            initialized = true
            return measurement.roundToInt()
        }

        errorCovariance += processNoise
        val gain = errorCovariance / (errorCovariance + measurementNoise)
        estimate += gain * (measurement - estimate)
        errorCovariance = (1.0 - gain) * errorCovariance
        return estimate.roundToInt()
    }

    fun reset() {
        estimate = 0.0
        errorCovariance = initialErrorCovariance
        initialized = false
    }
}
