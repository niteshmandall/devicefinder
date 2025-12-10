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
    trackingDevice: SeenDevice? = null,
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
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }

    // MapView State
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(19.0)
        }
    }

    // User Location Overlay
    val locationOverlay = remember {
        org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(
            org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(context),
            mapView
        ).apply {
            enableMyLocation()
            enableFollowLocation()
            isDrawAccuracyEnabled = true
        }
    }

    // Initial setup
    LaunchedEffect(Unit) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
    }

    // Effect to update Markers and Polyline when devices change
    LaunchedEffect(devices, trackingDevice) {
        // Keep the location overlay, clear others (Markers, Polylines)
        mapView.overlays.removeAll { it !is org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay }
        
        val currentUserLoc = locationOverlay.myLocation
        
        if (currentUserLoc != null) {
            
            // If Tracking, auto-center or bounds (Optional, for now just follow user)
            // if (trackingDevice != null) locationOverlay.enableFollowLocation() 

            devices.forEach { device ->
                // Simulate Geolocation relative to Real User Location
                val angleDegrees = abs(device.id.hashCode() % 360).toDouble()
                val angleRad = Math.toRadians(angleDegrees)
                
                val exponent = (-59 - device.rssi) / (10.0 * 2.7)
                val distMeters = 10.0.pow(exponent)
                
                val latOffset = (distMeters * cos(angleRad)) / 111111.0
                val lonOffset = (distMeters * sin(angleRad)) / (111111.0 * cos(Math.toRadians(currentUserLoc.latitude)))
                
                val devicePos = GeoPoint(currentUserLoc.latitude + latOffset, currentUserLoc.longitude + lonOffset)
                
                // Draw Tracking Line if this is the tracked device
                if (trackingDevice?.id == device.id) {
                    val line = org.osmdroid.views.overlay.Polyline(mapView)
                    line.setPoints(listOf(currentUserLoc, devicePos))
                    line.outlinePaint.color = android.graphics.Color.BLUE
                    line.outlinePaint.strokeWidth = 10f
                    // line.title = "Distance: ${distMeters.toInt()}m" // Optional
                    mapView.overlays.add(line)
                }

                val marker = Marker(mapView)
                marker.position = devicePos
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = "${device.name ?: "Unknown"}\n(${device.id})"
                marker.subDescription = "Signal: ${device.rssi} dBm"
                
                // Special icon for tracked device?
                if (trackingDevice?.id == device.id) {
                   marker.title = "TRACKING: ${marker.title}"
                   marker.showInfoWindow()
                }

                marker.setOnMarkerClickListener { m, _ ->
                    m.showInfoWindow()
                    onDeviceSelected(device)
                    true
                }
                
                mapView.overlays.add(marker)
            }
        }
        
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { 
            // Optional updates
        }
    )
    
    DisposableMapView(mapView)
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


