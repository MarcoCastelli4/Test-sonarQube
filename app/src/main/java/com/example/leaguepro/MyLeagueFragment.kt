package com.example.leaguepro


import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*
import kotlin.collections.ArrayList
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.location.Geocoder
import java.io.IOException
import android.preference.PreferenceManager
import android.location.Address
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class MyLeagueFragment : Fragment() {
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var mDbRef: DatabaseReference
    private lateinit var mAuth: FirebaseAuth
    private lateinit var leagueList: ArrayList<League>
    private lateinit var adapter: LeagueAdapter
    private lateinit var leagueRecyclerView: RecyclerView

    private lateinit var edtleague_name: EditText
    private lateinit var edtleague_address: EditText
    private lateinit var edtleague_level: RatingBar
    private lateinit var edtleague_description: EditText
    private lateinit var edtleague_entryfee: EditText
    private lateinit var edtleague_prize: EditText
    private lateinit var edtleague_restrictions: EditText
    private lateinit var edtleague_playingPeriod: TextView
    private lateinit var edtleague_MaxTeamNumber:Spinner
    private lateinit var searchView: EditText
    private lateinit var osmMapView: MapView
    private lateinit var searchBar: RelativeLayout
    private lateinit var mapsBar: LinearLayout
    private lateinit var backButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_league, container, false)
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MyLeagueFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupView(view)
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupView(view: View) {
        val addLeagueContainer: ConstraintLayout = view.findViewById(R.id.add_league_container)
        addLeagueContainer.visibility = if (UserInfo.userType==getString(R.string.LeagueManager)) View.VISIBLE else View.GONE

        setupFirebase()
        setupLeagueRecyclerView(view)
        // carica la mappa
        loadMap(view)
        // load league create by league manager
        if (UserInfo.userType==getString(R.string.LeagueManager)){
            fetchLeaguesFromDatabase()
        }

        // load league that team has subscribe
        if (UserInfo.userType==getString(R.string.TeamManager)){
            fetchTeamLeaguesFromDatabase()
        }
        val addLeagueIcon: ImageView = view.findViewById(R.id.add_league_icon)
        val addLeagueText: TextView = view.findViewById(R.id.add_league_text)
        addLeagueIcon.setOnClickListener {
            showAddLeaguePopup(view)
        }
        addLeagueText.setOnClickListener {
            showAddLeaguePopup(view)
        }
        // Inizializza il SearchView
        searchView = view.findViewById(R.id.search_bar)
        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                filter(s.toString())
            }
        })
    }

    private fun addMarkers(leagueList: ArrayList<League>) {
        view?.let { loadMap(it) }
        if (isAdded) {
            val context = requireContext()
            osmMapView.overlays.clear() // Clear existing markers
            for (league in leagueList) {
                val location = GeoPoint(league.latitude ?: 0.0, league.longitude ?: 0.0)
                val marker = Marker(osmMapView)
                marker.position = location
                marker.title = league.name
                marker.icon = context.getDrawable(R.drawable.location)
                // listener per il clic sul marker
                marker.setOnMarkerClickListener { _, _ ->
                    showLeagueDetailsDialog(league)
                    true // True indica che l'evento è stato gestito
                }
                osmMapView.overlays.add(marker)
            }
            osmMapView.invalidate()
        } else {
            Log.e("MyLeagueFragment", "Fragment is not added to an activity")
        }
    }
    private fun showLeagueDetailsDialog(league: League) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.league_more)

        // Inizializzazione view del dialog
        val leagueNameTextView: TextView = dialog.findViewById(R.id.more_league_description)
        val addressTextView: TextView = dialog.findViewById(R.id.edt_more_address)
        val playingPeriodTextView: TextView = dialog.findViewById(R.id.edt_more_playing_period)
        val entryFeeTextView: TextView = dialog.findViewById(R.id.edt_more_euro)
        val firstPrizeTextView: TextView = dialog.findViewById(R.id.edt_first_prize)
        val restrictionsTextView: TextView = dialog.findViewById(R.id.edt_more_info)
        val closeButton: ImageView = dialog.findViewById(R.id.btn_close)

        // Popolazione campi con i dati della lega
        leagueNameTextView.text = league.name
        addressTextView.text = league.address
        playingPeriodTextView.text = league.playingPeriod
        entryFeeTextView.text = "${league.entryfee}€ for registration"
        firstPrizeTextView.text = "${league.prize}€ for first prize"
        restrictionsTextView.text = league.restrictions

        // chiusura del dialog
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        // mostra dialog
        dialog.show()
    }
    // carica la mappa
    private fun loadMap(view: View){
        // Configurazione OSM
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        mapsBar = view.findViewById(R.id.maps_bar)
        backButton = view.findViewById(R.id.back_button)
        searchBar = view.findViewById(R.id.search_bar_layout)
        osmMapView = view.findViewById(R.id.osmMapView)
        osmMapView.setTileSource(TileSourceFactory.MAPNIK)
        osmMapView.setMultiTouchControls(true)

        // Centra la mappa su una posizione iniziale
        osmMapView.controller.setZoom(13.0)
        osmMapView.controller.setCenter(GeoPoint(45.539855914185615, 10.221154248320246))  // Centra su Brescia

        // Listener per il click sull'icona della mappa
        val mapIcon = view.findViewById<ImageView>(R.id.map_icon)
        mapIcon.setOnClickListener {
            searchBar.visibility=View.GONE
            mapsBar.visibility = View.VISIBLE
            osmMapView.visibility = View.VISIBLE
            val addLeagueContainer: ConstraintLayout = view.findViewById(R.id.add_league_container)
            addLeagueContainer.visibility = View.GONE
        }
        // Listener per il click sul pulsante "indietro"
        backButton.setOnClickListener {
            searchBar.visibility = View.VISIBLE
            mapsBar.visibility = View.GONE
            osmMapView.visibility = View.GONE
        }
    }
    private fun setupLeagueRecyclerView(view: View) {
        leagueList = ArrayList()
        leagueRecyclerView = view.findViewById(R.id.leagueRecyclerView)
        leagueRecyclerView.layoutManager = LinearLayoutManager(context)
        leagueRecyclerView.setHasFixedSize(true)
        adapter = LeagueAdapter(requireContext(), leagueList,mDbRef,false){ league ->
            // Listener per il click su una card della RecyclerView
            val fragment = ActLeagueFragment.newInstance(league)
            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, fragment)
                .addToBackStack(null)
                .commit()
        }
        leagueRecyclerView.adapter = adapter
    }

    private fun setupFirebase() {
        mAuth = FirebaseAuth.getInstance()
        mDbRef = FirebaseDatabase.getInstance().getReference()
    }

    // carica le leghe che gestisce il LeagueManager
    private fun fetchLeaguesFromDatabase() {
        mDbRef.child("leagues").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                leagueList.clear()
                for (postSnapshot in snapshot.children) {
                    val league = postSnapshot.getValue(League::class.java)
                    league?.let {
                        if (it.leagueManager == mAuth.currentUser?.uid) {
                            leagueList.add(it)
                        } else {
                            Log.d("FirebaseData", "non sei il gestore della lega: $it")
                        }
                    }
                }
                adapter.notifyDataSetChanged()
                addMarkers(leagueList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load data: ${error.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }
    // carica le leghe a cui partecipa il TeamManager
    private fun fetchTeamLeaguesFromDatabase() {

        // First, fetch all leagues associated with the current user's team
        mDbRef.child("leagues_team").orderByChild("team_id").equalTo(UserInfo.team_id)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(teamSnapshot: DataSnapshot) {
                    // Clear the current leagueList
                    leagueList.clear()

                    // Iterate through each league-team association
                    teamSnapshot.children.forEach { teamLeagueSnapshot ->
                        val leagueUid = teamLeagueSnapshot.child("league_id").getValue(String::class.java)

                        // Fetch details of the league from leagues table using leagueUid
                        if (!leagueUid.isNullOrEmpty()) {
                            mDbRef.child("leagues").child(leagueUid)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(leagueSnapshot: DataSnapshot) {
                                        val league = leagueSnapshot.getValue(League::class.java)
                                        league?.let {
                                            leagueList.add(it)
                                        }
                                        adapter.notifyDataSetChanged()
                                        addMarkers(leagueList)
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        Toast.makeText(
                                            context,
                                            "Failed to load league data: ${error.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                })
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        context,
                        "Failed to load team leagues data: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun showAddLeaguePopup(view: View) {

        // Nascondi la RecyclerView
        (context as Activity).findViewById<RecyclerView>(R.id.leagueRecyclerView).visibility = View.GONE

        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.add_league, null)
        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setOnDismissListener {
            // Mostra di nuovo la RecyclerView quando la popup viene chiusa
            (context as Activity).findViewById<RecyclerView>(R.id.leagueRecyclerView).visibility = View.VISIBLE
        }

        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
        initializePopupFields(popupView)
        setupPopupListeners(popupView, popupWindow)

    }

    // inizializza campi per inserimento lega
    private fun initializePopupFields(popupView: View) {
        edtleague_name = popupView.findViewById(R.id.edt_league_name)
        edtleague_address = popupView.findViewById(R.id.edt_address)
        edtleague_level = popupView.findViewById(R.id.edt_league_level)
        edtleague_description = popupView.findViewById(R.id.edt_league_description)
        edtleague_entryfee = popupView.findViewById(R.id.edt_entryfee)
        edtleague_prize = popupView.findViewById(R.id.edt_league_prize)
        edtleague_restrictions = popupView.findViewById(R.id.edt_league_restrictions)
        edtleague_playingPeriod = popupView.findViewById(R.id.edt_playing_period)
        edtleague_MaxTeamNumber=popupView.findViewById(R.id.edt_maxTeamNumber)
        osmMapView = popupView.findViewById(R.id.osmMapView)
        mAuth = FirebaseAuth.getInstance()

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.number_teams,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            edtleague_MaxTeamNumber.adapter = adapter
        }

        val btnPlayingPeriod: ImageView = popupView.findViewById(R.id.btn_playing_period)
        btnPlayingPeriod.setOnClickListener { datePickerDialog(edtleague_playingPeriod) }

        val btnSearchAddress: Button = popupView.findViewById(R.id.btn_search_address)
        btnSearchAddress.setOnClickListener {
            val address = edtleague_address.text.toString()
            if (address.isNotEmpty()) {
                searchAddressAndShowMarker(address)
            } else {
                Toast.makeText(context, "Please enter an address", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun searchAddressAndShowMarker(address: String) {
        val geocoder = Geocoder(requireContext())
        val addresses: List<Address>? = try {
            geocoder.getFromLocationName(address, 1)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
        if (!addresses.isNullOrEmpty()) {
            val location = addresses[0]
            val latLng = GeoPoint(location.latitude, location.longitude)

            // Configura la mappa per mostrare la posizione
            osmMapView.controller.setZoom(15.0)
            osmMapView.controller.setCenter(latLng)
            osmMapView.visibility = View.VISIBLE

            // Aggiungi un marker sulla mappa
            val marker = Marker(osmMapView)
            marker.position = latLng
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            osmMapView.overlays.clear()  // Rimuovi marker esistenti
            osmMapView.overlays.add(marker)
            osmMapView.invalidate()
        } else {
            Toast.makeText(context, "Address not found", Toast.LENGTH_SHORT).show()
        }
    }

    // gestione bottoni di close e save nell' add league
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupPopupListeners(popupView: View, popupWindow: PopupWindow) {
        val btnClose: ImageView = popupView.findViewById(R.id.btn_close)
        btnClose.setOnClickListener { popupWindow.dismiss() }

        val btnSave: Button = popupView.findViewById(R.id.btn_save)
        btnSave.setOnClickListener { saveLeague(popupWindow) }
    }

    private fun datePickerDialog(edtPlayingPeriod: TextView) {
        val constraintsBuilder = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())

        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select a date range")
            .setCalendarConstraints(constraintsBuilder.build())
            .setTheme(R.style.CustomDatePicker) // Usa il tema personalizzato

        val datePicker = builder.build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
            val startDateString = sdf.format(Date(selection.first ?: 0))
            val endDateString = sdf.format(Date(selection.second ?: 0))
            edtPlayingPeriod.text = "$startDateString - $endDateString"
        }
        datePicker.show(requireFragmentManager(), "DATE_PICKER")
    }

    // salva la lega nell'add league
    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveLeague(popupWindow: PopupWindow) {
        val leaguename = edtleague_name.text.toString()
        val leagueaddress = edtleague_address.text.toString()
        val latitude = osmMapView.mapCenter.latitude
        val longitude = osmMapView.mapCenter.longitude
        val leaguelevel = edtleague_level.rating
        val leaguedescription = edtleague_description.text.toString()
        val leagueentryfee = edtleague_entryfee.text.toString()
        val leagueprize = edtleague_prize.text.toString()
        val leaguerestrictions = edtleague_restrictions.text.toString()
        val leagueplayingPeriod = edtleague_playingPeriod.text.toString()
        val leagueMaxTeamNumber= edtleague_MaxTeamNumber.selectedItem.toString().toFloatOrNull()

        if (!validateFields(
                leaguename,
                leagueaddress,
                leaguedescription,
                leagueentryfee,
                leagueprize,
                leaguerestrictions,
                leagueplayingPeriod,
                leagueMaxTeamNumber
            )
        ) {
            return
        }

        addLeagueToDatabase(
            leaguename,
            leagueaddress,
            latitude,
            longitude,
            leaguelevel,
            leaguedescription,
            leagueentryfee,
            leagueprize,
            leaguerestrictions,
            leagueplayingPeriod,
            mAuth.currentUser?.uid!!,
            leagueMaxTeamNumber
        )
        popupWindow.dismiss()
    }

    // Function to check if the date range is in the correct format
    private fun isValidDateRange(dateRange: String): Boolean {
        // Define the regex pattern for the date range
        val dateRangePattern = Regex("""\b\d{2}/\d{2}/\d{4} - \d{2}/\d{2}/\d{4}\b""")
        // Check if the input string matches the pattern
        return dateRangePattern.matches(dateRange)
    }

    // Function to validate the fields
    @RequiresApi(Build.VERSION_CODES.O)
    private fun validateFields(leaguename: String?,
                               leagueaddress: String?,
                               leaguedescription: String?,
                               leagueentryfee: String?,
                               leagueprize: String?,
                               leaguerestrictions: String?,
                               leagueplayingPeriod: String?,
                               leagueMaxTeamNumber: Float?): Boolean {
        var valid = true

        // Check each field and set an error message if it's empty
        if (leaguename!!.isEmpty()) {
            edtleague_name.error = "Please enter League name"
            valid = false
        }
        if (leagueaddress!!.isEmpty()) {
            edtleague_address.error = "Please enter League address"
            valid = false
        }
        if (leaguedescription!!.isEmpty()) {
            edtleague_description.error = "Please enter League description"
            valid = false
        }
        if (leagueentryfee!!.isEmpty()) {
            edtleague_entryfee.error = "Please enter League entry fee"
            valid = false
        } else if (leagueentryfee.toDoubleOrNull() == null) {
            edtleague_entryfee.error = "Please enter a valid number"
            valid = false
        }
        if (leagueprize!!.isEmpty()) {
            edtleague_prize.error = "Please enter League first prize"
            valid = false
        } else if(leagueprize.toDoubleOrNull() == null) {
            edtleague_prize.error = "Please enter a valid number"
            valid=false
        }
        if (leaguerestrictions!!.isEmpty()) {
            edtleague_restrictions.error = "Please enter League restrictions"
            valid = false
        }
        // Specific validation for the playing period field
        if (leagueplayingPeriod != "Select playing period") {
            if (!isValidDateRange(leagueplayingPeriod!!)) {
                edtleague_playingPeriod.error = "Please enter a valid date range (dd/MM/yyyy - dd/MM/yyyy)"
                valid = false
            } else if (numberOfDay(leagueplayingPeriod)!! * resources.getInteger(R.integer.matchForDays) < calculateTotalMatches(leagueMaxTeamNumber!!.toInt())) {
                edtleague_playingPeriod.error = "Select a wider date range"
                valid = false
            } else {
                // Rimuove l'errore se il campo è valido
                edtleague_playingPeriod.error = null
            }
        } else {
            edtleague_playingPeriod.error = "Please enter a valid date range (dd/MM/yyyy - dd/MM/yyyy)"
            valid = false
        }

        return valid
    }

    // calcola numero di match in un torneo
    private fun calculateTotalMatches(numTeams: Int): Int {
        return (numTeams * (numTeams - 1)) / 2
    }
    // calcola numero di giorni durata torneo
    @RequiresApi(Build.VERSION_CODES.O)
    private fun numberOfDay(playingPeriod: String): Int? {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dates = playingPeriod.split(" - ")

        return if (dates.size == 2) {
            val startDate = dateFormat.parse(dates[0])
            val endDate = dateFormat.parse(dates[1])

            val startLocalDate = startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            val endLocalDate = endDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

            if (startDate != null && endDate != null) {
                java.time.Period.between(startLocalDate, endLocalDate).days
            } else {
                null
            }
        } else {
            null
        }
    }


    private fun addLeagueToDatabase(
        name: String?,
        place: String?,
        latitude: Double,
        longitude: Double,
        level: Float?,
        description: String?,
        entry: String?,
        prize: String?,
        restrictions: String?,
        playingPeriod: String?,
        leagueManager: String?,
        maxTeamNumber: Float?
    ) {
        // Generate a unique key using push()
        val leagueId = mDbRef.child("leagues").push().key

        if (leagueId != null) {
            val league = League(leagueId, name, place,latitude,longitude, level, description, entry, prize, restrictions, playingPeriod, leagueManager,maxTeamNumber)
            // Set the value for the new node
            mDbRef.child("leagues").child(leagueId).setValue(league)
                .addOnSuccessListener {
                    // Handle success
                    Toast.makeText(context, "League added successfully!", Toast.LENGTH_SHORT).show()
                    addLeagueTableToDatabase(leagueId,name,null)
                }
                .addOnFailureListener { e ->
                    // Handle failure
                    Toast.makeText(context, "Failed to add league: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Handle error: Failed to generate unique key
            Toast.makeText(context, "Failed to generate unique key", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addLeagueTableToDatabase(
        idLeague: String?,
        nameLeague: String?,
        teams: MutableList<Team>?
    ) {

        if (idLeague != null) {
            // Crea un oggetto LeagueTable con l'ID generato
            val table = LeagueTable(idLeague, nameLeague, teams ?: mutableListOf())

            // Imposta il valore per il nuovo nodo con l'ID generato
            mDbRef.child("league_table").child(idLeague).setValue(table)
                .addOnSuccessListener {
                    // Gestisci il successo
                    Toast.makeText(context, "Table League added successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    // Gestisci il fallimento
                    Toast.makeText(context, "Failed to add Table league: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Gestisci l'errore: impossibile generare un ID unico
            Toast.makeText(context, "Failed to generate unique key", Toast.LENGTH_SHORT).show()
        }
    }


    private fun filter(text: String) {
        val filteredList = ArrayList<League>()
        leagueList.forEach {
            if (it.name?.contains(text, ignoreCase = true) == true) {
                filteredList.add(it)
            }
        }
        adapter.setData(filteredList)
    }

}