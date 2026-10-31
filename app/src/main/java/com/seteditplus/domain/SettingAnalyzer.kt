package com.seteditplus.domain

import com.seteditplus.data.SettingsRepository
import com.seteditplus.data.SettingsTable

enum class SettingValueType(val label: String) {
    BOOLEAN_INT("Boolean (0 / 1)"),
    BOOLEAN_TEXT("Boolean (true / false)"),
    INTEGER("Integer"),
    FLOAT("Decimal / Scale"),
    PACKAGE_COMPONENT("Component / Package"),
    URI("URI"),
    TEXT("String")
}

enum class SettingRiskLevel {
    SAFE,
    CAUTION,
    DANGEROUS
}

data class SettingInspection(
    val valueType: SettingValueType,
    val riskLevel: SettingRiskLevel,
    val riskWarning: String? = null,
    val suggestedPresets: List<Pair<String, String>> = emptyList(),
    val categoryHint: String? = null
)

data class SettingValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val warningMessage: String? = null
)

/**
 * First-principles domain analyzer for Android Settings keys and values.
 * Detects data types, provides quick-action presets, and guards against brick/bootloop-inducing values.
 */
object SettingAnalyzer {

    private val DANGEROUS_KEY_PATTERNS = listOf(
        Regex("(^|_)(density_forced|display_density_forced)($|_)"),
        Regex("(^|_)(smallest_width|wm_size|overscan)($|_)"),
        Regex("^(device_provisioned|user_setup_complete)$"),
        Regex("^(adb_enabled|development_settings_enabled|usb_mass_storage_enabled)$"),
        Regex("^(hidden_api_policy|hidden_api_policy_p_apps|hidden_api_policy_pre_p_apps)$"),
        Regex("^(enabled_accessibility_services|enabled_input_methods|default_input_method)$"),
        Regex("^(lockscreen|lock_screen|biometric).*")
    )

    private val CAUTION_KEY_PATTERNS = listOf(
        Regex(".*(animation_scale|transition_animation_scale|animator_duration_scale)$"),
        Regex(".*(refresh_rate|peak_refresh_rate|min_refresh_rate).*"),
        Regex(".*(wifi_on|bluetooth_on|airplane_mode|mobile_data|tether).*"),
        Regex(".*(font_scale|screen_off_timeout|stay_on_while_plugged_in).*")
    )

    private val KNOWN_BOOLEAN_INT_KEYS = setOf(
        "adb_enabled",
        "development_settings_enabled",
        "accelerometer_rotation",
        "haptic_feedback_enabled",
        "sound_effects_enabled",
        "dtmf_tone",
        "lockscreen_sounds_enabled",
        "charging_sounds_enabled",
        "vibrate_when_ringing",
        "notification_light_pulse",
        "auto_time",
        "auto_time_zone",
        "airplane_mode_on",
        "bluetooth_on",
        "wifi_on",
        "mobile_data",
        "roaming_settings",
        "usb_mass_storage_enabled",
        "install_non_market_apps",
        "device_provisioned",
        "user_setup_complete"
    )

    private val SCALE_PRESETS = listOf(
        "0.0" to "Off (0x)",
        "0.5" to "Fast (0.5x)",
        "1.0" to "Stock (1x)",
        "1.5" to "Slow (1.5x)",
        "2.0" to "2x"
    )

    private val REFRESH_RATE_PRESETS = listOf(
        "60.0" to "60 Hz",
        "90.0" to "90 Hz",
        "120.0" to "120 Hz",
        "144.0" to "144 Hz"
    )

    private val FONT_SCALE_PRESETS = listOf(
        "0.85" to "Small (0.85)",
        "1.0" to "Default (1.0)",
        "1.15" to "Large (1.15)",
        "1.3" to "XL (1.30)"
    )

    private val TIMEOUT_PRESETS = listOf(
        "15000" to "15s",
        "30000" to "30s",
        "60000" to "1m",
        "120000" to "2m",
        "300000" to "5m",
        "600000" to "10m"
    )

