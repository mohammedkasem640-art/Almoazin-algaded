package com.example.util

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.PrayerApplication
import com.example.data.local.AppSettingsEntity
import com.example.ui.adhan.AdhanScreenActivity
import com.example.ui.adhan.DuaVideoActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NotificationHelper {

    const val CHANNEL_ADHAN = "channel_prayer_adhan_high"
    const val CHANNEL_ALERTS = "channel_prayer_alerts"
    const val CHANNEL_SALAWAT = "channel_salawat"
    const val CHANNEL_SERVICE = "channel_foreground_service"
    const val CHANNEL_ONGOING = "channel_ongoing_prayer_widget"

    const val NOTIFICATION_ID_ADHAN = 1001
    const val NOTIFICATION_ID_ALERT = 1002
    const val NOTIFICATION_ID_SALAWAT = 1003
    const val NOTIFICATION_ID_SERVICE = 1004
    const val NOTIFICATION_ID_ONGOING = 1005

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val adhanChannel = NotificationChannel(
                CHANNEL_ADHAN,
                "أذان ومواقيت الصلاة (Adhan)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات صوت الأذان وشاشة الأذان على شاشة القفل"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                enableVibration(true)
                setBypassDnd(true)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "تنبيهات قبل الأذان (Prayer Alerts)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "التنبيهات المسبقة قبل مواعيد الأذان"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                enableVibration(true)
            }

            val salawatChannel = NotificationChannel(
                CHANNEL_SALAWAT,
                "التذكير بالصلاة على النبي ﷺ",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تذكيرات دورية بالصلاة على النبي ﷺ طوال اليوم"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "خدمة تشغيل الأذان في الخلفية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "تشغيل الصوت في الخلفية بدقة"
            }

            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING,
                "شريط الإشعارات المستمر لمواقيت الصلاة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "عرض بطاقة مواقيت الصلاة والعد التنازلي في شريط الإشعارات"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            nm.createNotificationChannels(listOf(adhanChannel, alertChannel, salawatChannel, serviceChannel, ongoingChannel))
        }
    }

    fun isDeviceLocked(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true
        val isKeyguardLocked = km?.isKeyguardLocked ?: false
        return !isInteractive || isKeyguardLocked
    }

    fun getFullPrayerName(prayerIdOrName: String): String {
        return when (prayerIdOrName.trim().uppercase()) {
            "FAJR", "الفجر" -> "صلاة الفجر"
            "DHUHR", "الظهر" -> "صلاة الظهر"
            "ASR", "العصر" -> "صلاة العصر"
            "MAGHRIB", "المغرب" -> "صلاة المغرب"
            "ISHA", "العشاء" -> "صلاة العشاء"
            "JUMUAH", "الجمعة" -> "صلاة الجمعة"
            else -> if (prayerIdOrName.startsWith("صلاة")) prayerIdOrName else "صلاة $prayerIdOrName"
        }
    }

    fun showAdhanNotification(
        context: Context,
        prayerName: String,
        prayerId: String
    ) {
        val fullPrayerName = getFullPrayerName(prayerName)

        val screenIntent = Intent(context, AdhanScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PRAYER_ID", prayerId)
            putExtra("EXTRA_PRAYER_NAME", fullPrayerName)
            putExtra("EXTRA_IS_ALERT", false)
        }
        val screenPendingIntent = PendingIntent.getActivity(
            context,
            prayerId.hashCode(),
            screenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // If screen is locked, launch the lockscreen AdhanScreenActivity directly without opening the app
        if (isDeviceLocked(context)) {
            try {
                context.startActivity(screenIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val duaIntent = Intent(context, DuaVideoActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PRAYER_ID", prayerId)
            putExtra("EXTRA_PRAYER_NAME", fullPrayerName)
        }
        val duaPendingIntent = PendingIntent.getActivity(
            context,
            prayerId.hashCode() + 200,
            duaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ADHAN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان الآن أذان $fullPrayerName")
            .setContentText("حي على الصلاة • حي على الفلاح")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(screenPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "عرض الشاشة", screenPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "دعاء بعد الأذان (أفقي)", duaPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ADHAN, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showAlertNotification(
        context: Context,
        prayerName: String,
        minutesRemaining: Int,
        ringtoneUri: String? = null
    ) {
        val fullPrayerName = getFullPrayerName(prayerName)
        val alertText = "يتبقى $minutesRemaining دقيقة على أذان $fullPrayerName"

        val screenIntent = Intent(context, AdhanScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PRAYER_NAME", fullPrayerName)
            putExtra("EXTRA_MINUTES_REMAINING", minutesRemaining)
            putExtra("EXTRA_IS_ALERT", true)
            putExtra("EXTRA_RINGTONE_URI", ringtoneUri)
        }
        val screenPendingIntent = PendingIntent.getActivity(
            context,
            minutesRemaining.hashCode(),
            screenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // If device is locked, show on lock screen
        if (isDeviceLocked(context)) {
            try {
                context.startActivity(screenIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("تنبيه اقتراب الصلاة")
            .setContentText(alertText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(screenPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "عرض التنبيه", screenPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALERT, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showSalawatNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SALAWAT)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ﷺ صلّ على الحبيب المصطفى ﷺ")
            .setContentText("اللهم صل وسلم وبارك على نبينا محمد وعلى آله وصحبه أجمعين")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SALAWAT, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun updateOngoingPrayerNotification(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PrayerApplication.instance.database
                val settings = db.settingsDao().getSettingsDirect() ?: AppSettingsEntity()

                if (!settings.notificationBarEnabled) {
                    cancelOngoingNotification(context)
                    return@launch
                }

                val views = PrayerWidgetHelper.buildWidgetRemoteViews(context, settings)

                val intent = Intent(context, MainActivity::class.java)
                val contentPendingIntent = PendingIntent.getActivity(
                    context,
                    101,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val playIntent = Intent(context, com.example.receiver.WidgetActionReceiver::class.java).apply {
                    action = com.example.receiver.WidgetActionReceiver.ACTION_PLAY_SALAWAT
                }
                val playPendingIntent = PendingIntent.getBroadcast(
                    context,
                    202,
                    playIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ONGOING)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(views)
                    .setCustomBigContentView(views)
                    .setContentIntent(contentPendingIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .addAction(android.R.drawable.ic_media_play, "صلّ الآن ﷺ", playPendingIntent)
                    .build()

                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ONGOING, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cancelOngoingNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ONGOING)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
