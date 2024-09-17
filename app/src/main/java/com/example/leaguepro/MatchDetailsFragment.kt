package com.example.leaguepro

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class MatchDetailsFragment : Fragment() {

    private lateinit var matchId: String
    private lateinit var leagueId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_match_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        matchId = arguments?.getString("MATCH_ID") ?: return
        leagueId = arguments?.getString("LEAGUE_ID") ?: return
        loadMatchDetails(matchId)
    }

    private fun loadMatchDetails(matchId: String) {
        val database = FirebaseDatabase.getInstance()
        val matchRef = database.getReference("matches").child(leagueId).child(matchId)

        matchRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val match = dataSnapshot.getValue(Match::class.java)
                if (match != null) {
                    updateUI(match)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("MatchDetailsFragment", "loadMatchDetails:onCancelled", databaseError.toException())
            }
        })
    }

    private fun updateUI(match: Match) {
        view?.findViewById<TextView>(R.id.group_stage)?.text = "League match: ${match.date}"
        view?.findViewById<TextView>(R.id.team1_name)?.text = match.team1?.name ?: "Unknown"
        view?.findViewById<TextView>(R.id.team1_score)?.text = match.result1?.toString() ?: ""
        view?.findViewById<TextView>(R.id.team2_name)?.text = match.team2?.name ?: "Unknown"
        view?.findViewById<TextView>(R.id.team2_score)?.text = match.result2?.toString() ?: ""

        // Popola i marcatori della squadra 1
        val team1Scorers = match.scorersTeam1?.joinToString("\n") { it } ?: ""
        view?.findViewById<TextView>(R.id.team1_scorer_1)?.text = team1Scorers

        // Popola i marcatori della squadra 2
        val team2Scorers = match.scorersTeam2?.joinToString("\n") { it }  ?: ""
        view?.findViewById<TextView>(R.id.team2_scorer_1)?.text = team2Scorers
    }

    companion object {
        fun newInstance(matchId: String, leagueId: String): MatchDetailsFragment {
            return MatchDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString("MATCH_ID", matchId)
                    putString("LEAGUE_ID", leagueId)
                }
            }
        }
    }
}
