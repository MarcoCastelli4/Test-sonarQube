package com.example.leaguepro


import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// LeagueAdapter è collegato al layout league_item
class LeagueAdapter(
    val context: Context,
    var leagueList: ArrayList<League>,
    val dbRef: DatabaseReference,
    val fromAllLeague: Boolean,
    val listener: (League) -> Unit
) : RecyclerView.Adapter<LeagueAdapter.LeagueViewHolder>() {

    class LeagueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textName: TextView = itemView.findViewById(R.id.league_name)
        val textPrize: TextView = itemView.findViewById(R.id.league_prize)
        val floatRating: RatingBar = itemView.findViewById(R.id.league_level)
        val binButton: ImageView = itemView.findViewById(R.id.bin)
        val moreButton: ImageView = itemView.findViewById(R.id.more)
        val availablePlaces: TextView=itemView.findViewById(R.id.numberAvailable)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeagueViewHolder {
        val view: View = LayoutInflater.from(context).inflate(R.layout.card_league_layout, parent, false)
        return LeagueViewHolder(view)
    }

    override fun getItemCount(): Int {
        return leagueList.size
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: LeagueViewHolder, position: Int) {
        val currentLeague = leagueList[position]
        holder.textName.text = currentLeague.name
        holder.textPrize.text = "${currentLeague.prize} €"
        holder.floatRating.rating = currentLeague.level!!

        // Effettua una query al database per ottenere il numero di team iscritti
        val leagueId = currentLeague.uid
        if (leagueId != null) {
            dbRef.child("leagues_team")
                .orderByChild("league_id")
                .equalTo(leagueId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        val numberOfTeams = dataSnapshot.childrenCount
                        val maxTeams = currentLeague.maxNumberTeam
                        val availablePlaces = maxTeams?.minus(numberOfTeams.toFloat())
                        if (availablePlaces != null) {
                            holder.availablePlaces.text = "${availablePlaces.toInt()}"
                        }
                    }

                    override fun onCancelled(databaseError: DatabaseError) {
                        // Handle error
                        holder.availablePlaces.text = "Error loading places"
                    }
                })
        } else {
            holder.availablePlaces.text = "Error loading places"
        }

        // Mostra la possibilità di cancellare se sono in MyLeague
        if (fromAllLeague) {
            holder.binButton.visibility = View.GONE

        } else {
            holder.binButton.setOnClickListener {
                showConfirmationDialog(currentLeague)
            }
        }

        holder.moreButton.setOnClickListener {
            showLeagueInfoPopup(currentLeague)
        }

        // Add listener on the entire card
        holder.itemView.setOnClickListener {
            listener.invoke(currentLeague)
        }
    }

    // dialogo di conferma per rimozione lega nei 2 tipi di utenti
    private fun showConfirmationDialog(league: League) {
        val builder = AlertDialog.Builder(context, R.style.CustomAlertDialog)
        // Leaugue manager cancella il torneo
        if (UserInfo.userType==context.getString(R.string.LeagueManager)) {
            builder.setTitle("Delete confirm")
            builder.setMessage("Are you sure to delete ${league.name} and all info connected?")

            builder.setPositiveButton("Delete") { dialog, _ ->
                removeLeagueFromDatabase(league)
                dialog.dismiss()
            }

            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        }
        // Team manager si disiscrive al torneo
        if (UserInfo.userType==context.getString(R.string.TeamManager)){
            builder.setTitle("Unsubscribe confirm")
            builder.setMessage("Are you sure to unsubscribe from ${league.name}?")

            builder.setPositiveButton("Unsubscribe") { dialog, _ ->
                UserInfo.team_id?.let { removeTeamFromLeague(league, it)
                }
                dialog.dismiss()
            }

            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        }
        builder.create().show()
    }
    //disiscrizione team da una lega
    private fun removeTeamFromLeague(league: League, teamUid: String) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val today = Calendar.getInstance().time

        try {
            val startDateString = league.playingPeriod?.split(" - ")?.get(0)
            val startDate = sdf.parse(startDateString)

            if (startDate != null && startDate.after(today)) {
                val leaguesTeamRef = dbRef.child("leagues_team")
                leaguesTeamRef.orderByChild("league_id").equalTo(league.uid).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (dataSnapshot in snapshot.children) {
                            val leagueTeamData = dataSnapshot.value as? Map<String, String>
                            val leagueId = leagueTeamData?.get("league_id")
                            val teamId = leagueTeamData?.get("team_id")

                            if (leagueId == league.uid && teamId == teamUid) {
                                getTeamNameById(teamUid) { teamName ->
                                    if (teamName != null) {
                                        dataSnapshot.ref.removeValue().addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                league.uid?.let { removeTeamFromLeagueTable(it, teamName) }
                                                removeTeamTournaments(teamId,leagueId)
                                                removeLeaguePerformanceFromTeam(teamId,leagueId)
                                                leagueList.remove(league)
                                                notifyDataSetChanged()
                                                Toast.makeText(context, "Team removed from league successfully!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to remove team from league", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Failed to get team name", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                break
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(context, "Failed to remove team from league: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                Toast.makeText(context, "Cannot unsubscribe because the league has already started!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error parsing date: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getTeamNameById(teamId: String, callback: (String?) -> Unit) {
        val teamsRef = dbRef.child("teams") // Assumi che "teams" sia il percorso in cui i dati delle squadre sono memorizzati
        teamsRef.child(teamId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val team = snapshot.getValue(Team::class.java)
                    callback(team?.name) // Passa il nome del team al callback
                } else {
                    callback(null) // Passa null se il team non esiste
                }
            }

            override fun onCancelled(error: DatabaseError) {
                callback(null) // Passa null se c'è un errore
                Toast.makeText(context, "Failed to get team name: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // rimozione team dal nodo leagueTable associato ad una lega
    private fun removeTeamFromLeagueTable(leagueId: String, teamName: String) {
        val leagueTableRef = dbRef.child("league_table").child(leagueId)
        leagueTableRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val leagueTable = snapshot.getValue(LeagueTable::class.java)
                if (leagueTable != null) {
                    leagueTable.removeTeamByName(teamName)
                    leagueTableRef.setValue(leagueTable).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(context, "Team removed from league table successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to remove team from league table", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "League table not found", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to remove team from league table: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // rimozione lega da Db
    private fun removeLeagueFromDatabase(league: League) {
        val leagueId = league.uid!!
        val leaguesTeamRef = dbRef.child("leagues_team")

        // Verifica se ci sono team associati a questa lega
        leaguesTeamRef.orderByChild("league_id").equalTo(leagueId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Ci sono team associati a questa lega, quindi non possiamo eliminarla
                    Toast.makeText(context, "Cannot delete league with registered teams!", Toast.LENGTH_LONG).show()
                } else {
                    // Nessun team è associato a questa lega, quindi possiamo eliminarla
                    dbRef.child("leagues").child(leagueId).removeValue().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Rimuovi l'elemento dalla lista locale
                            leagueList.remove(league)
                            // Notifica l'adapter della rimozione
                            notifyDataSetChanged()
                            Toast.makeText(context, "League deleted!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Error in league deletion", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Error checking league teams: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    //mostra info sulla lega
    @SuppressLint("SetTextI18n")
    private fun showLeagueInfoPopup(league: League) {
        val popupView = LayoutInflater.from(context).inflate(R.layout.league_more, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Nascondi la RecyclerView
        (context as Activity).findViewById<RecyclerView>(R.id.leagueRecyclerView).visibility = View.GONE
        popupWindow.setOnDismissListener {
            // Mostra di nuovo la RecyclerView quando la popup viene chiusa
            (context as Activity).findViewById<RecyclerView>(R.id.leagueRecyclerView).visibility = View.VISIBLE
        }
        popupWindow.showAtLocation(popupView, Gravity.CENTER, 0, 0)

        val play: Button = popupView.findViewById(R.id.join_button)

        val leagueId = league.uid
        val leaguesTeamRef = dbRef.child("leagues_team")

        leaguesTeamRef.orderByChild("league_id").equalTo(leagueId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var isLeagueInTeamLeagues = false
                val numberOfTeams = snapshot.childrenCount
                val maxTeams = league.maxNumberTeam ?: 0

                for (dataSnapshot in snapshot.children) {
                    val leagueTeamData = dataSnapshot.value as Map<String, String>
                    val fetchedLeagueId = leagueTeamData["league_id"]
                    val fetchedTeamId = leagueTeamData["team_id"]

                    if (fetchedLeagueId == leagueId && fetchedTeamId == UserInfo.team_id) {
                        isLeagueInTeamLeagues = true
                        break
                    }
                }

                // Determina la visibilità del pulsante "play" in base alle condizioni
                if (UserInfo.userType==context.getString(R.string.TeamManager) && !UserInfo.team_id.equals("") && !isLeagueInTeamLeagues && numberOfTeams < maxTeams.toFloat()) {
                    play.visibility = View.VISIBLE
                }
            }


            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to check leagues_team: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // Handle data visualization
        val description: TextView = popupView.findViewById(R.id.more_league_description)
        val address: TextView = popupView.findViewById(R.id.edt_more_address)
        val period: TextView = popupView.findViewById(R.id.edt_more_playing_period)
        val entry: TextView = popupView.findViewById(R.id.edt_more_euro)
        val prize: TextView = popupView.findViewById(R.id.edt_first_prize)
        val restrictions: TextView = popupView.findViewById(R.id.edt_more_info)

        description.text = league.description
        address.text = league.address
        period.text = league.playingPeriod
        entry.text = "${league.entryfee}€ for registration"
        prize.text=  "${league.prize}€ for the winner"
        restrictions.text = league.restrictions

        val closeButton: ImageView = popupView.findViewById(R.id.btn_close)
        closeButton.setOnClickListener {
            popupWindow.dismiss()
        }

        play.setOnClickListener {
            UserInfo.team_id?.let { it1 -> addTeamToALeague(league, it1) }
            popupWindow.dismiss()
        }
    }
    // aggiunge un team ad una lega
    private fun addTeamToALeague(league: League, teamUid: String) {
        // Verifica che la data di inizio del torneo sia maggiore di oggi
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val today = Calendar.getInstance().time

        try {
            val startDateString = league.playingPeriod?.split(" - ")?.get(0)
            val startDate = sdf.parse(startDateString)

            if (startDate != null && startDate.after(today)) {
                // Verifica il numero massimo di team consentiti
                val leagueUid: String? = league.uid
                val leagueTeamsRef = dbRef.child("leagues_team").orderByChild("league_id").equalTo(leagueUid)

                leagueTeamsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val currentTeamCount = snapshot.childrenCount
                        if (league.maxNumberTeam != null && currentTeamCount < league.maxNumberTeam!!) {
                            // Continua con l'aggiunta del team alla league
                            val leagueTeamRef = dbRef.child("leagues_team")
                            val leagueTeamData = mapOf(
                                "league_id" to leagueUid,
                                "team_id" to teamUid
                            )

                            // Usiamo push() per generare una chiave univoca per ogni associazione league-team
                            val leagueTeamKey = leagueTeamRef.push().key

                            if (leagueTeamKey != null) {
                                leagueTeamRef.child(leagueTeamKey).setValue(leagueTeamData)
                                    .addOnSuccessListener {
                                        // Gestione successo
                                        Toast.makeText(context, "Team joined league successfully!", Toast.LENGTH_SHORT).show()

                                        // Aggiungi il team alla classifica della lega
                                        addTeamToLeagueTable(leagueUid, teamUid)
                                        // Inizializza il campo tournaments del team
                                        initializeTeamTournaments(teamUid, leagueUid)
                                        //Inizializza le performance dei giocatori nel torneo
                                        if (leagueUid != null) {
                                            initializePlayersPerformance(teamUid,leagueUid)
                                        }

                                        notifyDataSetChanged()
                                    }
                                    .addOnFailureListener { e ->
                                        // Gestione fallimento
                                        Toast.makeText(context, "Failed to join league: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                Toast.makeText(context, "Failed to generate a unique key for the league-team association", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Cannot join league because it has reached the maximum number of teams!", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(context, "Failed to check league team count: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                })
            } else {
                Toast.makeText(context, "Cannot join league because the league has already started!", Toast.LENGTH_LONG).show()
            }
        } catch (e: ParseException) {
            Toast.makeText(context, "Error parsing date: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    //aggiunge un team alla classifica alla sua iscrizione
    private fun addTeamToLeagueTable(leagueUid: String?, teamUid: String) {
        if (leagueUid != null) {
            val teamRef = dbRef.child("teams").child(teamUid)
            teamRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val team = snapshot.getValue(Team::class.java)

                    if (team != null) {
                        val leagueTableRef = dbRef.child("league_table").child(leagueUid)

                        leagueTableRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val leagueTable = snapshot.getValue(LeagueTable::class.java) ?: LeagueTable(leagueUid, "League Name",)

                                // Aggiungi o aggiorna il team nella classifica
                                leagueTable.addOrUpdateTeam(team)

                                leagueTableRef.setValue(leagueTable)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Team added to league table successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "Failed to update league table: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Toast.makeText(context, "Failed to retrieve league table: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        })
                    } else {
                        Toast.makeText(context, "Team data not found!", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Failed to retrieve team data: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    // inizializza le statistiche(classifica) di un team all'interno di una lega alla sua iscrizione
    private fun initializeTeamTournaments(teamUid: String, leagueUid: String?) {
        val teamRef = dbRef.child("teams").child(teamUid)
        val tournamentStats = TournamentStats()  // Inizializza tutte le stats a 0

        teamRef.child("tournaments").child(leagueUid!!).setValue(tournamentStats)
            .addOnSuccessListener {
                Toast.makeText(context, "Team tournaments initialized successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to initialized team tournaments: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun initializePlayersPerformance(teamUid: String, leagueId: String) {
        val teamRef = dbRef.child("teams").child(teamUid)

        teamRef.child("players").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (playerSnapshot in dataSnapshot.children) {
                    val player = playerSnapshot.getValue(Player::class.java)
                    val playerKey = playerSnapshot.key

                    if (player != null && playerKey != null) {
                        // Crea una nuova mappa per il campo tournaments del giocatore
                        val updatedTournaments = player.tournaments?.toMutableMap() ?: mutableMapOf()

                        // Imposta la performance del giocatore per il nuovo torneo
                        updatedTournaments[leagueId] = PlayerPerformance()

                        // Aggiorna il nodo del giocatore con la nuova mappa tournaments
                        teamRef.child("players").child(playerKey).child("tournaments").setValue(updatedTournaments)
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("FirebaseError", "loadPlayers:onCancelled", databaseError.toException())
            }
        })
    }

    // rimuove le statistiche del team alla disiscrizione dal torneo
    private fun removeTeamTournaments(teamUid: String, leagueUid: String?){
        if (leagueUid != null) {
            dbRef.child("teams").child(teamUid).child("tournaments").child(leagueUid).removeValue()
        }
    }

    fun removeLeaguePerformanceFromTeam(teamUid: String, leagueId: String?) {
        val teamRef = dbRef.child("teams").child(teamUid)

        teamRef.child("players").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (playerSnapshot in dataSnapshot.children) {
                    val playerKey = playerSnapshot.key

                    // Se la chiave non è nulla, rimuovi la performance per la lega specifica
                    if (playerKey != null) {
                        // Costruisci il path per il campo specifico in 'tournaments'
                        val tournamentRef =
                            leagueId?.let {
                                teamRef.child("players").child(playerKey).child("tournaments").child(
                                    it
                                )
                            }

                        // Rimuovi il campo 'leagueId' sotto 'tournaments'
                        if (tournamentRef != null) {
                            tournamentRef.removeValue().addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Log.d("FirebaseSuccess", "Removed performance for league $leagueId from player $playerKey")
                                } else {
                                    Log.w("FirebaseError", "Failed to remove performance for league $leagueId from player $playerKey", task.exception)
                                }
                            }
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("FirebaseError", "loadPlayers:onCancelled", databaseError.toException())
            }
        })
    }

    fun setData(newLeagueList: List<League>) {
        leagueList.clear()
        leagueList.addAll(newLeagueList)
        notifyDataSetChanged()
    }


}
