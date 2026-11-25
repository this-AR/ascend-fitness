package com.ascend.fitness.domain

import com.ascend.fitness.data.ExerciseEntity
import com.ascend.fitness.data.Metric
import com.ascend.fitness.data.MuscleSet
import com.ascend.fitness.data.SetWithTime
import com.ascend.fitness.data.WorkoutEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class Suggestion(val weightKg: Double, val reps: Int, val note: String)

object Progression {
    /**
     * Double progression: stay at a weight until every working set reaches the top of the rep range,
     * then add the smallest sensible jump and go back to the bottom of the range.
     * [sessions] = this exercise's past working sets grouped by workout, oldest first.
     */
    fun suggest(ex: ExerciseEntity, sessions: List<List<SetWithTime>>, repMin: Int, repMax: Int): Suggestion? {
        val last = sessions.lastOrNull()?.filter { !it.warmup } ?: return null
        if (last.isEmpty()) return null
        if (ex.metric == Metric.REPS) {
            val best = last.maxOf { it.reps }
            return Suggestion(last.maxOf { it.weightKg }, best + 1, "Beat last time: aim for ${best + 1} reps on your first set")
        }
        val top = last.maxOf { it.weightKg }
        val topSets = last.filter { it.weightKg == top }
        val inc = increment(ex)
        if (topSets.all { it.reps >= repMax }) {
            return Suggestion(top + inc, repMin, "You hit $repMax reps on every set. Add ${Strength.format(inc)} kg.")
        }
        val missed = topSets.maxOf { it.reps } < repMin
        val prev = sessions.dropLast(1).lastOrNull()?.filter { !it.warmup && it.weightKg == top }.orEmpty()
        if (missed && prev.isNotEmpty() && prev.maxOf { it.reps } < repMin) {
            val back = roundDown(top * 0.9, inc)
            return Suggestion(back, repMin, "Missed $repMin reps twice. Drop 10% and build back up.")
        }
        val target = min(repMax, max(repMin, topSets.minOf { it.reps } + 1))
        return Suggestion(top, target, "Same weight, aim for $target reps on every set.")
    }

    fun increment(ex: ExerciseEntity): Double = when {
        ex.perHand -> 1.0
        ex.equipment in setOf("Barbell", "Machine") && ex.primaryMuscle in setOf("quads", "glutes", "hamstrings") -> 5.0
        ex.equipment == "Dumbbell" || ex.equipment == "Kettlebell" -> 2.0
        else -> 2.5
    }

    private fun roundDown(v: Double, step: Double) = kotlin.math.floor(v / step) * step
}

object Recovery {
    /** 0 = fresh, 1 = fully fatigued, per muscle. Each hard set adds load that fades over ~2 days. */
    fun fatigue(sets: List<MuscleSet>, now: Long = System.currentTimeMillis()): Map<String, Float> {
        val load = HashMap<String, Double>()
        for (s in sets) {
            val hours = (now - s.startedAt) / 3_600_000.0
            if (hours < 0 || hours > 96) continue
            val decay = exp(-hours / 30.0)
            load.merge(s.primaryMuscle, decay, Double::plus)
            s.secondaryMuscles.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { load.merge(it, 0.5 * decay, Double::plus) }
        }
        return load.mapValues { (it.value / 6.0).coerceIn(0.0, 1.0).toFloat() }
    }
}

data class StreakInfo(val weekStreak: Int, val bestWeekStreak: Int, val thisWeek: Int, val doneDays: Set<Int>)

