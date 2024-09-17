package com.example.leaguepro

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.leaguepro.databinding.InfoLeagueBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ActLeagueFragment : Fragment() {

    private var leagueId: String? = null
    private lateinit var layout_chat: LinearLayout
    private lateinit var layout_communication: LinearLayout
    private lateinit var btn_addCommunication: ImageView
    private lateinit var btn_addChat: ImageView
    private lateinit var mDbRef: DatabaseReference
    private lateinit var binding: InfoLeagueBinding
    private var currentMenuItemId: Int = R.id.match
    private var leagueOwnerId: String? = null



    companion object {
        @JvmStatic
        fun newInstance(league: League): ActLeagueFragment {
            val fragment = ActLeagueFragment()
            val args = Bundle().apply {
                putString("league_id", league.uid) // Assuming uid is a String; adjust accordingly
                putString("league_name", league.name) // Assuming league.name is available
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            leagueId = it.getString("league_id")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Initialize ViewBinding
        binding = InfoLeagueBinding.inflate(inflater, container, false)
        val view = binding.root

        // Imposta il nome e l'immagine della lega
        arguments?.let {
            val leagueName = it.getString("league_name")
            binding.leagueName.text = leagueName
        }

        // Configura il menu di navigazione
        binding.upperNavigationView.inflateMenu(R.menu.league_upper_nav_menu)
        binding.upperNavigationView.setOnItemSelectedListener { item ->
            currentMenuItemId = item.itemId // Aggiorna l'ID dell'elemento selezionato
            updateCommunicationAndChatButtonVisibility()
            when (item.itemId) {
                R.id.match -> {
                    leagueId?.let { id ->
                        val fragment = AllMatchFragment.newInstance(id)
                        NavigationManager.replaceFragment(this, fragment)
                    } ?: run {
                        Toast.makeText(requireContext(), "League ID not available", Toast.LENGTH_LONG).show()
                    }
                    NavigationManager.showIndicator(binding, item)
                    true
                }
                R.id.leaguetable -> {
                    leagueId?.let { id ->
                        val fragment = LeagueTableFragment().apply {
                            arguments = Bundle().apply {
                                putString("league_id", id)
                            }
                        }
                        NavigationManager.replaceFragment(this, fragment)
                    } ?: run {
                        Toast.makeText(requireContext(), "League ID not available", Toast.LENGTH_LONG).show()
                    }
                    NavigationManager.showIndicator(binding, item)
                    true
                }
                R.id.statistics -> {
                    leagueId?.let { id ->
                        val fragment = StatisticsFragment().apply{
                            arguments = Bundle().apply {
                                putString("league_id", id)
                            }
                        }
                        NavigationManager.replaceFragment(this,fragment)
                    } ?: run {
                        Toast.makeText(requireContext(), "League ID not available", Toast.LENGTH_LONG).show()
                    }
                    NavigationManager.showIndicator(binding, item)
                    true
                }
                R.id.comunications -> {
                    leagueId?.let { id ->
                        val fragment = CommunicationFragment().apply {
                            arguments = Bundle().apply {
                                putString("league_id", id)
                            }
                        }
                        NavigationManager.replaceFragment(this, fragment)
                    } ?: run {
                        Toast.makeText(requireContext(), "League ID not available", Toast.LENGTH_LONG).show()
                    }
                        NavigationManager.showIndicator(binding, item)
                        true
                    }
                else -> false
            }
        }
        binding.upperNavigationView.selectedItemId = R.id.match
        binding.upperNavigationView.post {
            val selectedItem = binding.upperNavigationView.menu.findItem(R.id.match)
            if (selectedItem != null) {
                NavigationManager.showIndicator(binding, selectedItem)
            }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mDbRef = FirebaseDatabase.getInstance().reference
        setupView(view)
        lifecycleScope.launch {
            try {
                // Recupera solo l'ID del proprietario della lega
                val leagueSnapshot = mDbRef.child("leagues").child(leagueId!!).get().await()
                val league = leagueSnapshot.getValue(League::class.java)
                leagueOwnerId = league?.leagueManager // Imposta l'ID del proprietario della lega

                // Aggiorna la visibilità dei pulsanti in base all'ID del proprietario e al menu corrente
                updateCommunicationAndChatButtonVisibility()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error fetching league: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ActLeagueFragment", "Error getting league", e)
            }
        }
    }

    private fun setupView(view: View) {
        layout_communication = view.findViewById(R.id.layout_add_communication)
        btn_addCommunication = view.findViewById(R.id.add_communication)
        layout_chat=view.findViewById(R.id.layout_chat)
        btn_addChat = view.findViewById(R.id.add_chat)

        // Configura la visibilità iniziale del layout di calendar e communication e chat
        //updateCreateCalendarButtonVisibility()
        updateCommunicationAndChatButtonVisibility()

        //click listener per il pulsante Add Communication
        layout_communication.setOnClickListener {
                showAddCommunicationDialog()
        }
        btn_addCommunication.setOnClickListener {
            showAddCommunicationDialog()
        }
        // Listener per il pulsante "Add Chat"
        layout_chat.setOnClickListener {
            openChat()
        }
        btn_addChat.setOnClickListener {
            openChat()
        }
    }


    private fun openChat() {
        // codice per aprire la chat
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("league_id", leagueId)
        startActivity(intent)
    }
    private fun showAddCommunicationDialog() {
        // Inflazione del layout del dialogo
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.add_communication, null)
        val inputText = dialogView.findViewById<EditText>(R.id.input_communication_text)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

        // Costruzione e visualizzazione del dialogo
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Listener per il pulsante "Save"
        btnSave.setOnClickListener {
            val text = inputText.text.toString()
            if (text.isNotBlank()) {
                saveCommunication(text)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please enter communication text", Toast.LENGTH_SHORT).show()
            }
        }

        // Listener per il pulsante "Cancel"
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun saveCommunication(text: String) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        lifecycleScope.launch {
            try {
                val communicationId = mDbRef.child("communications").push().key
                if (communicationId != null) {
                    val communication = Communication(
                        communicationId = communicationId,
                        text = text,
                        date = currentDate,
                        leagueId = leagueId!!
                    )
                    mDbRef.child("communications").child(communicationId).setValue(communication)
                        .await()
                    Toast.makeText(requireContext(), "Communication added successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error generating communication ID", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error saving communication: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ActLeagueFragment", "Error saving communication", e)
            }
        }
    }
    private fun updateCommunicationAndChatButtonVisibility() {
        if (this::layout_communication.isInitialized) {
            // Mostra addCommunicationLayout solo se l'utente è un League Manager e la voce del menu selezionata è "communications"
            val isLeagueOwner = UserInfo.userId == leagueOwnerId
            layout_communication.visibility =
                if (isLeagueOwner && UserInfo.userType == getString(R.string.LeagueManager) &&
                    currentMenuItemId == R.id.comunications
                ) View.VISIBLE else View.GONE
        }
        if(this::layout_chat.isInitialized){
            layout_chat.visibility =
                if(UserInfo.userType != "" && currentMenuItemId == R.id.comunications) View.VISIBLE else View.GONE
        }
    }

}
