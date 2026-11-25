package com.ascend.fitness.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class, RoutineEntity::class, RoutineExerciseEntity::class, ScheduleEntity::class,
        WorkoutEntity::class, SetEntity::class, MeasurementEntity::class, PrEventEntity::class,
        AchievementEntity::class, CoachTipEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ascend.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

        /** v2: exercise library grew to 1,700+ with instructions; keeps every existing workout. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ExerciseEntity ADD COLUMN category TEXT NOT NULL DEFAULT 'strength'")
                db.execSQL("ALTER TABLE ExerciseEntity ADD COLUMN level TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ExerciseEntity ADD COLUMN instructions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ExerciseEntity ADD COLUMN estimated INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: exercise photos. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ExerciseEntity ADD COLUMN images TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4: photos borrowed from a similar exercise are labelled. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ExerciseEntity ADD COLUMN imagesFrom TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
