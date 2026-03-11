package com.vtrainer.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.vtrainer.app.data.local.VTrainerDatabase

class VTrainerApplication : Application() {
    
    /**
     * Lazy initialization of Room database.
     * Database instance is created on first access.
     */
    val database: VTrainerDatabase by lazy {
        VTrainerDatabase.getInstance(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Room Database is initialized lazily via the database property
        // This ensures the database is only created when first accessed
        
        // TODO: Initialize WorkManager for background sync
        // TODO: Setup notification channels
    }
}
