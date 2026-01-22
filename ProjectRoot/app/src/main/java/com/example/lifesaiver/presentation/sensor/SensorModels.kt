package com.example.lifesaiver.presentation.sensor

import android.hardware.Sensor

data class SensorProbe(
    val label: String,
    val type: Int
)

enum class SensorStatus {
    Unsupported,
    Checking,
    Active,
    NoData
}

object SensorDefaults {
    val probes = listOf(
        SensorProbe("가속도", Sensor.TYPE_ACCELEROMETER),
        SensorProbe("자이로", Sensor.TYPE_GYROSCOPE),
        SensorProbe("지자기", Sensor.TYPE_MAGNETIC_FIELD),
        SensorProbe("조도", Sensor.TYPE_LIGHT),
        SensorProbe("근접", Sensor.TYPE_PROXIMITY)
    )
}
