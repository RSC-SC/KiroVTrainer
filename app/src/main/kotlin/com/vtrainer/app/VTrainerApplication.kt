package com.vtrainer.app

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.app.data.local.VTrainerDatabase
import com.vtrainer.app.data.repositories.TrainingLogRepository
import com.vtrainer.app.data.repositories.TrainingLogRepositoryImpl
import com.vtrainer.app.services.BackgroundSyncScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VTrainerApplication : Application() {
    
    /**
     * Lazy initialization of Room database.
     */
    val database: VTrainerDatabase by lazy {
        VTrainerDatabase.getInstance(this)
    }

    /**
     * Provide TrainingLogRepository instance.
     */
    val trainingLogRepository: TrainingLogRepository by lazy {
        TrainingLogRepositoryImpl(
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance(),
            trainingLogDao = database.trainingLogDao()
        )
    }
    
    override fun onCreate() {
        super.onCreate()
        
        FirebaseApp.initializeApp(this)
        
        // Ensure Google Play Services security provider is up to date
        //installSecurityProvider()
        
        setupCoil()
        
        // Schedule background sync
        BackgroundSyncScheduler.schedule(this)
    }

    private fun installSecurityProvider() {
        ProviderInstaller.installIfNeededAsync(this, object : ProviderInstaller.ProviderInstallListener {
            override fun onProviderInstalled() {
                Log.d("VTrainerApp", "Security provider installed successfully")
            }

            override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: android.content.Intent?) {
                Log.e("VTrainerApp", "Security provider installation failed: $errorCode")
            }
        })
    }

    private fun setupCoil() {
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("exercise_image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            }
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