    fun inspect(table: SettingsTable, key: String, currentValue: String): SettingInspection {
        val lowerKey = key.lowercase()
        val trimmedValue = currentValue.trim()

        val valueType = detectValueType(lowerKey, trimmedValue)
        val (riskLevel, riskWarning) = evaluateRisk(table, lowerKey)
        val presets = suggestPresets(lowerKey, valueType)
        val categoryHint = inferCategory(lowerKey)

        return SettingInspection(
            valueType = valueType,
            riskLevel = riskLevel,
            riskWarning = riskWarning,
            suggestedPresets = presets,
            categoryHint = categoryHint
        )
    }

    fun validate(table: SettingsTable, key: String, candidateValue: String, currentValue: String? = null): SettingValidationResult {
        val trimmedKey = key.trim()
        if (!SettingsRepository.isValidKey(trimmedKey)) {
            return SettingValidationResult(
                isValid = false,
                errorMessage = "Key may only contain letters, digits, underscores (_), dots (.), colons (:), or hyphens (-)."
            )
        }

        val lowerKey = trimmedKey.lowercase()
        val trimmedVal = candidateValue.trim()

        // Hard safety guards for known brick-inducing parameters
        if (lowerKey.contains("density_forced")) {
            val dpi = trimmedVal.toIntOrNull()
            if (trimmedVal.isNotEmpty() && (dpi == null || dpi !in 120..960)) {
                return SettingValidationResult(
                    isValid = false,
                    errorMessage = "Forced display density must be an integer between 120 and 960 DPI to prevent SystemUI crash."
                )
            }
        }

        if (lowerKey == "font_scale") {
            val scale = trimmedVal.toFloatOrNull()
            if (scale == null || scale !in 0.5f..2.5f) {
                return SettingValidationResult(
                    isValid = false,
                    errorMessage = "font_scale must be a decimal value between 0.5 and 2.5."
                )
            }
        }

        if (lowerKey.endsWith("_animation_scale") || lowerKey == "animator_duration_scale") {
            val scale = trimmedVal.toFloatOrNull()
            if (scale == null || scale !in 0.0f..10.0f) {
                return SettingValidationResult(
                    isValid = false,
                    errorMessage = "Animation scale must be a decimal number between 0.0 and 10.0."
                )
            }
        }

        val inspection = inspect(table, trimmedKey, currentValue ?: trimmedVal)
        val typeWarning = when (inspection.valueType) {
            SettingValueType.BOOLEAN_INT -> {
                if (trimmedVal != "0" && trimmedVal != "1") {
                    "This setting typically expects '0' (disabled) or '1' (enabled)."
                } else null
            }
            SettingValueType.BOOLEAN_TEXT -> {
                if (!trimmedVal.equals("true", ignoreCase = true) && !trimmedVal.equals("false", ignoreCase = true)) {
                    "This setting typically expects 'true' or 'false'."
                } else null
            }
            SettingValueType.INTEGER -> {
                if (trimmedVal.toLongOrNull() == null) {
                    "Current value is an integer, but the new value is non-numeric."
                } else null
            }
            SettingValueType.FLOAT -> {
                if (trimmedVal.toDoubleOrNull() == null) {
                    "Current value is a decimal number, but the new value is non-numeric."
                } else null
            }
            else -> null
        }

        val combinedWarning = listOfNotNull(inspection.riskWarning, typeWarning)
            .joinToString(" ")
            .ifBlank { null }

        return SettingValidationResult(
            isValid = true,
            warningMessage = combinedWarning
        )
    }

