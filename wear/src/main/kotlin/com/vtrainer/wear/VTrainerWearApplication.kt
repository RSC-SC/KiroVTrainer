package com.vtrainer.wear

import android.app.Application
import com.google.firebase.FirebaseApp

class VTrainerWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // TODO: Initialize Room Database
        // TODO: Initialize Health Services Client
        // TODO: Setup notification channels
    }
}
