package com.example.leaguepro

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerViewMessages: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageButton
    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var messagesList: MutableList<Message>
    private lateinit var leagueId: String
    private lateinit var mDbRef: DatabaseReference
    private lateinit var userId: String
    private lateinit var username: String
    private var leagueOwnerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerViewMessages = findViewById(R.id.recyclerViewMessages)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)

        leagueId = intent.getStringExtra("league_id")!!
        userId = UserInfo.userId!! // Ottieni l'ID dell'utente corrente
        mDbRef = FirebaseDatabase.getInstance().reference
        // Carica il nome dell'utente dal database
        loadUsername()

        // Recupera l'ID del proprietario della lega
        leagueId.let { id ->
            mDbRef.child("leagues").child(id).child("leagueManager").get()
                .addOnSuccessListener { snapshot ->
                    leagueOwnerId = snapshot.getValue(String::class.java)
                    messagesList = mutableListOf()
                    messagesAdapter = MessagesAdapter(messagesList, userId, leagueOwnerId)
                    recyclerViewMessages.adapter = messagesAdapter
                    recyclerViewMessages.layoutManager = LinearLayoutManager(this)
                }
            buttonSend.setOnClickListener {
                sendMessage()
            }
            listenForMessages()
            // Trova il pulsante di ritorno e aggiungi il listener
            val backButton: ImageButton = findViewById(R.id.back_button)
            backButton.setOnClickListener {
                // Chiudi l'attività o torna indietro
                onBackPressed()
            }

        }
    }
    private fun sendMessage() {
        val messageText = editTextMessage.text.toString()
        if (messageText.isBlank()) return

        val messageId = mDbRef.child("leagues").child(leagueId).child("messages").push().key!!
        val message = Message(
            messageId = messageId,
            userId = userId,
            username = username,
            message = messageText,
            timestamp = System.currentTimeMillis()
        )

        mDbRef.child("leagues").child(leagueId).child("messages").child(messageId).setValue(message)
            .addOnCompleteListener {
            if (it.isSuccessful) {
                editTextMessage.text.clear()
                recyclerViewMessages.scrollToPosition(messagesList.size - 1)
            }
        }
    }

    private fun listenForMessages() {
        mDbRef.child("leagues").child(leagueId).child("messages").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java)
                // Controlla che il messaggio non sia nullo e che non sia vuoto
                if (message != null && message.message.isNotBlank()) {
                    messagesList.add(message)
                    messagesAdapter.notifyItemInserted(messagesList.size - 1)
                    recyclerViewMessages.scrollToPosition(messagesList.size - 1)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    private fun loadUsername() {
        // Esegui una query per ottenere il fullname dell'utente
        mDbRef.child("users").child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                username = snapshot.child("fullname").value as String
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Errore nel recupero del nome utente", Toast.LENGTH_SHORT).show()
        }
    }
}
