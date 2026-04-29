package com.xnigma.xnigma.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Define our routes as simple strings
object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // We set startDestination to ONBOARDING for now. 
    // Later, we will add logic to check DataStore: if keys exist, start at DASHBOARD.
    NavHost(navController = navController, startDestination = Routes.ONBOARDING) {
        
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    // Navigate to dashboard and remove onboarding from the backstack
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen() // We will build this in the next phase
        }
    }
}

// A temporary placeholder for the Dashboard
@Composable
fun DashboardScreen() {
    androidx.compose.material3.Text("Welcome to the Dashboard")
}
