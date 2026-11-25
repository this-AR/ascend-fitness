package com.ascend.fitness.data

import androidx.room.withTransaction
import com.ascend.fitness.ai.ChatTurn
import com.ascend.fitness.ai.Coach
import com.ascend.fitness.ai.Gemini
import com.ascend.fitness.domain.AchievementStats
import com.ascend.fitness.domain.Achievements
import com.ascend.fitness.domain.DayTemplate
import com.ascend.fitness.domain.Progression
import com.ascend.fitness.domain.Recovery
import com.ascend.fitness.domain.SplitTemplate
import com.ascend.fitness.domain.Streaks
import com.ascend.fitness.domain.Strength
import com.ascend.fitness.domain.Suggestion
import com.ascend.fitness.domain.TIERS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RankRow(
    val exercise: ExerciseEntity,
    val best: Double,
    val ladder: List<Double>,
    val tier: Int,
    val progress: Float,
)

data class PrHit(val exerciseId: Long, val exerciseName: String, val kind: String, val value: Double)
data class RankUp(val exerciseName: String, val tier: Int)
data class FinishResult(
    val workoutId: Long,
    val prs: List<PrHit>,
    val rankUps: List<RankUp>,
    val achievements: List<String>,
)

/** startedAt of a workout that is still being set up (the clock has not started). */
const val NOT_STARTED = 0L

class Repository(private val db: AppDatabase, val settings: SettingsStore, private val catalogJson: () -> String) {
    val dao = db.dao()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Loads the bundled library on first run, and adds/refreshes built-in exercises when the catalog version grows. */
    suspend fun ensureSeeded() {
        if (settings.catalogVersion() >= Catalog.VERSION && dao.exerciseCount() > 0) return
        val list = withContext(Dispatchers.Default) { Catalog.parse(catalogJson()) }
        db.withTransaction {
            dao.insertExercises(list)
            list.forEach { e ->
                dao.refreshBuiltIn(e.name, e.primaryMuscle, e.secondaryMuscles, e.equipment, e.metric, e.goldStandard,
                    e.perHand, e.category, e.level, e.instructions, e.estimated, e.images, e.imagesFrom)
            }
            val keep = list.map { it.name }.toHashSet()
            val stale = dao.exercisesOnce().filter { !it.isCustom && it.name !in keep }.map { it.id }
            stale.chunked(500).forEach { dao.deleteUnusedBuiltIn(it) } // SQLite caps bound parameters at 999
        }
        settings.setCatalogVersion(Catalog.VERSION)
    }

    private fun age(): Int = LocalDate.now().year - settings.state.value.birthYear

    fun ladder(ex: ExerciseEntity): List<Double> {
        val s = settings.state.value
        return Strength.ladder(ex, s.bodyweightKg, s.sex, age())
    }

    fun ranks(exercises: List<ExerciseEntity>, sets: List<SetWithTime>): List<RankRow> {
        val byEx = sets.groupBy { it.exerciseId }
        return exercises.mapNotNull { ex ->
            val hist = byEx[ex.id] ?: return@mapNotNull null
            val ladder = ladder(ex)
            if (ladder.isEmpty()) return@mapNotNull null
            val best = Strength.best(ex, hist)
            RankRow(ex, best, ladder, Strength.tierIndex(best, ladder), Strength.progressToNext(best, ladder))
        }.sortedByDescending { it.tier * 10 + it.progress }
    }

    // ---------- Workouts ----------

    suspend fun suggestionFor(ex: ExerciseEntity, repMin: Int, repMax: Int): Suggestion? {
        val sessions = dao.historyForOnce(ex.id).groupBy { it.workoutId }.values.toList()
        return Progression.suggest(ex, sessions, repMin, repMax)
    }

