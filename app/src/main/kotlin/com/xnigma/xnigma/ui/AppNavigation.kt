package com.xnigma.xnigma.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xnigma.xnigma.database.AppDatabase

// Define our routes as simple strings
object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // STATE RETENTION: Read the flag to check if keys exist and permissions are granted
    val sharedPrefs = context.getSharedPreferences("xnigma_prefs", Context.MODE_PRIVATE)
    val isOnboardingCompleted = sharedPrefs.getBoolean("is_onboarding_completed", false)

    // Dynamically set the start destination based on the flag
    val startDestination = if (isOnboardingCompleted) Routes.DASHBOARD else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {
        
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
            // 1. Get the local Room database instance
            val database = AppDatabase.getDatabase(context)
            
            // 2. Pass the DAO into our custom ViewModel Factory
            val factory = DashboardViewModelFactory(database.contactDao())
            
            // 3. Create the ViewModel and pass it to the actual DashboardScreen
            val viewModel: DashboardViewModel = viewModel(factory = factory)
            
            DashboardScreen(viewModel = viewModel)
        }
    }
}