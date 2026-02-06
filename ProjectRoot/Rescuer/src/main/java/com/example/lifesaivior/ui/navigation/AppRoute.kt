package com.example.lifesaivior.ui.navigation

sealed class AppRoute(val route: String) {
    // survivor
    data object SurvivorProfile : AppRoute("survivor_profile")
    data object SurvivorStandby : AppRoute("survivor_standby")
    data object SurvivorEmergency : AppRoute("survivor_emergency")
    data object SurvivorPTT : AppRoute("survivor_ptt")
    data object SurvivorChat : AppRoute("survivor_chat")

    // rescuer
    data object RescuerStandby : AppRoute("rescuer_standby")
    data object RescuerPTT : AppRoute("rescuer_ptt")
    data object RescuerChat : AppRoute("rescuer_chat")
    data object RescuerEmergency : AppRoute("rescuer_emergency")
    data object RescuerSurvivorDb : AppRoute("rescuer/survivor_db")
    data object RescuerMeshMap : AppRoute("rescuer/mesh_map")
}
