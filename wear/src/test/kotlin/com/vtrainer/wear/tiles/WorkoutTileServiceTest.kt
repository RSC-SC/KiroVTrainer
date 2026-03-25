package com.vtrainer.wear.tiles

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Unit tests for [WorkoutTileService] tile data loading and layout logic.
 *
 * Tests focus on [TileData] factory methods and content correctness,
 * avoiding Android framework dependencies.
 *
 * Validates: Requirements 11.2, 11.5
 */
class WorkoutTileServiceTest : StringSpec({

    // -------------------------------------------------------------------------
    // TileData factory — next workout (Req 11.1, 11.2)
    // -------------------------------------------------------------------------

    "nextWorkout tile shows workout name as title" {
        val data = TileData.nextWorkout(name = "Push Day", exerciseCount = 5)
        data.title shouldBe "Push Day"
    }

    "nextWorkout tile shows exercise count in subtitle" {
        val data = TileData.nextWorkout(name = "Push Day", exerciseCount = 5)
        data.subtitle shouldContain "5"
    }

    "nextWorkout tile shows start button" {
        val data = TileData.nextWorkout(name = "Push Day", exerciseCount = 5)
        data.showStartButton.shouldBeTrue()
    }

    // -------------------------------------------------------------------------
    // TileData factory — last workout (Req 11.1, 11.2)
    // -------------------------------------------------------------------------

    "lastWorkout tile shows workout name as title" {
        val data = TileData.lastWorkout(name = "Leg Day", date = "Mon 17 Mar")
        data.title shouldBe "Leg Day"
    }

    "lastWorkout tile shows date in subtitle" {
        val data = TileData.lastWorkout(name = "Leg Day", date = "Mon 17 Mar")
        data.subtitle shouldContain "Mon 17 Mar"
    }

    "lastWorkout tile does not show start button" {
        val data = TileData.lastWorkout(name = "Leg Day", date = "Mon 17 Mar")
        data.showStartButton.shouldBeFalse()
    }

    // -------------------------------------------------------------------------
    // TileData factory — edge cases
    // -------------------------------------------------------------------------

    "noWorkout tile has non-empty title" {
        val data = TileData.noWorkout()
        data.title.isNotBlank().shouldBeTrue()
    }

    "notLoggedIn tile has non-empty subtitle" {
        val data = TileData.notLoggedIn()
        data.subtitle.isNotBlank().shouldBeTrue()
    }

    "error tile does not show start button" {
        val data = TileData.error()
        data.showStartButton.shouldBeFalse()
    }

    // -------------------------------------------------------------------------
    // Tile update trigger (Req 11.5)
    // -------------------------------------------------------------------------

    "TileData nextWorkout with zero exercises shows subtitle" {
        val data = TileData.nextWorkout(name = "Rest Day", exerciseCount = 0)
        data.subtitle shouldContain "0"
    }

    "TileData nextWorkout with many exercises shows correct count" {
        val data = TileData.nextWorkout(name = "Full Body", exerciseCount = 12)
        data.subtitle shouldContain "12"
    }
})