    /**
     * Opens a workout for set-up: returns the session already running, or a plan for the same routine;
     * an unstarted plan for a different routine is replaced. Sets are pre-filled from the routine and the
     * progression engine. The clock only starts with [beginWorkout].
     */
    suspend fun startWorkout(routineId: Long?): Long {
        dao.activeWorkoutOnce()?.let { a ->
            if (a.startedAt != NOT_STARTED || a.routineId == routineId) return a.id
            dao.deleteWorkout(a)
        }
        val routine = routineId?.let { dao.routine(it) }
        val id = dao.insertWorkout(
            WorkoutEntity(name = routine?.name ?: "Workout", routineId = routine?.id, startedAt = NOT_STARTED)
        )
        if (routine != null) {
            var pos = 0
            val sets = ArrayList<SetEntity>()
            for (item in dao.routineItemsOnce(routine.id)) {
                val ex = dao.exercise(item.exerciseId) ?: continue
                val sug = suggestionFor(ex, item.repMin, item.repMax)
                repeat(item.targetSets) {
                    sets.add(SetEntity(workoutId = id, exerciseId = ex.id, position = pos++,
                        weightKg = sug?.weightKg ?: 0.0, reps = sug?.reps ?: item.repMin))
                }
            }
            dao.insertSets(sets)
        }
        return id
    }

    /** Starts the clock on a planned workout. */
    suspend fun beginWorkout(id: Long) {
        dao.workout(id)?.takeIf { it.startedAt == NOT_STARTED }?.let { dao.updateWorkout(it.copy(startedAt = System.currentTimeMillis())) }
    }

    suspend fun addExercise(workoutId: Long, exerciseId: Long) {
        val ex = dao.exercise(exerciseId) ?: return
        val existing = dao.setsForOnce(workoutId)
        val sug = suggestionFor(ex, 8, 12)
        val start = (existing.maxOfOrNull { it.position } ?: -1) + 1
        dao.insertSets((0 until 3).map {
            SetEntity(workoutId = workoutId, exerciseId = exerciseId, position = start + it,
                weightKg = sug?.weightKg ?: 0.0, reps = sug?.reps ?: 8)
        })
    }

    suspend fun addSet(workoutId: Long, exerciseId: Long) {
        val sets = dao.setsForOnce(workoutId)
        val last = sets.filter { it.exerciseId == exerciseId }.maxByOrNull { it.position }
        val pos = (sets.maxOfOrNull { it.position } ?: -1) + 1
        dao.insertSet(SetEntity(workoutId = workoutId, exerciseId = exerciseId, position = pos,
            weightKg = last?.weightKg ?: 0.0, reps = last?.reps ?: 8))
    }

    suspend fun discardWorkout(id: Long) {
        dao.workout(id)?.let { dao.deleteWorkout(it) }
    }

    suspend fun finishWorkout(id: Long): FinishResult = db.withTransaction {
        val w0 = dao.workout(id) ?: return@withTransaction FinishResult(id, emptyList(), emptyList(), emptyList())
        val sets = dao.setsForOnce(id)
        sets.filter { !it.done }.forEach { dao.deleteSet(it) }
        val done = sets.filter { it.done && !it.warmup }
        val now = System.currentTimeMillis()
        val w = if (w0.startedAt == NOT_STARTED) w0.copy(startedAt = now) else w0
        dao.updateWorkout(w.copy(endedAt = now))

        val prs = ArrayList<PrHit>()
        val rankUps = ArrayList<RankUp>()
        for ((exId, exSets) in done.groupBy { it.exerciseId }) {
            val ex = dao.exercise(exId) ?: continue
            val prior = dao.historyForOnce(exId).filter { it.workoutId != id && it.startedAt < w.startedAt }
            val mine = exSets.map { SetWithTime(it.id, id, exId, it.weightKg, it.reps, false, w.startedAt) }
            if (prior.isNotEmpty()) {
                if (ex.metric == Metric.REPS) {
                    val a = mine.maxOf { it.reps }; val b = prior.maxOf { it.reps }
                    if (a > b) prs.add(PrHit(exId, ex.name, "reps", a.toDouble()))
                } else {
                    val a = Strength.best(ex, mine); val b = Strength.best(ex, prior)
                    if (a > b + 0.01) prs.add(PrHit(exId, ex.name, "e1rm", a))
                    val hw = mine.maxOf { it.weightKg }; val pw = prior.maxOf { it.weightKg }
                    if (hw > pw) prs.add(PrHit(exId, ex.name, "weight", hw))
                }
            }
            val ladder = ladder(ex)
            if (ladder.isNotEmpty()) {
                val before = Strength.tierIndex(Strength.best(ex, prior), ladder)
                val after = Strength.tierIndex(Strength.best(ex, prior + mine), ladder)
                if (after > before && after >= 0) rankUps.add(RankUp(ex.name, after))
            }
        }
        prs.forEach { p -> dao.insertPr(PrEventEntity(exerciseId = p.exerciseId, workoutId = id, kind = p.kind, value = p.value, at = now)) }
        FinishResult(id, prs, rankUps, evaluateAchievements())
    }

