package com.example.leaguepro

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

class MatchAdapter(
    private val matchList: List<Match>,
    private val leagueOwnerId: String,
    private val leagueId: String,
    private val mDbRef: DatabaseReference,
    private val context: Context
) : RecyclerView.Adapter<MatchAdapter.MatchViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_match_layout, parent, false)
        return MatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        val match = matchList[position]
        holder.tvStage.text = "League match"
        holder.tvMatchTime.text = "${match.date} ${match.time}"
        holder.tvTeam1.text = match.team1?.name
        holder.tvTeam1Score.text = match.result1?.toString()
        holder.tvTeam2.text = match.team2?.name
        holder.tvTeam2Score.text = match.result2?.toString()
        holder.tvStatus.visibility = if (match.result1 != null && match.result2 != null) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (match.result1 != null && match.result2 != null) {
                showMatchDetails(match.id!!, leagueId)
            } else {
                Toast.makeText(holder.itemView.context, "Match results are not yet available.", Toast.LENGTH_SHORT).show()
            }
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == leagueOwnerId) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener {
                showEditDialog(holder.itemView.context, match)
            }
        } else {
            holder.btnEdit.visibility = View.GONE
        }
    }

    private fun showMatchDetails(matchId: String, leagueId: String) {
        val fragment = MatchDetailsFragment.newInstance(matchId, leagueId)
        (context as AppCompatActivity).supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout_info, fragment) // Assicurati che il container del fragment sia corretto
            .addToBackStack(null)
            .commit()
    }
    //mostra dialogo per modificare risultato match, marcatori e cartellini
    private fun showEditDialog(context: Context, match: Match) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.edit_match, null)
        val etTeam1Score = dialogView.findViewById<EditText>(R.id.et_team1_score)
        val etTeam2Score = dialogView.findViewById<EditText>(R.id.et_team2_score)
        val team1name = dialogView.findViewById<TextView>(R.id.tv_team1_name)
        val team2name = dialogView.findViewById<TextView>(R.id.tv_team2_name)
        val llTeam1Scorers = dialogView.findViewById<LinearLayout>(R.id.ll_team1_scorers)
        val llTeam2Scorers = dialogView.findViewById<LinearLayout>(R.id.ll_team2_scorers)
        val saveButton = dialogView.findViewById<Button>(R.id.save_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val llYellowCards = dialogView.findViewById<LinearLayout>(R.id.ll_yellow_card_players)
        val llRedCards = dialogView.findViewById<LinearLayout>(R.id.ll_red_card_players)
        val btnAddYellowCard = dialogView.findViewById<Button>(R.id.btn_add_yellow_card)
        val btnAddRedCard = dialogView.findViewById<Button>(R.id.btn_add_red_card)

        val database = FirebaseDatabase.getInstance()
        val matchesRef = database.getReference("matches").child(leagueId).child(match.id!!)
        // Function to create a row for a scorer
        fun createScorerRow(scorerList: MutableList<String>, llScorers: LinearLayout, team: String) {
            llScorers.removeAllViews()
            for (i in scorerList.indices) {
                val scorer = scorerList[i]
                val row = LayoutInflater.from(context).inflate(R.layout.scorer_row, llScorers, false)
                val scorerTextView = row.findViewById<TextView>(R.id.scorer_text)
                scorerTextView.text = scorer
                scorerTextView.setOnClickListener {
                    showScorerSelectionDialog(context, scorerList, i, team,llScorers)
                }
                llScorers.addView(row)
            }
        }

        // Update scorer rows when score changes
        fun updateScorerRows() {
            val team1Score = etTeam1Score.text.toString().toIntOrNull() ?: 0
            val team2Score = etTeam2Score.text.toString().toIntOrNull() ?: 0

            // Imposto tante righe quanti sono i goal della squadra
            val team1Scorers = mutableListOf<String>().apply {
                repeat(team1Score) { add("Select player") }
            }
            val team2Scorers = mutableListOf<String>().apply {
                repeat(team2Score) { add("Select player") }
            }

            createScorerRow(team1Scorers, llTeam1Scorers, match.team1!!.id!!)
            createScorerRow(team2Scorers, llTeam2Scorers, match.team2!!.id!!)
        }
        // Pre-fill current teams and scores
        team1name.text = match.team1?.name
        team2name.text = match.team2?.name
        etTeam1Score.setText(match.result1?.toString())
        etTeam2Score.setText(match.result2?.toString())
        // Pre-popola marcatori e cartellini
        loadPreviousMatchDetails(match) { scorersTeam1, scorersTeam2, yellowCards, redCards ->
            // Pre-popola i marcatori
            createScorerRow(scorersTeam1.toMutableList(), llTeam1Scorers, match.team1!!.id!!)
            createScorerRow(scorersTeam2.toMutableList(), llTeam2Scorers, match.team2!!.id!!)

            // Pre-popola i cartellini gialli
            yellowCards.forEach { player ->
                addCardRow(context, llYellowCards, match.team1!!.id!!, match.team2!!.id!!, "yellow",listOf(player))
            }

            // Pre-popola i cartellini rossi
            redCards.forEach { player ->
                addCardRow(context, llRedCards, match.team1!!.id!!, match.team2!!.id!!, "red",listOf(player))
            }
        }

        // Gestisci aggiunta di cartellini gialli
        btnAddYellowCard.setOnClickListener {
            addCardRow(context, llYellowCards, match.team1!!.id!!, match.team2!!.id!!, "yellow")
        }

        // Gestisci aggiunta di cartellini rossi
        btnAddRedCard.setOnClickListener {
            addCardRow(context, llRedCards, match.team1!!.id!!, match.team2!!.id!!, "red")
        }

        // Set up text change listeners to update scorer rows
        etTeam1Score.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateScorerRows()
            }
        })

        etTeam2Score.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateScorerRows()
            }
        })

        val dialog = AlertDialog.Builder(context)
            .setTitle("Edit Match Result")
            .setView(dialogView)
            .create()

        saveButton.setOnClickListener {
            val team1Score = etTeam1Score.text.toString().toIntOrNull()
            val team2Score = etTeam2Score.text.toString().toIntOrNull()
           
            // salvataggio goal e cartellini gialli,rossi
            val yellowCardPlayers = mutableMapOf<String, Int>()
            val redCardPlayers = mutableMapOf<String, Int>()
            // Raccogliere i giocatori che hanno segnato
            val scorers = mutableMapOf<String, Int>()
            val scorersTeam1 = mutableListOf<String>()
            val scorersTeam2 = mutableListOf<String>()

            // Raccolta dei marcatori del team 1
            val team1ScorersCount = llTeam1Scorers.childCount
            var validTeam1ScorersCount = 0
            for (i in 0 until llTeam1Scorers.childCount) {
                val row = llTeam1Scorers.getChildAt(i)
                val scorerTextView = row.findViewById<TextView>(R.id.scorer_text)
                val scorerName = scorerTextView.text.toString()
                if (scorerName.isNotEmpty()&& scorerName!="Select player") {
                    scorers[scorerName] = scorers.getOrDefault(scorerName, 0) + 1
                    validTeam1ScorersCount++
                }
            }
            // Raccolta dei marcatori del team 2
            val team2ScorersCount = llTeam2Scorers.childCount
            var validTeam2ScorersCount = 0
            for (i in 0 until llTeam2Scorers.childCount) {
                val row = llTeam2Scorers.getChildAt(i)
                val scorerTextView = row.findViewById<TextView>(R.id.scorer_text)
                val scorerName = scorerTextView.text.toString()
                Log.d("llteam2","${llTeam2Scorers.childCount}")
                if (scorerName.isNotEmpty() && scorerName!="Select player") {
                    scorers[scorerName] = scorers.getOrDefault(scorerName, 0) + 1
                    validTeam2ScorersCount++
                }
            }

            // Verifica che tutti i marcatori siano stati selezionati
            if (validTeam1ScorersCount != team1ScorersCount || validTeam2ScorersCount != team2ScorersCount) {
                Toast.makeText(context, "All scorer fields must be filled!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener // Interrompi l'esecuzione del salvataggio
            }
            // Raccogliere i giocatori con cartellini gialli
            for (i in 0 until llYellowCards.childCount) {
                val row = llYellowCards.getChildAt(i)
                val spinner = row.findViewById<Spinner>(R.id.spinner_players)
                val selectedPlayerId = spinner.selectedItem as String

                // Aggiungi il giocatore alla mappa o incrementa il suo contatore
                yellowCardPlayers[selectedPlayerId] = yellowCardPlayers.getOrDefault(selectedPlayerId, 0) + 1
            }
            // Raccogliere i giocatori con cartellini rossi
            for (i in 0 until llRedCards.childCount) {
                val row = llRedCards.getChildAt(i)
                val spinner = row.findViewById<Spinner>(R.id.spinner_players)
                val selectedPlayerId = spinner.selectedItem as String

                // Aggiungi il giocatore alla mappa o incrementa il suo contatore
                redCardPlayers[selectedPlayerId] = redCardPlayers.getOrDefault(selectedPlayerId, 0) + 1
            }
            val teamRef1 = database.getReference("teams").child(match.team1!!.id!!)
            val teamRef2 = database.getReference("teams").child(match.team2!!.id!!)
            var operationsCount = 0
            val totalOperations = scorers.size + yellowCardPlayers.size + redCardPlayers.size

            fun saveMatchUpdates() {
                if (team1Score != null && team2Score != null) {
                    // Prima di aggiornare i risultati, rimuovi l'effetto dei risultati precedenti
                    if (match.result1 != null && match.result2 != null) {
                        updateLeagueTableAfterMatch(match, leagueId, isReverting = true)
                    }
                    val matchUpdates = mapOf(
                        "result1" to team1Score,
                        "result2" to team2Score,
                        "scorersTeam1" to scorersTeam1,
                        "scorersTeam2" to scorersTeam2,
                        "yellowCards" to yellowCardPlayers.keys.toList(),
                        "redCards" to redCardPlayers.keys.toList()
                    )

                    matchesRef.updateChildren(matchUpdates)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Result updated successfully!", Toast.LENGTH_SHORT).show()
                            // Update the local match object
                            match.result1 = team1Score
                            match.result2 = team2Score
                            notifyItemChanged(matchList.indexOf(match)) // Refresh item
                            updateLeagueTableAfterMatch(match, leagueId, false)
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Failed to update results.", Toast.LENGTH_SHORT)
                                .show()
                        }
                }
                dialog.dismiss()
            }
            fun onOperationComplete() {
                operationsCount++
                if (operationsCount >= totalOperations) {
                    saveMatchUpdates()
                }
            }
            // Funzione per aggiornare le performance di un giocatore
            fun updatePlayerStats(teamRef: DatabaseReference, playerId: String, goals: Int, yellowCards: Int, redCards: Int,callback: UpdateCallback) {
                val playerRef =
                    teamRef.child("players").child(playerId).child("tournaments").child(leagueId)
                playerRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                        // Ottieni i valori attuali
                        val currentGoals = mutableData.child("goals").getValue(Int::class.java) ?: 0
                        val currentYellowCards = mutableData.child("yellowCards").getValue(Int::class.java) ?: 0
                        val currentRedCards = mutableData.child("redCards").getValue(Int::class.java) ?: 0

                        // Somma i nuovi valori a quelli esistenti
                        mutableData.child("goals").value = currentGoals + goals
                        mutableData.child("yellowCards").value = currentYellowCards + yellowCards
                        mutableData.child("redCards").value = currentRedCards + redCards

                        return Transaction.success(mutableData)
                    }
                    override fun onComplete(databaseError: DatabaseError?, committed: Boolean, dataSnapshot: DataSnapshot?) {
                        if (databaseError != null) {
                            // Gestione errore
                            Toast.makeText(context, "Error updating player stats: ${databaseError.message}", Toast.LENGTH_SHORT).show()
                        }
                        callback.onUpdateComplete()
                    }
                })
            }
            // Aggiorna le performance per ogni giocatore con goal
            fun updateScorers() {
                scorers.forEach { (playerName, goals) ->
                    findPlayerIdByNameInTeams(listOf(match.team1!!.id!!, match.team2!!.id!!), playerName) { playerId ->
                        val teamPlayersRef =
                            FirebaseDatabase.getInstance().getReference("teams")
                                .child(match.team1!!.id!!).child("players")

                        // Controlla se il playerId esiste nei giocatori di questo team
                        teamPlayersRef.child(playerId!!)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(dataSnapshot: DataSnapshot) {
                                    if (dataSnapshot.exists()) {
                                        // Il playerId esiste tra i giocatori di questo team
                                        for(i in 0 until goals){ // gestione goal > 1
                                            scorersTeam1.add(playerName)
                                        }
                                        Log.d("player goals","player:${playerId}, goals: ${goals}")
                                        updatePlayerStats(teamRef1, playerId, goals, 0, 0, object : UpdateCallback {
                                            override fun onUpdateComplete() {
                                                onOperationComplete()
                                            }
                                        })

                                    } else {
                                        for(i in 0 until goals){ // gestione goal > 1
                                            scorersTeam2.add(playerName)
                                        }
                                        updatePlayerStats(teamRef2, playerId, goals, 0, 0, object : UpdateCallback {
                                            override fun onUpdateComplete() {
                                                onOperationComplete()
                                            }
                                        })
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.e("CheckPlayer", "Database error")
                                    onOperationComplete()
                                }
                            })
                    }
                }
            }
            // Aggiorna le performance per ogni giocatore con cartellini gialli
            fun updateYellowCards() {
                yellowCardPlayers.forEach { (playerName, yellowCards) ->
                    findPlayerIdByNameInTeams(
                        listOf(match.team1!!.id!!, match.team2!!.id!!),
                        playerName
                    ) { playerId ->
                        val teamPlayersRef = FirebaseDatabase.getInstance().getReference("teams")
                            .child(match.team1!!.id!!).child("players")

                        // Controlla se il playerId esiste nei giocatori di questo team
                        teamPlayersRef.child(playerId!!)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(dataSnapshot: DataSnapshot) {
                                    if (dataSnapshot.exists()) {
                                        // Il playerId esiste tra i giocatori di questo team
                                        updatePlayerStats(teamRef1, playerId, 0, yellowCards, 0, object : UpdateCallback {
                                            override fun onUpdateComplete() {
                                                onOperationComplete()
                                            }
                                        })
                                    } else {
                                        updatePlayerStats(teamRef2, playerId, 0, yellowCards, 0, object : UpdateCallback {
                                            override fun onUpdateComplete() {
                                                onOperationComplete()
                                            }
                                        })
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.e("CheckPlayer", "Database error")
                                }
                            })
                    }
                }
            }
            // Aggiorna le performance per ogni giocatore con cartellini rossi
            fun updateRedCards() {
                redCardPlayers.forEach { (playerName, redCards) ->
                    findPlayerIdByNameInTeams(
                        listOf(match.team1!!.id!!, match.team2!!.id!!),
                        playerName
                    ) { playerId ->
                        val teamPlayersRef =
                            FirebaseDatabase.getInstance().getReference("teams")
                                .child(match.team1!!.id!!).child("players")

                        // Controlla se il playerId esiste nei giocatori di questo team
                        teamPlayersRef.child(playerId!!)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(dataSnapshot: DataSnapshot) {
                                    if (dataSnapshot.exists()) {
                                        // Il playerId esiste tra i giocatori di questo team
                                        updatePlayerStats(teamRef1, playerId, 0, 0, redCards, object : UpdateCallback {
                                            override fun onUpdateComplete() {
                                                onOperationComplete()
                                            }
                                        })
                                    } else {
                                        updatePlayerStats(teamRef2, playerId, 0, 0, redCards, object : UpdateCallback {
                                            override fun onUpdateComplete() {
                                                onOperationComplete()
                                            }
                                        })
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.e("CheckPlayer", "Database error")
                                }
                            })
                    }
                }
            }

            // Avvia l'aggiornamento
            updateScorers()
            updateYellowCards()
            updateRedCards()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }


        dialog.show()
    }


    // carica le statistiche del match già modificato
    private fun loadPreviousMatchDetails(match: Match, callback: (List<String>, List<String>, List<String>, List<String>) -> Unit) {
        val matchRef = FirebaseDatabase.getInstance().getReference("matches").child(leagueId).child(match.id!!)
        matchRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val team1Scorers = snapshot.child("scorersTeam1").children.mapNotNull { it.getValue(String::class.java) }
                val team2Scorers = snapshot.child("scorersTeam2").children.mapNotNull { it.getValue(String::class.java) }
                val yellowCards = snapshot.child("yellowCards").children.mapNotNull { it.getValue(String::class.java) }
                val redCards = snapshot.child("redCards").children.mapNotNull { it.getValue(String::class.java) }

                callback(team1Scorers, team2Scorers, yellowCards, redCards)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to load previous match details", error.toException())
                callback(emptyList(), emptyList(), emptyList(), emptyList())
            }
        })
    }


    // cerca l'id del player di una delle due squadre del match passandogli il suo nome
    private fun findPlayerIdByNameInTeams(teamIds: List<String>, playerName: String, callback: (String?) -> Unit) {
        val database = FirebaseDatabase.getInstance()
        val teamRef = database.getReference("teams")

        var found = false

        // Controlla ogni team per trovare il giocatore
        for (teamId in teamIds) {
            if (found) break

            val teamPlayersRef = teamRef.child(teamId).child("players")

            teamPlayersRef.orderByChild("name").equalTo(playerName).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (playerSnapshot in dataSnapshot.children) {
                            val playerId = playerSnapshot.key
                            callback(playerId)
                            found = true
                            return
                        }
                    }
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Log.e("Firebase", "Error finding player ID", databaseError.toException())
                    if (!found) callback(null)
                }
            })
        }
    }
    private fun addCardRow(context: Context, layout: LinearLayout, team1Id: String, team2Id: String, cardType: String, prepopulatedPlayers: List<String>? = null) {
        val rowView = LayoutInflater.from(context).inflate(R.layout.card_row, null)
        val spinner = rowView.findViewById<Spinner>(R.id.spinner_players)
        val btnRemove = rowView.findViewById<Button>(R.id.btn_remove)

        val players = mutableListOf<String>()

        getPlayersForTeam(team1Id) { team1Players ->
            getPlayersForTeam(team2Id) { team2Players ->
                players.addAll(team1Players)
                players.addAll(team2Players)

                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, players)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter

                // Se ci sono giocatori pre-popolati, impostali nello spinner
                if (prepopulatedPlayers != null && prepopulatedPlayers.isNotEmpty()) {
                    val position = adapter.getPosition(prepopulatedPlayers.first())
                    if (position >= 0) {
                        spinner.setSelection(position)
                    }
                }
            }
        }

        layout.addView(rowView)

        // Gestione del pulsante per rimuovere la riga
        btnRemove.setOnClickListener {
            layout.removeView(rowView)
        }
    }

    // Function to show a dialog for selecting or modifying a scorer
    private fun showScorerSelectionDialog(context: Context, scorerList: MutableList<String>, position: Int, teamId: String,llScorers:LinearLayout) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.select_scorer, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinner_scorers)

        getPlayersForTeam(teamId) { players ->
            if (players.isEmpty()) {
                Toast.makeText(context, "No players found.", Toast.LENGTH_SHORT).show()
            }
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, players)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter

            // Seleziona il giocatore attualmente scelto nella posizione
            val currentPlayer = scorerList.getOrNull(position)
            spinner.setSelection(adapter.getPosition(currentPlayer ?: ""))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Select Scorer")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedPlayer = spinner.selectedItem as? String ?: return@setPositiveButton
                scorerList[position] = selectedPlayer
                (llScorers.getChildAt(position) as? TextView)?.text = selectedPlayer
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    // get players of match's teams
    private fun getPlayersForTeam(teamId: String, callback: (List<String>) -> Unit) {
        val database = FirebaseDatabase.getInstance()
        val teamRef = database.getReference("teams").child(teamId).child("players")

        teamRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val playersList = mutableListOf<String>()

                for (playerSnapshot in snapshot.children) {
                    val playerName = playerSnapshot.child("name").getValue(String::class.java)
                    if (playerName != null) {
                        playersList.add(playerName)
                    }
                }
                callback(playersList) // Passa la lista di giocatori al callback
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to read players", error.toException())
                callback(emptyList()) // Restituisci una lista vuota in caso di errore
            }
        })
    }

    private fun updateLeagueTableAfterMatch(match: Match, leagueId: String, isReverting: Boolean) {
        val team1Id = match.team1?.id ?: return
        val team2Id = match.team2?.id ?: return

        val matchResult1 = match.result1 ?: return
        val matchResult2 = match.result2 ?: return

        // Calcola i punti
        val points1 = when {
            matchResult1 == matchResult2 -> 1 // Pareggio
            matchResult1 > matchResult2 -> 3 // Vittoria per team1
            else -> 0 // Vittoria per team2
        }

        val points2 = when {
            matchResult1 == matchResult2 -> 1 // Pareggio
            matchResult1 < matchResult2 -> 3 // Vittoria per team2
            else -> 0 // Vittoria per team1
        }

        // Se stiamo aggiornando match già inserito, togli i punti invece di aggiungerli
        updateTeamStatsInLeague(team1Id, leagueId, if (isReverting) -points1 else points1, matchResult1, matchResult2, isReverting)
        updateTeamStatsInLeague(team2Id, leagueId, if (isReverting) -points2 else points2, matchResult2, matchResult1, isReverting)
    }

    private fun updateTeamStatsInLeague(teamId: String, leagueId: String, points: Int, goalsFor: Int, goalsAgainst: Int, isReverting: Boolean) {
        val teamRef = mDbRef.child("teams").child(teamId).child("tournaments").child(leagueId)

        teamRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentStats = mutableData.getValue(TournamentStats::class.java) ?: TournamentStats()

                val updatedStats = currentStats.copy(
                    points = (currentStats.points ?: 0) + points,
                    wins = (currentStats.wins ?: 0) + if (points == 3 && !isReverting) 1 else if (points == -3 && isReverting) -1 else 0,
                    draws = (currentStats.draws ?: 0) + if (points == 1 && !isReverting) 1 else if (points == -1 && isReverting) -1 else 0,
                    losses = (currentStats.losses ?: 0) + if (points == 0 && !isReverting) 1 else if (points == 0 && isReverting) -1 else 0,
                    goalsFor = (currentStats.goalsFor ?: 0) + if (isReverting) -goalsFor else goalsFor,
                    goalsAgainst = (currentStats.goalsAgainst ?: 0) + if (isReverting) -goalsAgainst else goalsAgainst
                )

                mutableData.value = updatedStats
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Log.e("DatabaseUpdate", "Failed to update team $teamId in league $leagueId: ${error.message}")
                } else {
                    Log.d("DatabaseUpdate", "Team $teamId in league $leagueId updated successfully")
                }
            }
        })
    }
    override fun getItemCount(): Int = matchList.size
    class MatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvStage: TextView = itemView.findViewById(R.id.tv_stage)
        val tvMatchTime: TextView = itemView.findViewById(R.id.tv_match_time)
        val tvTeam1: TextView = itemView.findViewById(R.id.tv_team1)
        val tvTeam1Score: TextView = itemView.findViewById(R.id.tv_team1_score)
        val tvTeam2: TextView = itemView.findViewById(R.id.tv_team2)
        val tvTeam2Score: TextView = itemView.findViewById(R.id.tv_team2_score)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        val btnEdit: ImageView = itemView.findViewById(R.id.iv_edit)
    }
}
