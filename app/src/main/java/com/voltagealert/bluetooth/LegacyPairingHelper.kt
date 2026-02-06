package com.voltagealert.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import java.lang.reflect.Method

/**
 * Helper for legacy PIN-based Bluetooth pairing.
 *
 * Some devices (like ST9401-UP voltage sensor) don't support modern SSP (Secure Simple Pairing)
 * and require old-style PIN codes. This class uses reflection to set PIN programmatically.
 */
object LegacyPairingHelper {
    private const val TAG = "LegacyPairingHelper"

    // Common PIN codes for industrial/medical Bluetooth devices
    private val COMMON_PINS = listOf(
        "1234",  // Most common (ESP32 default in examples)
        "9527",  // ESP32 Espressif official AT command example
        "0000",  // Generic Bluetooth default
        "1111",  // Common for industrial devices
        "0001",  // Sometimes used
        "",      // Empty PIN (some ESP32 devices)
        "123456" // Extended PIN
    )

    /**
     * Attempt to pair with device using legacy PIN method.
     * Tries to set PIN before starting pairing process.
     *
     * @param device The Bluetooth device to pair with
     * @param pinIndex Which PIN from the list to try (0-based)
     * @return true if pairing was initiated successfully, false otherwise
     */
    @SuppressLint("MissingPermission")
    fun attemptLegacyPairing(device: BluetoothDevice, pinIndex: Int = 0): Boolean {
        Log.d(TAG, "Attempting legacy PIN pairing with ${device.name} (${device.address})")

        // Check if already paired
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "Device already bonded")
            return true
        }

        // If currently bonding, wait for it to complete
        if (device.bondState == BluetoothDevice.BOND_BONDING) {
            Log.d(TAG, "Device currently bonding, waiting...")
            return true
        }

        try {
            // Try to set PIN BEFORE creating bond
            if (pinIndex < COMMON_PINS.size) {
                val pin = COMMON_PINS[pinIndex]
                Log.d(TAG, "Pre-setting PIN ${pinIndex + 1}/${COMMON_PINS.size}: $pin")
                setPin(device, pin)
            }

            // Small delay to let PIN set
            Thread.sleep(100)

            // Start pairing process
            Log.d(TAG, "Starting createBond()...")
            val bondResult = device.createBond()
            Log.d(TAG, "createBond() returned: $bondResult")

            return bondResult

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate pairing", e)
            return false
        }
    }

    /**
     * Try to set PIN for pairing using reflection.
     * This must be called when the device is in BOND_BONDING state.
     *
     * @param device The device being paired
     * @param pin The PIN code to try
     * @return true if PIN was set successfully
     */
    fun setPin(device: BluetoothDevice, pin: String): Boolean {
        return try {
            Log.d(TAG, "Attempting to set PIN: $pin")

            val setPinMethod: Method = device.javaClass.getMethod(
                "setPin",
                ByteArray::class.java
            )

            val pinBytes = pin.toByteArray()
            val result = setPinMethod.invoke(device, pinBytes) as? Boolean ?: false

            Log.d(TAG, "setPin() result: $result")
            result

        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "setPin method not found (API might not support it)", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set PIN", e)
            false
        }
    }

    /**
     * Try to confirm pairing using reflection.
     * Some devices require explicit confirmation after PIN is set.
     */
    fun confirmPairing(device: BluetoothDevice): Boolean {
        return try {
            val setPairingConfirmationMethod: Method = device.javaClass.getMethod(
                "setPairingConfirmation",
                Boolean::class.javaPrimitiveType
            )

            val result = setPairingConfirmationMethod.invoke(device, true) as? Boolean ?: false
            Log.d(TAG, "setPairingConfirmation(true) result: $result")
            result

        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "setPairingConfirmation method not available")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to confirm pairing", e)
            false
        }
    }

    /**
     * Cancel pairing attempt.
     */
    fun cancelPairing(device: BluetoothDevice): Boolean {
        return try {
            Log.d(TAG, "Cancelling pairing...")

            val cancelBondMethod: Method = device.javaClass.getMethod("cancelBondProcess")
            val result = cancelBondMethod.invoke(device) as? Boolean ?: false

            Log.d(TAG, "cancelBondProcess() result: $result")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel pairing", e)
            false
        }
    }

    /**
     * Get list of common PIN codes to try.
     */
    fun getCommonPins(): List<String> = COMMON_PINS
}
