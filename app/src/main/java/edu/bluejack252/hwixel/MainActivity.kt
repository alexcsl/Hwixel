package edu.bluejack252.hwixel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.navHostFragment) as NavHostFragment
            val graphResId = if (FirebaseAuth.getInstance().currentUser != null) {
                R.navigation.main_nav_graph
            } else {
                R.navigation.auth_nav_graph
            }
            navHostFragment.navController.setGraph(graphResId)
        }
    }
}
