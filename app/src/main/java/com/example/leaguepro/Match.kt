package com.example.leaguepro

class Match() {

    var id: String? = null
    var team1: Team? = null
    var team2: Team? = null
    var date: String? = null
    var time: String? = null
    var result1: Int? = null
    var result2: Int? = null
    var scorersTeam1: List<String>? = null
    var scorersTeam2: List<String>? = null
    var yellowCards: List<String>? = null
    var redCards: List<String>? = null

    constructor(id: String?, team1: Team?, team2: Team?, date: String?, time: String?, result1: Int?, result2: Int?,scorersTeam1: List<String>?,scorersTeam2: List<String>?,yellowCards: List<String>?,redCards: List<String>?) : this() {
        this.id = id
        this.team1 = team1
        this.team2 = team2
        this.date = date
        this.time = time
        this.result1 = result1
        this.result2 = result2
        this.scorersTeam1=scorersTeam1
        this.scorersTeam2=scorersTeam2
        this.yellowCards=yellowCards
        this.redCards=redCards
    }
}
