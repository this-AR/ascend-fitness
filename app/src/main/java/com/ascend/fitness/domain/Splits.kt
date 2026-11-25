package com.ascend.fitness.domain

/** One planned exercise inside a day template: "Exercise name:sets:repMin-repMax". */
data class PlannedExercise(val name: String, val sets: Int, val repMin: Int, val repMax: Int)

data class DayTemplate(val name: String, val exercises: List<PlannedExercise>)

/**
 * A famous training split. [week] maps Monday..Sunday (index 0..6) to a day template name, or null for rest.
 */
data class SplitTemplate(
    val name: String,
    val level: String,
    val summary: String,
    val days: List<DayTemplate>,
    val week: List<String?>,
)

private fun day(name: String, spec: String) = DayTemplate(
    name,
    spec.split(";").map { it.trim() }.filter { it.isNotEmpty() }.map { e ->
        val p = e.split(":")
        val r = p[2].split("-")
        PlannedExercise(p[0].trim(), p[1].toInt(), r[0].toInt(), r.getOrElse(1) { r[0] }.toInt())
    },
)

private val PUSH = day("Push", "Bench press:4:5-8; Overhead press:3:6-10; Incline dumbbell press:3:8-12; Lateral raise:3:12-15; Triceps pushdown:3:10-15; Overhead triceps extension:2:10-15")
private val PULL = day("Pull", "Deadlift:3:3-5; Pull-up:3:6-10; Barbell row:3:6-10; Face pull:3:12-15; Hammer curl:3:8-12; Dumbbell curl:2:10-15")
private val LEGS = day("Legs", "Squat:4:5-8; Romanian deadlift:3:8-10; Leg press:3:10-12; Lying leg curl:3:10-15; Standing calf raise:4:10-15; Hanging leg raise:3:10-15")
private val UPPER = day("Upper", "Bench press:4:5-8; Barbell row:4:6-10; Overhead press:3:6-10; Lat pulldown:3:8-12; Dumbbell curl:2:10-12; Triceps pushdown:2:10-12")
private val LOWER = day("Lower", "Squat:4:5-8; Romanian deadlift:3:6-10; Bulgarian split squat:3:8-12; Leg extension:2:12-15; Seated leg curl:2:12-15; Standing calf raise:3:10-15")
private val FULL_A = day("Full body A", "Squat:3:5-8; Bench press:3:5-8; Barbell row:3:6-10; Lateral raise:2:12-15; Dumbbell curl:2:10-12")
private val FULL_B = day("Full body B", "Deadlift:3:3-5; Overhead press:3:6-8; Pull-up:3:6-10; Bulgarian split squat:2:8-12; Triceps pushdown:2:10-12")
private val FULL_C = day("Full body C", "Front squat:3:6-8; Incline dumbbell press:3:8-12; Seated cable row:3:8-12; Romanian deadlift:2:8-10; Face pull:2:12-15")

