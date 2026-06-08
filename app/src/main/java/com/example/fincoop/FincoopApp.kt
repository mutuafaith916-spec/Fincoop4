package com.example.fincoop

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class FincoopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val repository = FincoopRepository(this)
        val isDarkMode = repository.isDarkMode()
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
