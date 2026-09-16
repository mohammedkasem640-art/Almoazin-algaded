package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    // Language
    val language: String = "ar", // "ar", "en", "fr"
    
    // Location & Calculation
    val locationMode: String = "MANUAL", // "MANUAL", "AUTO"
    val cityName: String = "مكة المكرمة",
    val latitude: Double = 21.4225,
    val longitude: Double = 39.8262,
    val timezoneId: String = "Asia/Riyadh",
    val dstMode: Int = 0, // 0=off, 1=on, -1=auto
    val calcMethod: String = "UMM_AL_QURA",
    val asrMadhab: String = "SHAFI", // "SHAFI", "MALIKI", "HANBALI", "HANAFI"
    
    // Manual Prayer Time Adjustments (Minutes)
    val adjFajr: Int = 0,
    val adjSunrise: Int = 0,
    val adjDhuhr: Int = 0,
    val adjAsr: Int = 0,
    val adjMaghrib: Int = 0,
    val adjIsha: Int = 0,
    
    // Formatting & Calendar
    val timeFormat24: Boolean = false,
    val hijriAdjustmentDays: Int = 0,
    
    // Adhan Audio per prayer (URI string or null for built-in)
    val adhanAudioFajr: String? = null,
    val adhanAudioDhuhr: String? = null,
    val adhanAudioAsr: String? = null,
    val adhanAudioMaghrib: String? = null,
    val adhanAudioIsha: String? = null,
    val adhanAudioJumuah: String? = null,
    
    // Pre-Adhan Time Announcement / Chime per prayer
    val preAdhanSoundFajr: String? = null,
    val preAdhanSoundDhuhr: String? = null,
    val preAdhanSoundAsr: String? = null,
    val preAdhanSoundMaghrib: String? = null,
    val preAdhanSoundIsha: String? = null,
    val preAdhanSoundJumuah: String? = null,
    
    // Du'a Video after Adhan per prayer
    val duaVideoFajr: String? = null,
    val duaVideoDhuhr: String? = null,
    val duaVideoAsr: String? = null,
    val duaVideoMaghrib: String? = null,
    val duaVideoIsha: String? = null,
    val duaVideoJumuah: String? = null,
    
    // Adhan Screen (Slideshow/Static from ZIP)
    val adhanScreenImagesDir: String? = null,
    val adhanScreenMode: String = "SLIDESHOW", // "SLIDESHOW" or "STATIC"
    val adhanSlideDurationSec: Int = 5, // 3, 5, 10, 15
    
    // Ramadan Tab
    val ramadanCannonVideoUri: String? = null,
    val mesaharatyVideoUri: String? = null,
    val mesaharatyMode: String = "BEFORE_FAJR", // "FIXED_TIME", "BEFORE_FAJR"
    val mesaharatyFixedTime: String = "02:30",
    val mesaharatyBeforeFajrMinutes: Int = 45,
    
    // Salawat upon the Prophet ﷺ Tab
    val salawatAudioDir: String? = null,
    val salawatIntervalMinutes: Int = 15,
    val salawatSelectionMode: String = "ORDER", // "ORDER", "RANDOM", "SPECIFIC"
    val salawatSpecificSoundIndex: Int = 0,
    val salawatLastPlayedIndex: Int = 0,
    val salawatEnabled: Boolean = true,
    val nextSalawatTimestamp: Long = 0L,

    // Notification Bar & Widget Settings
    val notificationBarEnabled: Boolean = true,
    val notificationBarShowSeconds: Boolean = true,
    val notificationBarShowSalawat: Boolean = true,
    val widgetShowSeconds: Boolean = true,
    val widgetThemeStyle: String = "EMERALD", // "EMERALD", "BURGUNDY", "CLASSIC"

    // Post-Adhan Dua Video settings
    val autoPlayDuaAfterAdhan: Boolean = true,
    val duaVideoFillScreen: Boolean = true,

    // App Theme (Light by default)
    val isDarkMode: Boolean = false,

    // User requested toggles
    val preAdhanAlertsEnabled: Boolean = true,
    val timeAlertsEnabled: Boolean = true,
    val adhanSoundEnabled: Boolean = true,
    val ramadanCannonEnabled: Boolean = true,
    val mesaharatyEnabled: Boolean = true
)
