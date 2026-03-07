package com.memorystream.service

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ExclusionZone(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 200.0,
    val enabled: Boolean = true
)

class ExclusionZoneManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "exclusion_zones_prefs"
        private const val KEY_ZONES = "exclusion_zones"
        private const val KEY_ENABLED = "exclusion_zones_enabled"
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getZones(): List<ExclusionZone> {
        val json = prefs.getString(KEY_ZONES, null) ?: return emptyList()
        val type = object : TypeToken<List<ExclusionZone>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addZone(zone: ExclusionZone) {
        val zones = getZones().toMutableList()
        zones.add(zone)
        saveZones(zones)
    }

    fun removeZone(id: String) {
        val zones = getZones().filter { it.id != id }
        saveZones(zones)
    }

    fun replaceAllZones(zones: List<ExclusionZone>) {
        saveZones(zones)
    }

    fun isInsideAnyZone(lat: Double, lng: Double): ExclusionZone? {
        if (!isFeatureEnabled()) return null
        return getZones().firstOrNull { zone ->
            zone.enabled && haversineDistance(lat, lng, zone.latitude, zone.longitude) <= zone.radiusMeters
        }
    }

    fun isFeatureEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setFeatureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun saveZones(zones: List<ExclusionZone>) {
        prefs.edit().putString(KEY_ZONES, gson.toJson(zones)).apply()
    }

    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }
}
