package com.possatstack.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.possatstack.app.ui.home.HomeScreen
import com.possatstack.app.ui.theme.PosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_showsChargeCard() {
        composeTestRule.setContent {
            PosTheme { HomeScreen() }
        }
        composeTestRule.onNodeWithText("Charge").assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsSettingsCard() {
        composeTestRule.setContent {
            PosTheme { HomeScreen() }
        }
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsChargeSubtitle() {
        composeTestRule.setContent {
            PosTheme { HomeScreen() }
        }
        composeTestRule.onNodeWithText("Create a Bitcoin payment request").assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsSettingsSubtitle() {
        composeTestRule.setContent {
            PosTheme { HomeScreen() }
        }
        composeTestRule.onNodeWithText("Wallet, network and preferences").assertIsDisplayed()
    }
}
