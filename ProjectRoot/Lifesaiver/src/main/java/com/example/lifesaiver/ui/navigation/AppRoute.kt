package com.example.lifesaiver.ui.navigation

sealed class AppRoute(val route: String) {
    // survivor
    data object SurvivorProfile : AppRoute("survivor_profile")
    data object SurvivorStandby : AppRoute("survivor_standby")
    data object SurvivorStandbySettings : AppRoute("survivor_standby_settings")
    data object SurvivorEmergency : AppRoute("survivor_emergency")
    data object SurvivorPTT : AppRoute("survivor_ptt")
    data object SurvivorChat : AppRoute("survivor_chat")
    data object Settings : AppRoute("settings")
}
