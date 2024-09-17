package com.example.leaguepro

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

private lateinit var mDbRef: DatabaseReference

class User {
    var userType: String?=null
    var fullname: String? = null
    var email: String? = null
    var uid: String? = null


    constructor() {}

    constructor(userType:String?, fullname: String?, email: String?, uid: String?) {
        this.fullname = fullname
        this.email = email
        this.uid = uid
        this.userType=userType
    }



}
