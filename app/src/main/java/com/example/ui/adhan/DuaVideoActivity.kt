package com.example.ui.adhan

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.PrayerApplication
import com.example.data.local.AppSettingsEntity
import com.example.ui.theme.DigitalGreenLed
import com.example.ui.theme.IslamicGold
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AudioPlayerHelper
import kotlinx.coroutines.delay
import java.io.File

class DuaVideoActivity : ComponentActivity() {

    private fun enableImmersiveLandscape() {
        // Enforce Landscape Orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Show when locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Cutout support for edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Hide system bars completely (Immersive Sticky Fullscreen)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveLandscape()

        val rawPrayerId = intent.getStringExtra("EXTRA_PRAYER_ID") ?: "FAJR"
        val prayerName = intent.getStringExtra("EXTRA_PRAYER_NAME") ?: "الصلاة"
        val explicitVideoUri = intent.getStringExtra("EXTRA_VIDEO_URI")

        setContent {
            MyApplicationTheme(darkTheme = true) {
                DuaVideoScreen(
                    prayerId = rawPrayerId,
                    prayerName = prayerName,
                    explicitVideoUri = explicitVideoUri,
                    onClose = {
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveLandscape()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioPlayerHelper.stopAudio()
    }
}

@Composable
fun DuaVideoScreen(
    prayerId: String,
    prayerName: String,
    explicitVideoUri: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AppSettingsEntity()) }

    LaunchedEffect(Unit) {
        val s = PrayerApplication.instance.database.settingsDao().getSettingsDirect()
        if (s != null) settings = s
    }

    // Determine video URI (per prayer or direct)
    val videoUri = remember(explicitVideoUri, prayerId, settings) {
        if (!explicitVideoUri.isNullOrBlank()) explicitVideoUri
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

    // Toggle for fill screen (100% stretched to edge-to-edge without black bars)
    var isFillScreen by remember { mutableStateOf(settings.duaVideoFillScreen) }
    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var videoEnded by remember { mutableStateOf(false) }

    // Reference to video view for controls
    var fullScreenVideoView by remember { mutableStateOf<FullScreenVideoView?>(null) }

    // Auto-hide controls overlay after 3.5 seconds
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying && !videoEnded) {
            delay(3500L)
            isControlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isControlsVisible = !isControlsVisible
            }
    ) {
        // -------------------------------------------------------------
        // 1. VIDEO LAYER: Custom Video OR Built-in Landscape Cinematic Dua
        // -------------------------------------------------------------
        if (!videoUri.isNullOrBlank()) {
            // Real Video Playback in Fullscreen Landscape
            AndroidView(
                factory = { ctx ->
                    FullScreenVideoView(ctx).apply {
                        this.fillScreen = isFillScreen
                        fullScreenVideoView = this
                        val uri = if (videoUri.startsWith("content://") || videoUri.startsWith("file://")) {
                            Uri.parse(videoUri)
                        } else {
                            Uri.fromFile(File(videoUri))
                        }
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            videoEnded = true
                            isControlsVisible = true
                        }
                        setOnErrorListener { _, _, _ ->
                            isPlaying = false
                            videoEnded = true
                            isControlsVisible = true
                            true
                        }
                    }
                },
                update = { view ->
                    view.fillScreen = isFillScreen
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Built-in Landscape Cinematic Dua Presentation (Fills entire screen horizontally)
            BuiltInCinematicDuaView(
                prayerName = prayerName,
                isPlaying = isPlaying,
                onComplete = {
                    videoEnded = true
                    isControlsVisible = true
                }
            )
        }

        // -------------------------------------------------------------
        // 2. CONTROLS OVERLAY: Animated Top & Bottom Bars
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = IslamicGold.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IslamicGold),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = IslamicGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "دعاء ما بعد الأذان • $prayerName",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (!videoUri.isNullOrBlank()) "فيديو مخصص (أفقي ملء الشاشة)" else "فيديو دعاء الوسيلة (مدمج أفقي)",
                                color = IslamicGold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Top Action Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle Fill Screen mode (Full Edge-to-Edge vs Fit Aspect Ratio)
                        if (!videoUri.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    isFillScreen = !isFillScreen
                                    fullScreenVideoView?.fillScreen = isFillScreen
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFillScreen) IslamicGold else Color(0xFF233549),
                                    contentColor = if (isFillScreen) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFillScreen) Icons.Default.Fullscreen else Icons.Default.FitScreen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isFillScreen) "ملء الشاشة بالكامل" else "أبعاد الفيديو الأصلية",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Close button (X)
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .testTag("btn_close_dua_video")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Center Play/Replay / Finished Banner
                if (videoEnded) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .wrapContentSize(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, IslamicGold)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "تقبل الله صلاتكم ودعاءكم 🤲",
                                color = IslamicGold,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "اللهم رب هذه الدعوة التامة والصلاة القائمة آت محمداً الوسيلة والفضيلة",
                                color = Color.White,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        videoEnded = false
                                        isPlaying = true
                                        fullScreenVideoView?.seekTo(0)
                                        fullScreenVideoView?.start()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = IslamicGold, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("إعادة التشغيل", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onClose,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("إغلاق الشاشة")
                                }
                            }
                        }
                    }
                }

                // Bottom Control Bar (Play, Pause, Replay, Dismiss)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause / Replay toggle
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (fullScreenVideoView != null) {
                                    if (isPlaying) {
                                        fullScreenVideoView?.pause()
                                        isPlaying = false
                                    } else {
                                        fullScreenVideoView?.start()
                                        isPlaying = true
                                    }
                                } else {
                                    isPlaying = !isPlaying
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(IslamicGold)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                videoEnded = false
                                isPlaying = true
                                fullScreenVideoView?.seekTo(0)
                                fullScreenVideoView?.start()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "إعادة من البداية",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = if (isFillScreen) "وضع ملء الشاشة الأفقي: مفعل (Edge-to-Edge)" else "العرض القياسي للأبعاد",
                            color = Color(0xFFA0B2C6),
                            fontSize = 11.sp
                        )
                    }

                    // Close / Return button
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تم والدخول للتطبيق", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * High-definition cinematic horizontal Dua presentation when no custom video is uploaded.
 * Fills the entire screen in landscape with deep emerald mosque background,
 * golden Arabic calligraphy, and animated progression.
 */
