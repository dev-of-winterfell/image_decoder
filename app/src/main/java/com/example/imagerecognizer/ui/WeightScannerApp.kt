package com.example.imagerecognizer.ui

import android.app.Application
import android.util.Log
import androidx.camera.core.CameraXConfig

class WeightScannerApp : Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder()
            .setMinimumLoggingLevel(Log.ERROR)
            .build()
    }
}