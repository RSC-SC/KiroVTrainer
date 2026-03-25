package com.vtrainer.app.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vtrainer.app.presentation.auth.AuthState
import com.vtrainer.app.presentation.auth.AuthViewModel
import com.vtrainer.app.presentation.auth.LoginScreen
import com.vtrainer.app.presentation.auth.ProfileCreationScreen
import com.vtrainer.app.presentation.dashboard.DashboardScreen
import com.vtrainer.app.presentation.dashboard.DashboardViewModel
import com.vtrainer.app.presentation.exercise.ExerciseLibraryScreen
import com.vtrainer.app.presentation.exercise.ExerciseLibraryViewModel
import com.vtrainer.app.presentation.history.TrainingHistoryScreen
import com.vtrainer.app.presentation.history.TrainingHistoryViewModel
import com.vtrainer.app.presentation.plan.WorkoutPlanEditorScreen
import com.vtrainer.app.presentation.plan.WorkoutPlanViewModel
import com.vtrainer.app.presentation.settings.SettingsScreen
import com.vtrainer.app.presentation.settings.SettingsViewModel
import com.vtrainer.app.presentation.viewmodel.DashboardViewModelFactory
import com.vtrainer.app.presentation.viewmodel.WorkoutExecutionViewModelFactory
import com.vtrainer.app.presentation.workout.WorkoutExecutionScreen
import com.vtrainer.app.presentation.workout.WorkoutExecutionViewModel

/** Route constants used throughout the navigation graph. */
object Routes {
    const val LOGIN = "login"
    const val PROFILE_CREATION = "profile_creation"
    const val DASHBOARD = "dashboard"
    const val WORKOUT_PLANS = "workout_plans"
    const val HISTORY = "history"
    const val EXERCISES = "exercises"
    const val WORKOUT_EXECUTION = "workout_execution"
    const val SETTINGS = "settings"
}

/** Bottom navigation items. */
sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : BottomNavItem(Routes.DASHBOARD, "Início", Icons.Default.Dashboard)
    object Plans : BottomNavItem(Routes.WORKOUT_PLANS, "Treinos", Icons.Default.List)
    object History : BottomNavItem(Routes.HISTORY, "Histórico", Icons.Default.History)
    object Exercises : BottomNavItem(Routes.EXERCISES, "Biblioteca", Icons.Default.LibraryBooks)
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = viewModel(),
    onSignInWithGoogle: () -> Unit = {},
    onSignInWithSamsung: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    val authState by authViewModel.authState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine if the bottom bar should be shown
    val showBottomBar = currentDestination?.route in listOf(
        Routes.DASHBOARD,
        Routes.WORKOUT_PLANS,
        Routes.HISTORY,
        Routes.EXERCISES
    )

    LaunchedEffect(authState) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        when (authState) {
            is AuthState.Authenticated -> {
                if (currentRoute == Routes.LOGIN) {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            is AuthState.Unauthenticated -> {
                if (currentRoute != null && currentRoute != Routes.LOGIN) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            else -> Unit
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                VTrainerBottomBar(navController)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    authViewModel = authViewModel,
                    onSignInWithGoogle = onSignInWithGoogle,
                    onSignInWithSamsung = onSignInWithSamsung,
                    onNavigateToDashboard = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.PROFILE_CREATION) {
                ProfileCreationScreen(
                    onNavigateToDashboard = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.PROFILE_CREATION) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.DASHBOARD) {
                val context = LocalContext.current
                val dashboardViewModel: DashboardViewModel =
                    viewModel(factory = DashboardViewModelFactory(context))
                val workoutExecutionViewModel: WorkoutExecutionViewModel =
                    viewModel(factory = WorkoutExecutionViewModelFactory(context))
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onStartWorkout = { plan ->
                        workoutExecutionViewModel.startWorkout(plan, dayIndex = 0)
                        navController.navigate(Routes.WORKOUT_EXECUTION)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS)
                    }
                )
            }

            composable(Routes.WORKOUT_PLANS) {
                val planViewModel: WorkoutPlanViewModel = viewModel()
                WorkoutPlanEditorScreen(
                    viewModel = planViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.HISTORY) {
                val historyViewModel: TrainingHistoryViewModel = viewModel()
                TrainingHistoryScreen(
                    viewModel = historyViewModel
                )
            }

            composable(Routes.EXERCISES) {
                val exerciseViewModel: ExerciseLibraryViewModel = viewModel()
                ExerciseLibraryScreen(
                    viewModel = exerciseViewModel
                )
            }

            composable(Routes.WORKOUT_EXECUTION) {
                val context = LocalContext.current
                val workoutExecutionViewModel: WorkoutExecutionViewModel =
                    viewModel(factory = WorkoutExecutionViewModelFactory(context))
                val state by workoutExecutionViewModel.state.collectAsState()
                val plan = state.currentPlan
                if (plan != null) {
                    WorkoutExecutionScreen(
                        viewModel = workoutExecutionViewModel,
                        workoutPlan = plan,
                        dayIndex = state.currentDayIndex,
                        onWorkoutFinished = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.DASHBOARD) { inclusive = true }
                            }
                        }
                    )
                }
            }

            composable(Routes.SETTINGS) {
                val settingsViewModel: SettingsViewModel = viewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun VTrainerBottomBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Plans,
        BottomNavItem.History,
        BottomNavItem.Exercises
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
