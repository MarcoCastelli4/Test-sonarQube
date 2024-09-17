package com.example.leaguepro

data class Player (
    var name: String?=null,
    var role: String? = null,
    var birthday: String? = null,
    var uid: String? = null,
    var tournaments: Map<String,PlayerPerformance>? = mapOf()
)
    data class PlayerPerformance (
        val goals: Int? = 0,
        val yellowCards: Int? = 0,
        val redCards: Int? = 0)


