package com.example

import android.app.Application
import com.example.data.local.PrayerDatabase
import com.example.data.repository.PrayerRepository
import com.example.util.AlarmScheduler
import com.example.util.NotificationHelper

class PrayerApplication : Application() {
    val database: PrayerDatabase by lazy { PrayerDatabase.getDatabase(this) }
    val repository: PrayerRepository by lazy { PrayerRepository(this, database) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationHelper.createNotificationChannels(this)
        AlarmScheduler.scheduleAll(this)
    }

    companion object {
        lateinit var instance: PrayerApplication
            private set
    }
}
