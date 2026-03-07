package com.memorystream

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MemoryStreamApp : Application() {

    companion object {
        const val RECORDING_CHANNEL_ID = "recording_channel"
        const val INSIGHTS_CHANNEL_ID = "insights_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val recordingChannel = NotificationChannel(
            RECORDING_CHANNEL_ID,
            getString(R.string.recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing recording notification"
            setShowBadge(false)
        }
        manager.createNotificationChannel(recordingChannel)

        val insightsChannel = NotificationChannel(
            INSIGHTS_CHANNEL_ID,
            "Memory Insights",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Proactive memory insights and reminders"
            enableVibration(true)
        }
        manager.createNotificationChannel(insightsChannel)
    }
}
