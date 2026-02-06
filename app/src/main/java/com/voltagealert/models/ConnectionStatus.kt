package com.voltagealert.models

/**
 * Bluetooth connection status states.
 */
enum class ConnectionStatus {
    /** No device connected or connection attempt */
    DISCONNECTED,

    /** Actively scanning for the voltage sensor device */
    SCANNING,

    /** Connection attempt in progress */
    CONNECTING,

    /** Successfully connected and receiving data */
    CONNECTED
}
