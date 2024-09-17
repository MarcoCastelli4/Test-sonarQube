package com.example.leaguepro

import android.app.Dialog
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [AllLeagueFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AllLeagueFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private lateinit var leagueRecyclerView: RecyclerView
    private lateinit var leagueList: ArrayList<League>
    private lateinit var adapter: LeagueAdapter
    private lateinit var mDbRef: DatabaseReference
    private lateinit var mAuth: FirebaseAuth
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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_all_league, container, false)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment AllLeagueFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            AllLeagueFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAuth = FirebaseAuth.getInstance()
        // creo collegamento con il database
        mDbRef = FirebaseDatabase.getInstance().getReference()

        leagueList= ArrayList()
        leagueRecyclerView = view.findViewById(R.id.leagueRecyclerView)
        leagueRecyclerView.layoutManager = LinearLayoutManager(context)
        leagueRecyclerView.setHasFixedSize(true)

        adapter = LeagueAdapter(requireContext(),leagueList,mDbRef,true){ league ->
            // Listener per il click su una card della RecyclerView
            val fragment = ActLeagueFragment.newInstance(league)
            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, fragment)
                .addToBackStack(null)
                .commit()
        }
        leagueRecyclerView.adapter = adapter

        loadMap(view)
        // Inizializza il SearchView
        searchView = view.findViewById(R.id.search_bar)
        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                filter(s.toString())
            }
        })
        mDbRef.child("leagues").addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                leagueList.clear()
                for (postSnapshot in snapshot.children) {
                    val league = postSnapshot.getValue(League::class.java)
                    if (league != null) {
                        leagueList.add(league)
                    } else {
                        Log.d("FirebaseData", "Invalid league data: $league") // Log per i dati non validi
                    }
                }
                adapter.notifyDataSetChanged()
                addMarkers(leagueList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })

    }

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
            searchBar.visibility = View.GONE
            mapsBar.visibility = View.VISIBLE
            osmMapView.visibility = View.VISIBLE
        }
        // Listener per il click sul pulsante "indietro"
        backButton.setOnClickListener {
            searchBar.visibility = View.VISIBLE
            mapsBar.visibility = View.GONE
            osmMapView.visibility = View.GONE
        }
    }
    private fun addMarkers(leagueList: ArrayList<League>) {
        view?.let { loadMap(it) }
        if (isAdded){
            val context = requireContext()
            osmMapView.overlays.clear() // Clear existing markers
            for (league in leagueList) {
                val location = GeoPoint(league.latitude ?: 0.0, league.longitude ?: 0.0)
                val marker = Marker(osmMapView)
                marker.position = location
                marker.title = league.name
                marker.icon = context.getDrawable(R.drawable.location)
                // listener per clic sul marker
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
        firstPrizeTextView.text= "${league.prize}€ for first prize"
        restrictionsTextView.text = league.restrictions

        // chiusura del dialog
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        // mostra dialog
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        osmMapView.onDetach()
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
