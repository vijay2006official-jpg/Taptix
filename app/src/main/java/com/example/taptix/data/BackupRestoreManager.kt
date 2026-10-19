package com.example.taptix.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles backup export and restore of zones, keywords, and configurations.
 * Excludes sensitive personal data and activity logs. Validates file size (< 1MB).
 */
object BackupRestoreManager {

    private const val MAX_BACKUP_SIZE_BYTES = 1024 * 1024 // 1 MB limit

    fun exportBackupJson(prefsRepo: PreferencesRepository): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("appId", "com.example.taptix")
        root.put("timestamp", System.currentTimeMillis())

        // 1. Settings (Custom keywords, negative keywords, etc.)
        val settings = prefsRepo.getSettings()
        val sObj = JSONObject()
        sObj.put("customKeywords", settings.customKeywords)
        sObj.put("negativeKeywords", settings.negativeKeywords)
        sObj.put("preferredAreas", settings.preferredAreas)
        sObj.put("clickIntervalMs", settings.clickIntervalMs)
        sObj.put("platformPreset", settings.platformPreset.id)
        sObj.put("vibrationOnAccept", settings.vibrationOnAccept)
        sObj.put("ttsFeedbackEnabled", settings.ttsFeedbackEnabled)
        sObj.put("ultraModeEnabled", settings.ultraModeEnabled)
        root.put("settings", sObj)

        // 2. Map Zones
        val zonesArr = JSONArray()
        prefsRepo.getMapZones().forEach { zonesArr.put(it.toJson()) }
        root.put("zones", zonesArr)

        // 3. Area Groups
        val groupsArr = JSONArray()
        prefsRepo.getAreaGroups().forEach { groupsArr.put(it.toJson()) }
        root.put("areaGroups", groupsArr)

        // 4. Schedule
        root.put("schedule", prefsRepo.getScheduleConfig().toJson())

        return root.toString(2)
    }

    fun restoreBackupJson(prefsRepo: PreferencesRepository, jsonString: String): Result<String> {
        if (jsonString.toByteArray().size > MAX_BACKUP_SIZE_BYTES) {
            return Result.failure(IllegalArgumentException("Backup file exceeds maximum allowed size (1 MB)"))
        }

        return try {
            val root = JSONObject(jsonString)
            if (root.optString("appId") != "com.example.taptix") {
                return Result.failure(IllegalArgumentException("Invalid backup file: wrong app ID"))
            }

            // Restore settings
            val sObj = root.optJSONObject("settings")
            if (sObj != null) {
                if (sObj.has("customKeywords")) prefsRepo.saveCustomKeywords(sObj.getString("customKeywords"))
                if (sObj.has("negativeKeywords")) prefsRepo.saveNegativeKeywords(sObj.getString("negativeKeywords"))
                if (sObj.has("preferredAreas")) prefsRepo.savePreferredAreas(sObj.getString("preferredAreas"))
                if (sObj.has("clickIntervalMs")) prefsRepo.saveClickInterval(sObj.getLong("clickIntervalMs"))
                if (sObj.has("vibrationOnAccept")) prefsRepo.saveVibrationOnAccept(sObj.getBoolean("vibrationOnAccept"))
                if (sObj.has("ttsFeedbackEnabled")) prefsRepo.saveTtsFeedbackEnabled(sObj.getBoolean("ttsFeedbackEnabled"))
            }

            // Restore zones
            val zonesArr = root.optJSONArray("zones")
            if (zonesArr != null) {
                val zones = mutableListOf<com.example.taptix.model.MapZone>()
                for (i in 0 until zonesArr.length()) {
                    zones.add(com.example.taptix.model.MapZone.fromJson(zonesArr.getJSONObject(i)))
                }
                prefsRepo.saveMapZones(zones)
            }

            // Restore area groups
            val groupsArr = root.optJSONArray("areaGroups")
            if (groupsArr != null) {
                val groups = mutableListOf<com.example.taptix.model.AreaGroup>()
                for (i in 0 until groupsArr.length()) {
                    groups.add(com.example.taptix.model.AreaGroup.fromJson(groupsArr.getJSONObject(i)))
                }
                prefsRepo.saveAreaGroups(groups)
            }

            // Restore schedule
            val schedObj = root.optJSONObject("schedule")
            if (schedObj != null) {
                prefsRepo.saveScheduleConfig(ScheduleConfig.fromJson(schedObj))
            }

            Result.success("Backup restored successfully!")
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Malformed backup data: ${e.message}"))
        }
    }
}
