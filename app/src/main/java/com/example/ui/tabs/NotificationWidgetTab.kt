package com.example.ui.tabs

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettingsEntity
import com.example.receiver.PrayerTimesWidgetProvider
import com.example.receiver.WidgetActionReceiver
import com.example.ui.PrayerViewModel
import com.example.util.AppStrings
import com.example.util.HijriCalendarHelper
import com.example.util.PrayerTimesCalculator
import com.example.util.PrayerWidgetHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun NotificationWidgetTab(
    viewModel: PrayerViewModel,
    settings: AppSettingsEntity,
    lang: String
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Live ticking timer for preview (ticks every second!)
    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val cal = remember(currentMillis / 60000) { Calendar.getInstance() }
    val schedule = remember(settings, cal) {
        PrayerTimesCalculator.calculateTimes(cal.time, settings)
    }
    val (currentPrayer, nextPrayer) = remember(schedule, currentMillis) {
        PrayerWidgetHelper.getCurrentAndNextPrayer(schedule, currentMillis)
    }

    // Remaining time to next prayer
    val remainingToNextMs = (nextPrayer.timestamp - currentMillis).coerceAtLeast(0L)
    val nextCountdownFormatted = remember(remainingToNextMs, settings.notificationBarShowSeconds) {
        PrayerWidgetHelper.formatDuration(remainingToNextMs, settings.notificationBarShowSeconds)
    }

    // Remaining time to next Salawat
    val salawatRemainingMs = (settings.nextSalawatTimestamp - currentMillis).coerceAtLeast(0L)
    val salawatCountdownFormatted = remember(salawatRemainingMs) {
        PrayerWidgetHelper.formatSalawatTimer(salawatRemainingMs)
    }

    // Hijri date
    val hijriDate = remember(settings.hijriAdjustmentDays, cal) {
        HijriCalendarHelper.getHijriDate(cal.time, settings.hijriAdjustmentDays)
    }
    val hijriString = remember(hijriDate, cal) {
        val dayOfWeekAr = SimpleDateFormat("EEEE", Locale("ar")).format(cal.time)
        "$dayOfWeekAr ${hijriDate.formattedAr}"
    }
    val gregorianString = remember(cal) {
        SimpleDateFormat("yyyy-M-d", Locale.ENGLISH).format(cal.time)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tab Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Column {
                Text(
                    text = AppStrings.get("tab_notification_widget", lang),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.get("notif_widget_desc", lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // -------------------------------------------------------------
        // SECTION 1: Exact Prayer Times Widget & Notification Bar Card
        // -------------------------------------------------------------
        Text(
            text = "معاينة شريط الإشعارات والتطبيق المصغر (Live Preview)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1723)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B324D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Header: Right "🕌 مواقيت الصلاة", Left City
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = settings.cityName.ifBlank { "مكة المكرمة" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "مواقيت الصلاة 🕌",
                        color = Color(0xFFE5A93C),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Cyan Hero Banner: Next prayer & ticking countdown
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF00A2E8), Color(0xFF0084C2))
                            )
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "يتبقى $nextCountdownFormatted على",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = nextPrayer.arabicName,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // 5 Prayer Times Columns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val prayers = listOf(
                        schedule.fajr,
                        schedule.dhuhr,
                        schedule.asr,
                        schedule.maghrib,
                        schedule.isha
                    )

                    prayers.forEach { prayer ->
                        val isCurrent = prayer.id == currentPrayer.id
                        val isNext = prayer.id == nextPrayer.id

                        val colBg = if (isCurrent) Color.White else Color.Transparent
                        val textColor = if (isCurrent) Color(0xFF0077B6) else Color.White
                        val timeColor = if (isCurrent) Color(0xFF0077B6) else Color(0xFFA0B2C6)
                        val badgeText = if (isCurrent) "الحالية" else if (isNext) "القادمة" else ""
                        val badgeBg = if (isCurrent) Color(0xFFE1F5FE) else if (isNext) Color(0xFF0288D1) else Color.Transparent
                        val badgeTextColor = if (isCurrent) Color(0xFF0077B6) else Color.White

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colBg)
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (badgeText.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = badgeTextColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(13.dp))
                            }

                            Text(
                                text = prayer.arabicName,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent || isNext) FontWeight.Bold else FontWeight.Normal
                            )

                            Text(
                                text = PrayerWidgetHelper.formatClockTime(
                                    prayer.hour24,
                                    prayer.minute,
                                    settings.timeFormat24
                                ),
                                color = timeColor,
                                fontSize = 10.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Hijri & Gregorian Date Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = gregorianString,
                        color = Color(0xFFA0B2C6),
                        fontSize = 11.sp
                    )
                    Text(
                        text = hijriString,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Salawat Bottom Pill
                if (settings.salawatEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0B3326))
                            .border(1.dp, Color(0xFF13573F), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                viewModel.playSalawatNow()
                                Toast.makeText(context, "ﷺ اللهم صل وسلم على نبينا محمد ﷺ", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE5A93C),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "صلّ الآن ﷺ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "الصلاة على النبي ﷺ: يتبقى $salawatCountdownFormatted",
                            color = Color(0xFF4EBA86),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 2: Android System Notification Shade Preview
        // -------------------------------------------------------------
        Text(
            text = "معاينة إشعار الصلاة على النبي ﷺ الدائم (Notification Shade)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1218)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1C2733)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Notification Shade Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Prayer Time & Adhan • تذكير مستمر • $salawatCountdownFormatted",
                        color = Color(0xFFA0B2C6),
                        fontSize = 11.sp
                    )
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color(0xFFE5A93C),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Dark Green Forest Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D3B2E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Title row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "الصلاة على النبي ﷺ",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "تذكير دائم",
                                color = Color(0xFFE5A93C),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Inner cyan-bordered card with Digital LED countdown
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0A281E))
                                .border(1.dp, Color(0xFF1DE9B6), RoundedCornerShape(10.dp))
                                .padding(vertical = 10.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "الوقت المتبقي للتذكير القادم:",
                                    color = Color(0xFFA0B2C6),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = salawatCountdownFormatted,
                                    color = Color(0xFF00E5FF),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "الموعد القادم: " + remember(settings.nextSalawatTimestamp) {
                                        val c = Calendar.getInstance().apply { timeInMillis = settings.nextSalawatTimestamp }
                                        PrayerWidgetHelper.formatClockTime(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false)
                                    },
                                    color = Color(0xFFA0B2C6),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Bottom action row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    viewModel.playSalawatNow()
                                    Toast.makeText(context, "ﷺ اللهم صل وسلم على نبينا محمد ﷺ", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE5A93C),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = "صلّ الآن ﷺ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "الصوت: بالترتيب (صوت مختلف في كل مرة)",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 3: Settings & Configuration Controls
        // -------------------------------------------------------------
        Text(
            text = "إعدادات شريط الإشعارات والتطبيقات المصغرة",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Switch 1: Ongoing Notification
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "شريط الإشعارات المستمر (Ongoing Notification)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "إبقاء بطاقة مواقيت الصلاة والعد التنازلي ظاهرة باستمرار في ستارة إشعارات الهاتف",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.notificationBarEnabled,
                        onCheckedChange = { viewModel.setNotificationBarEnabled(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Switch 2: Show Seconds in Countdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تشغيل الثواني والعد التنازلي المباشر",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تحديث العداد لحظياً بالثواني التنازلية الدقيقة (HH:mm:ss)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.notificationBarShowSeconds,
                        onCheckedChange = { viewModel.setNotificationBarShowSeconds(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Switch 3: Show Salawat Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "إظهار شريط الصلاة على النبي ﷺ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "إظهار عداد الصلاة على النبي ﷺ مع زر (صلّ الآن) في البطاقة والإشعار",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.notificationBarShowSalawat,
                        onCheckedChange = { viewModel.setNotificationBarShowSalawat(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Button: Pin Widget to Home Screen
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                            val myProvider = ComponentName(context, PrayerTimesWidgetProvider::class.java)
                            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                val callbackIntent = Intent(context, WidgetActionReceiver::class.java)
                                val callbackPendingIntent = PendingIntent.getBroadcast(
                                    context,
                                    0,
                                    callbackIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                appWidgetManager.requestPinAppWidget(myProvider, null, callbackPendingIntent)
                                Toast.makeText(context, "تم إرسال طلب إضافة الودجت لشاشتك الرئيسية", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "يمكنك إضافة الودجت بالضغط مطولاً على الشاشة الرئيسية ثم اختيار الودجت", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "اضغط مطولاً على الشاشة الرئيسية لإضافة ودجت مواقيت الصلاة", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.AddHome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "إضافة التطبيق المصغر (Widget) للشاشة الرئيسية", fontWeight = FontWeight.Bold)
                }

                // Button: Update Notification & Widget Now
                OutlinedButton(
                    onClick = {
                        viewModel.refreshNotificationAndWidgets()
                        Toast.makeText(context, "تم تحديث شريط الإشعارات والودجت بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "تحديث شريط الإشعارات فوراً", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
