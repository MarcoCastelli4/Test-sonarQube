package com.example.leaguepro

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class MyTeamFragment : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    private lateinit var mDbRef: DatabaseReference
    private lateinit var mAuth: FirebaseAuth

    private lateinit var edtplayer_name: EditText
    private lateinit var edtplayer_role: Spinner
    private lateinit var edtplayer_birthday: TextView
    private lateinit var edtteam_name: TextView
    private lateinit var addPlayerContainer: ConstraintLayout
    private lateinit var addPlayer: ImageView
    private lateinit var team_bin: ImageView

    private lateinit var playerList: ArrayList<Player>
    private lateinit var adapter: PlayerAdapter
    private lateinit var playerRecyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_team, container, false)
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MyTeamFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView(view)
    }

    private fun setupView(view: View) {
        setupFirebase()
        edtteam_name = view.findViewById(R.id.team_name)
        team_bin = view.findViewById(R.id.team_bin)
        addPlayerContainer = view.findViewById(R.id.add_player_container)

        mAuth.currentUser?.uid?.let {
            updateTeamName(it, edtteam_name) { teamId ->
                UserInfo.team_id = teamId
                toggleVisibility()
            }
        }

        setupLeagueRecyclerView(view)

        if (UserInfo.userType == getString(R.string.TeamManager)) {
            fetchTeamFromDatabase()
        }

        addPlayer = view.findViewById(R.id.add_player_icon)
        addPlayer.setOnClickListener {
            showAddPlayerPopup(view)
        }

        edtteam_name.setOnClickListener {
            showEditTeamNameDialog()
        }

        team_bin.setOnClickListener {
            UserInfo.team_id?.let { it1 -> showConfirmationDialog(it1) }
        }
    }

    private fun showEditTeamNameDialog() {
        val editText = EditText(requireContext()).apply {
            setText(edtteam_name.text)
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogPositive)
            .setTitle("Change Team Name")
            .setView(editText)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = editText.text.toString().trim()
            if (newName.isEmpty()) {
                editText.error = "Team name cannot be empty"
            } else {
                edtteam_name.text = newName
                val currentTeamId = UserInfo.team_id

                if (currentTeamId.isNullOrEmpty() || edtteam_name.text == "Click to create Team") {
                    // Crea un nuovo team se non esiste
                    createNewTeamInDatabase(newName, mAuth.currentUser?.uid) { teamId ->
                        UserInfo.team_id = teamId
                        toggleVisibility()
                    }
                } else {
                    // Aggiorna il team esistente
                    addOrUpdateTeamToDatabase(newName, mAuth.currentUser?.uid, currentTeamId) { teamId ->
                        UserInfo.team_id = teamId
                        toggleVisibility()
                    }
                }
                dialog.dismiss()
            }
        }
    }

    private fun createNewTeamInDatabase(name: String, managerId: String?, callback: (String) -> Unit) {
        val teamId = mDbRef.child("teams").push().key // Genera un nuovo team ID
        if (teamId != null) {
            val newTeam = Team(
                id = teamId,
                name = name,
                team_manager = managerId,
                players = mapOf(),  // Inizialmente senza giocatori
                tournaments = mapOf()  // Inizialmente senza tornei
            )

            mDbRef.child("teams").child(teamId).setValue(newTeam)
                .addOnSuccessListener {
                    callback(teamId)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to create team: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }



    private fun showConfirmationDialog(teamId: String) {
        val builder = AlertDialog.Builder(context, R.style.CustomAlertDialog)
        builder.setTitle("Delete confirm")
            .setMessage("Are you sure to delete your team and all players?")
            .setPositiveButton("Delete") { dialog, _ ->
                removeTeamFromDatabase(teamId) { teamId ->
                    UserInfo.team_id = teamId
                    toggleVisibility()
                    edtteam_name.text = "Click to create Team"
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun setupLeagueRecyclerView(view: View) {
        playerList = ArrayList()
        playerRecyclerView = view.findViewById(R.id.playersRecyclerView)
        playerRecyclerView.layoutManager = LinearLayoutManager(context)
        playerRecyclerView.setHasFixedSize(true)
        adapter = PlayerAdapter(requireContext(), playerList, mDbRef, mAuth)
        playerRecyclerView.adapter = adapter
    }

    private fun fetchTeamFromDatabase() {
        mDbRef.child("teams").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                playerList.clear()
                for (postSnapshot in snapshot.children) {
                    val teamManagerId = postSnapshot.child("team_manager").getValue(String::class.java)
                    if (teamManagerId == mAuth.currentUser?.uid) {
                        val playersSnapshot = postSnapshot.child("players")
                        for (playerSnapshot in playersSnapshot.children) {
                            val player = playerSnapshot.getValue(Player::class.java)
                            player?.let { playerList.add(it) }
                        }
                    } else {
                        Log.d("FirebaseData", "Invalid team data for manager ID: $teamManagerId")
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateTeamName(userId: String, teamNameTextView: TextView, callback: (String) -> Unit) {
        mDbRef.child("teams").orderByChild("team_manager").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (teamSnapshot in snapshot.children) {
                            val id = teamSnapshot.child("id").getValue(String::class.java)
                            if (id != null && id.isNotEmpty()) {
                                teamNameTextView.text = teamSnapshot.child("name").getValue(String::class.java)
                                callback(id)
                            }
                        }
                    } else {
                        callback("")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setupFirebase() {
        mAuth = FirebaseAuth.getInstance()
        mDbRef = FirebaseDatabase.getInstance().getReference()
    }

    private fun showAddPlayerPopup(view: View) {
        (context as Activity).findViewById<RecyclerView>(R.id.playersRecyclerView).visibility = View.GONE

        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.add_player, null)
        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setOnDismissListener {
            (context as Activity).findViewById<RecyclerView>(R.id.playersRecyclerView).visibility = View.VISIBLE
        }

        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
        initializePopupFields(popupView)
        setupPopupListeners(popupView, popupWindow)
    }

    private fun initializePopupFields(popupView: View) {
        edtplayer_name = popupView.findViewById(R.id.edt_player_name)
        edtplayer_role = popupView.findViewById(R.id.edt_player_role)
        edtplayer_birthday = popupView.findViewById(R.id.edt_player_birthday)

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.role_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            edtplayer_role.adapter = adapter
        }

        popupView.findViewById<ImageView>(R.id.btn_birthday).setOnClickListener {
            datePickerDialog(edtplayer_birthday)
        }
    }

    private fun setupPopupListeners(popupView: View, popupWindow: PopupWindow) {
        popupView.findViewById<ImageView>(R.id.btn_close).setOnClickListener {
            popupWindow.dismiss()
        }

        popupView.findViewById<Button>(R.id.btn_save).setOnClickListener {
            savePlayer(popupWindow)
        }
    }

    private fun savePlayer(popupWindow: PopupWindow) {
        val playername = edtplayer_name.text.toString()
        val playerrole = edtplayer_role.selectedItem.toString()
        val playerbirthday = edtplayer_birthday.text.toString()

        if (!validateFields(playername, playerbirthday)) {
            return
        }

        addPlayerToTeam(playername, playerrole, playerbirthday, UserInfo.team_id) {
            Toast.makeText(context, "Player added successfully!", Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }
    }

    private fun validateFields(playername: String, playerbirthday: String): Boolean {
        var isValid = true
        if (playername.isEmpty()) {
            edtplayer_name.error = "Name cannot be empty"
            isValid = false
        }
        if (playerbirthday.isEmpty()) {
            edtplayer_birthday.error = "Birthday cannot be empty"
            isValid = false
        }
        return isValid
    }

    private fun addPlayerToTeam(name: String, role: String, birthday: String, teamId: String?, onSuccess: () -> Unit) {
        teamId?.let {
            val playerId = mDbRef.child("teams").child(it).child("players").push().key
            if (playerId != null) {
                val player = Player(uid = playerId, name = name, role = role, birthday = birthday, tournaments= mapOf())
                mDbRef.child("teams").child(it).child("players").child(playerId).setValue(player)
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Failed to add player: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun addOrUpdateTeamToDatabase(name: String, managerId: String?, teamId: String?, callback: (String) -> Unit) {
        if (teamId != null) {
            // Aggiorna solo il nome del team nel database
            val updates = mapOf<String, Any>(
                "name" to name
            )

            mDbRef.child("teams").child(teamId).updateChildren(updates)
                .addOnSuccessListener {
                    callback(teamId)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to update team name: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun removeTeamFromDatabase(teamId: String, onSuccess: (String) -> Unit) {
        mDbRef.child("teams").child(teamId).removeValue()
            .addOnSuccessListener {
                onSuccess(teamId)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to remove team: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun datePickerDialog(dateTextView: TextView) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
            dateTextView.text = selectedDate
        }, year, month, day).show()
    }

    private fun toggleVisibility() {
        if (UserInfo.team_id.isNullOrEmpty()) {
            addPlayerContainer.visibility = View.GONE
        } else {
            addPlayerContainer.visibility = View.VISIBLE
        }
    }
}
