package com.example.ui.adhan

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.PrayerApplication
import com.example.data.local.AppSettingsEntity
import com.example.service.AdhanAudioService
import com.example.ui.theme.DigitalAmberLed
import com.example.ui.theme.DigitalGreenLed
import com.example.ui.theme.IslamicGold
import com.example.ui.theme.MosqueEmeraldDark
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AudioPlayerHelper
import com.example.util.NotificationHelper
import com.example.util.ZipExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AdhanScreenActivity : ComponentActivity() {

    private fun closeAndTurnOffScreen() {
        try {
            AdhanAudioService.stop(this)
            AudioPlayerHelper.stopAudio()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(false)
                setTurnScreenOn(false)
            }
            val lp = window.attributes
            lp.screenBrightness = 0.001f
            window.attributes = lp
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure activity displays specifically over lock screen and turns screen on without launching the main app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val rawPrayerId = intent.getStringExtra("EXTRA_PRAYER_ID") ?: "FAJR"
        val rawPrayerName = intent.getStringExtra("EXTRA_PRAYER_NAME") ?: "الصلاة"
        val isAlert = intent.getBooleanExtra("EXTRA_IS_ALERT", false)
        val minutesRemaining = intent.getIntExtra("EXTRA_MINUTES_REMAINING", 15)
        val directVideoUri = intent.getStringExtra("EXTRA_VIDEO_URI")

        val formattedPrayerName = NotificationHelper.getFullPrayerName(rawPrayerName)

        // If pre-alert, listen for audio finish to immediately shut off screen and finish
        if (isAlert) {
            AudioPlayerHelper.onAlertCompletedListener = {
                runOnUiThread {
                    closeAndTurnOffScreen()
                }
            }
        } else {
            // When Adhan finishes, transition automatically to horizontal fullscreen Dua video if enabled
            AudioPlayerHelper.onAlertCompletedListener = {
                lifecycleScope.launch(Dispatchers.Main) {
                    val s = withContext(Dispatchers.IO) {
                        PrayerApplication.instance.database.settingsDao().getSettingsDirect()
                    }
                    if (s?.autoPlayDuaAfterAdhan == true) {
                        val videoUri = when (rawPrayerId) {
                            "FAJR" -> s.duaVideoFajr
                            "DHUHR" -> s.duaVideoDhuhr
                            "ASR" -> s.duaVideoAsr
                            "MAGHRIB" -> s.duaVideoMaghrib
                            "ISHA" -> s.duaVideoIsha
                            "JUMUAH" -> s.duaVideoJumuah
                            else -> null
                        }
                        val intent = Intent(this@AdhanScreenActivity, DuaVideoActivity::class.java).apply {
                            putExtra("EXTRA_PRAYER_ID", rawPrayerId)
                            putExtra("EXTRA_PRAYER_NAME", formattedPrayerName)
                            putExtra("EXTRA_VIDEO_URI", videoUri)
                        }
                        startActivity(intent)
                        finishAndRemoveTask()
                    }
                }
            }
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                AdhanScreenContent(
                    prayerId = rawPrayerId,
                    prayerName = formattedPrayerName,
                    isAlert = isAlert,
                    minutesRemaining = minutesRemaining,
                    directVideoUri = directVideoUri,
                    onDismiss = {
                        closeAndTurnOffScreen()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioPlayerHelper.onAlertCompletedListener = null
        AudioPlayerHelper.stopAudio()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdhanScreenContent(
    prayerId: String,
    prayerName: String,
    isAlert: Boolean,
    minutesRemaining: Int,
    directVideoUri: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AppSettingsEntity()) }

    LaunchedEffect(Unit) {
        val s = PrayerApplication.instance.database.settingsDao().getSettingsDirect()
        if (s != null) settings = s
    }

    val images = remember(settings.adhanScreenImagesDir) {
        ZipExtractor.getExtractedFiles(context, "adhan_screen_images")
    }

    var currentImageIndex by remember { mutableStateOf(0) }

    // Slideshow timer
    LaunchedEffect(images, settings.adhanScreenMode, settings.adhanSlideDurationSec) {
        if (images.isNotEmpty() && settings.adhanScreenMode == "SLIDESHOW") {
            val durationMs = settings.adhanSlideDurationSec.coerceAtLeast(2) * 1000L
            while (true) {
                delay(durationMs)
                currentImageIndex = (currentImageIndex + 1) % images.size
            }
        }
    }

    // Auto-close alert screen immediately as soon as alert sound finishes
    if (isAlert) {
        LaunchedEffect(Unit) {
            // Give audio engine a brief moment (up to 400ms) to start if it's currently launching
            var waitStartCount = 0
            while (!AudioPlayerHelper.isPlaying() && waitStartCount < 8) {
                delay(50)
                waitStartCount++
            }

            // If sound is playing, poll continuously every 60ms until sound finishes
            if (AudioPlayerHelper.isPlaying()) {
                val startPlayTime = System.currentTimeMillis()
                while (AudioPlayerHelper.isPlaying() && (System.currentTimeMillis() - startPlayTime < 30000)) {
                    delay(60)
                }
                // Sound finished right now! Close immediately!
                onDismiss()
            } else {
                // If no sound is playing (e.g. silent mode or sound couldn't play), display for 3.5s then close
                delay(3500)
                onDismiss()
            }
        }
    }

    // Video to play (if any specified for dua or ramadan)
    val videoUriToPlay = remember(directVideoUri, prayerId, settings) {
        if (!directVideoUri.isNullOrBlank()) directVideoUri
        else when (prayerId) {
            "FAJR" -> settings.duaVideoFajr
            "DHUHR" -> settings.duaVideoDhuhr
            "ASR" -> settings.duaVideoAsr
            "MAGHRIB" -> settings.duaVideoMaghrib
            "ISHA" -> settings.duaVideoIsha
            "JUMUAH" -> settings.duaVideoJumuah
            else -> null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Background (Video, ZIP image slideshow, or Deep Emerald Mosque aesthetic)
        if (!videoUriToPlay.isNullOrBlank()) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val uri = if (videoUriToPlay.startsWith("content://") || videoUriToPlay.startsWith("file://")) {
                            Uri.parse(videoUriToPlay)
                        } else {
                            Uri.fromFile(File(videoUriToPlay))
                        }
                        setVideoURI(uri)
                        setMediaController(MediaController(ctx))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (images.isNotEmpty()) {
            val activeImage = images[currentImageIndex.coerceIn(0, images.size - 1)]
            AnimatedContent(
                targetState = activeImage,
                transitionSpec = { fadeIn(tween(600)) with fadeOut(tween(600)) },
                label = "adhan_image_transition",
                modifier = Modifier.fillMaxSize()
            ) { imgFile ->
                AsyncImage(
                    model = imgFile,
                    contentDescription = "Adhan image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF011A13),
                                Color(0xFF042B20),
                                Color(0xFF011A13)
                            )
                        )
                    )
            )
        }

        // Overlay scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        // 2. Central Content Display
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, IslamicGold),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isAlert) Icons.Default.NotificationsActive else Icons.Default.NightsStay,
                            contentDescription = null,
                            tint = IslamicGold,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Text(
                    text = if (isAlert) "تنبيه اقتراب الصلاة" else "حان الآن الأذان",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IslamicGold
                )
            }

            // Core Message Banner (Specified by user requirement)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, if (isAlert) DigitalAmberLed else IslamicGold, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isAlert) {
                        // User-requested exact pattern: "يتبقى + عدد الدقائق علي اذان + اسم الصلاة القادمة"
                        Text(
                            text = "يتبقى $minutesRemaining دقيقة على أذان $prayerName",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = DigitalAmberLed,
                            textAlign = TextAlign.Center,
                            lineHeight = 38.sp
                        )

                        Text(
                            text = "استعد للوضوء والصلاة وأداء الفريضة في وقتها",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "⚡ يغلق تلقائياً فور انتهاء التنبيه لحفظ طاقة الهاتف",
                            fontSize = 13.sp,
                            color = DigitalGreenLed,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Full Adhan Display
                        Text(
                            text = "أذان $prayerName",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = IslamicGold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "الله أكبر • الله أكبر\nحي على الصلاة • حي على الفلاح",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp
                        )
                    }
                }
            }

            // Bottom Actions: Dismiss / Close without opening the main app
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isAlert) {
                    Button(
                        onClick = {
                            AudioPlayerHelper.stopAudio()
                            val intent = Intent(context, DuaVideoActivity::class.java).apply {
                                putExtra("EXTRA_PRAYER_ID", prayerId)
                                putExtra("EXTRA_PRAYER_NAME", prayerName)
                                putExtra("EXTRA_VIDEO_URI", videoUriToPlay)
                            }
                            context.startActivity(intent)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IslamicGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("btn_play_dua_video_from_adhan")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "فيديو دعاء بعد الأذان (أفقي ملء الشاشة)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAlert) IslamicGold else Color(0xFFD32F2F),
                        contentColor = if (isAlert) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_dismiss_screen")
                ) {
                    Icon(
                        imageVector = if (isAlert) Icons.Default.Close else Icons.Default.VolumeOff,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isAlert) "إغلاق التنبيه وقفل الشاشة" else "إيقاف الأذان والإغلاق",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
