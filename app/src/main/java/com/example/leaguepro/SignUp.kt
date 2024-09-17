package com.example.leaguepro

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class SignUp : AppCompatActivity() {

    private lateinit var edtFullname: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var edtConfirmPws: EditText
    private lateinit var btnSignUp: Button
    private lateinit var btnGoBack: Button
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mDbRef: DatabaseReference
    private lateinit var edtUserType: Spinner
    private lateinit var edtPswButton: ImageView
    private lateinit var edtConfirmPswButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        edtFullname = findViewById(R.id.edt_fullname)
        edtEmail = findViewById(R.id.edt_email)
        edtPassword = findViewById(R.id.edt_psw)
        edtConfirmPws = findViewById(R.id.edt_confirmpsw)
        btnSignUp = findViewById(R.id.btnSignUp)
        btnGoBack=findViewById(R.id.btnBack)
        mAuth = FirebaseAuth.getInstance()
        edtPswButton = findViewById(R.id.psw_eye_button)
        edtConfirmPswButton = findViewById(R.id.confirm_psw_eye_button)

        edtUserType = findViewById(R.id.user_type)
        // Create an ArrayAdapter using the string array and a default spinner layout.
        ArrayAdapter.createFromResource(
            this,
            R.array.user_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears.
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner.
            edtUserType.adapter = adapter
        }

        // Toggle password visibility
        setupPasswordToggle(edtPassword, edtPswButton)
        setupPasswordToggle(edtConfirmPws, edtConfirmPswButton)

            btnSignUp.setOnClickListener {
                val fullName = edtFullname.text.toString()
                val email = edtEmail.text.toString()
                val password = edtPassword.text.toString()
                val confirmPassword = edtConfirmPws.text.toString()
                val userType = edtUserType.selectedItem.toString()

                signup(userType, fullName, email, password, confirmPassword)
            }

        btnGoBack.setOnClickListener{val intent = Intent(this, Login::class.java)
            startActivity(intent)}
    }


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

    private fun signup(userType: String, fullName: String, email: String, password: String, confirmPassword: String) {
        // Reset errors
        edtFullname.error = null
        edtEmail.error = null
        edtPassword.error = null
        edtConfirmPws.error = null


        // Validate inputs
        var valid = true

        if (fullName.isEmpty()) {
            edtFullname.error = "Please enter your full name"
            valid = false
        }

        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        if (email.isEmpty()) {
            edtEmail.error = "Please enter your email"
            valid = false
        } else if (!email.matches(emailPattern.toRegex())) {
            edtEmail.error = "Please enter a valid email address"
            valid = false
        }


        if (password.isEmpty()) {
            edtPassword.error = "Please enter your password"
            valid = false
        }  else if (password.length<6) {
            edtPassword.error = "Please enter at least 6 characters"
            valid = false
        }

        if (confirmPassword.isEmpty()) {
            edtConfirmPws.error = "Please confirm your password"
            valid = false
        }

        if (password != confirmPassword) {
            edtConfirmPws.error = "Passwords do not match"
            valid = false
        }

        if (!valid) {
            return
        }

        // Proceed with Firebase authentication
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Save user to the database
                    addUserToDatabase(userType, fullName, email, mAuth.currentUser?.uid!!)

                    val intent = Intent(this@SignUp, Login::class.java)
                    startActivity(intent)
                    finish()

                } else {
                    Toast.makeText(this, "Sign Up error", Toast.LENGTH_SHORT).show()
                }
            }
    }



    private fun addUserToDatabase(userType: String, fullName: String, email: String, uid: String) {
        // recupero il riferimento del db
        mDbRef = FirebaseDatabase.getInstance().getReference()
        // tramite il riferiemnto aggiungo un elemento
        mDbRef.child("users").child(uid).setValue(User(userType,fullName, email, uid))
    }

}