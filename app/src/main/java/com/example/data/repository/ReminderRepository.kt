package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.PXReminderDatabase
import com.example.data.entities.AlarmHistoryEntity
import com.example.data.entities.CategoryEntity
import com.example.data.entities.ReminderEntity
import com.example.domain.models.AlarmEventType
import com.example.domain.models.AlarmHistoryItem
import com.example.domain.models.Category
import com.example.domain.models.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReminderRepository(private val database: PXReminderDatabase) {

    private val reminderDao = database.reminderDao()
    private val categoryDao = database.categoryDao()
    private val historyDao = database.alarmHistoryDao()

    val remindersFlow: Flow<List<Reminder>> =
        reminderDao.observeAll().map { list -> list.map { it.toDomain() } }

    val categoriesFlow: Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    val historyFlow: Flow<List<AlarmHistoryItem>> =
        historyDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAllRemindersSnapshot(): List<Reminder> =
        reminderDao.getAllSnapshot().map { it.toDomain() }

    suspend fun getAllActiveReminders(): List<Reminder> =
        reminderDao.getAllActive().map { it.toDomain() }

    suspend fun getReminderById(id: Long): Reminder? =
        reminderDao.getById(id)?.toDomain()

    suspend fun saveReminder(reminder: Reminder): Long {
        val now = System.currentTimeMillis()
        val entity = ReminderEntity.fromDomain(
            reminder.copy(
                createdAtMillis = if (reminder.id == 0L) now else reminder.createdAtMillis,
                updatedAtMillis = now
            )
        )
        return if (reminder.id == 0L) {
            reminderDao.insert(entity)
        } else {
            reminderDao.update(entity)
            reminder.id
        }
    }

    suspend fun deleteReminder(id: Long) {
        reminderDao.deleteById(id)
    }

    suspend fun deleteAllCompleted() {
        reminderDao.deleteAllCompleted()
    }

    suspend fun getAllCategoriesSnapshot(): List<Category> {
        val current = categoryDao.getAllSnapshot()
        if (current.isEmpty()) {
            categoryDao.insertAllIgnore(PXReminderDatabase.DEFAULT_CATEGORIES)
            return categoryDao.getAllSnapshot().map { it.toDomain() }
        }
        return current.map { it.toDomain() }
    }

    suspend fun getCategoryById(id: Long): Category? =
        categoryDao.getById(id)?.toDomain()

    suspend fun saveCategory(category: Category): Long {
        val entity = CategoryEntity.fromDomain(category)
        return if (category.id == 0L) {
            categoryDao.insert(entity)
        } else {
            categoryDao.update(entity)
            category.id
        }
    }

    suspend fun deleteCategory(category: Category) {
        if (!category.isDefault) {
            categoryDao.delete(CategoryEntity.fromDomain(category))
        }
    }

    suspend fun recordHistory(
        reminderId: Long,
        title: String,
        categoryName: String,
        categoryColorHex: Long,
        eventType: AlarmEventType
    ) {
        historyDao.insert(
            AlarmHistoryEntity(
                reminderId = reminderId,
                reminderTitle = title,
                categoryName = categoryName,
                categoryColorHex = categoryColorHex,
                eventType = eventType.name,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    suspend fun replaceAllDataForRestore(
        categories: List<Category>,
        reminders: List<Reminder>
    ) {
        database.withTransaction {
            reminderDao.deleteAll()
            categoryDao.deleteAll()
            val categoriesToInsert = if (categories.isEmpty()) {
                PXReminderDatabase.DEFAULT_CATEGORIES
            } else {
                categories.map { CategoryEntity.fromDomain(it) }
            }
            categoryDao.insertAllReplace(categoriesToInsert)
            if (reminders.isNotEmpty()) {
                reminderDao.insertAll(reminders.map { ReminderEntity.fromDomain(it) })
            }
        }
    }
}
