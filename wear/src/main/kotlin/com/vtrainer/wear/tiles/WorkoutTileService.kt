package com.vtrainer.wear.tiles

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.TimelineBuilders
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vtrainer.wear.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.tasks.await

/**
 * Wear OS Tile that displays the next scheduled workout or last completed workout.
 *
 * Features:
 * - Shows workout name and exercise count (Req 11.1, 11.2)
 * - Tap to launch workout directly (Req 11.3)
 * - Updates automatically after workout completion (Req 11.5)
 *
 * Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5
 */
class WorkoutTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // -------------------------------------------------------------------------
    // Tile data loading (Req 11.2)
    // -------------------------------------------------------------------------

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = serviceScope.future {
        val tileData = loadTileData()
        buildTile(tileData)
    }

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = serviceScope.future {
        ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
    }

    // -------------------------------------------------------------------------
    // Data loading from Firestore
    // -------------------------------------------------------------------------

    private suspend fun loadTileData(): TileData {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return TileData.notLoggedIn()

        return try {
            val userDoc = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .await()

            val nextWorkoutName = userDoc.getString("nextWorkoutName")
            val lastWorkoutName = userDoc.getString("lastWorkoutName")
            val lastWorkoutDate = userDoc.getString("lastWorkoutDate")
            val exerciseCount = userDoc.getLong("nextWorkoutExerciseCount")?.toInt()

            when {
                nextWorkoutName != null -> TileData.nextWorkout(
                    name = nextWorkoutName,
                    exerciseCount = exerciseCount ?: 0
                )
                lastWorkoutName != null -> TileData.lastWorkout(
                    name = lastWorkoutName,
                    date = lastWorkoutDate ?: ""
                )
                else -> TileData.noWorkout()
            }
        } catch (e: Exception) {
            TileData.error()
        }
    }

    // -------------------------------------------------------------------------
    // Tile layout builder (Req 11.1, 11.3)
    // -------------------------------------------------------------------------

    private fun buildTile(data: TileData): TileBuilders.Tile {
        val layout = buildLayout(data)
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(layout)
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
            .build()
    }

    private fun buildLayout(data: TileData): LayoutElementBuilders.Layout {
        // Tap action launches MainActivity (Req 11.3)
        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            )
            .build()

        val clickable = Clickable.Builder()
            .setOnClick(launchAction)
            .build()

        val content = Column.Builder()
            .setWidth(dp(192f))
            .setHeight(dp(192f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder()
                    .setText(data.title)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(16f))
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .build()
                    )
                    .setMaxLines(1)
                    .build()
            )
            .addContent(Spacer.Builder().setHeight(dp(4f)).build())
            .addContent(
                Text.Builder()
                    .setText(data.subtitle)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(12f))
                            .setColor(argb(0xFFCCCCCC.toInt()))
                            .build()
                    )
                    .setMaxLines(2)
                    .build()
            )
            .apply {
                if (data.showStartButton) {
                    addContent(Spacer.Builder().setHeight(dp(8f)).build())
                    addContent(
                        Text.Builder()
                            .setText("▶ Start")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(14f))
                                    .setColor(argb(0xFF4CAF50.toInt()))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .build()

        val root = Box.Builder()
            .setWidth(dp(192f))
            .setHeight(dp(192f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .addContent(content)
            .build()

        return LayoutElementBuilders.Layout.Builder()
            .setRoot(root)
            .build()
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

    companion object {
        private const val RESOURCES_VERSION = "1"
        /** Refresh tile every 30 minutes (Req 11.4) */
        private const val FRESHNESS_INTERVAL_MS = 30 * 60 * 1_000L

        /**
         * Requests a tile update — call this after workout completion (Req 11.5).
         */
        fun requestUpdate(context: Context) {
            TileService.getUpdater(context)
                .requestUpdate(WorkoutTileService::class.java)
        }
    }
}

// ---------------------------------------------------------------------------
// Tile data model
// ---------------------------------------------------------------------------

data class TileData(
    val title: String,
    val subtitle: String,
    val showStartButton: Boolean
) {
    companion object {
        fun nextWorkout(name: String, exerciseCount: Int) = TileData(
            title = name,
            subtitle = "$exerciseCount exercises",
            showStartButton = true
        )

        fun lastWorkout(name: String, date: String) = TileData(
            title = name,
            subtitle = "Last: $date",
            showStartButton = false
        )

        fun noWorkout() = TileData(
            title = "V-Trainer",
            subtitle = "No workout planned",
            showStartButton = false
        )

        fun notLoggedIn() = TileData(
            title = "V-Trainer",
            subtitle = "Sign in to start",
            showStartButton = false
        )

        fun error() = TileData(
            title = "V-Trainer",
            subtitle = "Tap to open",
            showStartButton = false
        )
    }
}
