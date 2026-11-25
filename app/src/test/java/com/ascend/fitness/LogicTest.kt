package com.ascend.fitness

import com.ascend.fitness.ai.Coach
import com.ascend.fitness.data.ExerciseEntity
import com.ascend.fitness.data.Metric
import com.ascend.fitness.data.Catalog
import com.ascend.fitness.data.SetWithTime
import com.ascend.fitness.data.Sex
import com.ascend.fitness.data.WorkoutEntity
import com.ascend.fitness.domain.Progression
import com.ascend.fitness.domain.SPLITS
import com.ascend.fitness.domain.Streaks
import com.ascend.fitness.domain.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LogicTest {
    private val SEED_EXERCISES = Catalog.parse(java.io.File("src/main/assets/exercises.json").readText())
    private val bench = SEED_EXERCISES.first { it.name == "Bench press" }.copy(id = 1)
    private val curl = SEED_EXERCISES.first { it.name == "Dumbbell curl" }.copy(id = 2)
    private fun set(w: Double, r: Int, workout: Long = 1) = SetWithTime(0, workout, 1, w, r, false, workout * 1000)

    @Test fun ladderIsTunedPerExercise() {
        val b = Strength.ladder(bench, 75.0, Sex.MALE, 25)
        val c = Strength.ladder(curl, 75.0, Sex.MALE, 25)
        assertEquals(10, b.size)
        assertEquals(60.0, b[3], 0.01) // Gold bench at 75 kg = 0.8 × bw
        assertTrue("ladders strictly increase", b.zipWithNext().all { it.first < it.second } && c.zipWithNext().all { it.first < it.second })
        // 50 kg is Silver on bench but beyond the top tier on a dumbbell curl.
        assertEquals(2, Strength.tierIndex(50.0, b))
        assertEquals(9, Strength.tierIndex(50.0, c))
    }

    @Test fun womenAndMastersGetEasierLadders() {
        val male = Strength.ladder(bench, 75.0, Sex.MALE, 25)
        val female = Strength.ladder(bench, 75.0, Sex.FEMALE, 25)
        val masters = Strength.ladder(bench, 75.0, Sex.MALE, 55)
        assertTrue(female[3] < male[3] && masters[3] < male[3])
    }

    @Test fun epley() {
        assertEquals(100.0, Strength.e1rm(100.0, 1), 0.001)
        assertEquals(110.0, Strength.e1rm(100.0, 3), 0.001)
        assertEquals(0.0, Strength.e1rm(100.0, 20), 0.001)
    }

    @Test fun doubleProgression() {
        val up = Progression.suggest(bench, listOf(listOf(set(80.0, 12), set(80.0, 12), set(80.0, 12))), 8, 12)!!
        assertEquals(82.5, up.weightKg, 0.01); assertEquals(8, up.reps)
        val hold = Progression.suggest(bench, listOf(listOf(set(80.0, 10), set(80.0, 9))), 8, 12)!!
        assertEquals(80.0, hold.weightKg, 0.01); assertEquals(10, hold.reps)
        val deload = Progression.suggest(bench, listOf(listOf(set(80.0, 5, 1)), listOf(set(80.0, 6, 2))), 8, 12)!!
        assertEquals(70.0, deload.weightKg, 0.01)
    }

    @Test fun weeklyStreak() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 25) // Wednesday
        fun w(d: LocalDate) = WorkoutEntity(name = "x", startedAt = d.atStartOfDay(zone).toInstant().toEpochMilli(), endedAt = 1)
        val ws = (1..3).flatMap { k -> val mon = today.minusWeeks(k.toLong()).with(java.time.DayOfWeek.MONDAY); listOf(w(mon), w(mon.plusDays(2))) } + w(today)
        val s = Streaks.compute(ws, 2, today, zone)
        assertEquals(3, s.weekStreak)
        assertEquals(1, s.thisWeek)
    }

    @Test fun twoSessionsOnOneDayCountOnce() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 26)
        val t = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val ws = listOf(WorkoutEntity(name = "Push", startedAt = t + 3_600_000, endedAt = 1), WorkoutEntity(name = "Push", startedAt = t + 4_200_000, endedAt = 1))
        assertEquals(1, Streaks.compute(ws, 4, today, zone).thisWeek)
    }

    @Test fun twentySplitsUseOnlyLibraryExercises() {
        val names = SEED_EXERCISES.map { it.name }.toSet()
        assertEquals(20, SPLITS.size)
        SPLITS.forEach { s ->
            assertEquals(s.name, 7, s.week.size)
            s.days.flatMap { it.exercises }.forEach { assertTrue("${s.name}: ${it.name}", it.name in names) }
            s.week.filterNotNull().forEach { d -> assertTrue("${s.name}: $d", s.days.any { it.name == d }) }
        }
    }

    @Test fun aiPlanIsValidatedAndClamped() {
        val raw = """{"name":"Test","summary":"x","days":[{"name":"A","weekday":9,"exercises":[
            {"name":"bench press","sets":99,"repMin":5,"repMax":3},{"name":"Laser curls","sets":3,"repMin":8,"repMax":12}]}]}"""
        val p = Coach.parsePlan(raw, setOf("Bench press"))
        val (wd, day) = p.days.single()
        assertEquals(7, wd)
        assertEquals(1, day.exercises.size)
        assertEquals(10, day.exercises[0].sets)
        assertTrue(day.exercises[0].repMax >= day.exercises[0].repMin)
    }

    @Test fun backupRejectsNonFiniteNumbers() {
        val raw = """{"app":"ascend","version":1,
          "exercises":[{"id":1,"name":"X","primaryMuscle":"chest","equipment":"Barbell","metric":"WEIGHT","goldStandard":"NaN"}],
          "workouts":[{"id":1,"name":"w","startedAt":1,"endedAt":2}],
          "sets":[{"id":1,"workoutId":1,"exerciseId":1,"position":0,"weightKg":"Infinity","reps":99999,"done":true}],
          "measurements":[{"id":1,"date":1,"type":"bodyweight","value":-5}]}"""
        val b = com.ascend.fitness.data.Backup.parse(raw)
        assertEquals(0.0, b.exercises[0].goldStandard, 0.0)
        assertEquals(0.0, b.sets[0].weightKg, 0.0)
        assertEquals(1000, b.sets[0].reps)
        assertEquals(0.0, b.measurements[0].value, 0.0)
        assertTrue(runCatching { com.ascend.fitness.data.Backup.parse("""{"app":"other"}""") }.isFailure)
    }

    @Test fun searchPutsBestMatchFirst() {
        val hits = SEED_EXERCISES.filter { it.name.contains("bench", true) }
            .sortedWith(compareBy({ com.ascend.fitness.ui.components.searchRank(it, "Bench") }, { it.estimated }, { it.name.lowercase() }))
        assertEquals("Bench press", hits.first().name)
    }

    @Test fun catalogPhotosExist() {
        val withPhotos = SEED_EXERCISES.filter { it.images.isNotBlank() }
        assertTrue(withPhotos.size >= 1000)
        withPhotos.flatMap { it.images.split(",") }.forEach { p -> assertTrue(p, java.io.File("src/main/assets/$p").isFile) }
    }

    @Test fun catalogHasOver1000UniqueSaneExercises() {
        assertTrue("size ${SEED_EXERCISES.size}", SEED_EXERCISES.size >= 1000)
        assertEquals(SEED_EXERCISES.size, SEED_EXERCISES.map { it.name.lowercase() }.toSet().size)
        assertTrue(SEED_EXERCISES.all { it.goldStandard.isFinite() && it.goldStandard >= 0 && it.name.isNotBlank() })
        assertTrue(SEED_EXERCISES.count { it.goldStandard > 0 } >= 900) // 968 ranked after merging duplicates
        val tuned = SEED_EXERCISES.filter { !it.estimated }
        assertTrue(tuned.size >= 90 && tuned.all { it.goldStandard > 0 || it.equipment in setOf("Band", "Suspension") })
        // Every ranked exercise produces a strictly increasing 10-tier ladder.
        SEED_EXERCISES.filter { it.goldStandard > 0 }.forEach { e ->
            val l = Strength.ladder(e, 75.0, Sex.MALE, 30)
            assertTrue(e.name, l.size == 10 && l.zipWithNext().all { it.first < it.second })
        }
        assertTrue(SEED_EXERCISES.count { it.metric == Metric.REPS } > 10)
    }
}
