package com.example.devicefinder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.devicefinder.ble.ScannerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel to hold scanning state and manage ScannerManager.
 */
class DevicesViewModel(application: Application) : AndroidViewModel(application) {

    private val scannerManager = ScannerManager(application)
    
    // Expose the list of devices observed from ScannerManager
    val devices = scannerManager.devices

    init {
        // Automatically start cleanup loop
        viewModelScope.launch {
            while (isActive) {
                delay(10_000) // Cleanup check every 10 seconds
                scannerManager.cleanupOldEntries()
            }
        }
    }

    /**
     * Start scanning for both BLE and Wi-Fi devices.
     */
    fun startScan() {
        scannerManager.startScanning()
    }

    /**
     * Stop all scanning.
     */
    fun stopScan() {
        scannerManager.stopScanning()
    }

    /**
     * Helper to expose distance calculation to UI if needed, 
     * though it's also accessible via ScannerManager if we made it static/companion.
     * Here we just delegate instance method.
     */
    fun getDistanceString(rssi: Int): String {
        val dist = scannerManager.rssiToDistanceMeters(rssi)
        return if (dist < 0) "?" else "%.1fm".format(dist)
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
