package com.ascend.fitness.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class RoutineItem(
    val id: Long,
    val routineId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val exerciseName: String,
    val equipment: String,
)

data class SetWithTime(
    val id: Long,
    val workoutId: Long,
    val exerciseId: Long,
    val weightKg: Double,
    val reps: Int,
    val warmup: Boolean,
    val startedAt: Long,
)

data class MuscleSet(
    val primaryMuscle: String,
    val secondaryMuscles: String,
    val startedAt: Long,
    val reps: Int,
)

data class WorkoutSummary(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val setCount: Int,
    val volume: Double,
    val exerciseCount: Int,
)

@Dao
interface AppDao {
    // Exercises
    @Query("SELECT * FROM ExerciseEntity ORDER BY name")
    fun exercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM ExerciseEntity ORDER BY name")
    suspend fun exercisesOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM ExerciseEntity WHERE id = :id")
    suspend fun exercise(id: Long): ExerciseEntity?

    @Query("SELECT * FROM ExerciseEntity WHERE id = :id")
    fun exerciseFlow(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT COUNT(*) FROM ExerciseEntity")
    suspend fun exerciseCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(item: ExerciseEntity): Long

    /** Refreshes a built-in exercise from a newer catalog; never touches the user's custom exercises. */
    @Query(
        "UPDATE ExerciseEntity SET primaryMuscle = :primary, secondaryMuscles = :secondary, equipment = :equipment, " +
            "metric = :metric, goldStandard = :gold, perHand = :perHand, category = :category, level = :level, " +
            "instructions = :instructions, estimated = :estimated, images = :images, imagesFrom = :imagesFrom " +
            "WHERE name = :name AND isCustom = 0"
    )
    suspend fun refreshBuiltIn(
        name: String, primary: String, secondary: String, equipment: String, metric: Metric, gold: Double,
        perHand: Boolean, category: String, level: String, instructions: String, estimated: Boolean, images: String,
        imagesFrom: String,
    )

    /** Removes built-in exercises that left the catalog (merged duplicates), but only if never used anywhere. */
    @Query(
        "DELETE FROM ExerciseEntity WHERE isCustom = 0 AND id IN (:ids) " +
            "AND id NOT IN (SELECT exerciseId FROM SetEntity) AND id NOT IN (SELECT exerciseId FROM RoutineExerciseEntity) " +
            "AND id NOT IN (SELECT exerciseId FROM PrEventEntity)"
    )
    suspend fun deleteUnusedBuiltIn(ids: List<Long>)

    // Routines
    @Query("SELECT * FROM RoutineEntity ORDER BY name")
    fun routines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM RoutineEntity WHERE id = :id")
    suspend fun routine(id: Long): RoutineEntity?

    @Insert
    suspend fun insertRoutine(r: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(r: RoutineEntity)

    @Delete
    suspend fun deleteRoutine(r: RoutineEntity)

    @Query(
        """SELECT re.id, re.routineId, re.exerciseId, re.position, re.targetSets, re.repMin, re.repMax,
           e.name AS exerciseName, e.equipment AS equipment
           FROM RoutineExerciseEntity re JOIN ExerciseEntity e ON e.id = re.exerciseId
           WHERE re.routineId = :routineId ORDER BY re.position"""
    )
    fun routineItems(routineId: Long): Flow<List<RoutineItem>>

    @Query(
        """SELECT re.id, re.routineId, re.exerciseId, re.position, re.targetSets, re.repMin, re.repMax,
           e.name AS exerciseName, e.equipment AS equipment
           FROM RoutineExerciseEntity re JOIN ExerciseEntity e ON e.id = re.exerciseId
           WHERE re.routineId = :routineId ORDER BY re.position"""
    )
    suspend fun routineItemsOnce(routineId: Long): List<RoutineItem>

    @Query("SELECT * FROM RoutineExerciseEntity WHERE exerciseId = :exerciseId LIMIT 1")
    suspend fun anyRoutineItemFor(exerciseId: Long): RoutineExerciseEntity?

    @Insert
    suspend fun insertRoutineExercises(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM RoutineExerciseEntity WHERE routineId = :routineId")
    suspend fun clearRoutineExercises(routineId: Long)

    @Transaction
    suspend fun replaceRoutineExercises(routineId: Long, items: List<RoutineExerciseEntity>) {
        clearRoutineExercises(routineId)
        insertRoutineExercises(items.mapIndexed { i, it -> it.copy(id = 0, routineId = routineId, position = i) })
    }

    // Weekly schedule
    @Query("SELECT * FROM ScheduleEntity ORDER BY dayOfWeek")
    fun schedule(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM ScheduleEntity WHERE dayOfWeek = :day")
    suspend fun scheduleFor(day: Int): ScheduleEntity?

    @Upsert
    suspend fun upsertSchedule(items: List<ScheduleEntity>)

    // Workouts
    @Insert
    suspend fun insertWorkout(w: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(w: WorkoutEntity)

    @Delete
    suspend fun deleteWorkout(w: WorkoutEntity)

    @Query("SELECT * FROM WorkoutEntity WHERE id = :id")
    suspend fun workout(id: Long): WorkoutEntity?

    @Query("SELECT * FROM WorkoutEntity WHERE id = :id")
    fun workoutFlow(id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM WorkoutEntity WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun activeWorkout(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM WorkoutEntity WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeWorkoutOnce(): WorkoutEntity?

    @Query("SELECT * FROM RoutineEntity WHERE name = :name ORDER BY id LIMIT 1")
    suspend fun routineByName(name: String): RoutineEntity?

    @Query("SELECT * FROM WorkoutEntity WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun finishedWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM WorkoutEntity WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    suspend fun finishedWorkoutsOnce(): List<WorkoutEntity>

    @Query(
        """SELECT w.id, w.name, w.startedAt, w.endedAt,
           COUNT(s.id) AS setCount,
           COALESCE(SUM(s.weightKg * s.reps), 0) AS volume,
           COUNT(DISTINCT s.exerciseId) AS exerciseCount
           FROM WorkoutEntity w LEFT JOIN SetEntity s ON s.workoutId = w.id AND s.done = 1 AND s.warmup = 0
           WHERE w.endedAt IS NOT NULL
           GROUP BY w.id ORDER BY w.startedAt DESC"""
    )
    fun workoutSummaries(): Flow<List<WorkoutSummary>>

    // Sets
    @Query("SELECT * FROM SetEntity WHERE workoutId = :workoutId ORDER BY position")
    fun setsFor(workoutId: Long): Flow<List<SetEntity>>

    @Query("SELECT * FROM SetEntity WHERE workoutId = :workoutId ORDER BY position")
    suspend fun setsForOnce(workoutId: Long): List<SetEntity>

    @Insert
    suspend fun insertSet(s: SetEntity): Long

    @Insert
    suspend fun insertSets(s: List<SetEntity>)

    @Update
    suspend fun updateSet(s: SetEntity)

    @Delete
    suspend fun deleteSet(s: SetEntity)

    @Query("DELETE FROM SetEntity WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun deleteExerciseFromWorkout(workoutId: Long, exerciseId: Long)

    @Query(
        """SELECT s.id, s.workoutId, s.exerciseId, s.weightKg, s.reps, s.warmup, w.startedAt
           FROM SetEntity s JOIN WorkoutEntity w ON w.id = s.workoutId
           WHERE s.exerciseId = :exerciseId AND s.done = 1 AND w.endedAt IS NOT NULL
           ORDER BY w.startedAt, s.position"""
    )
    fun historyFor(exerciseId: Long): Flow<List<SetWithTime>>

    @Query(
        """SELECT s.id, s.workoutId, s.exerciseId, s.weightKg, s.reps, s.warmup, w.startedAt
           FROM SetEntity s JOIN WorkoutEntity w ON w.id = s.workoutId
           WHERE s.exerciseId = :exerciseId AND s.done = 1 AND w.endedAt IS NOT NULL
           ORDER BY w.startedAt, s.position"""
    )
    suspend fun historyForOnce(exerciseId: Long): List<SetWithTime>

    @Query(
        """SELECT s.id, s.workoutId, s.exerciseId, s.weightKg, s.reps, s.warmup, w.startedAt
           FROM SetEntity s JOIN WorkoutEntity w ON w.id = s.workoutId
           WHERE s.done = 1 AND w.endedAt IS NOT NULL
           ORDER BY w.startedAt, s.position"""
    )
    fun allDoneSets(): Flow<List<SetWithTime>>

    @Query(
        """SELECT s.id, s.workoutId, s.exerciseId, s.weightKg, s.reps, s.warmup, w.startedAt
           FROM SetEntity s JOIN WorkoutEntity w ON w.id = s.workoutId
           WHERE s.done = 1 AND w.endedAt IS NOT NULL
           ORDER BY w.startedAt, s.position"""
    )
    suspend fun allDoneSetsOnce(): List<SetWithTime>

    @Query(
        """SELECT e.primaryMuscle, e.secondaryMuscles, w.startedAt, s.reps
           FROM SetEntity s JOIN WorkoutEntity w ON w.id = s.workoutId JOIN ExerciseEntity e ON e.id = s.exerciseId
           WHERE s.done = 1 AND s.warmup = 0 AND w.startedAt >= :since"""
    )
    fun muscleSetsSince(since: Long): Flow<List<MuscleSet>>

    @Query(
        """SELECT e.primaryMuscle, e.secondaryMuscles, w.startedAt, s.reps
           FROM SetEntity s JOIN WorkoutEntity w ON w.id = s.workoutId JOIN ExerciseEntity e ON e.id = s.exerciseId
           WHERE s.done = 1 AND s.warmup = 0 AND w.startedAt >= :since"""
    )
    suspend fun muscleSetsSinceOnce(since: Long): List<MuscleSet>

    // Measurements
    @Query("SELECT * FROM MeasurementEntity ORDER BY date DESC")
    fun measurements(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM MeasurementEntity ORDER BY date DESC")
    suspend fun measurementsOnce(): List<MeasurementEntity>

    @Insert
    suspend fun insertMeasurement(m: MeasurementEntity): Long

    @Delete
    suspend fun deleteMeasurement(m: MeasurementEntity)

    // PRs
    @Insert
    suspend fun insertPr(p: PrEventEntity)

    @Query("SELECT * FROM PrEventEntity ORDER BY at DESC LIMIT :limit")
    fun recentPrs(limit: Int): Flow<List<PrEventEntity>>

    @Query("SELECT * FROM PrEventEntity WHERE workoutId = :workoutId")
    suspend fun prsForWorkout(workoutId: Long): List<PrEventEntity>

    @Query("SELECT COUNT(*) FROM PrEventEntity")
    suspend fun prCount(): Int

    // Achievements
    @Query("SELECT * FROM AchievementEntity")
    fun achievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM AchievementEntity")
    suspend fun achievementsOnce(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(a: AchievementEntity): Long

    // Coach tips
    @Query("SELECT * FROM CoachTipEntity WHERE day = :day")
    suspend fun tipFor(day: Int): CoachTipEntity?

    @Query("SELECT * FROM CoachTipEntity WHERE day = :day")
    fun tipFlow(day: Int): Flow<CoachTipEntity?>

    @Upsert
    suspend fun upsertTip(t: CoachTipEntity)

    // Backup
    @Query("SELECT * FROM RoutineEntity") suspend fun allRoutines(): List<RoutineEntity>
    @Query("SELECT * FROM RoutineExerciseEntity") suspend fun allRoutineExercises(): List<RoutineExerciseEntity>
    @Query("SELECT * FROM ScheduleEntity") suspend fun allSchedule(): List<ScheduleEntity>
    @Query("SELECT * FROM WorkoutEntity") suspend fun allWorkouts(): List<WorkoutEntity>
    @Query("SELECT * FROM SetEntity") suspend fun allSets(): List<SetEntity>
    @Query("SELECT * FROM PrEventEntity") suspend fun allPrs(): List<PrEventEntity>
    @Query("SELECT * FROM CoachTipEntity") suspend fun allTips(): List<CoachTipEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreExercises(x: List<ExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreRoutines(x: List<RoutineEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreRoutineExercises(x: List<RoutineExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSchedule(x: List<ScheduleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreWorkouts(x: List<WorkoutEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSets(x: List<SetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreMeasurements(x: List<MeasurementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restorePrs(x: List<PrEventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreAchievements(x: List<AchievementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTips(x: List<CoachTipEntity>)
    @Query("SELECT COUNT(*) FROM RoutineEntity WHERE notes NOT LIKE 'From split:%' AND notes NOT LIKE 'AI plan:%'")
    suspend fun customRoutineCount(): Int

    @Query("DELETE FROM SetEntity") suspend fun wipeSets()
    @Query("DELETE FROM WorkoutEntity") suspend fun wipeWorkouts()
    @Query("DELETE FROM RoutineExerciseEntity") suspend fun wipeRoutineExercises()
    @Query("DELETE FROM ScheduleEntity") suspend fun wipeSchedule()
    @Query("DELETE FROM RoutineEntity") suspend fun wipeRoutines()
    @Query("DELETE FROM MeasurementEntity") suspend fun wipeMeasurements()
    @Query("DELETE FROM PrEventEntity") suspend fun wipePrs()
    @Query("DELETE FROM AchievementEntity") suspend fun wipeAchievements()
    @Query("DELETE FROM CoachTipEntity") suspend fun wipeTips()
    @Query("DELETE FROM ExerciseEntity") suspend fun wipeExercises()

    suspend fun wipeAll() {
        wipeSets(); wipeWorkouts(); wipeRoutineExercises(); wipeSchedule(); wipeRoutines()
        wipeMeasurements(); wipePrs(); wipeAchievements(); wipeTips(); wipeExercises()
    }
}
