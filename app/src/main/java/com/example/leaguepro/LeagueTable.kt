package com.example.leaguepro

data class LeagueTable(
    val id: String? = null,              // Nullable ID della lega
    val name: String? = null,            // Nullable Nome della lega
    val teams: MutableList<Team> = mutableListOf(), // Lista di squadre inizialmente vuota
    var tournaments: List<String> = listOf() // Lista di ID dei tornei
) {

    // Aggiunge o aggiorna una squadra nella lega
    fun addOrUpdateTeam(team: Team) {
        teams.removeAll { it.name == team.name }
        teams.add(team)
        sortTeams()
    }

    // Rimuove una squadra dalla lega per nome
    fun removeTeamByName(teamName: String) {
        teams.removeAll { it.name == teamName }
    }

    // Ordina le squadre in base alle statistiche dei tornei
    fun getSortedTeams(tournamentId: String): List<Team> {
        return teams.sortedWith(
            compareByDescending<Team> { it.tournaments?.get(tournamentId)?.points ?: 0 }
                .thenByDescending { it.tournaments?.get(tournamentId)?.wins ?: 0 }
                .thenBy { it.tournaments?.get(tournamentId)?.goalsFor ?: 0 }
        )
    }

    // Restituisce una stringa di visualizzazione per il torneo specificato
    fun toDisplayString(tournamentId: String): String {
        return getSortedTeams(tournamentId).joinToString(separator = "\n") { team ->
            val stats = team.tournaments?.get(tournamentId) ?: TournamentStats()
            "${team.name}: Points: ${stats.points}, Wins: ${stats.wins}, Goals For: ${stats.goalsFor}"
        }
    }

    // Aggiunge un torneo alla lega
    fun addTournament(tournamentId: String) {
        if (tournamentId !in tournaments) {
            tournaments += tournamentId
        }
    }

    // Rimuove un torneo dalla lega
    fun removeTournament(tournamentId: String) {
        tournaments -= tournamentId
    }

    // Rimuove tutte le squadre
    fun clearTeams() {
        teams.clear()
    }

    // Ordina le squadre in base alle statistiche dei tornei
    private fun sortTeams() {
        // Ordinamento basato sul primo torneo nella lista dei tornei
        val firstTournamentId = tournaments.firstOrNull() ?: return
        teams.sortWith(
            compareByDescending<Team> { it.tournaments?.get(firstTournamentId)?.points ?: 0 }
                .thenByDescending { it.tournaments?.get(firstTournamentId)?.wins ?: 0 }
                .thenBy { it.tournaments?.get(firstTournamentId)?.goalsFor ?: 0 }
        )
    }
}
