package com.ascend.fitness.data

/**
 * Built-in exercise library, bundled as assets/exercises.json (1,700+ exercises).
 * Built by build_catalog.py from 93 hand-tuned lifts plus free-exercise-db (Unlicense) and wger (CC-BY-SA).
 */
object Catalog {
    const val VERSION = 5
    const val ATTRIBUTION = "Exercise library and photos: free-exercise-db (Unlicense) and wger.de (CC-BY-SA 4.0), adapted."

    fun parse(json: String): List<ExerciseEntity> {
        val arr = org.json.JSONObject(json).getJSONArray("exercises")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ExerciseEntity(
                name = o.getString("name"),
                primaryMuscle = o.getString("primary"),
                secondaryMuscles = o.optString("secondary"),
                equipment = o.getString("equipment"),
                metric = if (o.getString("metric") == "R") Metric.REPS else Metric.WEIGHT,
                goldStandard = o.getDouble("gold"),
                perHand = o.optBoolean("perHand"),
                category = o.optString("category", "strength"),
                level = o.optString("level"),
                instructions = o.optString("instructions"),
                estimated = o.optBoolean("estimated"),
                images = o.optJSONArray("images")?.let { a -> (0 until a.length()).joinToString(",") { a.getString(it) } }.orEmpty(),
                imagesFrom = o.optString("imagesFrom"),
            )
        }
    }
}

val MUSCLES = listOf(
    "chest", "shoulders", "triceps", "biceps", "forearms", "lats", "upperback", "traps",
    "lowerback", "abs", "obliques", "glutes", "quads", "hamstrings", "adductors", "calves", "cardio",
)

fun muscleLabel(key: String): String = when (key) {
    "upperback" -> "Upper back"
    "lowerback" -> "Lower back"
    else -> key.replaceFirstChar { it.uppercase() }
}
