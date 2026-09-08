package com.justjdupuis.summonpro

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.justjdupuis.summonpro.api.VehicleLocationManager
import com.justjdupuis.summonpro.utils.Carpenter
import com.justjdupuis.summonpro.utils.GeoHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SummonForegroundService : Service(), VehicleLocationManager.Listener {
    companion object {
        internal var isRunning = false
        private const val TAG = "SummonForegroundService"
        private const val PROVIDER = LocationManager.GPS_PROVIDER
        private const val MOCK_INTERVAL_MS = 500L
        const val ACTION_STOP_SERVICE = "com.justjdupuis.summonpro.action.STOP_SERVICE"
        const val EXTRA_LOCATION_LAT = "extra_location_lat"
        const val EXTRA_LOCATION_LNG = "extra_location_lng"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locationManager: LocationManager
    private val mockLocation = Location(PROVIDER)
    private var screenOffReceiver: BroadcastReceiver? = null

    private var geofenceRadius = 80.0
    private var distanceToClaim = 20.0

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        initMockProvider()
        registerScreenOffReceiver()
        VehicleLocationManager.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }


        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        distanceToClaim = prefs.getString("claim_distance", "20")?.toDoubleOrNull() ?: 20.0
        geofenceRadius = when (prefs.getString("mock_mode", "china_na")) {
            "global" -> 5.8
            "china_na" -> 80.0
            else -> 85.0
        }

        startForeground(1, Carpenter.buildNotification(this))
        val latitude = VehicleLocationManager.latitude
        val longitude = VehicleLocationManager.longitude
        if (latitude == null || longitude == null) {
            Log.e(TAG, "Refusing to start without a fresh vehicle location")
            stopSelf()
            return START_NOT_STICKY
        }
        onNewLocation(latitude, longitude)

        startMockLoop()
        Toast.makeText(this, "Summon service started", Toast.LENGTH_SHORT).show()

        isRunning = true;
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initMockProvider() {
        runCatching {
            locationManager.addTestProvider(
                PROVIDER,
                false, false, false, false,
                true, true, true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE
            )
        }
        locationManager.setTestProviderEnabled(PROVIDER, true)
    }

    private fun removeMockProvider() {
        runCatching { locationManager.setTestProviderEnabled(PROVIDER, false) }
        runCatching { locationManager.removeTestProvider(PROVIDER) }
    }

    private fun registerScreenOffReceiver() {
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    handleScreenOff()
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let { unregisterReceiver(it) }
        screenOffReceiver = null
    }

    private fun handleScreenOff() {
        serviceScope.launch {
            VehicleLocationManager.close()
            stopSelf()
        }
    }

    private fun startMockLoop() {
        serviceScope.launch {
            while (isActive) {
                pushMockLocation()
                delay(MOCK_INTERVAL_MS)
            }
        }
    }

    private fun pushMockLocation() {
        mockLocation.apply {
            accuracy = 1f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(PROVIDER, mockLocation)
    }

    override fun onOpen() {
    }

    override fun onNewLocation(latitude: Double, longitude: Double) {
        val center = LatLng(latitude, longitude)
        val path = FirstFragment.pathPoints
        while (path.size > 1 && SphericalUtil.computeDistanceBetween(
                center,
                path.first()
            ) <= distanceToClaim
        ) {
            path.removeFirst()
        }

        val target = path.firstOrNull() ?: return
        val inside = GeoHelper.clampToCircle(center, target, geofenceRadius)
        mockLocation.latitude = inside.latitude
        mockLocation.longitude = inside.longitude
        pushMockLocation()
    }

    override fun onNewHeading(heading: Double) {
    }

    override fun onClosed() {
    }

    override fun onFailure(t: Throwable) {
        Log.e(TAG, "Stopping because fresh vehicle location is unavailable", t)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        VehicleLocationManager.removeListener(this)
        unregisterScreenOffReceiver()
        removeMockProvider()
    }
}
