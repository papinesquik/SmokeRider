package com.smokerider.app.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun home_to_attesaOrdine() {
        lateinit var navController: TestNavHostController

        rule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            TestAppNavHost(navController)
        }

        rule.onNodeWithTag("EffettuaOrdineBtn").performClick()

        val route = navController.currentDestination?.route.orEmpty()
        assertTrue(route.startsWith("attesaOrdine"))

        rule.onNodeWithTag("AttesaOrdineScreen").assertExists()
    }
}