    /**
     * Removes copies left by applying the same split or AI plan twice. Only generated routines are touched,
     * and a copy is dropped only when no weekday uses it.
     */
    suspend fun removeDuplicateRoutines() = db.withTransaction {
        val used = dao.allSchedule().mapNotNull { it.routineId }.toSet()
        val generated = dao.allRoutines().filter { it.notes.startsWith("From split:") || it.notes.startsWith("AI plan:") }
        for (group in generated.groupBy { it.name.trim().lowercase() }.values) {
            if (group.size < 2) continue
            val keep = group.filter { it.id in used }.map { it.id }.toSet().ifEmpty { setOf(group.maxOf { it.id }) }
            group.filter { it.id !in keep }.forEach { dao.deleteRoutine(it) }
        }
    }

    suspend fun evaluateAchievements(): List<String> {
        val workouts = dao.finishedWorkoutsOnce()
        val sets = dao.allDoneSetsOnce().filter { !it.warmup }
        val exercises = dao.exercisesOnce()
        val ranks = ranks(exercises, sets)
        val perWorkoutVolume = sets.groupBy { it.workoutId }.mapValues { e -> e.value.sumOf { it.weightKg * it.reps } }
        val stats = AchievementStats(
            workouts = workouts.size,
            totalVolumeKg = perWorkoutVolume.values.sum(),
            bestSessionVolumeKg = perWorkoutVolume.values.maxOrNull() ?: 0.0,
            prs = dao.prCount(),
            weekStreak = Streaks.compute(workouts, settings.state.value.weeklyGoal).bestWeekStreak,
            bestTier = ranks.maxOfOrNull { it.tier } ?: -1,
            liftsAtGold = ranks.count { it.tier >= 3 },
            measurements = dao.measurementsOnce().size,
            earlyWorkout = workouts.any { hour(it.startedAt) < 6 },
            lateWorkout = workouts.any { hour(it.startedAt) >= 22 },
            longestWorkoutMin = workouts.maxOfOrNull { ((it.endedAt ?: it.startedAt) - it.startedAt) / 60_000 } ?: 0,
            customRoutines = dao.customRoutineCount(),
            totalSets = sets.size,
            distinctExercises = sets.map { it.exerciseId }.distinct().size,
        )
        val have = dao.achievementsOnce().map { it.key }.toSet()
        val now = System.currentTimeMillis()
        val new = Achievements.earned(stats) - have
        new.forEach { dao.unlock(AchievementEntity(it, now)) }
        return Achievements.ALL.filter { it.key in new }.map { it.title }
    }

    private fun hour(t: Long) = Instant.ofEpochMilli(t).atZone(zone).hour

    // ---------- Routines and splits ----------

