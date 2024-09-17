package com.example.leaguepro

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LeagueTableFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var leagueTableLayout: TableLayout
    private var leagueUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance().reference
        leagueUid = arguments?.getString("league_id")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_table_league, container, false)
        leagueTableLayout = view.findViewById(R.id.leagueTableLayout)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (leagueUid != null) {
            loadTeamsAndTournamentStats()
        } else {
            addNoDataRow()
        }
    }

    private fun loadTeamsAndTournamentStats() {
        leagueUid?.let { leagueId ->
            val leagueTeamsRef = database.child("leagues_team").orderByChild("league_id").equalTo(leagueId)

            leagueTeamsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val teamIds = mutableListOf<String>()
                    for (snapshot in dataSnapshot.children) {
                        val teamId = snapshot.child("team_id").getValue(String::class.java)
                        if (teamId != null) {
                            teamIds.add(teamId)
                        }
                    }
                    loadTournamentStatsForTeams(teamIds, leagueId)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to retrieve teams: ${databaseError.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun loadTournamentStatsForTeams(teamIds: List<String>, leagueId: String) {
        val teamStatsRef = database.child("teams")
        val teamStatsList = mutableListOf<Pair<Team, TournamentStats>>()

        teamIds.forEach { teamId ->
            teamStatsRef.child(teamId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val team = dataSnapshot.getValue(Team::class.java)
                    team?.let {
                        val tournamentStats = it.tournaments?.get(leagueId) ?: TournamentStats()
                        teamStatsList.add(Pair(it, tournamentStats))

                        if (teamStatsList.size == teamIds.size) {
                            // Tutti i dati sono stati caricati, ora ordina e visualizza
                            displayLeagueTable(teamStatsList)
                        }
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to retrieve team stats: ${databaseError.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun addTableHeader() {
        val context = context ?: return
        val headerRow = TableRow(context)

        val headers = listOf("Team", "PT", "V", "N", "P", "GF", "GS")
        headers.forEach { headerText ->
            val textView = TextView(context).apply {
                text = headerText
                setPadding(8, 8, 8, 8)
                textSize = 16f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
            }
            headerRow.addView(textView)
        }
        leagueTableLayout.addView(headerRow)
    }

    private fun displayLeagueTable(teamStatsList: List<Pair<Team, TournamentStats>>) {
        // Ordina la lista per punti in modo decrescente
        val sortedTeamStatsList = teamStatsList.sortedByDescending { it.second.points }

        // Rimuovi tutte le righe esistenti nella tabella
        leagueTableLayout.removeAllViews()

        // Aggiungi l'intestazione della tabella
        addTableHeader()

        // Aggiungi le righe per ciascun team ordinato
        sortedTeamStatsList.forEach { (team, stats) ->
            updateLeagueTable(team, stats)
        }
    }
    private fun updateLeagueTable(team: Team, tournamentStats: TournamentStats) {
        val context = requireContext()
        // Crea la linea divisoria
        val divider = View(context).apply {
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                1 // Spessore della linea
            ).apply {
                setMargins(0, 8, 0, 8) // Margini sopra e sotto la linea divisoria
            }
            setBackgroundResource(R.drawable.divider_line) // Usa il drawable creato
        }
        // Aggiungi la linea divisoria sopra la riga
        leagueTableLayout.addView(divider)
        val row = TableRow(context)

        val values = listOf(
            team.name ?: "N/A",
            tournamentStats.points.toString(),
            tournamentStats.wins.toString(),
            tournamentStats.draws.toString(),
            tournamentStats.losses.toString(),
            tournamentStats.goalsFor.toString(),
            tournamentStats.goalsAgainst.toString()
        )

        values.forEach { value ->
            val textView = TextView(context).apply {
                text = value
                setPadding(8, 8, 8, 8)
                textSize = 14f
                gravity = Gravity.CENTER
            }
            row.addView(textView)
        }

        leagueTableLayout.addView(row)
    }

    private fun addNoDataRow() {
        val context = requireContext()
        val noDataRow = TableRow(context)
        val noDataTextView = TextView(context).apply {
            text = "No data available"
            setPadding(16, 16, 16, 16)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        noDataRow.addView(noDataTextView)
        leagueTableLayout.addView(noDataRow)
    }
}

