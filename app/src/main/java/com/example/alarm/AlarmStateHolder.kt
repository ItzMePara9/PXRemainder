package com.example.alarm

import com.example.domain.models.ActiveAlarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide holder for currently ringing alarms so AlarmRingingActivity
 * can restore state cleanly after backgrounding, rotation, or recreation.
 */
object AlarmStateHolder {

    private val _activeAlarms = MutableStateFlow<List<ActiveAlarm>>(emptyList())
    val activeAlarms: StateFlow<List<ActiveAlarm>> = _activeAlarms.asStateFlow()

    fun addOrUpdate(alarm: ActiveAlarm): Boolean {
        var addedNew = false
        _activeAlarms.update { current ->
            val exists = current.any { it.reminderId == alarm.reminderId && it.slotIndex == alarm.slotIndex }
            if (exists) {
                current.map {
                    if (it.reminderId == alarm.reminderId && it.slotIndex == alarm.slotIndex) alarm else it
                }
            } else {
                addedNew = true
                current + alarm
            }
        }
        return addedNew
    }

    fun remove(reminderId: Long, slotIndex: Int) {
        _activeAlarms.update { current ->
            current.filterNot { it.reminderId == reminderId && it.slotIndex == slotIndex }
        }
    }

    fun clearAll() {
        _activeAlarms.value = emptyList()
    }
}
