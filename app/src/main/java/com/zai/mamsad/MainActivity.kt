package com.zai.mamsad

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.zai.mamsad.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch from splash theme to app theme before super.onCreate
        setTheme(R.style.Theme_Mamsad)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host)
        binding.bottomNav.setupWithNavController(navController)

        // Keep an "All" item highlighted visually when on Home (start destination).
        // Default behavior already maps menu item IDs to destinations — Home is the start.
    }
}
