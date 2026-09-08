package com.justjdupuis.summonpro.api

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

/** Lifecycle-aware direct Fleet API location poller shared by the map and service. */
object VehicleLocationManager {
    // Global mode has a 5.8 m radius, so location updates cannot be spaced by several seconds.
    private const val POLL_INTERVAL_MS = 1_000L
    private const val MAX_LOCATION_AGE_MS = 120_000L
    private const val MAX_CONSECUTIVE_FAILURES = 3
    private const val TAG = "LocationPoller"

    private val listeners = CopyOnWriteArraySet<Listener>()
    private var scope: CoroutineScope? = null
    private var pollJob: Job? = null

    var latitude: Double? = null
    var longitude: Double? = null
    var vin: String? = null

    interface Listener {
        fun onOpen()
        fun onNewLocation(latitude: Double, longitude: Double)
        fun onNewHeading(heading: Double)
        fun onClosed()
        fun onFailure(t: Throwable)
    }

    fun connect(vehicleVin: String, token: String) {
        if (pollJob?.isActive == true) return
        vin = vehicleVin
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        pollJob = scope?.launch {
            listeners.forEach { it.onOpen() }
            var consecutiveFailures = 0
            while (isActive) {
                try {
                    val state = TeslaApi.service.getVehicleLocation(token, vehicleVin)
                        .response.driveState ?: error("Tesla returned no location data")
                    val lat = state.latitude ?: error("Tesla returned no latitude")
                    val lon = state.longitude ?: error("Tesla returned no longitude")
                    val timestamp = state.timestamp ?: error("Tesla returned no location timestamp")
                    val age = LocationFreshness.ageMillis(timestamp)
                    check(LocationFreshness.isFresh(timestamp, MAX_LOCATION_AGE_MS)) {
                        "Tesla location is stale (${age.coerceAtLeast(0) / 1000}s old)"
                    }

                    consecutiveFailures = 0
                    latitude = lat
                    longitude = lon
                    listeners.forEach { it.onNewLocation(lat, lon) }
                    state.heading?.let { heading ->
                        listeners.forEach { it.onNewHeading(heading) }
                    }
                    delay(POLL_INTERVAL_MS)
                } catch (t: Throwable) {
                    if (!isActive) break
                    consecutiveFailures++
                    Log.e(TAG, "Location poll failed", t)
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        latitude = null
                        longitude = null
                        listeners.forEach { it.onFailure(t) }
                        break
                    }
                    delay((POLL_INTERVAL_MS * consecutiveFailures).coerceAtMost(30_000L))
                }
            }
        }
    }

    fun close() {
        pollJob?.cancel()
        pollJob = null
        scope?.cancel()
        scope = null
        latitude = null
        longitude = null
        listeners.forEach { it.onClosed() }
    }

    fun shutdown() {
        close()
        listeners.clear()
    }

    fun isConnected(): Boolean = pollJob?.isActive == true

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
