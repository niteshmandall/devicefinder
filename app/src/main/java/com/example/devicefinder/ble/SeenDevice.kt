package com.example.devicefinder.ble

/**
 * Represents a device seen via BLE or Wi-Fi.
 *
 * @property id Unique ID (e.g. BLE MAC or Wi-Fi BSSID)
 * @property name Human-readable name (if available)
 * @property rssi Signal strength in dBm
 * @property source Source of the signal ("BLE" or "WiFi")
 * @property lastSeen Timestamp when the device was last seen (System.currentTimeMillis())
 */
data class SeenDevice(
    val id: String,
    val name: String?,
    val rssi: Int,
    val source: String,
    val lastSeen: Long = System.currentTimeMillis()
)
