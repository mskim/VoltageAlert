package com.voltagealert.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that intercepts Bluetooth pairing requests
 * and automatically provides PIN codes for legacy devices.
 *
 * This is needed for devices like ST9401-UP that use PIN-based pairing
 * instead of modern SSP (Secure Simple Pairing).
 */
class PairingRequestReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PairingRequestReceiver"

        // Track which PIN we're currently trying
        private var currentPinIndex = 0
        private var targetDevice: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)

                device?.let {
                    Log.d(TAG, "Pairing request from: ${it.name} (${it.address})")
                    Log.d(TAG, "Pairing variant: $pairingVariant")

                    when (pairingVariant) {
                        BluetoothDevice.PAIRING_VARIANT_PIN -> {
                            // Device requests PIN entry
                            Log.d(TAG, "Device requests PIN (legacy pairing)")
                            handlePinPairing(device)
                            abortBroadcast() // Prevent system dialog
                        }

                        BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> {
                            // SSP with numeric confirmation (current problem)
                            Log.d(TAG, "Device uses SSP numeric confirmation")

                            // Try to accept the pairing automatically
                            // This might not work if device has no display
                            LegacyPairingHelper.confirmPairing(device)
                        }

                        // PAIRING_VARIANT_CONSENT = 3 (not publicly defined in all Android versions)
                        3 -> {
                            // Just consent required (no PIN)
                            Log.d(TAG, "Device requests consent only")
                            LegacyPairingHelper.confirmPairing(device)
                            abortBroadcast()
                        }

                        else -> {
                            Log.d(TAG, "Unknown pairing variant: $pairingVariant")
                        }
                    }
                }
            }

            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                val previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)

                device?.let {
                    Log.d(TAG, "Bond state changed: ${it.name} (${it.address})")
                    Log.d(TAG, "  Previous: ${bondStateToString(previousState)} -> New: ${bondStateToString(bondState)}")

                    when (bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            Log.d(TAG, "✓ Successfully bonded!")
                            currentPinIndex = 0 // Reset for next device
                            targetDevice = null
                        }

                        BluetoothDevice.BOND_BONDING -> {
                            Log.d(TAG, "Bonding in progress...")
                            if (targetDevice == null) {
                                targetDevice = it.address
                            }
                        }

                        BluetoothDevice.BOND_NONE -> {
                            if (previousState == BluetoothDevice.BOND_BONDING) {
                                Log.d(TAG, "✗ Bonding failed")

                                // If this was our target device and we have more PINs to try
                                if (it.address == targetDevice) {
                                    tryNextPin(device)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle PIN-based pairing by trying common PIN codes.
     */
    private fun handlePinPairing(device: BluetoothDevice) {
        val pins = LegacyPairingHelper.getCommonPins()

        if (currentPinIndex < pins.size) {
            val pin = pins[currentPinIndex]
            Log.d(TAG, "Trying PIN ${currentPinIndex + 1}/${pins.size}: $pin")

            if (LegacyPairingHelper.setPin(device, pin)) {
                Log.d(TAG, "PIN set successfully, waiting for result...")
            } else {
                Log.e(TAG, "Failed to set PIN")
                currentPinIndex++
                if (currentPinIndex < pins.size) {
                    handlePinPairing(device) // Try next PIN
                }
            }
        } else {
            Log.e(TAG, "All PINs tried, none worked")
            currentPinIndex = 0
        }
    }

    /**
     * Try the next PIN code after a failure.
     */
    private fun tryNextPin(device: BluetoothDevice) {
        currentPinIndex++
        val pins = LegacyPairingHelper.getCommonPins()

        if (currentPinIndex < pins.size) {
            Log.d(TAG, "Previous PIN failed, trying next one...")

            // Wait a bit before retrying
            Thread.sleep(1000)

            // Attempt pairing again
            LegacyPairingHelper.attemptLegacyPairing(device)
        } else {
            Log.e(TAG, "All ${pins.size} PINs failed for ${device.name}")
            currentPinIndex = 0
            targetDevice = null
        }
    }

    private fun bondStateToString(state: Int): String {
        return when (state) {
            BluetoothDevice.BOND_NONE -> "BOND_NONE"
            BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
            BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
            else -> "UNKNOWN($state)"
        }
    }
}
