package com.voltagealert.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.voltagealert.R

/**
 * Voltage detection levels with associated metadata.
 * Maps Bluetooth byte codes to voltage types and UI resources.
 */
enum class VoltageLevel(
    val byteCode: Byte,
    @StringRes val displayNameRes: Int,
    @DrawableRes val settingImageRes: Int,
    @DrawableRes val detectionImageRes: Int,
    @DrawableRes val detectionInvertedImageRes: Int,
    val isDangerous: Boolean
) {
    VOLTAGE_220V(
        byteCode = 0x01,
        displayNameRes = R.string.voltage_220v,
        settingImageRes = R.drawable.voltage_220v_setting,
        detectionImageRes = R.drawable.voltage_220v_detection,
        detectionInvertedImageRes = R.drawable.voltage_220v_detection_inverted,
        isDangerous = false  // Normal voltage - safe
    ),
    VOLTAGE_380V(
        byteCode = 0x02,
        displayNameRes = R.string.voltage_380v,
        settingImageRes = R.drawable.voltage_380v_setting,
        detectionImageRes = R.drawable.voltage_380v_detection,
        detectionInvertedImageRes = R.drawable.voltage_380v_detection_inverted,
        isDangerous = false  // Normal voltage - safe
    ),
    VOLTAGE_229KV(
        byteCode = 0x03,
        displayNameRes = R.string.voltage_229kv,
        settingImageRes = R.drawable.voltage_229kv_setting,
        detectionImageRes = R.drawable.voltage_229kv_detection,
        detectionInvertedImageRes = R.drawable.voltage_229kv_detection_inverted,
        isDangerous = true
    ),
    VOLTAGE_154KV(
        byteCode = 0x04,
        displayNameRes = R.string.voltage_154kv,
        settingImageRes = R.drawable.voltage_154kv_setting,
        detectionImageRes = R.drawable.voltage_154kv_detection,
        detectionInvertedImageRes = R.drawable.voltage_154kv_detection_inverted,
        isDangerous = true
    ),
    VOLTAGE_345KV(
        byteCode = 0x05,
        displayNameRes = R.string.voltage_345kv,
        settingImageRes = R.drawable.voltage_345kv_setting,
        detectionImageRes = R.drawable.voltage_345kv_detection,
        detectionInvertedImageRes = R.drawable.voltage_345kv_detection_inverted,
        isDangerous = true
    ),
    VOLTAGE_500KV(
        byteCode = 0x06,
        displayNameRes = R.string.voltage_500kv,
        settingImageRes = R.drawable.voltage_500kv_setting,
        detectionImageRes = R.drawable.voltage_500kv_detection,
        detectionInvertedImageRes = R.drawable.voltage_500kv_detection_inverted,
        isDangerous = true
    ),
    VOLTAGE_765KV(
        byteCode = 0x07,
        displayNameRes = R.string.voltage_765kv,
        settingImageRes = R.drawable.voltage_765kv_setting,
        detectionImageRes = R.drawable.voltage_765kv_detection,
        detectionInvertedImageRes = R.drawable.voltage_765kv_detection_inverted,
        isDangerous = true
    ),
    DIAGNOSTIC_OK(
        byteCode = 0xF0.toByte(),
        displayNameRes = R.string.diagnostic_ok,
        settingImageRes = R.drawable.diagnostic_ok,
        detectionImageRes = R.drawable.diagnostic_ok,
        detectionInvertedImageRes = R.drawable.diagnostic_ok,
        isDangerous = false
    ),
    DIAGNOSTIC_NG(
        byteCode = 0xF1.toByte(),
        displayNameRes = R.string.diagnostic_ng,
        settingImageRes = R.drawable.diagnostic_ng,
        detectionImageRes = R.drawable.diagnostic_ng,
        detectionInvertedImageRes = R.drawable.diagnostic_ng,
        isDangerous = false
    );

    companion object {
        /**
         * Find VoltageLevel by its byte code from Bluetooth packet.
         */
        fun fromByteCode(code: Byte): VoltageLevel? {
            return entries.find { it.byteCode == code }
        }

        /**
         * All dangerous voltage levels requiring alerts.
         */
        fun dangerousLevels(): List<VoltageLevel> {
            return entries.filter { it.isDangerous }
        }
    }
}