@Composable
private fun BuiltInCinematicDuaView(
    prayerName: String,
    isPlaying: Boolean,
    onComplete: () -> Unit
) {
    // 25-second animated recitation timer
    var progressSeconds by remember { mutableIntStateOf(0) }
    val totalSeconds = 22

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            AudioPlayerHelper.playTimeChime()
            while (progressSeconds < totalSeconds) {
                delay(1000L)
                progressSeconds++
            }
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF063327),
                        Color(0xFF031E17),
                        Color(0xFF010E0B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative geometric border frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .border(1.5.dp, IslamicGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(6.dp)
                .border(0.5.dp, IslamicGold.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        )

        // Main Horizontal Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Column: Crescent Emblem & Prayer Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(180.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, IslamicGold),
                    modifier = Modifier.size(70.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NightsStay,
                            contentDescription = null,
                            tint = IslamicGold,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Text(
                    text = "دعاء ما بعد الأذان",
                    color = IslamicGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "أذان $prayerName",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(4.dp))

                // Time remaining pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0B4633))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "00:${(totalSeconds - progressSeconds).coerceAtLeast(0).toString().padStart(2, '0')}",
                        color = DigitalGreenLed,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right Column: Duaa Al-Wasilah in Prominent Arabic Typography
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "« اللَّهُمَّ رَبَّ هَذِهِ الدَّعْوَةِ التَّامَّةِ ، وَالصَّلَاةِ الْقَائِمَةِ »",
                    color = IslamicGold,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "« آتِ مُحَمَّدًا الْوَسِيلَةَ وَالْفَضِيلَةَ ، وَابْعَثْهُ مَقَامًا مَحْمُودًا الَّذِي وَعَدْتَهُ »",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )

                Text(
                    text = "« حَلَّتْ لَهُ شَفَاعَتِي يَوْمَ الْقِيَامَةِ » — صحيح البخاري",
                    color = Color(0xFFA0B2C6),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progressSeconds.toFloat() / totalSeconds.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = IslamicGold,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}
