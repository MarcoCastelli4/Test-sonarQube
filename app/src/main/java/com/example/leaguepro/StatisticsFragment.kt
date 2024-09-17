package com.example.leaguepro

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class StatisticsFragment: Fragment() {
    private lateinit var goalLayout: TableLayout
    private lateinit var yellowCardLayout: TableLayout
    private lateinit var redCardsLayout: TableLayout
    private var leagueUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        leagueUid = arguments?.getString("league_id")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Recupera i riferimenti ai TableLayout
        goalLayout = view.findViewById(R.id.goalLayout)
        yellowCardLayout = view.findViewById(R.id.yellowCardLayout)
        redCardsLayout = view.findViewById(R.id.redCardsLayout)
        if (leagueUid != null) {
            loadStatistics()
        }
    }

    private fun loadStatistics() {
        val database = FirebaseDatabase.getInstance()
        val teamsRef = database.getReference("teams")

        teamsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val goalStats = mutableListOf<Triple<String, String, Int>>()
                val yellowCardStats = mutableListOf<Triple<String, String, Int>>()
                val redCardStats = mutableListOf<Triple<String, String, Int>>()

                for (teamSnapshot in dataSnapshot.children) {
                    val teamName = teamSnapshot.child("name").getValue(String::class.java) ?: ""
                    for (playerSnapshot in teamSnapshot.child("players").children) {
                        val tournamentSnapshot = playerSnapshot.child("tournaments").child(leagueUid!!)
                        if (tournamentSnapshot.exists()) {
                            val playerName = playerSnapshot.child("name").getValue(String::class.java) ?: ""
                            val goals = tournamentSnapshot.child("goals").getValue(Int::class.java) ?: 0
                            val yellowCards = tournamentSnapshot.child("yellowCards").getValue(Int::class.java) ?: 0
                            val redCards = tournamentSnapshot.child("redCards").getValue(Int::class.java) ?: 0
                            if(goals > 0) {
                                goalStats.add(Triple(playerName, teamName, goals))
                            }
                            if(yellowCards > 0) {
                                yellowCardStats.add(Triple(playerName, teamName, yellowCards))
                            }
                            if(redCards > 0) {
                                redCardStats.add(Triple(playerName, teamName, redCards))
                            }
                        }
                    }
                }

                // Ordina le statistiche in base ai valori
                goalStats.sortByDescending { it.third }
                yellowCardStats.sortByDescending { it.third }
                redCardStats.sortByDescending { it.third }

                // Popola le tabelle
                var headers: List<String>
                if (goalStats.isNotEmpty()) {
                    headers = listOf("Player", "Goal")
                    populateTable(goalStats, goalLayout, headers)
                } else {
                    addNoDataRow(goalLayout)
                }

                if (yellowCardStats.isNotEmpty()) {
                    headers = listOf("Player", "Yellow Cards")
                    populateTable(yellowCardStats, yellowCardLayout, headers)
                } else {
                    addNoDataRow(yellowCardLayout)
                }

                if (redCardStats.isNotEmpty()) {
                    headers = listOf("Player", "Red Cards")
                    populateTable(redCardStats, redCardsLayout, headers)
                } else {
                    addNoDataRow(redCardsLayout)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Gestione dell'errore
            }
        })
    }


    @SuppressLint("Range")
    private fun populateTable(stats: List<Triple<String, String, Int>>, layout: TableLayout, headers: List<String>) {
        if (!isAdded) return  // Verifica che il fragment sia aggiunto
        layout.removeAllViews()  // Rimuove tutte le visualizzazioni esistenti nel TableLayout

        // Aggiungi l'intestazione solo se la tabella è vuota
        if (layout.childCount == 0) {
            addTableHeader(layout, headers)
        }

        for (stat in stats) {
            //linea divisoria tra ogni riga
            val divider = View(context).apply {
                layoutParams = TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    1 // spessore linea
                ).apply {
                    setMargins(0, 20, 0, 20) // Margini per lo spazio tra le righe
                }
                setBackgroundResource(R.drawable.divider_line) // Usa il drawable creato
            }

            layout.addView(divider)

            val row = TableRow(context)
            // Layout verticale per contenere il nome del giocatore e del team
            val playerNameLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2.5f)
            }

            val playerName = TextView(context).apply {
                text = stat.first
                setTypeface(null, Typeface.BOLD)
                textSize = 16f // Font size for player name
            }
            // TextView per il nome della squadra
            val teamName = TextView(context).apply {
                text = stat.second
                textSize = 12f // Font size smaller for team name
                setTextColor(ContextCompat.getColor(context, R.color.gray)) // Colore più chiaro per il nome della squadra
            }
            playerNameLayout.addView(playerName)
            playerNameLayout.addView(teamName)

            val value = TextView(context).apply {
                text = stat.third.toString()
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.7f)
                gravity = Gravity.END
                setTypeface(null, Typeface.BOLD)
            }

            row.addView(playerNameLayout)
            row.addView(value)

            layout.addView(row)
        }
    }


    private fun addTableHeader(layout: TableLayout, headers: List<String>) {
        val context = requireContext()
        val headerRow = TableRow(context)

        headers.forEach { headerText ->
            val textView = TextView(context).apply {
                text = headerText
                setPadding(8, 8, 8, 8)
                textSize = 16f
                gravity = if (headerText == "Goal" ||headerText == "Yellow Cards" || headerText == "Red Cards" ) Gravity.END else Gravity.START
                setTypeface(null, Typeface.BOLD)
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, if (headerText == "Goal" ||headerText == "Yellow Cards" || headerText == "Red Cards" ) 0.7f else 2.5f)
            }
            headerRow.addView(textView)
        }

        layout.addView(headerRow)
    }

    private fun addNoDataRow(layout: TableLayout) {
        if (!isAdded) return
        val context = requireContext()
        val noDataRow = TableRow(context)
        val noDataTextView = TextView(context).apply {
            text = "No data available"
            setPadding(16, 16, 16, 16)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        noDataRow.addView(noDataTextView)
        layout.addView(noDataRow)
    }
}
