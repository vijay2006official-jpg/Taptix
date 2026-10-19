package com.example.taptix.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Represents a named list of zones (e.g. "Morning Commute", "Airport Duties", "Weekend Night").
 */
data class AreaGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val zoneIds: List<String> = emptyList(),
    val isSelected: Boolean = false
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        val arr = JSONArray()
        zoneIds.forEach { arr.put(it) }
        obj.put("zoneIds", arr)
        obj.put("isSelected", isSelected)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): AreaGroup {
            val list = mutableListOf<String>()
            val arr = obj.optJSONArray("zoneIds")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    list.add(arr.optString(i))
                }
            }
            return AreaGroup(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Area List"),
                zoneIds = list,
                isSelected = obj.optBoolean("isSelected", false)
            )
        }
    }
}
