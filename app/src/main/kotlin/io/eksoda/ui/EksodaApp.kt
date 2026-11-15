package io.eksoda.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.eksoda.feature.onboarding.OnboardingScreen
/**
 * The app's navigation graph. L0 has only onboarding. The other screens join the graph as their
 * data becomes local: capture and receipts in L4, products in L5, insights and home in L6.
 */
@Composable
fun EksodaApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = OnboardingRoute) {
        composable<OnboardingRoute> {
            OnboardingScreen()
        }
    }
}
