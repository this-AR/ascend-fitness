package com.ascend.fitness.data

import org.json.JSONArray
import org.json.JSONObject

/** Full local backup as one JSON file the user saves wherever they like. The Gemini key is never included. */
object Backup {
    const val VERSION = 1

    class Data(
        val exercises: List<ExerciseEntity>, val routines: List<RoutineEntity>, val routineExercises: List<RoutineExerciseEntity>,
        val schedule: List<ScheduleEntity>, val workouts: List<WorkoutEntity>, val sets: List<SetEntity>,
        val measurements: List<MeasurementEntity>, val prs: List<PrEventEntity>, val achievements: List<AchievementEntity>,
        val tips: List<CoachTipEntity>,
    )

    private fun <T> arr(xs: List<T>, f: (T) -> JSONObject) = JSONArray().apply { xs.forEach { put(f(it)) } }
    private fun <T> list(o: JSONObject, key: String, f: (JSONObject) -> T): List<T> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)

    /** org.json happily turns "NaN"/"Infinity" strings into doubles; clamp everything to sane, finite ranges. */
    internal fun num(v: Double, max: Double): Double = if (v.isFinite()) v.coerceIn(0.0, max) else 0.0
    private fun JSONObject.num(k: String, max: Double, fallback: Double = 0.0) = num(optDouble(k, fallback), max)
    private fun JSONObject.count(k: String, max: Int) = optInt(k, 0).coerceIn(0, max)

    suspend fun export(d: AppDao): String = JSONObject()
        .put("app", "ascend").put("version", VERSION).put("exportedAt", System.currentTimeMillis())
        .put("exercises", arr(d.exercisesOnce()) {
            JSONObject().put("id", it.id).put("name", it.name).put("primaryMuscle", it.primaryMuscle)
                .put("secondaryMuscles", it.secondaryMuscles).put("equipment", it.equipment).put("metric", it.metric.name)
                .put("goldStandard", it.goldStandard).put("perHand", it.perHand).put("isCustom", it.isCustom)
                .put("category", it.category).put("level", it.level).put("instructions", it.instructions)
                .put("estimated", it.estimated).put("images", it.images).put("imagesFrom", it.imagesFrom)
        })
        .put("routines", arr(d.allRoutines()) { JSONObject().put("id", it.id).put("name", it.name).put("notes", it.notes) })
        .put("routineExercises", arr(d.allRoutineExercises()) {
            JSONObject().put("id", it.id).put("routineId", it.routineId).put("exerciseId", it.exerciseId).put("position", it.position)
                .put("targetSets", it.targetSets).put("repMin", it.repMin).put("repMax", it.repMax)
        })
        .put("schedule", arr(d.allSchedule()) { JSONObject().put("dayOfWeek", it.dayOfWeek).put("routineId", it.routineId ?: JSONObject.NULL) })
        .put("workouts", arr(d.allWorkouts()) {
            JSONObject().put("id", it.id).put("name", it.name).put("routineId", it.routineId ?: JSONObject.NULL)
                .put("startedAt", it.startedAt).put("endedAt", it.endedAt ?: JSONObject.NULL).put("notes", it.notes)
        })
        .put("sets", arr(d.allSets()) {
            JSONObject().put("id", it.id).put("workoutId", it.workoutId).put("exerciseId", it.exerciseId).put("position", it.position)
                .put("weightKg", it.weightKg).put("reps", it.reps).put("done", it.done).put("warmup", it.warmup)
                .put("completedAt", it.completedAt ?: JSONObject.NULL)
        })
        .put("measurements", arr(d.measurementsOnce()) {
            JSONObject().put("id", it.id).put("date", it.date).put("type", it.type).put("value", it.value)
        })
        .put("prs", arr(d.allPrs()) {
            JSONObject().put("id", it.id).put("exerciseId", it.exerciseId).put("workoutId", it.workoutId)
                .put("kind", it.kind).put("value", it.value).put("at", it.at)
        })
        .put("achievements", arr(d.achievementsOnce()) { JSONObject().put("key", it.key).put("unlockedAt", it.unlockedAt) })
        .put("tips", arr(d.allTips()) { JSONObject().put("day", it.day).put("text", it.text).put("fromAi", it.fromAi) })
        .toString()

    /** Throws on anything that isn't an Ascend backup, before any data is touched. */
    fun parse(json: String): Data {
        val o = JSONObject(json)
        require(o.optString("app") == "ascend") { "This file is not an Ascend backup." }
        require(o.optInt("version") in 1..VERSION) { "This backup is from a newer version of the app." }
        return Data(
            list(o, "exercises") {
                ExerciseEntity(it.getLong("id"), it.getString("name").take(80), it.getString("primaryMuscle"), it.optString("secondaryMuscles"),
                    it.getString("equipment"), if (it.optString("metric") == "REPS") Metric.REPS else Metric.WEIGHT,
                    it.num("goldStandard", if (it.optString("metric") == "REPS") 200.0 else 10.0), it.optBoolean("perHand"), it.optBoolean("isCustom"),
                    it.optString("category", "strength").take(40), it.optString("level").take(20),
                    it.optString("instructions").take(5000), it.optBoolean("estimated"),
                    // only paths inside the bundled img/ folder are kept
                    it.optString("images").split(",").filter { p -> p.matches(Regex("img/[0-9a-f]{12}\\.webp")) }.joinToString(","),
                    it.optString("imagesFrom").take(80))
            },
            list(o, "routines") { RoutineEntity(it.getLong("id"), it.getString("name").take(80), it.optString("notes")) },
            list(o, "routineExercises") {
                RoutineExerciseEntity(it.getLong("id"), it.getLong("routineId"), it.getLong("exerciseId"), it.getInt("position"),
                    it.count("targetSets", 20), it.count("repMin", 100), it.count("repMax", 100))
            },
            list(o, "schedule") { ScheduleEntity(it.getInt("dayOfWeek").coerceIn(1, 7), it.optLongOrNull("routineId")) },
            list(o, "workouts") {
                WorkoutEntity(it.getLong("id"), it.getString("name").take(80), it.optLongOrNull("routineId"), it.getLong("startedAt"),
                    it.optLongOrNull("endedAt"), it.optString("notes"))
            },
            list(o, "sets") {
                SetEntity(it.getLong("id"), it.getLong("workoutId"), it.getLong("exerciseId"), it.getInt("position"),
                    it.num("weightKg", 1000.0), it.count("reps", 1000), it.getBoolean("done"), it.optBoolean("warmup"), it.optLongOrNull("completedAt"))
            },
            list(o, "measurements") { MeasurementEntity(it.getLong("id"), it.getLong("date"), it.getString("type").take(20), it.num("value", 1000.0)) },
            list(o, "prs") {
                PrEventEntity(it.getLong("id"), it.getLong("exerciseId"), it.getLong("workoutId"), it.getString("kind"),
                    it.num("value", 2000.0), it.getLong("at"))
            },
            list(o, "achievements") { AchievementEntity(it.getString("key"), it.getLong("unlockedAt")) },
            list(o, "tips") { CoachTipEntity(it.getInt("day"), it.getString("text"), it.optBoolean("fromAi")) },
        )
    }

    suspend fun restore(d: AppDao, b: Data) {
        d.restoreExercises(b.exercises); d.restoreRoutines(b.routines); d.restoreRoutineExercises(b.routineExercises)
        d.restoreSchedule(b.schedule); d.restoreWorkouts(b.workouts); d.restoreSets(b.sets)
        d.restoreMeasurements(b.measurements); d.restorePrs(b.prs); d.restoreAchievements(b.achievements); d.restoreTips(b.tips)
    }
}