    suspend fun createRoutine(name: String, notes: String, items: List<RoutineExerciseEntity>): Long {
        val id = dao.insertRoutine(RoutineEntity(name = name.trim().ifEmpty { "My routine" }.take(60), notes = notes))
        dao.replaceRoutineExercises(id, items)
        return id
    }

    private suspend fun routineFromTemplate(prefix: String, notes: String, t: DayTemplate): Long {
        val byName = dao.exercisesOnce().associateBy { it.name }
        val items = t.exercises.mapNotNull { p ->
            byName[p.name]?.let { RoutineExerciseEntity(routineId = 0, exerciseId = it.id, position = 0,
                targetSets = p.sets, repMin = p.repMin, repMax = p.repMax) }
        }
        // Re-applying a split updates its routines instead of adding copies.
        val name = "$prefix · ${t.name}".take(60)
        val existing = dao.routineByName(name) ?: return createRoutine(name, notes, items)
        dao.updateRoutine(existing.copy(notes = notes))
        dao.replaceRoutineExercises(existing.id, items)
        return existing.id
    }

    /** Creates one routine per day of the split and assigns them to weekdays. */
    suspend fun applySplit(split: SplitTemplate) = db.withTransaction {
        val short = split.name.substringBefore(" (")
        val ids = split.days.associate { it.name to routineFromTemplate(short, "From split: ${split.name}", it) }
        dao.upsertSchedule((1..7).map { ScheduleEntity(it, split.week[it - 1]?.let { n -> ids[n] }) })
    }

    suspend fun saveAiPlan(plan: Coach.AiPlan) = db.withTransaction {
        val sched = (1..7).associateWith<Int, Long?> { null }.toMutableMap()
        for ((wd, day) in plan.days) sched[wd] = routineFromTemplate(plan.name, "AI plan: ${plan.summary}", day)
        dao.upsertSchedule(sched.map { ScheduleEntity(it.key, it.value) })
    }

    // ---------- AI ----------

    fun hasAi() = settings.geminiKey() != null

    suspend fun ask(turns: List<ChatTurn>, json: Boolean = false): String {
        val key = settings.geminiKey() ?: throw com.ascend.fitness.ai.GeminiException("Add your Gemini API key in Settings to use the coach.")
        return Gemini.generate(key, settings.state.value.geminiModel, Coach.SYSTEM, turns, json)
    }

    /** Checks a key with one tiny request before it is stored. */
    suspend fun testGeminiKey(key: String, model: String): String =
        Gemini.generate(key, model, Coach.SYSTEM, listOf(ChatTurn(true, "Reply with the single word: ready")), temperature = 0.0)

