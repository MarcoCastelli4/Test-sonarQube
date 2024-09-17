package com.example.leaguepro

import android.content.Intent
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import com.example.leaguepro.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomNavigationView: BottomNavigationView = binding.bottomNavigationView

        // Inflate the correct menu based on user type
        when (UserInfo.userType) {
            getString(R.string.LeagueManager) -> {
                bottomNavigationView.inflateMenu(R.menu.league_nav_menu)
            }
            getString(R.string.TeamManager) -> {
                bottomNavigationView.inflateMenu(R.menu.team_nav_menu)
            }
            else -> {
                bottomNavigationView.inflateMenu(R.menu.visitor_nav_menu) // Fallback menu
            }
        }

        // Set the initial fragment based on user login status
        val initialFragment = if (UserInfo.logged) {
            MyLeagueFragment()
        } else {
            AllLeagueFragment()
        }
        NavigationManager.replaceFragment(this, initialFragment)

        // Add a GlobalLayoutListener to ensure that the layout is fully done before setting the indicator
        binding.bottomNavigationView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // Ensure the layout is done before proceeding
                    val selectedItem = if (UserInfo.logged) {
                        binding.bottomNavigationView.menu.findItem(R.id.myleague)
                    } else {
                        binding.bottomNavigationView.menu.findItem(R.id.allLeague)
                    }

                    // Show the indicator for the initial selected item
                    selectedItem?.let {
                        NavigationManager.showIndicator(this@MainActivity, binding, it)
                    }

                    // Remove the listener after the layout is done to prevent repeated calls
                    binding.bottomNavigationView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )

        // Set listener for bottom navigation item selection
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.myleague -> {
                    NavigationManager.replaceFragment(this, MyLeagueFragment())
                    NavigationManager.showIndicator(this, binding, item)
                    true
                }
                R.id.allLeague -> {
                    NavigationManager.replaceFragment(this, AllLeagueFragment())
                    NavigationManager.showIndicator(this, binding, item)
                    true
                }
                R.id.myteam -> {
                    NavigationManager.replaceFragment(this, MyTeamFragment())
                    NavigationManager.showIndicator(this, binding, item)
                    true
                }
                R.id.profile -> {
                    NavigationManager.replaceFragment(this, ProfileFragment())
                    NavigationManager.showIndicator(this, binding, item)
                    true
                }
                R.id.goBack -> {
                    val intent = Intent(this, Login::class.java)
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }

        // Set the initial selected item
        bottomNavigationView.selectedItemId = if (UserInfo.logged) {
            R.id.myleague
        } else {
            R.id.allLeague
        }
    }
}