val SPLITS: List<SplitTemplate> = listOf(
    SplitTemplate("Push / pull / legs (6 days)", "Intermediate", "Each muscle twice a week. The classic high-volume split.",
        listOf(PUSH, PULL, LEGS), listOf("Push", "Pull", "Legs", "Push", "Pull", "Legs", null)),
    SplitTemplate("Push / pull / legs (3 days)", "Beginner", "PPL once a week on Mon, Wed, Fri.",
        listOf(PUSH, PULL, LEGS), listOf("Push", null, "Pull", null, "Legs", null, null)),
    SplitTemplate("Upper / lower (4 days)", "Beginner", "Upper and lower body twice a week. Easy to recover from.",
        listOf(UPPER, LOWER), listOf("Upper", "Lower", null, "Upper", "Lower", null, null)),
    SplitTemplate("Full body (3 days)", "Beginner", "Whole body every session, Mon/Wed/Fri. Best start for new lifters.",
        listOf(FULL_A, FULL_B, FULL_C), listOf("Full body A", null, "Full body B", null, "Full body C", null, null)),
    SplitTemplate("Bro split (5 days)", "Intermediate", "One muscle group per day, high volume each session.",
        listOf(
            day("Chest", "Bench press:4:6-10; Incline dumbbell press:3:8-12; Cable fly:3:12-15; Dip:3:8-12"),
            day("Back", "Deadlift:3:3-5; Pull-up:4:6-10; Seated cable row:3:8-12; Dumbbell row:3:8-12"),
            day("Shoulders", "Overhead press:4:6-10; Lateral raise:4:12-15; Rear delt fly:3:12-15; Barbell shrug:3:8-12"),
            day("Arms", "Barbell curl:3:8-12; Close-grip bench press:3:6-10; Hammer curl:3:10-12; Triceps pushdown:3:10-15"),
            day("Legs", "Squat:4:6-10; Leg press:3:10-12; Romanian deadlift:3:8-10; Seated leg curl:3:10-15; Standing calf raise:4:10-15"),
        ), listOf("Chest", "Back", "Shoulders", "Arms", "Legs", null, null)),
    SplitTemplate("Arnold split (6 days)", "Advanced", "Chest + back, shoulders + arms, legs, twice a week.",
        listOf(
            day("Chest and back", "Bench press:4:6-10; Pull-up:4:6-10; Incline dumbbell press:3:8-12; Barbell row:3:8-12; Dumbbell pullover:3:10-12"),
            day("Shoulders and arms", "Overhead press:4:6-10; Lateral raise:3:12-15; Barbell curl:3:8-12; Skull crusher:3:8-12; Hammer curl:3:10-12"),
            day("Legs", "Squat:4:6-10; Romanian deadlift:3:8-10; Leg extension:3:12-15; Lying leg curl:3:12-15; Standing calf raise:4:10-15"),
        ), listOf("Chest and back", "Shoulders and arms", "Legs", "Chest and back", "Shoulders and arms", "Legs", null)),
    SplitTemplate("PHUL (4 days)", "Intermediate", "Power upper/lower early in the week, hypertrophy upper/lower after.",
        listOf(
            day("Power upper", "Bench press:4:3-5; Barbell row:4:3-5; Overhead press:3:5-8; Lat pulldown:3:6-10; Barbell curl:2:6-10"),
            day("Power lower", "Squat:4:3-5; Deadlift:3:3-5; Leg press:3:8-12; Lying leg curl:3:6-10; Standing calf raise:4:6-10"),
            day("Hypertrophy upper", "Incline dumbbell press:4:8-12; Dumbbell fly:3:10-12; Seated cable row:4:8-12; Lateral raise:3:12-15; Triceps pushdown:3:10-15"),
            day("Hypertrophy lower", "Front squat:4:8-12; Bulgarian split squat:3:8-12; Leg extension:3:12-15; Seated leg curl:3:12-15; Seated calf raise:4:12-15"),
        ), listOf("Power upper", "Power lower", null, "Hypertrophy upper", "Hypertrophy lower", null, null)),
    SplitTemplate("PHAT (5 days)", "Advanced", "Two power days then three body-part hypertrophy days.",
        listOf(
            day("Upper power", "Barbell row:3:3-5; Pull-up:2:6-10; Bench press:3:3-5; Dip:2:6-10; Overhead press:3:6-10"),
            day("Lower power", "Squat:3:3-5; Hack squat:2:6-10; Romanian deadlift:2:5-8; Lying leg curl:2:6-10; Standing calf raise:3:6-10"),
            day("Back and shoulders", "Barbell row:6:3-3; Pull-up:3:8-12; Seated cable row:3:8-12; Lateral raise:3:12-20; Rear delt fly:3:12-20"),
            day("Lower hypertrophy", "Squat:6:3-3; Hack squat:3:8-12; Leg press:2:12-15; Leg extension:3:15-20; Seated leg curl:3:12-15"),
            day("Chest and arms", "Dumbbell bench press:6:3-3; Incline dumbbell press:3:8-12; Cable fly:2:15-20; Barbell curl:3:8-12; Skull crusher:3:8-12"),
        ), listOf("Upper power", "Lower power", null, "Back and shoulders", "Lower hypertrophy", "Chest and arms", null)),
    SplitTemplate("StrongLifts 5×5", "Beginner", "Alternate A and B, three times a week. Add weight every session.",
        listOf(
            day("Workout A", "Squat:5:5-5; Bench press:5:5-5; Barbell row:5:5-5"),
            day("Workout B", "Squat:5:5-5; Overhead press:5:5-5; Deadlift:1:5-5"),
        ), listOf("Workout A", null, "Workout B", null, "Workout A", null, null)),
    SplitTemplate("Starting Strength", "Beginner", "Linear progression on the big barbell lifts, 3×5.",
        listOf(
            day("Workout A", "Squat:3:5-5; Bench press:3:5-5; Deadlift:1:5-5"),
            day("Workout B", "Squat:3:5-5; Overhead press:3:5-5; Power clean:5:3-3"),
        ), listOf("Workout A", null, "Workout B", null, "Workout A", null, null)),
    SplitTemplate("5/3/1 Boring But Big", "Intermediate", "One main lift a day plus 5×10 volume work.",
        listOf(
            day("Press day", "Overhead press:3:3-5; Overhead press:5:10-10; Lat pulldown:5:10-10"),
            day("Deadlift day", "Deadlift:3:3-5; Deadlift:5:10-10; Hanging leg raise:5:10-15"),
            day("Bench day", "Bench press:3:3-5; Bench press:5:10-10; Dumbbell row:5:10-10"),
            day("Squat day", "Squat:3:3-5; Squat:5:10-10; Lying leg curl:5:10-10"),
        ), listOf("Press day", "Deadlift day", null, "Bench day", "Squat day", null, null)),
    SplitTemplate("GZCLP (4 days)", "Beginner", "Tier 1 heavy, tier 2 moderate, tier 3 pump work.",
        listOf(
            day("Day 1", "Squat:5:3-3; Bench press:3:10-10; Lat pulldown:3:15-25"),
            day("Day 2", "Overhead press:5:3-3; Deadlift:3:10-10; Dumbbell row:3:15-25"),
            day("Day 3", "Bench press:5:3-3; Squat:3:10-10; Lat pulldown:3:15-25"),
            day("Day 4", "Deadlift:5:3-3; Overhead press:3:10-10; Dumbbell row:3:15-25"),
        ), listOf("Day 1", "Day 2", null, "Day 3", "Day 4", null, null)),
    SplitTemplate("Full body (2 days)", "Beginner", "Minimal time, whole body twice a week.",
        listOf(FULL_A, FULL_B), listOf("Full body A", null, null, "Full body B", null, null, null)),
    SplitTemplate("Upper / lower / PPL (5 days)", "Intermediate", "Upper and lower, then push, pull, legs.",
        listOf(UPPER, LOWER, PUSH, PULL, LEGS), listOf("Upper", "Lower", null, "Push", "Pull", "Legs", null)),
    SplitTemplate("Torso / limbs (4 days)", "Intermediate", "Chest, back and shoulders, then arms and legs.",
        listOf(
            day("Torso", "Bench press:4:6-10; Barbell row:4:6-10; Overhead press:3:6-10; Lat pulldown:3:8-12; Cable fly:2:12-15"),
            day("Limbs", "Squat:4:6-10; Romanian deadlift:3:8-10; Barbell curl:3:8-12; Skull crusher:3:8-12; Standing calf raise:3:10-15"),
        ), listOf("Torso", "Limbs", null, "Torso", "Limbs", null, null)),
    SplitTemplate("Anterior / posterior (4 days)", "Intermediate", "Front-of-body muscles, then back-of-body muscles.",
        listOf(
            day("Anterior", "Squat:4:5-8; Bench press:4:5-8; Overhead press:3:6-10; Leg extension:3:12-15; Cable crunch:3:10-15"),
            day("Posterior", "Deadlift:3:3-5; Pull-up:4:6-10; Barbell row:3:6-10; Lying leg curl:3:10-12; Face pull:3:12-15"),
        ), listOf("Anterior", "Posterior", null, "Anterior", "Posterior", null, null)),
    SplitTemplate("Push / pull (4 days)", "Intermediate", "Pushing muscles and quads, then pulling muscles and hamstrings.",
        listOf(
            day("Push and quads", "Squat:4:5-8; Bench press:4:5-8; Overhead press:3:6-10; Leg extension:3:12-15; Triceps pushdown:3:10-15"),
            day("Pull and hamstrings", "Deadlift:3:3-5; Pull-up:4:6-10; Seated cable row:3:8-12; Lying leg curl:3:10-12; Barbell curl:3:8-12"),
        ), listOf("Push and quads", "Pull and hamstrings", null, "Push and quads", "Pull and hamstrings", null, null)),
    SplitTemplate("Calisthenics recommended routine", "Beginner", "No equipment needed beyond a bar. Three days a week.",
        listOf(
            day("Bodyweight", "Pull-up:3:5-8; Pistol squat:3:5-8; Dip:3:5-8; Back extension:3:8-12; Push-up:3:8-15; Inverted row:3:8-12; Hanging leg raise:3:8-12"),
        ), listOf("Bodyweight", null, "Bodyweight", null, "Bodyweight", null, null)),
    SplitTemplate("Home dumbbell full body", "Beginner", "Only a pair of dumbbells and the floor.",
        listOf(
            day("Dumbbell A", "Goblet squat:3:8-12; Dumbbell bench press:3:8-12; Dumbbell row:3:8-12; Dumbbell shoulder press:3:8-12; Dumbbell curl:2:10-15"),
            day("Dumbbell B", "Dumbbell Romanian deadlift:3:8-12; Push-up:3:8-15; Bulgarian split squat:3:8-12; Lateral raise:3:12-15; Overhead triceps extension:2:10-15"),
        ), listOf("Dumbbell A", null, "Dumbbell B", null, "Dumbbell A", null, null)),
    SplitTemplate("Glute focus (4 days)", "Beginner", "Two lower days built around glutes, two upper days.",
        listOf(
            day("Glutes and hamstrings", "Barbell hip thrust:4:8-12; Romanian deadlift:3:8-10; Bulgarian split squat:3:8-12; Hip abduction:3:12-20; Lying leg curl:3:10-15"),
            day("Upper", "Dumbbell shoulder press:3:8-12; Lat pulldown:3:8-12; Incline dumbbell press:3:8-12; Seated cable row:3:8-12; Lateral raise:3:12-15"),
            day("Glutes and quads", "Squat:4:6-10; Barbell hip thrust:3:10-12; Dumbbell step-up:3:8-12; Leg extension:3:12-15; Glute bridge:2:15-20"),
        ), listOf("Glutes and hamstrings", "Upper", null, "Glutes and quads", "Upper", null, null)),
)
