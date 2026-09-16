package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.PrayerApplication
import com.example.data.local.AppSettingsEntity
import com.example.receiver.PrayerAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

object AlarmScheduler {

    const val ACTION_ADHAN = "com.example.ACTION_ADHAN"
    const val ACTION_PRE_ALERT = "com.example.ACTION_PRE_ALERT"
    const val ACTION_SALAWAT = "com.example.ACTION_SALAWAT"
    const val ACTION_MESAHARATY = "com.example.ACTION_MESAHARATY"

    private const val REQUEST_CODE_SALAWAT = 9999
    private const val REQUEST_CODE_MESAHARATY = 9998

    fun scheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = PrayerApplication.instance.database
            val settings = db.settingsDao().getSettingsDirect() ?: AppSettingsEntity()
            val alerts = db.alertDao().getEnabledAlerts()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch

            val now = System.currentTimeMillis()

            // 1. Calculate today's and tomorrow's prayers
            val todaySchedule = PrayerTimesCalculator.calculateTimes(Date(), settings)
            val calTomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val tomorrowSchedule = PrayerTimesCalculator.calculateTimes(calTomorrow.time, settings)

            val prayers = listOf(
                todaySchedule.fajr,
                todaySchedule.dhuhr,
                todaySchedule.asr,
                todaySchedule.maghrib,
                todaySchedule.isha,
                tomorrowSchedule.fajr,
                tomorrowSchedule.dhuhr,
                tomorrowSchedule.asr,
                tomorrowSchedule.maghrib,
                tomorrowSchedule.isha
            )

            // 2. Schedule Adhans
            for (prayer in prayers) {
                if (prayer.timestamp > now) {
                    val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                        action = ACTION_ADHAN
                        putExtra("EXTRA_PRAYER_ID", prayer.id)
                        putExtra("EXTRA_PRAYER_NAME", prayer.arabicName)
                    }
                    val pi = PendingIntent.getBroadcast(
                        context,
                        (prayer.id + prayer.timestamp).hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    setExactAlarm(alarmManager, prayer.timestamp, pi)
                }
            }

            // 3. Schedule Pre-Adhan User Alerts
            val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            for (alert in alerts) {
                val repeatDaysList = alert.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                val isTodayActive = alert.repeatDays == "ALL" || repeatDaysList.contains(currentDayOfWeek)

                if (isTodayActive) {
                    for (prayer in listOf(todaySchedule.fajr, todaySchedule.dhuhr, todaySchedule.asr, todaySchedule.maghrib, todaySchedule.isha)) {
                        val matchesPrayer = alert.prayerTarget == "ALL" ||
                                alert.prayerTarget == prayer.id ||
                                (alert.prayerTarget == "JUMUAH" && todaySchedule.isFriday && prayer.id == "JUMUAH")

                        if (matchesPrayer) {
                            val alertTime = prayer.timestamp - (alert.minutesBefore * 60 * 1000L)
                            if (alertTime > now) {
                                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                                    action = ACTION_PRE_ALERT
                                    putExtra("EXTRA_PRAYER_NAME", prayer.arabicName)
                                    putExtra("EXTRA_MINUTES_BEFORE", alert.minutesBefore)
                                    putExtra("EXTRA_RINGTONE_URI", alert.ringtoneUri)
                                }
                                val pi = PendingIntent.getBroadcast(
                                    context,
                                    (alert.id * 1000 + prayer.id.hashCode()).hashCode(),
                                    intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                setExactAlarm(alarmManager, alertTime, pi)
                            }
                        }
                    }
                }
            }

            // 4. Schedule Salawat
            if (settings.salawatEnabled) {
                scheduleSalawat(context, settings)
            }
        }
    }

    fun scheduleSalawat(context: Context, settings: AppSettingsEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intervalMillis = settings.salawatIntervalMinutes * 60 * 1000L
        val nextTime = System.currentTimeMillis() + intervalMillis

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_SALAWAT
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SALAWAT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExactAlarm(alarmManager, nextTime, pi)

        // Save timestamp for UI countdown
        CoroutineScope(Dispatchers.IO).launch {
            PrayerApplication.instance.database.settingsDao().insertOrUpdate(
                settings.copy(nextSalawatTimestamp = nextTime)
            )
        }
    }

    private fun setExactAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, operation: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            // Fallback to normal alarm if exact alarm permission restricted
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }
}
