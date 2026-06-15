package edu.bluejack25_2.hwixel

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.NavDestination
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.data.ServiceLocator
import edu.bluejack25_2.hwixel.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isUpdatingNavSelection = false
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideStatusBar()
        binding.bottomNavigationView.isVisible = FirebaseAuth.getInstance().currentUser != null
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        binding.bottomNavigationView.post {
            syncNavHostPadding(binding.bottomNavigationView.isVisible)
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
        observeNotificationBadge()
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
    }

    private fun updateBottomNavVisibility(destination: NavDestination) {
        val isBottomDestination = destination.id in BOTTOM_NAV_DESTINATIONS
        binding.bottomNavigationView.isVisible = isBottomDestination
        syncNavHostPadding(isBottomDestination)
        if (isBottomDestination && binding.bottomNavigationView.selectedItemId != destination.id) {
            isUpdatingNavSelection = true
            binding.bottomNavigationView.selectedItemId = destination.id
            isUpdatingNavSelection = false
        }
    }

    private fun syncNavHostPadding(bottomNavVisible: Boolean) {
        val height = if (bottomNavVisible) {
            binding.bottomNavigationView.height.takeIf { it > 0 }
                ?: (80 * resources.displayMetrics.density).toInt()
        } else 0
        binding.navHostFragment.updatePadding(bottom = height)
    }

    private fun setupBottomNavigation(navController: NavController) {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            if (isUpdatingNavSelection) return@setOnItemSelectedListener true
            navigateToBottomDestination(navController, item.itemId)
        }
        binding.bottomNavigationView.setOnItemReselectedListener { item ->
            if (isUpdatingNavSelection) return@setOnItemReselectedListener
            navigateToBottomDestination(navController, item.itemId)
        }
    }

    private fun navigateToBottomDestination(
        navController: NavController,
        destinationId: Int
    ): Boolean {
        if (destinationId !in BOTTOM_NAV_DESTINATIONS) return false
        if (FirebaseAuth.getInstance().currentUser == null) return false
        if (destinationId == R.id.dashboardFragment) {
            return navigateToDashboard(navController)
        }
        if (navController.currentDestination?.id == destinationId) return true

        return runCatching {
            ensureMainGraph(navController)
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.dashboardFragment, false, false)
                .build()
            navController.navigate(destinationId, null, options)
        }.recoverCatching {
            navController.setGraph(R.navigation.main_nav_graph)
            if (destinationId != R.id.dashboardFragment) {
                navController.navigate(destinationId)
            }
        }.isSuccess
    }

    private fun navigateToDashboard(navController: NavController): Boolean {
        return runCatching {
            val needsRecovery = ServiceLocator.getProfileSettingsRepository(this)
                .consumeNavigationRecoveryRequired()
            if (needsRecovery) {
                navController.setGraph(R.navigation.main_nav_graph)
                return@runCatching
            }
            ensureMainGraph(navController)
            if (!navController.popBackStack(R.id.dashboardFragment, false) ||
                navController.currentDestination?.id != R.id.dashboardFragment
            ) {
                navController.navigate(
                    R.id.dashboardFragment,
                    null,
                    NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.dashboardFragment, false, false)
                        .build()
                )
            }
        }.recoverCatching {
            navController.setGraph(R.navigation.main_nav_graph)
        }.isSuccess
    }

    private fun ensureMainGraph(navController: NavController) {
        if (navController.graph.id != R.id.main_nav_graph || navController.currentDestination == null) {
            navController.setGraph(R.navigation.main_nav_graph)
        }
    }

    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun observeNotificationBadge() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ServiceLocator.getNotificationRepository()
            .observeNotifications(uid)
            .observe(this) { notifications ->
                val unread = notifications.count { !it.isRead }
                val badge = binding.bottomNavigationView.getOrCreateBadge(R.id.notificationsFragment)
                badge.isVisible = unread > 0
                if (unread > 0) badge.number = unread
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
