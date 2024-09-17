package com.example.leaguepro

class League {

    var uid: String?=null
    var name: String? = null
    var address: String? = null
    var latitude: Double? = null
    var longitude: Double? = null
    var level: Float? = null
    var description: String? = null
    var entryfee: String? = null
    var prize: String? = null
    var restrictions: String? = null
    var playingPeriod: String?=null
    var leagueManager: String? = null
    var maxNumberTeam: Float?= null


    constructor(
        uid: String?,
        name: String?, address: String?,
        latitude: Double,
        longitude: Double, level: Float?,
        description: String?,
        entryfee: String?,
        prize: String?,
        restrictions: String?,
        playingPeriod: String?,
        leagueManager: String?,
        maxNumberTeam: Float?) {
        this.uid=uid
        this.name = name
        this.address=address
        this.latitude=latitude
        this.longitude=longitude
        this.level=level
        this.description=description
        this.entryfee=entryfee
        this.restrictions=restrictions
        this.playingPeriod=playingPeriod
        this.leagueManager=leagueManager
        this.prize=prize
        this.maxNumberTeam=maxNumberTeam
    }

    // Costruttore vuoto richiesto da Firebase
    constructor() : this(
        null,null, null,0.0,0.0, 0.0f, null, null, null, null, null, null,0.0f
    )



}