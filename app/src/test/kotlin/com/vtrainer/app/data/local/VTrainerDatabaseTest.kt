package com.vtrainer.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vtrainer.app.data.local.dao.ExerciseDao
import com.vtrainer.app.data.local.dao.TrainingLogDao
import com.vtrainer.app.data.local.dao.WorkoutPlanDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

/**
 * Unit tests for VTrainerDatabase.
 * Verifies database creation, DAO access, and configuration.
 * 
 * Requirements: 12.1 - Room database for local caching
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VTrainerDatabaseTest {
    
    private lateinit var database: VTrainerDatabase
    private lateinit var workoutPlanDao: WorkoutPlanDao
    private lateinit var trainingLogDao: TrainingLogDao
    private lateinit var exerciseDao: ExerciseDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Create an in-memory database for testing
        // This database is destroyed when the process is killed
        database = Room.inMemoryDatabaseBuilder(
            context,
            VTrainerDatabase::class.java
        )
            .allowMainThreadQueries() // Allow queries on main thread for testing
            .build()
        
        workoutPlanDao = database.workoutPlanDao()
        trainingLogDao = database.trainingLogDao()
        exerciseDao = database.exerciseDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun `database creation should succeed`() {
        assertNotNull(database, "Database should be created successfully")
    }
    
    @Test
    fun `workoutPlanDao should be accessible`() {
        assertNotNull(workoutPlanDao, "WorkoutPlanDao should be accessible from database")
    }
    
    @Test
    fun `trainingLogDao should be accessible`() {
        assertNotNull(trainingLogDao, "TrainingLogDao should be accessible from database")
    }
    
    @Test
    fun `exerciseDao should be accessible`() {
        assertNotNull(exerciseDao, "ExerciseDao should be accessible from database")
    }
    
    @Test
    fun `database should have correct version`() {
        val version = database.openHelper.readableDatabase.version
        assert(version == 1) { "Database version should be 1, but was $version" }
    }
    
    @Test
    fun `singleton getInstance should return same instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Clear any existing instance
        VTrainerDatabase.clearInstance()
        
        val instance1 = VTrainerDatabase.getInstance(context)
        val instance2 = VTrainerDatabase.getInstance(context)
        
        assert(instance1 === instance2) { "getInstance should return the same singleton instance" }
        
        // Cleanup
        instance1.close()
        VTrainerDatabase.clearInstance()
    }
}
