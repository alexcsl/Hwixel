package edu.bluejack252.hwixel

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController
        if (savedInstanceState == null) {
            val graphResId = if (FirebaseAuth.getInstance().currentUser != null) {
                R.navigation.main_nav_graph
            } else {
                R.navigation.auth_nav_graph
            }
            navController.setGraph(graphResId)
        }
        binding.bottomNavigationView.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigationView.isVisible = destination.id in setOf(
                R.id.dashboardFragment,
                R.id.notificationsFragment,
                R.id.profileFragment
            )
        }
    }
}
