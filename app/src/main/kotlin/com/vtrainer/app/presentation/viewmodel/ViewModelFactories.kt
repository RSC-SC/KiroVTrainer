package com.vtrainer.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.local.VTrainerDatabase
import com.vtrainer.app.data.repositories.TrainingLogRepositoryImpl
import com.vtrainer.app.data.repositories.WorkoutRepositoryImpl
import com.vtrainer.app.presentation.dashboard.DashboardViewModel
import com.vtrainer.app.presentation.workout.WorkoutExecutionViewModel

/**
 * Factory for [DashboardViewModel].
 * Wires up Room DAOs and Firebase dependencies.
 */
class DashboardViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = VTrainerDatabase.getInstance(context)
        val workoutRepo = WorkoutRepositoryImpl(
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance(),
            workoutPlanDao = db.workoutPlanDao()
        )
        val trainingLogRepo = TrainingLogRepositoryImpl(
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance(),
            trainingLogDao = db.trainingLogDao()
        )
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(workoutRepo, trainingLogRepo) as T
    }
}

/**
 * Factory for [WorkoutExecutionViewModel].
 * Wires up Room DAO and Firebase dependencies.
 */
class WorkoutExecutionViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = VTrainerDatabase.getInstance(context)
        val trainingLogRepo = TrainingLogRepositoryImpl(
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance(),
            trainingLogDao = db.trainingLogDao()
        )
        @Suppress("UNCHECKED_CAST")
        return WorkoutExecutionViewModel(trainingLogRepo) as T
    }
}
