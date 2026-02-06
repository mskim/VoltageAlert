package com.voltagealert.bluetooth

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Helper to show pairing instructions when automatic PIN entry fails.
 *
 * Since BLUETOOTH_PRIVILEGED permission is not available to user apps,
 * we cannot automatically enter PIN codes. This helper shows the user
 * which PIN codes to try manually.
 */
object PairingInstructionsHelper {
    private const val TAG = "PairingInstructions"

    /**
     * Show dialog with PIN code instructions for ESP32-based devices.
     */
    fun showPinInstructions(context: Context, deviceName: String? = "ST9401-UP") {
        val pins = LegacyPairingHelper.getCommonPins()
            .filterNot { it.isEmpty() }
            .joinToString(", ")

        val message = """
            Android will ask for a pairing PIN code.

            For ESP32-based devices like $deviceName, try these PINs in order:

            📱 Common PINs:
            • 1234 (most common)
            • 9527 (ESP32 default)
            • 0000 (generic default)
            • 1111
            • 0001

            ℹ️ If none work, the device may use SSP (Secure Simple Pairing) and require firmware reconfiguration.
        """.trimIndent()

        try {
            AlertDialog.Builder(context)
                .setTitle("Bluetooth Pairing Instructions")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNeutralButton("Copy PINs") { dialog, _ ->
                    // Copy PINs to clipboard
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Bluetooth PINs", "1234, 9527, 0000, 1111, 0001")
                    clipboard.setPrimaryClip(clip)

                    android.widget.Toast.makeText(context, "PINs copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()

            Log.d(TAG, "Showed pairing instructions dialog")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show pairing instructions", e)
            // Fallback to toast
            android.widget.Toast.makeText(
                context,
                "Try PINs: 1234, 9527, 0000, 1111, 0001",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Show a persistent notification with PIN instructions.
     */
    fun showPinNotification(context: Context) {
        // This would show a notification with the PIN codes
        // For now, just log it
        Log.d(TAG, "PIN codes to try: 1234, 9527, 0000, 1111, 0001")
    }
}
