package com.example.lifesaiver.ui.navigation

sealed class AppRoute(val route: String) {
    data object ModeGate : AppRoute("mode_gate")
    data object StandbyStatus : AppRoute("standby_status")
    data object EmergencyBeacon : AppRoute("emergency_beacon")
    data object PTTLink : AppRoute("ptt_link")
    data object RescueChat : AppRoute("rescue_chat")
}
