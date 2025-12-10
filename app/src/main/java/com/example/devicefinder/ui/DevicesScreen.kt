package com.example.devicefinder.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.devicefinder.DevicesViewModel
import com.example.devicefinder.ble.SeenDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = viewModel()
) {
    val devices by viewModel.devices.collectAsState()
    var isScanning by remember { mutableStateOf(false) }
    var useRadarView by remember { mutableStateOf(true) } // Default to Radar
    var selectedDevice by remember { mutableStateOf<SeenDevice?>(null) }

    // Required permissions depending on Android version
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= 31) {
        // Android 12+
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE
        )
    } else {
        // Pre-Android 12
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { params ->
        val allGranted = params.values.all { it }
        if (allGranted) {
            isScanning = true
            viewModel.startScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Radar") },
                actions = {
                    // View Toggle
                    Button(onClick = { useRadarView = !useRadarView }) {
                        Text(if (useRadarView) "List View" else "Radar View")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (isScanning) {
                            isScanning = false
                            viewModel.stopScan()
                        } else {
                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        }
                    }) {
                        Text(if (isScanning) "Stop" else "Scan")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            
            // Privacy Note
            Text(
                text = "Note: ID randomization hides real identities. Distances are rough estimates.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No devices found yet.")
                        if (isScanning) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator()
                            Text("Scanning...", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Press 'Scan' to begin.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (useRadarView) {
                        RadarView(
                            devices = devices,
                            onDeviceSelected = { device -> selectedDevice = device },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(devices) { device ->
                                DeviceItem(device, viewModel)
                            }
                        }
                    }

                    // Device Details Overlay (Map style card at bottom)
                    if (selectedDevice != null && useRadarView) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Selected Device",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = selectedDevice?.name ?: "Unknown Name",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = selectedDevice?.id ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Signal: ${selectedDevice?.rssi} dBm")
                                    Text("Distance: ${viewModel.getDistanceString(selectedDevice?.rssi ?: 0)}")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { selectedDevice = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: SeenDevice, viewModel: DevicesViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = device.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Source: ${device.source}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = viewModel.getDistanceString(device.rssi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
