package edu.bluejack252.hwixel

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.NavDestination
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNavigationView.isVisible = FirebaseAuth.getInstance().currentUser != null
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
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
        setupBottomNavigation(navController)
        binding.bottomNavigationView.post {
            navController.currentDestination?.let(::updateBottomNavVisibility)
                ?: run { binding.bottomNavigationView.isVisible = FirebaseAuth.getInstance().currentUser != null }
        }
        requestNotificationPermissionIfNeeded()
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavVisibility(destination)
        }
        navController.currentDestination?.let(::updateBottomNavVisibility)
            ?: run { binding.bottomNavigationView.isVisible = FirebaseAuth.getInstance().currentUser != null }
    }

    override fun onResume() {
        super.onResume()
        val navController = (supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as? NavHostFragment)
            ?.navController
        navController?.currentDestination?.let(::updateBottomNavVisibility)
            ?: run { binding.bottomNavigationView.isVisible = FirebaseAuth.getInstance().currentUser != null }
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_surface)
    }

    private fun updateBottomNavVisibility(destination: NavDestination) {
        val isBottomDestination = destination.id in BOTTOM_NAV_DESTINATIONS
        binding.bottomNavigationView.isVisible = isBottomDestination
        if (isBottomDestination && binding.bottomNavigationView.selectedItemId != destination.id) {
            binding.bottomNavigationView.selectedItemId = destination.id
        }
    }

    private fun setupBottomNavigation(navController: NavController) {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val destinationId = item.itemId
            navigateToBottomDestination(navController, destinationId)
        }
        binding.bottomNavigationView.setOnItemReselectedListener { }
    }

    private fun navigateToBottomDestination(
        navController: NavController,
        destinationId: Int
    ): Boolean {
        if (destinationId !in BOTTOM_NAV_DESTINATIONS) return false
        if (navController.currentDestination?.id == destinationId) return true
        if (FirebaseAuth.getInstance().currentUser == null) return false

        return runCatching {
            ensureMainGraph(navController)
            if (!navController.popBackStack(R.id.dashboardFragment, false)) {
                navController.setGraph(R.navigation.main_nav_graph)
            }
            if (destinationId != R.id.dashboardFragment) {
                navController.navigate(
                    destinationId,
                    null,
                    NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .build()
                )
            }
        }.recoverCatching {
            navController.setGraph(R.navigation.main_nav_graph)
            if (destinationId != R.id.dashboardFragment) {
                navController.navigate(destinationId)
            }
        }.isSuccess
    }

    private fun ensureMainGraph(navController: NavController) {
        if (navController.graph.id != R.id.main_nav_graph || navController.currentDestination == null) {
            navController.setGraph(R.navigation.main_nav_graph)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        val BOTTOM_NAV_DESTINATIONS = setOf(
            R.id.dashboardFragment,
            R.id.notificationsFragment,
            R.id.profileFragment
        )
    }
}
