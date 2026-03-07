package com.memorystream.service

import javax.inject.Singleton

@Singleton
class PlaceResolver {
    suspend fun resolve(location: ResolvedLocation?): String? {
        if (location == null) return null
        return location.address
    }
}
