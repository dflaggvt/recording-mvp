package com.memorystream.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Singleton
import kotlin.coroutines.resume

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String?
)

@Singleton
class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider"
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): ResolvedLocation? = withContext(Dispatchers.IO) {
        try {
            val cancellationSource = CancellationTokenSource()
            val location = suspendCancellableCoroutine<Location?> { cont ->
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationSource.token
                ).addOnSuccessListener { loc ->
                    cont.resume(loc)
                }.addOnFailureListener {
                    cont.resume(null)
                }
                cont.invokeOnCancellation {
                    cancellationSource.cancel()
                }
            }

            if (location == null) {
                Log.w(TAG, "Location unavailable")
                return@withContext null
            }

            val address = reverseGeocode(location.latitude, location.longitude)
            Log.d(TAG, "Location: ${location.latitude}, ${location.longitude} -> $address")

            ResolvedLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                address = address
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get location", e)
            null
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String? {
        return try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(lat, lng, 1)
            if (results.isNullOrEmpty()) return null
            val addr = results[0]
            addr.thoroughfare ?: addr.subLocality ?: addr.locality ?: addr.getAddressLine(0)?.substringBefore(",")
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed", e)
            null
        }
    }
}
