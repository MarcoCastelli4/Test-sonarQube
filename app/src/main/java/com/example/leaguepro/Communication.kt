package com.example.leaguepro

data class Communication(
    val communicationId: String = "",
    val text: String = "",
    val date: String = "",
    val leagueId: String = ""
) {
    // Costruttore senza argomenti è necessario per Firebase
    constructor() : this("", "", "","")
}
