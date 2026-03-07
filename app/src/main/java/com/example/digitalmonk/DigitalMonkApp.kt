package com.example.digitalmonk

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DigitalMonkApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}