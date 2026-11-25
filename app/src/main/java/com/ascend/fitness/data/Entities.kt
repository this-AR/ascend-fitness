package com.ascend.fitness.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** How an exercise is ranked: by estimated 1RM relative to bodyweight, or by clean reps. */
enum class Metric { WEIGHT, REPS }

@Entity(indices = [Index(value = ["name"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscle: String,
    /** Comma-separated muscle keys. */
    val secondaryMuscles: String = "",
    val equipment: String,
    val metric: Metric = Metric.WEIGHT,
    /**
     * Gold-tier standard for this exercise: 1RM ÷ bodyweight for WEIGHT, clean reps for REPS.
     * The whole 10-tier ladder scales from this one number, so each exercise is tuned on its own.
     * 0 = not ranked.
     */
    val goldStandard: Double = 0.0,
    /** True for lifts done one dumbbell per hand where the standard is per dumbbell. */
    val perHand: Boolean = false,
    val isCustom: Boolean = false,
    /** strength, stretching, cardio, plyometrics, powerlifting, olympic weightlifting, strongman */
    @ColumnInfo(defaultValue = "strength") val category: String = "strength",
    @ColumnInfo(defaultValue = "") val level: String = "",
    @ColumnInfo(defaultValue = "") val instructions: String = "",
    /** True when the gold standard comes from rules rather than hand tuning. */
    @ColumnInfo(defaultValue = "0") val estimated: Boolean = false,
    /** Comma-separated asset paths of start/end position photos (img/....webp). */
    @ColumnInfo(defaultValue = "") val images: String = "",
    /** When the photos belong to a near-identical exercise, its name (shown as "similar exercise"). */
    @ColumnInfo(defaultValue = "") val imagesFrom: String = "",
)

@Entity
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
)

@Entity(
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
)

/** Which routine is planned for each weekday (1 = Monday … 7 = Sunday). Null routine = rest day. */
@Entity
data class ScheduleEntity(
    @PrimaryKey val dayOfWeek: Int,
    val routineId: Long?,
)

@Entity(indices = [Index("startedAt")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val routineId: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val notes: String = "",
)

@Entity(
    foreignKeys = [
        ForeignKey(WorkoutEntity::class, ["id"], ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val done: Boolean = false,
    val warmup: Boolean = false,
    val completedAt: Long? = null,
)

@Entity(indices = [Index("type"), Index("date")])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    /** bodyweight, bodyfat, chest, waist, hips, arm, thigh, calf, neck, shoulders */
    val type: String,
    val value: Double,
)

@Entity(indices = [Index("exerciseId")])
data class PrEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val workoutId: Long,
    /** e1rm, weight, reps, volume */
    val kind: String,
    val value: Double,
    val at: Long,
)

@Entity
data class AchievementEntity(
    @PrimaryKey val key: String,
    val unlockedAt: Long,
)

/** One coach tip per calendar day (yyyymmdd), from Gemini or the offline list. */
@Entity
data class CoachTipEntity(
    @PrimaryKey val day: Int,
    val text: String,
    val fromAi: Boolean,
)
