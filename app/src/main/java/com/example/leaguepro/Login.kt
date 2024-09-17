package com.example.leaguepro;

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.jakewharton.threetenabp.AndroidThreeTen

class Login : AppCompatActivity() {
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnSignUp: TextView
    private lateinit var mAuth: FirebaseAuth
    private lateinit var edtPswButton: ImageView
    private lateinit var mDbRef: DatabaseReference
    private lateinit var btnGuest: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this)
        setContentView(R.layout.activity_login)

        edtEmail = findViewById(R.id.edt_email)
        edtPassword = findViewById(R.id.edt_psw)
        btnLogin = findViewById(R.id.login_button)
        btnSignUp = findViewById(R.id.signup_text)
        edtPswButton = findViewById(R.id.psw_eye_button)
        btnGuest=findViewById(R.id.btnGuest)
        mAuth = FirebaseAuth.getInstance()
        mDbRef = FirebaseDatabase.getInstance().getReference()

        btnGuest.setOnClickListener {
            UserInfo.logged=false
            UserInfo.userId=""
            UserInfo.team_id=""
            UserInfo.userType=""
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        btnSignUp.setOnClickListener {
            val intent = Intent(this, SignUp::class.java)
            startActivity(intent)
        }

        btnLogin.setOnClickListener {
            val email = edtEmail.text.toString()
            val password = edtPassword.text.toString()
            if (email.isEmpty()) {
                edtEmail.error = "Email is required"
                edtEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                edtPassword.error = "Password is required"
                edtPassword.requestFocus()
                return@setOnClickListener
            }

            login(email, password)
        }
        // Toggle password visibility
        setupPasswordToggle(edtPassword, edtPswButton)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPasswordToggle(editText: EditText, eyeButton: ImageView) {
        eyeButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Show password
                    editText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    editText.setSelection(editText.text.length)
                    eyeButton.setImageResource(R.drawable.eye_open)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Hide password
                    editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    editText.setSelection(editText.text.length)
                    eyeButton.setImageResource(R.drawable.eye_closed)
                }
            }
            true
        }
    }

    private fun login(email: String, password: String) {
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = mAuth.currentUser
                    user?.let {
                        // Read user information from Firestore
                        mDbRef.child("users").child(it.uid).get()
                            .addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    val type = document.child("userType").getValue(String::class.java)
                                    // Update userType global
                                    UserInfo.userType = type
                                    UserInfo.userId=mAuth.currentUser?.uid
                                    UserInfo.logged=true
                                    if (type==getString(R.string.TeamManager)){
                                    mAuth.currentUser?.uid?.let { it1 -> findTeamId(it1)} }

                                    // Navigate to MainActivity
                                    val intent = Intent(this@Login, MainActivity::class.java)
                                    startActivity(intent)
                                    finish() // Finish login activity so user can't go back to it
                                } else {
                                    Toast.makeText(this@Login, "User data not found", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { exception ->
                                Toast.makeText(this@Login, "Error getting user data", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this@Login, "Invalid email or password", Toast.LENGTH_SHORT).show()
                }
            }
    }
    private fun findTeamId (userId: String) {
        mDbRef.child("teams").orderByChild("team_manager").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Trova il team associato all'ID dell'utente corrente
                        for (teamSnapshot in snapshot.children) {
                            val id = teamSnapshot.child("id").getValue().toString()
                            if (id != "") {
                                // Aggiorna variabile globale
                                UserInfo.team_id = id
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    TODO("Not yet implemented")
                }
            })
    }
}