object Streaks {
    private fun weekStart(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun compute(workouts: List<WorkoutEntity>, goal: Int, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): StreakInfo {
        // Two sessions on the same day count as one training day.
        val dates = workouts.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.distinct()
        val perWeek = dates.groupingBy { weekStart(it) }.eachCount()
        val thisWeekStart = weekStart(today)
        val thisWeek = perWeek[thisWeekStart] ?: 0
        // The current week only counts once the goal is met; it never breaks the streak while in progress.
        var w = if (thisWeek >= goal) thisWeekStart else thisWeekStart.minusWeeks(1)
        var streak = 0
        while ((perWeek[w] ?: 0) >= goal) { streak++; w = w.minusWeeks(1) }
        var best = 0
        var run = 0
        val first = perWeek.keys.minOrNull()
        if (first != null) {
            var c: LocalDate = first
            while (!c.isAfter(thisWeekStart)) {
                if ((perWeek[c] ?: 0) >= goal) { run++; best = max(best, run) } else if (c != thisWeekStart) run = 0
                c = c.plusWeeks(1)
            }
        }
        val doneDays = dates.filter { weekStart(it) == thisWeekStart }.map { it.dayOfWeek.value }.toSet()
        return StreakInfo(streak, max(best, streak), thisWeek, doneDays)
    }
}

data class Achievement(val key: String, val title: String, val description: String)

data class AchievementStats(
    val workouts: Int,
    val totalVolumeKg: Double,
    val bestSessionVolumeKg: Double,
    val prs: Int,
    val weekStreak: Int,
    val bestTier: Int,
    val liftsAtGold: Int,
    val measurements: Int,
    val earlyWorkout: Boolean,
    val lateWorkout: Boolean,
    val longestWorkoutMin: Long,
    val customRoutines: Int,
    val totalSets: Int = 0,
    val distinctExercises: Int = 0,
)

object Achievements {
    val ALL = listOf(
        Achievement("first_workout", "First rep", "Finish your first workout."),
        Achievement("workouts_10", "Regular", "Finish 10 workouts."),
        Achievement("workouts_25", "Quarter century", "Finish 25 workouts."),
        Achievement("workouts_50", "Committed", "Finish 50 workouts."),
        Achievement("workouts_100", "Centurion", "Finish 100 workouts."),
        Achievement("workouts_200", "Iron veteran", "Finish 200 workouts."),
        Achievement("workouts_365", "Year of iron", "Finish 365 workouts."),
        Achievement("streak_1", "Goal getter", "Hit your weekly goal for the first time."),
        Achievement("streak_4", "Month strong", "Hit your weekly goal 4 weeks in a row."),
        Achievement("streak_12", "Quarter beast", "Hit your weekly goal 12 weeks in a row."),
        Achievement("streak_26", "Half-year hero", "Hit your weekly goal 26 weeks in a row."),
        Achievement("streak_52", "Unbreakable", "Hit your weekly goal 52 weeks in a row."),
        Achievement("pr_1", "New best", "Set your first personal record."),
        Achievement("pr_25", "Record breaker", "Set 25 personal records."),
        Achievement("pr_50", "Record hunter", "Set 50 personal records."),
        Achievement("pr_100", "History maker", "Set 100 personal records."),
        Achievement("volume_10t", "Ten tonnes", "Lift 10,000 kg in total."),
        Achievement("volume_100t", "Hundred tonnes", "Lift 100,000 kg in total."),
        Achievement("volume_500t", "Half a million", "Lift 500,000 kg in total."),
        Achievement("volume_1000t", "Mountain mover", "Lift 1,000,000 kg in total."),
        Achievement("session_10t", "Big day", "Lift 10,000 kg in one workout."),
        Achievement("tier_gold", "Golden", "Reach Gold on any exercise."),
        Achievement("tier_platinum", "Platinum plated", "Reach Platinum on any exercise."),
        Achievement("tier_diamond", "Shine on", "Reach Diamond on any exercise."),
        Achievement("tier_titan", "Titan rising", "Reach Titan on any exercise."),
        Achievement("tier_spartan", "This is Sparta", "Reach Spartan on any exercise."),
        Achievement("tier_mythic", "Myth in the making", "Reach Mythic on any exercise."),
        Achievement("tier_demon", "Galactic demon", "Reach the top tier on any exercise."),
        Achievement("gold_5", "Well rounded", "Reach Gold on 5 different exercises."),
        Achievement("gold_10", "Gold collector", "Reach Gold on 10 different exercises."),
        Achievement("explorer_25", "Explorer", "Train 25 different exercises."),
        Achievement("sets_1000", "Thousand sets", "Complete 1,000 working sets."),
        Achievement("early_bird", "Early bird", "Start a workout before 6 am."),
        Achievement("night_owl", "Night owl", "Start a workout after 10 pm."),
        Achievement("marathon", "Marathon session", "Train for over 2 hours in one go."),
        Achievement("measure_1", "Know your numbers", "Log a body measurement."),
        Achievement("measure_10", "Tracker", "Log 10 body measurements."),
        Achievement("architect", "Architect", "Create your own routine."),
    )

    fun earned(s: AchievementStats): Set<String> = buildSet {
        if (s.workouts >= 1) add("first_workout")
        if (s.workouts >= 10) add("workouts_10")
        if (s.workouts >= 50) add("workouts_50")
        if (s.workouts >= 100) add("workouts_100")
        if (s.workouts >= 365) add("workouts_365")
        if (s.weekStreak >= 4) add("streak_4")
        if (s.weekStreak >= 12) add("streak_12")
        if (s.weekStreak >= 52) add("streak_52")
        if (s.prs >= 1) add("pr_1")
        if (s.prs >= 25) add("pr_25")
        if (s.prs >= 100) add("pr_100")
        if (s.totalVolumeKg >= 10_000) add("volume_10t")
        if (s.totalVolumeKg >= 100_000) add("volume_100t")
        if (s.totalVolumeKg >= 1_000_000) add("volume_1000t")
        if (s.bestSessionVolumeKg >= 10_000) add("session_10t")
        if (s.bestTier >= 3) add("tier_gold")
        if (s.bestTier >= 5) add("tier_diamond")
        if (s.bestTier >= 7) add("tier_spartan")
        if (s.bestTier >= 9) add("tier_demon")
        if (s.liftsAtGold >= 5) add("gold_5")
        if (s.earlyWorkout) add("early_bird")
        if (s.lateWorkout) add("night_owl")
        if (s.longestWorkoutMin >= 120) add("marathon")
        if (s.measurements >= 1) add("measure_1")
        if (s.customRoutines >= 1) add("architect")
        if (s.workouts >= 25) add("workouts_25")
        if (s.workouts >= 200) add("workouts_200")
        if (s.weekStreak >= 1) add("streak_1")
        if (s.weekStreak >= 26) add("streak_26")
        if (s.prs >= 50) add("pr_50")
        if (s.totalVolumeKg >= 500_000) add("volume_500t")
        if (s.bestTier >= 4) add("tier_platinum")
        if (s.bestTier >= 6) add("tier_titan")
        if (s.bestTier >= 8) add("tier_mythic")
        if (s.liftsAtGold >= 10) add("gold_10")
        if (s.distinctExercises >= 25) add("explorer_25")
        if (s.totalSets >= 1000) add("sets_1000")
        if (s.measurements >= 10) add("measure_10")
    }
}
