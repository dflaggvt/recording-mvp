package com.memorystream.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import com.memorystream.service.ExclusionZone
import com.memorystream.service.ExclusionZoneManager
import com.memorystream.service.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val exclusionZoneManager: ExclusionZoneManager,
    private val cloudApi: CloudApi,
    private val locationProvider: LocationProvider
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val _zones = MutableStateFlow<List<ExclusionZone>>(emptyList())
    val zones: StateFlow<List<ExclusionZone>> = _zones.asStateFlow()

    private val _featureEnabled = MutableStateFlow(exclusionZoneManager.isFeatureEnabled())
    val featureEnabled: StateFlow<Boolean> = _featureEnabled.asStateFlow()

    private val _currentLatLng = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLatLng: StateFlow<Pair<Double, Double>?> = _currentLatLng.asStateFlow()

    init {
        refreshZones()
        syncFromCloud()
    }

    fun setFeatureEnabled(enabled: Boolean) {
        exclusionZoneManager.setFeatureEnabled(enabled)
        _featureEnabled.value = enabled
    }

    fun addZone(label: String, lat: Double, lng: Double, radius: Double) {
        val zone = ExclusionZone(
            label = label,
            latitude = lat,
            longitude = lng,
            radiusMeters = radius
        )
        exclusionZoneManager.addZone(zone)
        refreshZones()

        viewModelScope.launch {
            try {
                cloudApi.createExclusionZone(label, lat, lng, radius)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync zone to cloud", e)
            }
        }
    }

    fun removeZone(id: String) {
        exclusionZoneManager.removeZone(id)
        refreshZones()

        viewModelScope.launch {
            try {
                cloudApi.deleteExclusionZone(id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete zone from cloud", e)
            }
        }
    }

    fun resolveCurrentLocation() {
        viewModelScope.launch {
            try {
                val loc = locationProvider.getCurrentLocation()
                if (loc != null) {
                    _currentLatLng.value = Pair(loc.latitude, loc.longitude)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get current location", e)
            }
        }
    }

    private fun syncFromCloud() {
        viewModelScope.launch {
            try {
                val cloudZones = cloudApi.getExclusionZones()
                if (cloudZones.isNotEmpty()) {
                    val merged = mergeZones(exclusionZoneManager.getZones(), cloudZones.map {
                        ExclusionZone(
                            id = it.id,
                            label = it.label,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            radiusMeters = it.radius_meters
                        )
                    })
                    exclusionZoneManager.replaceAllZones(merged)
                    refreshZones()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync zones from cloud", e)
            }
        }
    }

    private fun mergeZones(
        local: List<ExclusionZone>,
        cloud: List<ExclusionZone>
    ): List<ExclusionZone> {
        val byId = LinkedHashMap<String, ExclusionZone>()
        for (z in local) byId[z.id] = z
        for (z in cloud) byId.putIfAbsent(z.id, z)
        return byId.values.toList()
    }

    private fun refreshZones() {
        _zones.value = exclusionZoneManager.getZones()
    }
}