    fun today(): Int = LocalDate.now().let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }

    /** Today's tip: cached per day; from Gemini when a key is set and online, otherwise from the offline list. */
    suspend fun dailyTip(): CoachTipEntity {
        dao.tipFor(today())?.let { if (it.fromAi || !hasAi()) return it }
        val tip = if (hasAi()) {
            runCatching { ask(listOf(ChatTurn(true, Coach.dailyTipPrompt(trainingContext())))) }.getOrNull()
                ?.let { CoachTipEntity(today(), it.take(400), true) }
        } else null
        val result = tip ?: CoachTipEntity(today(), Coach.OFFLINE_TIPS[(LocalDate.now().toEpochDay() % Coach.OFFLINE_TIPS.size).toInt()], false)
        dao.upsertTip(result)
        return result
    }

    /** Compact plain-text summary of the lifter's data, sent to Gemini only when the user uses an AI feature. */
    suspend fun trainingContext(): String {
        val s = settings.state.value
        val df = DateTimeFormatter.ofPattern("EEE d MMM")
        val exercises = dao.exercisesOnce().associateBy { it.id }
        val sets = dao.allDoneSetsOnce().filter { !it.warmup }
        val workouts = dao.finishedWorkoutsOnce()
        val sb = StringBuilder()
        sb.append("Profile: ${s.sex.name.lowercase()}, age ${age()}, bodyweight ${Strength.format(s.bodyweightKg)} kg, goal ${s.weeklyGoal} workouts/week.\n")
        sb.append("Today: ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMM yyyy"))}.\n")
        val st = Streaks.compute(workouts, s.weeklyGoal)
        sb.append("Streak: ${st.weekStreak} weeks hitting goal; ${st.thisWeek} workouts so far this week. Total workouts: ${workouts.size}.\n")
        val sched = dao.allSchedule().sortedBy { it.dayOfWeek }
        if (sched.any { it.routineId != null }) {
            sb.append("Weekly plan:\n")
            for (d in sched) {
                val r = d.routineId?.let { dao.routine(it) }
                val items = r?.let { dao.routineItemsOnce(it.id).joinToString(", ") { i -> "${i.exerciseName} ${i.targetSets}x${i.repMin}-${i.repMax}" } }
                sb.append("- ${java.time.DayOfWeek.of(d.dayOfWeek).name.take(3)}: ${r?.name ?: "rest"}${items?.let { " ($it)" } ?: ""}\n")
            }
        }
        val since = System.currentTimeMillis() - 42L * 86_400_000
        val recent = workouts.filter { it.startedAt >= since }.take(24)
        if (recent.isNotEmpty()) {
            sb.append("Workouts in the last 6 weeks (newest first):\n")
            val byWorkout = sets.groupBy { it.workoutId }
            for (w in recent) {
                val ws = byWorkout[w.id].orEmpty().groupBy { it.exerciseId }
                val mins = ((w.endedAt ?: w.startedAt) - w.startedAt) / 60_000
                sb.append("- ${Instant.ofEpochMilli(w.startedAt).atZone(zone).toLocalDate().format(df)} ${w.name} ($mins min): ")
                sb.append(ws.entries.joinToString("; ") { (exId, xs) ->
                    "${exercises[exId]?.name ?: "?"} " + xs.joinToString(",") { "${Strength.format(it.weightKg)}x${it.reps}" }
                })
                sb.append("\n")
            }
        } else sb.append("No workouts logged in the last 6 weeks.\n")
        val rk = ranks(exercises.values.toList(), sets).take(12)
        if (rk.isNotEmpty()) {
            sb.append("Strength ranks (10 tiers, Rookie..Galactic demon):\n")
            rk.forEach { r ->
                val unit = if (r.exercise.metric == Metric.REPS) "reps" else "kg e1RM"
                val next = r.ladder.getOrNull(r.tier + 1)
                sb.append("- ${r.exercise.name}: ${Strength.format(r.best)} $unit = ${TIERS.getOrNull(r.tier)?.name ?: "unranked"}" +
                    (next?.let { ", next tier at ${Strength.format(it)}" } ?: "") + "\n")
            }
        }
        val fatigue = Recovery.fatigue(dao.muscleSetsSinceOnce(System.currentTimeMillis() - 4L * 86_400_000)).filter { it.value > 0.25f }
        if (fatigue.isNotEmpty()) sb.append("Muscle fatigue now (0-1): " + fatigue.entries.sortedByDescending { it.value }
            .joinToString(", ") { "${muscleLabel(it.key)} ${"%.1f".format(it.value)}" } + "\n")
        val ms = dao.measurementsOnce().groupBy { it.type }
        if (ms.isNotEmpty()) sb.append("Body measurements (latest, first logged): " + ms.entries.joinToString(", ") { (t, xs) ->
            "$t ${Strength.format(xs.first().value)} (was ${Strength.format(xs.last().value)})"
        } + "\n")
        return sb.toString().take(9000)
    }

    // ---------- Backup ----------

    suspend fun exportJson(): String = Backup.export(dao)

    /** Parses first, then replaces everything in one transaction, so a bad file never wipes data. */
    suspend fun importJson(json: String) = db.withTransaction {
        val b = Backup.parse(json)
        dao.wipeAll()
        Backup.restore(dao, b)
    }
}
