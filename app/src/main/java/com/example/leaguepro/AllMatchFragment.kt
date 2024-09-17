package com.example.leaguepro

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random

class AllMatchFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var matchAdapter: MatchAdapter
    private val matchList = mutableListOf<Match>()
    private lateinit var leagueOwnerId: String
    private lateinit var addCalendarContainer: ConstraintLayout
    private lateinit var addCalendar: ImageView
    private lateinit var calendar: List<Match>
    private lateinit var mDbRef: DatabaseReference

    companion object {
        private const val LEAGUE_ID_KEY = "league_id"

        fun newInstance(leagueId: String): AllMatchFragment {
            return AllMatchFragment().apply {
                arguments = Bundle().apply {
                    putString(LEAGUE_ID_KEY, leagueId)
                }
            }
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_all_match, container, false)
        recyclerView = view.findViewById(R.id.matchRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        addCalendar = view.findViewById(R.id.add_calendar)
        addCalendarContainer = view.findViewById(R.id.addCalendarContainer)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mDbRef = FirebaseDatabase.getInstance().reference
        val leagueId = arguments?.getString(LEAGUE_ID_KEY) ?: return
        loadLeagueOwnerId(leagueId)
        addCalendar.setOnClickListener {    //click listener per create calendar
            checkIfCalendarExists(leagueId)
        }
    }
    private fun loadLeagueOwnerId(leagueId: String) {
        val database = FirebaseDatabase.getInstance()
        val leagueRef = database.getReference("leagues").child(leagueId)

        leagueRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val league = dataSnapshot.getValue(League::class.java)
                leagueOwnerId = league?.leagueManager ?: return
                setupRecyclerView()  // inizializza recyclerview
                loadMatchesFromDatabase(leagueId)  // carica match e aggiorna recyclerview
                updateVisibilityCreateCalendar()   // attiva create calendar se utente owner
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("RealtimeDB", "loadLeagueOwnerId:onCancelled", databaseError.toException())
            }
        })
    }
    private fun setupRecyclerView() {
        val leagueId = arguments?.getString(LEAGUE_ID_KEY) ?: return
        matchAdapter = MatchAdapter(matchList, leagueOwnerId, leagueId,mDbRef,requireContext())
        recyclerView.adapter = matchAdapter
    }
    private fun updateVisibilityCreateCalendar() {
        val currentUserId = UserInfo.userId
        if (currentUserId == leagueOwnerId) {
            addCalendarContainer.visibility = View.VISIBLE
        } else {
            addCalendarContainer.visibility = View.GONE
        }
    }
    private fun checkIfCalendarExists(leagueId: String) {
        val database = FirebaseDatabase.getInstance()
        val matchRef = database.getReference("matches").child(leagueId)

        matchRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Il calendario esiste già
                    Toast.makeText(requireContext(), "The calendar has already been created for this league", Toast.LENGTH_LONG).show()
                } else {
                    // Il calendario non esiste, procedi con la creazione
                    createAndSaveCalendar(leagueId)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("Firebase", "checkIfCalendarExists:onCancelled", databaseError.toException())
            }
        })
    }
    private fun createAndSaveCalendar(leagueId: String){
        lifecycleScope.launch {
            try {
                val result = fetchLeagueAndTeams(leagueId!!, requireContext())
                if (result == null) return@launch // Early return if no teams are found or an error occurred

                val (league, teams) = result
                // Print league and teams details in the log
                league?.let { Log.d("ActLeagueFragment", "League Name: ${it.name}") }
                teams!!.forEach { team ->
                    Log.d(
                        "ActLeagueFragment",
                        "Team Name: ${team.name}"
                    )
                }
                calendar = createCalendar(league!!, teams)

                // Print the calendar matches in the log
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                for (match in calendar) {
                    Log.d(
                        "Calendar",
                        "Match: ${match.toString()}"
                    )
                }

                saveMatchesToDatabase(calendar, leagueId!!)
            } catch (e: Exception) {
                // Gestire eventuali errori
                Log.e("ActLeagueFragment", "Error getting league and teams", e)
            }
        }
    }
    private fun createCalendar(league: League, teams: List<Team>): List<Match> {
        val matches = mutableListOf<Match>()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val (startDate, endDate) = splitPlayingPeriod(league.playingPeriod!!)
            ?: throw IllegalArgumentException("Invalid playing period format")

        val calendar = Calendar.getInstance()
        calendar.time = startDate

        val allPossibleMatches = mutableListOf<Pair<Team, Team>>()
        for (i in teams.indices) {
            for (j in i + 1 until teams.size) {
                allPossibleMatches.add(Pair(teams[i], teams[j]))
            }
        }
        allPossibleMatches.shuffle()

        val matchDay = mutableMapOf<String, MutableList<String>>()
        val tempCalendar = Calendar.getInstance()
        tempCalendar.time = startDate
        while (!tempCalendar.time.after(endDate)) {
            val dayKey = dateFormat.format(tempCalendar.time)
            matchDay[dayKey] = mutableListOf()
            tempCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val matchTimes = listOf("20:30", "21:30")

        var matchId = 1
        val random = Random()

        for (i in teams.indices) {
            for (j in i + 1 until teams.size) {
                val team1 = teams[i]
                val team2 = teams[j]

                var validDateFound = false
                while (!validDateFound) {
                    val randomDayOffset = random.nextInt((endDate.time - startDate.time).toInt() / (1000 * 60 * 60 * 24) + 1)
                    calendar.time = startDate
                    calendar.add(Calendar.DAY_OF_MONTH, randomDayOffset)

                    val currentDay = dateFormat.format(calendar.time)
                    val availableTimes = matchTimes - matchDay[currentDay]?.toSet().orEmpty()

                    if (availableTimes.isNotEmpty()) {
                        val currentTime = availableTimes.random()

                        if (matchDay[currentDay]?.size ?: 0 < 2 &&
                            (matchDay[currentDay]?.contains(currentTime) ?: false).not()
                        ) {
                            val match = Match(
                                id = matchId.toString(),
                                team1 = team1,
                                team2 = team2,
                                date = currentDay,
                                time = currentTime,
                                result1 = null,
                                result2 = null,
                                scorersTeam1= listOf(),
                                scorersTeam2= listOf(),
                                yellowCards= listOf(),
                                redCards= listOf()
                            )
                            matches.add(match)
                            matchId++

                            matchDay[currentDay]?.add(currentTime)

                            validDateFound = true
                        }
                    } else {
                        // Se tutti gli orari sono occupati, passare al giorno successivo
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }
                }
            }
        }

        // Ordinamento della lista di partite per data e ora
        matches.sortWith(Comparator { m1, m2 ->
            val dateComparison = dateFormat.parse(m1.date).compareTo(dateFormat.parse(m2.date))
            if (dateComparison == 0) {
                timeFormat.parse(m1.time).compareTo(timeFormat.parse(m2.time))
            } else {
                dateComparison
            }
        })

        return matches
    }
    fun saveMatchesToDatabase(matches: List<Match>, leagueId: String) {
        val database = FirebaseDatabase.getInstance()
        val matchRef = database.getReference("matches").child(leagueId)

        matches.forEach { match ->
            match.id = matchRef.push().key // Genera una chiave unica per ogni match
            matchRef.child(match.id!!).setValue(match)
                .addOnSuccessListener {
                    Log.d("RealtimeDB", "Match added with ID: ${match.id}")
                }
                .addOnFailureListener { e ->
                    Log.w("RealtimeDB", "Error adding match", e)
                }
        }
    }
    private fun loadMatchesFromDatabase(leagueId: String) {
        val database = FirebaseDatabase.getInstance()
        val matchRef = database.getReference("matches").child(leagueId)

        matchRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                matchList.clear()
                for (matchSnapshot in dataSnapshot.children) {
                    val match = matchSnapshot.getValue(Match::class.java)
                    val matchId = matchSnapshot.key
                    if (match != null) {
                        match.id = matchId
                        matchList.add(match)
                    }
                }
                matchAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("RealtimeDB", "loadMatches:onCancelled", databaseError.toException())
            }
        })
    }
    private suspend fun fetchLeagueAndTeams(leagueId: String, context: Context): Pair<League?, List<Team>?>? {
        return withContext(Dispatchers.IO) {
            var league: League? = null
            val teamsList = mutableListOf<Team>()

            try {
                // Fetch league details
                val leagueSnapshot = mDbRef.child("leagues").child(leagueId).get().await()
                league = leagueSnapshot.getValue(League::class.java)
                leagueOwnerId = league?.leagueManager!!  // Imposta l'ID del proprietario della lega

                // Fetch teams associated with the league
                val teamsSnapshot = mDbRef.child("leagues_team")
                    .orderByChild("league_id")
                    .equalTo(leagueId)
                    .get()
                    .await()
                for (teamLeagueSnapshot in teamsSnapshot.children) {
                    val teamId = teamLeagueSnapshot.child("team_id").getValue(String::class.java)
                    if (teamId != null) {
                        val teamSnapshot = mDbRef.child("teams").child(teamId).get().await()
                        // Handle the team snapshot data
                        val team = teamSnapshot.getValue(Team::class.java)
                        if (team != null) {
                            teamsList.add(team)
                        }
                    }
                }

                if (league != null) {
                    // aggiungere con or or (teamsList.size<league.maxNumberTeam!!)
                    if (teamsList.isEmpty() || teamsList.size < league.maxNumberTeam!!  ) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Impossible to create a calendar: there are not enough team!", Toast.LENGTH_SHORT).show()
                        }
                        return@withContext null
                    }
                }

                return@withContext Pair(league, teamsList)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error fetching league and teams: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("ActLeagueFragment", "Error getting league and teams", e)
                }
                return@withContext null
            }
        }
    }
    private fun splitPlayingPeriod(playingPeriod: String): Pair<Date, Date>? {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        val dates = playingPeriod.split(" - ")

        return if (dates.size == 2) {
            val startDate = dateFormat.parse(dates[0])
            val endDate = dateFormat.parse(dates[1])
            if (startDate != null && endDate != null) {
                Pair(startDate, endDate)
            } else {
                null
            }
        } else {
            null
        }
    }


}
