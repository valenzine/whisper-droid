package com.valenzine.whisperdroid.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun purple40RequiresLightStatusBarIcons() {
        assertFalse(shouldUseDarkStatusBarIcons(Color(0xFF6650A4)))
    }

    @Test
    fun purple80RequiresDarkStatusBarIcons() {
        assertTrue(shouldUseDarkStatusBarIcons(Color(0xFFD0BCFF)))
    }
}
