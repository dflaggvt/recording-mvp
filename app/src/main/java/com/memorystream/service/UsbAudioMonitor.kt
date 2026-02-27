package com.memorystream.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log

class UsbAudioMonitor : BroadcastReceiver() {

    companion object {
        private const val TAG = "UsbAudioMonitor"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Log.i(TAG, "USB device attached")
                // The RecordingService will detect the USB mic on next chunk start
                // via AudioCaptureManager.findUsbMicrophone()
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.w(TAG, "USB device detached")
                // Recording continues with built-in mic as fallback.
                // AudioCaptureManager.setPreferredDevice will fail gracefully
                // and Android falls back to default input.
            }
        }
    }
}
