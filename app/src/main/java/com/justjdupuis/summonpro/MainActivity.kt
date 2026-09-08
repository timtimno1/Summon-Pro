package com.justjdupuis.summonpro

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.navigation.navOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.justjdupuis.summonpro.api.VehicleLocationManager
import com.justjdupuis.summonpro.databinding.ActivityMainBinding
import com.justjdupuis.summonpro.utils.Carpenter
import android.provider.Settings
import android.widget.Toast
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    companion object {
        var currentInstance: MainActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        Carpenter.createNotificationChannel(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionManager.isGranted(this, android.Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val fab = findViewById<FloatingActionButton>(R.id.fab)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.VehicleListFragment)
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            val isMockProvider = Carpenter.isSetAsMockProvider(this)
            if (!isMockProvider) {
                showMockLocationDialog()
                return@setOnClickListener
            }


            if (FirstFragment.pathPoints.isEmpty()) {
                Snackbar.make(view, "No path points available — Tap on map to set a target location", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                return@setOnClickListener
            }

            if (VehicleLocationManager.latitude == null || VehicleLocationManager.longitude == null) {
                Snackbar.make(view, "Cannot start service without initial location", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                return@setOnClickListener
            }

            Snackbar.make(view, "Starting personal location service", Snackbar.LENGTH_SHORT).show()

            Intent(this, SummonForegroundService::class.java).also { intent ->
                ContextCompat.startForegroundService(this, intent)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.WelcomeFragment) {
                toolbar.visibility = View.GONE
                fab.visibility = View.GONE
                window.navigationBarColor = Color.BLACK
            } else if (destination.id != R.id.FirstFragment) {
                toolbar.visibility = View.VISIBLE
                fab.visibility = View.GONE
            } else {
                toolbar.visibility = View.VISIBLE
                fab.visibility = View.VISIBLE
            }
        }
    }

    private fun showMockLocationDialog() {
        val message =
            """
            To enable Summon Pro, you need to select mock location:
            
            1. Open Settings → Developer options
            2. Scroll or search for “Select mock location app”
            3. Choose “Summon Pro” from the list
            
            If you don’t see Developer options:
            - Go to Settings > About phone
            - Tap “Build number” 7 times to unlock it
            
            Stop immediately if vehicle location becomes stale or unavailable.
            """.trimIndent()

        val spannable = SpannableString(message)
        Linkify.addLinks(spannable, Linkify.WEB_URLS)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Almost there…")
            .setMessage(spannable)
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "Developer options not enabled", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Now force link clicks to work
        (dialog.findViewById<TextView>(android.R.id.message))?.movementMethod =
            LinkMovementMethod.getInstance()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                SettingsDialogFragment().show(supportFragmentManager, "SettingsDialog")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()

        if (SummonForegroundService.isRunning) {
            Log.d("MainActivity", "onResume — Summon service is running — stay on FirstFragment")
            return
        }

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        val currentDest = navController.currentDestination?.id
        if (currentDest == R.id.FirstFragment) {
            try {
                navController.navigate(
                    R.id.action_FirstFragment_to_VehicleListFragment,
                    null,
                    navOptions {
                        popUpTo(R.id.FirstFragment) {
                            inclusive = true
                        }
                    }
                )
            } catch (e: IllegalArgumentException) {
                Log.w("MainActivity", "Navigation failed: ${e.message}")
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (!SummonForegroundService.isRunning && VehicleLocationManager.isConnected()) {
            VehicleLocationManager.shutdown()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (VehicleLocationManager.isConnected()) {
            VehicleLocationManager.shutdown()
        }
    }
}
