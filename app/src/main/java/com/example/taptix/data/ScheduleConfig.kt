package com.example.taptix.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ScheduleConfig(
    val isEnabled: Boolean = false,
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val endHour: Int = 20,
    val endMinute: Int = 0,
    val activeDays: Set<Int> = setOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )
) {
    fun isWithinActiveWindow(calendar: Calendar = Calendar.getInstance()): Boolean {
        if (!isEnabled) return true // When schedule is disabled, manual control rules
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        if (day !in activeDays) return false

        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            // Spans overnight (e.g. 20:00 to 04:00)
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("isEnabled", isEnabled)
        obj.put("startHour", startHour)
        obj.put("startMinute", startMinute)
        obj.put("endHour", endHour)
        obj.put("endMinute", endMinute)
        val arr = JSONArray()
        activeDays.forEach { arr.put(it) }
        obj.put("activeDays", arr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): ScheduleConfig {
            val days = mutableSetOf<Int>()
            val arr = obj.optJSONArray("activeDays")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    days.add(arr.optInt(i))
                }
            } else {
                days.addAll(listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY))
            }
            return ScheduleConfig(
                isEnabled = obj.optBoolean("isEnabled", false),
                startHour = obj.optInt("startHour", 8),
                startMinute = obj.optInt("startMinute", 0),
                endHour = obj.optInt("endHour", 20),
                endMinute = obj.optInt("endMinute", 0),
                activeDays = days
            )
        }
    }
}