    private fun detectValueType(lowerKey: String, value: String): SettingValueType {
        if (value == "0" || value == "1") {
            if (lowerKey in KNOWN_BOOLEAN_INT_KEYS ||
                lowerKey.endsWith("_enabled") ||
                lowerKey.endsWith("_on") ||
                lowerKey.startsWith("show_") ||
                lowerKey.startsWith("enable_") ||
                lowerKey.startsWith("use_") ||
                lowerKey.startsWith("is_") ||
                lowerKey.contains("_vibrate") ||
                lowerKey.contains("_sound")
            ) {
                return SettingValueType.BOOLEAN_INT
            }
        }
        if (lowerKey in KNOWN_BOOLEAN_INT_KEYS) {
            return SettingValueType.BOOLEAN_INT
        }
        if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
            return SettingValueType.BOOLEAN_TEXT
        }
        if (lowerKey.endsWith("_scale") || (value.contains('.') && value.toDoubleOrNull() != null)) {
            return SettingValueType.FLOAT
        }
        if (value.toLongOrNull() != null) {
            return if (value == "0" || value == "1") SettingValueType.BOOLEAN_INT else SettingValueType.INTEGER
        }
        if (value.startsWith("content://") || value.startsWith("file://") || value.startsWith("https://")) {
            return SettingValueType.URI
        }
        if (value.contains('/') && value.contains('.') && !value.contains(' ')) {
            return SettingValueType.PACKAGE_COMPONENT
        }
        return SettingValueType.TEXT
    }

    private fun evaluateRisk(table: SettingsTable, lowerKey: String): Pair<SettingRiskLevel, String?> {
        if (DANGEROUS_KEY_PATTERNS.any { it.matches(lowerKey) }) {
            return SettingRiskLevel.DANGEROUS to
                "Caution: Modifying '$lowerKey' in ${table.displayName} with an incompatible value can cause UI rendering issues, input loss, or prevent ADB recovery."
        }
        if (CAUTION_KEY_PATTERNS.any { it.matches(lowerKey) }) {
            return SettingRiskLevel.CAUTION to
                "Affects system behavior or display timing. Verify the value format before saving."
        }
        if (table == SettingsTable.SECURE) {
            return SettingRiskLevel.CAUTION to
                "Secure table settings control sensitive system & accessibility policies."
        }
        return SettingRiskLevel.SAFE to null
    }

    private fun suggestPresets(lowerKey: String, valueType: SettingValueType): List<Pair<String, String>> {
        return when {
            lowerKey.endsWith("_animation_scale") || lowerKey == "animator_duration_scale" -> SCALE_PRESETS
            lowerKey.contains("refresh_rate") -> REFRESH_RATE_PRESETS
            lowerKey == "font_scale" -> FONT_SCALE_PRESETS
            lowerKey == "screen_off_timeout" -> TIMEOUT_PRESETS
            valueType == SettingValueType.BOOLEAN_INT -> listOf("0" to "0 (Off)", "1" to "1 (On)")
            valueType == SettingValueType.BOOLEAN_TEXT -> listOf("false" to "false", "true" to "true")
            else -> emptyList()
        }
    }

    private fun inferCategory(lowerKey: String): String? {
        return when {
            lowerKey.contains("display") || lowerKey.contains("refresh_rate") ||
                lowerKey.contains("brightness") || lowerKey.contains("night") ||
                lowerKey.contains("font_scale") || lowerKey.contains("density") -> "Display"
            lowerKey.contains("animation") || lowerKey.contains("animator") ||
                lowerKey.contains("transition") -> "Animations"
            lowerKey.contains("wifi") || lowerKey.contains("bluetooth") ||
                lowerKey.contains("network") || lowerKey.contains("tether") ||
                lowerKey.contains("mobile_data") || lowerKey.contains("airplane") -> "Connectivity"
            lowerKey.contains("sound") || lowerKey.contains("volume") ||
                lowerKey.contains("vibrate") || lowerKey.contains("haptic") ||
                lowerKey.contains("ring") || lowerKey.contains("audio") -> "Audio & Haptics"
            lowerKey.contains("adb") || lowerKey.contains("development") ||
                lowerKey.contains("debug") || lowerKey.contains("verifier") -> "Developer"
            lowerKey.contains("battery") || lowerKey.contains("power") ||
                lowerKey.contains("low_power") || lowerKey.contains("charging") -> "Power & Battery"
            lowerKey.contains("input") || lowerKey.contains("ime") ||
                lowerKey.contains("keyboard") || lowerKey.contains("pointer") -> "Input"
            else -> null
        }
    }
}
