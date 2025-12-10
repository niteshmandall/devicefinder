package com.example.devicefinder.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.devicefinder.ble.SeenDevice
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A Radar-style view that displays devices as blips on a circular grid.
 *
 * @param devices List of devices to display.
 * @param maxDistanceMeters The distance represented by the edge of the radar.
 */
@Composable

fun RadarView(
    devices: List<SeenDevice>,
    maxDistanceMeters: Double = 20.0,
    onDeviceSelected: (SeenDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Initialize OSMDroid Config
    remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )
        // Set User Agent to avoid being blocked
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }

    // Default "User Location" - In a real app, use FusedLocationProvider.
    // For this demo, we pick a fixed point (e.g., London Eye) or valid coordinates.
    // We'll use a state to allow updates if we ever added real GPS.
    val userLocation by remember { mutableStateOf(GeoPoint(51.5033, -0.1195)) } 

    // MapView State
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(19.0)
            controller.setCenter(userLocation)
        }
    }

    // Effect to update Markers when devices change
    LaunchedEffect(devices) {
        mapView.overlays.clear()
        
        // Add User Marker (Center)
        val userMarker = Marker(mapView)
        userMarker.position = userLocation
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        userMarker.title = "You are here"
        // Use default icon or custom
        mapView.overlays.add(userMarker)

        devices.forEach { device ->
            // Simulate Geolocation
            // We have distance and a consistent random angle.
            // New Point = Origin + Distance * Angle
            val angleDegrees = abs(device.id.hashCode() % 360).toDouble()
            val angleRad = Math.toRadians(angleDegrees)
            
            val exponent = (-59 - device.rssi) / (10.0 * 2.7)
            val distMeters = 10.0.pow(exponent)
            
            // Simple flat earth approx for small distances is fine
            // 1 deg Lat ~= 111,111 meters
            // 1 deg Lon ~= 111,111 * cos(lat) meters
            val latOffset = (distMeters * cos(angleRad)) / 111111.0
            val lonOffset = (distMeters * sin(angleRad)) / (111111.0 * cos(Math.toRadians(userLocation.latitude)))
            
            val devicePos = GeoPoint(userLocation.latitude + latOffset, userLocation.longitude + lonOffset)
            
            val marker = Marker(mapView)
            marker.position = devicePos
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = "${device.name ?: "Unknown"}\n(${device.id})"
            marker.subDescription = "Signal: ${device.rssi} dBm"
            
            // On Click
            marker.setOnMarkerClickListener { m, _ ->
                m.showInfoWindow() // Show the title bubble
                onDeviceSelected(device)
                true
            }
            
            mapView.overlays.add(marker)
        }
        
        mapView.invalidate() // Redraw
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { 
            // Optional: Update center if user moved
            // it.controller.setCenter(userLocation)
        }
    )
}

// Cleanup lifecycle
@Composable
fun DisposableMapView(mapView: MapView) {
    androidx.compose.runtime.DisposableEffect(mapView) {
        onDispose {
            mapView.onDetach()
        }
    }
}


