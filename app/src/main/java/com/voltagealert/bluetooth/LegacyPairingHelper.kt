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
        "0000",  // Most common default
        "1234",  // Second most common
        "1111",  // Common for industrial devices
        "0001",  // Sometimes used
        "123456" // Extended PIN
    )

    /**
     * Attempt to pair with device using legacy PIN method.
     * Tries common PIN codes in order until one succeeds.
     *
     * @param device The Bluetooth device to pair with
     * @return true if pairing was initiated successfully, false otherwise
     */
    @SuppressLint("MissingPermission")
    fun attemptLegacyPairing(device: BluetoothDevice): Boolean {
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
