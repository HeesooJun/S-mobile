package com.example.lifesaiver.core.ble

import kotlin.math.roundToInt

internal class RssiKalmanFilter(
    processNoise: Double = DEFAULT_PROCESS_NOISE,
    measurementNoise: Double = DEFAULT_MEASUREMENT_NOISE,
    private val initialErrorCovariance: Double = DEFAULT_INITIAL_ERROR_COVARIANCE
) {
    private var processNoise: Double = normalizeNoise(processNoise)
    private var measurementNoise: Double = normalizeNoise(measurementNoise)
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

    fun updateNoise(
        processNoise: Double = this.processNoise,
        measurementNoise: Double = this.measurementNoise,
        resetErrorCovariance: Boolean = false
    ) {
        this.processNoise = normalizeNoise(processNoise)
        this.measurementNoise = normalizeNoise(measurementNoise)
        if (resetErrorCovariance) {
            errorCovariance = initialErrorCovariance
        }
    }

    fun reset() {
        estimate = 0.0
        errorCovariance = initialErrorCovariance
        initialized = false
    }

    companion object {
        // Higher Q + lower R => faster response, less smoothing.
        private const val DEFAULT_PROCESS_NOISE = 2.2
        private const val DEFAULT_MEASUREMENT_NOISE = 7.0
        private const val DEFAULT_INITIAL_ERROR_COVARIANCE = 4.0
        private const val MIN_NOISE = 1e-6

        private fun normalizeNoise(value: Double): Double = value.coerceAtLeast(MIN_NOISE)
    }
}
