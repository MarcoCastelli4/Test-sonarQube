package com.example.leaguepro

import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesAdapter(private val messagesList: List<Message>, private val currentUserId: String,private val leagueOwnerId: String?) :
    RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.message_text)
        val usernameText: TextView = itemView.findViewById(R.id.username_text)
        val timeText: TextView = itemView.findViewById(R.id.time_text)
        val messageLayout: LinearLayout = itemView.findViewById(R.id.message_layout)
        val fullMessageLayout: LinearLayout = itemView.findViewById(R.id.full_message_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messagesList[position]

        holder.messageText.text = message.message
        if(message.userId == leagueOwnerId) {
            holder.usernameText.text = "League Manager: ${message.username}"
        } else {
            holder.usernameText.text = message.username
        }
        holder.timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

        // Controlla se il messaggio è dell'utente corrente
        if (message.userId == currentUserId) {
            holder.fullMessageLayout.gravity = Gravity.END
            holder.messageLayout.setBackgroundResource(R.drawable.message_background_user)
        } else {
            holder.fullMessageLayout.gravity = Gravity.START
            holder.messageLayout.setBackgroundResource(R.drawable.message_background_other)

        }
    }
    override fun getItemCount() = messagesList.size
}
