package com.seteditplus

import com.seteditplus.data.ModifiedSetting
import com.seteditplus.data.SettingsRepository
import com.seteditplus.data.SettingsTable
import com.seteditplus.domain.SettingAnalyzer
import com.seteditplus.domain.SettingRiskLevel
import com.seteditplus.domain.SettingValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetEditPlusUnitTest {

    @Test
    fun parseSettingsList_trimsTrailingNewlineAndPreservesMultilineValuesWithEquals() {
        val rawOutput = """
            adb_enabled=1
            complex_uri=content://com.example/provider?param1=foo
            &param2=bar
            animator_duration_scale=0.5
            
        """.trimIndent() + "\n"

        val entries = SettingsRepository.parseSettingsList(rawOutput, SettingsTable.GLOBAL)
        assertEquals(3, entries.size)

        val byKey = entries.associateBy { it.key }
        assertEquals("1", byKey["adb_enabled"]?.value)
        assertEquals("0.5", byKey["animator_duration_scale"]?.value)
        assertEquals(
            "content://com.example/provider?param1=foo\n&param2=bar",
            byKey["complex_uri"]?.value
        )
        assertEquals(SettingsTable.GLOBAL, byKey["adb_enabled"]?.table)
    }

    @Test
    fun escapeShellArg_properlyEscapesSingleQuotes() {
        val raw = "value'with'quotes"
        assertEquals("'value'\\''with'\\''quotes'", SettingsRepository.escapeShellArg(raw))
    }

    @Test
    fun settingAnalyzer_detectsTypesPresetsAndBlocksUnsafeDensity() {
        val animInspection = SettingAnalyzer.inspect(
            SettingsTable.GLOBAL,
            "animator_duration_scale",
            "1.0"
        )
        assertEquals(SettingValueType.FLOAT, animInspection.valueType)
        assertEquals(SettingRiskLevel.CAUTION, animInspection.riskLevel)
        assertTrue(animInspection.suggestedPresets.isNotEmpty())

        val invalidDensity = SettingAnalyzer.validate(
            SettingsTable.SECURE,
            "display_density_forced",
            "50"
        )
        assertFalse(invalidDensity.isValid)

        val validDensity = SettingAnalyzer.validate(
            SettingsTable.SECURE,
            "display_density_forced",
            "420"
        )
        assertTrue(validDensity.isValid)
    }

    @Test
    fun buildShellScript_generatesValidPosixScript() {
        val mods = listOf(
            ModifiedSetting("GLOBAL", "animator_duration_scale", "0.5", "1.0", false),
            ModifiedSetting("SYSTEM", "obsolete_flag", "", "1", true)
        )
        val script = SettingsRepository.buildShellScript(mods)
        assertTrue(script.contains("settings put global 'animator_duration_scale' '0.5'"))
        assertTrue(script.contains("settings delete system 'obsolete_flag'"))
    }
}
