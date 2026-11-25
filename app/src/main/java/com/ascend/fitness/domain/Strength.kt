package com.ascend.fitness.domain

import com.ascend.fitness.data.ExerciseEntity
import com.ascend.fitness.data.Metric
import com.ascend.fitness.data.Sex
import com.ascend.fitness.data.SetWithTime
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class Tier(val index: Int, val name: String, val color: Long)

val TIERS = listOf(
    Tier(0, "Rookie", 0xFF9EA2AA),
    Tier(1, "Bronze", 0xFFCD7F32),
    Tier(2, "Silver", 0xFFC3C9D2),
    Tier(3, "Gold", 0xFFFFD23F),
    Tier(4, "Platinum", 0xFF8FD3C9),
    Tier(5, "Diamond", 0xFF5BC6F5),
    Tier(6, "Titan", 0xFFFF8A3D),
    Tier(7, "Spartan", 0xFFE5333F),
    Tier(8, "Mythic", 0xFF2FD39E),
    Tier(9, "Galactic demon", 0xFFFF2D55),
)

/** Ladder shape relative to the Gold (tier 4) standard. Tuned so the bench ladder matches common strength standards. */
private val SHAPE = doubleArrayOf(0.3125, 0.625, 0.8125, 1.0, 1.25, 1.5, 1.75, 2.0625, 2.375, 2.75)

object Strength {
    /** Epley estimate of a one-rep max. Sets above 12 reps are too unreliable to count. */
    fun e1rm(weightKg: Double, reps: Int): Double = when {
        reps <= 0 || weightKg <= 0 -> 0.0
        reps == 1 -> weightKg
        reps > 12 -> 0.0
        else -> weightKg * (1 + reps / 30.0)
    }

    /**
     * Sex and age adjustment applied to the ladder. Women's standards are lower, more for upper body.
     * Masters lifters (over 40) get 1% easier per year, capped at 30%.
     */
    fun adjustment(ex: ExerciseEntity, sex: Sex, age: Int): Double {
        val lower = ex.primaryMuscle in setOf("quads", "glutes", "hamstrings", "calves", "adductors")
        val s = if (sex == Sex.FEMALE) (if (lower) 0.72 else 0.58) else 1.0
        val a = 1.0 - (0.01 * (age - 40)).coerceIn(0.0, 0.30)
        return s * a
    }

    /** The 10 thresholds for this exercise in kg (WEIGHT) or reps (REPS). Empty when the exercise is unranked. */
    fun ladder(ex: ExerciseEntity, bodyweightKg: Double, sex: Sex, age: Int): List<Double> {
        if (ex.goldStandard <= 0) return emptyList()
        val adj = adjustment(ex, sex, age)
        return SHAPE.map { f ->
            if (ex.metric == Metric.REPS) max(1.0, (ex.goldStandard * f * adj).roundToInt().toDouble())
            else roundTo(ex.goldStandard * f * adj * bodyweightKg, if (ex.perHand) 1.0 else 2.5)
        }.let { monotonic(it) }
    }

    private fun monotonic(xs: List<Double>): List<Double> {
        val out = ArrayList<Double>(xs.size)
        for (x in xs) out.add(if (out.isNotEmpty() && x <= out.last()) out.last() + (if (x >= 10) 2.5 else 1.0) else x)
        return out
    }

    private fun roundTo(v: Double, step: Double) = (v / step).roundToInt() * step

    /** Best performance value used for ranking: best e1RM for WEIGHT, best reps for REPS. */
    fun best(ex: ExerciseEntity, sets: List<SetWithTime>): Double = sets.filter { !it.warmup }.maxOfOrNull {
        if (ex.metric == Metric.REPS) it.reps.toDouble() else e1rm(it.weightKg, it.reps)
    } ?: 0.0

    /** Highest tier index reached, or -1 when below Rookie. */
    fun tierIndex(value: Double, ladder: List<Double>): Int {
        var t = -1
        ladder.forEachIndexed { i, th -> if (value >= th) t = i }
        return t
    }

    /** Fraction of the way from the current tier to the next (0..1). */
    fun progressToNext(value: Double, ladder: List<Double>): Float {
        if (ladder.isEmpty()) return 0f
        val t = tierIndex(value, ladder)
        if (t >= ladder.lastIndex) return 1f
        val lo = if (t >= 0) ladder[t] else 0.0
        val hi = ladder[t + 1]
        return ((value - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
    }

    fun format(v: Double): String = if (v == floor(v)) v.toInt().toString() else String.format("%.1f", v)
}
