package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AlarmHistoryDao
import com.example.data.dao.CategoryDao
import com.example.data.dao.ReminderDao
import com.example.data.entities.AlarmHistoryEntity
import com.example.data.entities.CategoryEntity
import com.example.data.entities.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ReminderEntity::class,
        CategoryEntity::class,
        AlarmHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PXReminderDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun categoryDao(): CategoryDao
    abstract fun alarmHistoryDao(): AlarmHistoryDao

    companion object {
        const val DATABASE_NAME = "pxreminder.db"

        /**
         * Explicit Room migration from v1 to v2 ensuring safe upgrades for installed builds
         * when additional scheduling & history tables were introduced.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `alarm_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `reminderId` INTEGER NOT NULL,
                        `reminderTitle` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `categoryColorHex` INTEGER NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `timestampMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_alarm_history_timestampMillis` ON `alarm_history` (`timestampMillis`)"
                )
            }
        }

        val DEFAULT_CATEGORIES = listOf(
            CategoryEntity(id = 1L, name = "Personal", iconKey = "person", colorHex = 0xFFA78BFA, isDefault = true),
            CategoryEntity(id = 2L, name = "Study", iconKey = "school", colorHex = 0xFF60A5FA, isDefault = true),
            CategoryEntity(id = 3L, name = "Work", iconKey = "work", colorHex = 0xFF34D399, isDefault = true),
            CategoryEntity(id = 4L, name = "Health", iconKey = "favorite", colorHex = 0xFFF87171, isDefault = true),
            CategoryEntity(id = 5L, name = "Shopping", iconKey = "shopping_bag", colorHex = 0xFFFBBF24, isDefault = true),
            CategoryEntity(id = 6L, name = "Birthday", iconKey = "cake", colorHex = 0xFFF472B6, isDefault = true),
            CategoryEntity(id = 7L, name = "Other", iconKey = "label", colorHex = 0xFF94A3B8, isDefault = true)
        )

        @Volatile
        private var INSTANCE: PXReminderDatabase? = null

        fun getInstance(context: Context): PXReminderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): PXReminderDatabase {
            return Room.databaseBuilder(
                context,
                PXReminderDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.categoryDao()?.insertAllIgnore(DEFAULT_CATEGORIES)
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = INSTANCE?.categoryDao() ?: return@launch
                            if (dao.getAllSnapshot().isEmpty()) {
                                dao.insertAllIgnore(DEFAULT_CATEGORIES)
                            }
                        }
                    }
                })
                .build()
        }
    }
}
