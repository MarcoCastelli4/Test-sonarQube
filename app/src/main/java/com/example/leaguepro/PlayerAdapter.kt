package com.example.leaguepro

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PlayerAdapter(
    val context: Context,
    val playerList: ArrayList<Player>,
    val dbRef: DatabaseReference,
    val mAuth: FirebaseAuth
): RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val player_name: TextView = itemView.findViewById(R.id.player_name)
        val player_role: TextView = itemView.findViewById(R.id.player_role)
        val player_birthday: TextView = itemView.findViewById(R.id.player_birthday)
        val edit: ImageView = itemView.findViewById(R.id.edit)
        val delete: ImageView= itemView.findViewById(R.id.delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view: View = LayoutInflater.from(context).inflate(R.layout.card_player_layout, parent, false)
        return PlayerViewHolder(view)
    }

    override fun getItemCount(): Int {
        return playerList.size
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val currentPlayer = playerList[position]
        holder.player_name.text = currentPlayer.name
        holder.player_role.text = currentPlayer.role
        holder.player_birthday.text = currentPlayer.birthday


        // hold edit button
        holder.edit.setOnClickListener {
            showEditPlayerPopup(holder.itemView, currentPlayer)
        }

        holder.delete.setOnClickListener{
            showDeleteConfirmationDialog(currentPlayer)
        }
    }

    private fun showDeleteConfirmationDialog(player: Player) {
        val builder = AlertDialog.Builder(context, R.style.CustomAlertDialog)
        builder.setTitle("Delete Player")
            .setMessage("Are you sure you want to delete ${player.name} from your team?")
            .setPositiveButton("Delete") { dialog, _ ->
                checkTeamInLeagueAndDeletePlayer(player)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun checkTeamInLeagueAndDeletePlayer(player: Player) {
        val leaguesTeamRef = dbRef.child("leagues_team")
        leaguesTeamRef.orderByChild("team_id").equalTo(UserInfo.team_id).addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(context, "Cannot delete player from a team that has joined a league!", Toast.LENGTH_LONG).show()
                } else {
                    deletePlayerFromTeam(player)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Error checking league teams: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun deletePlayerFromTeam(player: Player) {
        val teamId = UserInfo.team_id
        val playerUid = player.uid

        if (teamId != null && playerUid != null) {
            dbRef.child("teams").child(teamId).child("players").child(playerUid).removeValue()
                .addOnSuccessListener {
                    Toast.makeText(context, "Player deleted successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to delete player: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "Invalid team or player ID", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showEditPlayerPopup(view: View, currentPlayer: Player) {
        (context as Activity).findViewById<RecyclerView>(R.id.playersRecyclerView).visibility = View.GONE

        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.add_player, null)
        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        // Set the title to "Edit Player"
        val titleTextView: TextView = popupView.findViewById(R.id.add_player_title)
        titleTextView.text = "Edit Player"

        popupWindow.setOnDismissListener {
            (context as Activity).findViewById<RecyclerView>(R.id.playersRecyclerView).visibility = View.VISIBLE
        }

        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
        initializePopupFields(popupView,currentPlayer)
        setupPopupListeners(popupView, popupWindow,currentPlayer)
    }

    private fun initializePopupFields(popupView: View, player: Player) {
        val edtPlayerName = popupView.findViewById<EditText>(R.id.edt_player_name)
        val edtPlayerRole = popupView.findViewById<Spinner>(R.id.edt_player_role)
        val edtPlayerBirthday = popupView.findViewById<TextView>(R.id.edt_player_birthday)

        edtPlayerName.setText(player.name)
        edtPlayerBirthday.text = player.birthday

        ArrayAdapter.createFromResource(
            context,
            R.array.role_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            edtPlayerRole.adapter = adapter
        }

        val rolesArray = context.resources.getStringArray(R.array.role_types)
        val roleIndex = rolesArray.indexOf(player.role)
        if (roleIndex >= 0) {
            edtPlayerRole.setSelection(roleIndex)
        }

        popupView.findViewById<ImageView>(R.id.btn_birthday).setOnClickListener {
            datePickerDialog(edtPlayerBirthday, player.birthday)
        }
    }

    private fun setupPopupListeners(popupView: View, popupWindow: PopupWindow, player: Player) {
        popupView.findViewById<ImageView>(R.id.btn_close).setOnClickListener {
            popupWindow.dismiss()
        }

        popupView.findViewById<Button>(R.id.btn_save).setOnClickListener {
            val playerName = popupView.findViewById<EditText>(R.id.edt_player_name)
            val playerRole = popupView.findViewById<Spinner>(R.id.edt_player_role).selectedItem.toString()
            val playerBirthday = popupView.findViewById<TextView>(R.id.edt_player_birthday)

            if (!validateFields(
                    playerName,
                    playerBirthday
                )
            ) {
                return@setOnClickListener
            }

            val updatedPlayer = Player(
                uid = player.uid,
                name = playerName.text.toString(),
                role = playerRole,
                birthday = playerBirthday.text.toString(),
                tournaments= player.tournaments
            )
            updatePlayerInDatabase(updatedPlayer)
            popupWindow.dismiss()
        }
    }

    private fun validateFields(playername: EditText?, playerbirthday: TextView?): Boolean {
        var valid = true
        // Check each field and set an error message if it's empty
        if (playername != null) {
            if (playername.text.toString().isEmpty()) {
                playername.error = "Please enter player name"
                valid = false
            }
        }
        if (playerbirthday != null) {
            if (!isValidDateRange(playerbirthday.text.toString())) {
                    playerbirthday.error = "Please select a player birthday"
                    valid = false
            }
        }
        return valid
    }
    private fun isValidDateRange(dateRange: String): Boolean {
        // Define the regex pattern for the date range
        val dateRangePattern = Regex("""\b\d{2}/\d{2}/\d{4}\b""")
        // Check if the input string matches the pattern
        return dateRangePattern.matches(dateRange)
    }
    private fun updatePlayerInDatabase(player: Player) {
        val teamId = UserInfo.team_id
        if (teamId != null) {
            player.uid?.let { dbRef.child("teams").child(teamId).child("players").child(it).setValue(player) }
        }
    }
    private fun datePickerDialog(edtPlayingPeriod: TextView, currentBirthday: String?) {
        // Definisci il formato della data
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault() // Imposta il fuso orario locale

        // Se la data di nascita esiste, convertila in millisecondi
        val defaultDateInMillis = currentBirthday?.let {
            try {
                dateFormat.parse(it)?.time ?: MaterialDatePicker.todayInUtcMilliseconds()
            } catch (e: ParseException) {
                MaterialDatePicker.todayInUtcMilliseconds()
            }
        } ?: MaterialDatePicker.todayInUtcMilliseconds()

        val constraintsBuilder = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now()) // Consenti solo date nel passato

        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select birthday")
            .setSelection(defaultDateInMillis) // Imposta la data di nascita corrente come selezione iniziale
            .setCalendarConstraints(constraintsBuilder.build())
            .setTheme(R.style.CustomDatePicker)

        val datePicker = builder.build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectedDateString = dateFormat.format(Date(selection ?: 0))
            edtPlayingPeriod.text = selectedDateString
        }
        datePicker.show((context as FragmentActivity).supportFragmentManager, "date_picker")
    }


}