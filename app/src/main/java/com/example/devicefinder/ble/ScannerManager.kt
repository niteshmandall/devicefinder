package com.example.devicefinder.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Manages device scanning for both BLE and Wi-Fi.
 *
 * @param context Application context used to access system services.
 */
class ScannerManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // In-memory store of unique devices
    private val _seenDevices = ConcurrentHashMap<String, SeenDevice>()

    // Exposed state for UI
    private val _devicesFlow = MutableStateFlow<List<SeenDevice>>(emptyList())
    val devices: StateFlow<List<SeenDevice>> = _devicesFlow.asStateFlow()

    private var isScanning = false

    // BLE Callback
    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val deviceName = it.device.name ?: it.scanRecord?.deviceName
                val mac = it.device.address
                val rssi = it.rssi

                val device = SeenDevice(
                    id = "BLE:$mac",
                    name = deviceName,
                    rssi = rssi,
                    source = "BLE",
                    lastSeen = System.currentTimeMillis()
                )
                updateDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            // Log failure or handle error
        }
    }
    
    // Wi-Fi Broadcast Receiver
    private val wifiScanReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                val results = wifiManager.scanResults
                for (scanResult in results) {
                    val ssid = scanResult.SSID
                    val bssid = scanResult.BSSID
                    val level = scanResult.level
                    
                    // Filter out empty SSIDs if desired, but for a "Radar" seeing everything is cool
                    if (ssid.isNotBlank()) {
                         val device = SeenDevice(
                            id = "WIFI:$bssid",
                            name = ssid,
                            rssi = level,
                            source = "WiFi",
                            lastSeen = System.currentTimeMillis()
                        )
                        updateDevice(device)
                    }
                }
            }
        }
    }
    
    // Periodically trigger Wi-Fi scans
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wifiScanRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                // Trigger a new Wi-Fi scan
                try {
                    wifiManager.startScan()
                } catch (e: Exception) {
                    // Ignore errors (e.g. throttling)
                }
                // Schedule next scan (Android throttles this, so 10-15s is realistic for foreground)
                mainHandler.postDelayed(this, 10000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (isScanning) return
        isScanning = true

        // 1. Start BLE Scan
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner?.startScan(null, settings, bleScanCallback)
        }

        // 2. Start Wi-Fi Scan
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)
        
        // Initial scan trigger
        wifiScanRunnable.run()
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        isScanning = false

        // 1. Stop BLE Scan
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(bleScanCallback)
        }

        // 2. Stop Wi-Fi Scan
        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        mainHandler.removeCallbacks(wifiScanRunnable)
    }

    /**
     * Updates the map and emits a new list.
     */
    private fun updateDevice(device: SeenDevice) {
        _seenDevices[device.id] = device
        emitList()
    }

    /**
     * Removes devices not seen in the last [timeoutMs].
     */
    fun cleanupOldEntries(timeoutMs: Long = 60000) {
        val now = System.currentTimeMillis()
        val iterator = _seenDevices.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeen > timeoutMs) {
                iterator.remove()
                changed = true
            }
        }
        if (changed) {
            emitList()
        }
    }

    private fun emitList() {
         // Convert map values to list and sort by RSSI (closest first)
        val sortedList = _seenDevices.values.sortedByDescending { it.rssi }
        _devicesFlow.value = sortedList
    }
    
    /**
     * Estimates distance in meters from RSSI.
     * Note: This is a rough approximation.
     * @param rssi Signal strength in dBm
     * @param txPower Assumed transmission power at 1 meter (default -59)
     * @param n Path-loss exponent (2.0 to 4.0 typically, 2.7 is a good average)
     */
    fun rssiToDistanceMeters(rssi: Int, txPower: Int = -59, n: Double = 2.7): Double {
        if (rssi == 0) return -1.0 // unknown
        val exponent = (txPower - rssi) / (10.0 * n)
        return 10.0.pow(exponent)
    }
}
