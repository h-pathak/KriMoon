package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.MedicineReminder
import com.example.data.UserNote
import com.example.data.UserProfile
import com.example.model.CyclePhase
import com.example.model.CycleStatus
import java.util.Calendar

object NotificationHelper {

    const val CHANNEL_CYCLE = "phases_cycle_v4"
    const val CHANNEL_WATER = "phases_water_v4"
    const val CHANNEL_CHECKLIST = "phases_checklist_v4"
    const val CHANNEL_CHECKIN = "phases_checkin_v4"
    const val CHANNEL_MEDICINE = "phases_medicine_v4"
    const val CHANNEL_NOTES = "phases_notes_v4"

    const val ACTION_WATER_REMINDER = "com.example.ACTION_WATER_REMINDER"
    const val ACTION_CYCLE_REMINDER = "com.example.ACTION_CYCLE_REMINDER"
    const val ACTION_CHECKIN_REMINDER = "com.example.ACTION_CHECKIN_REMINDER"
    const val ACTION_ADD_WATER_QUICK = "com.example.ACTION_ADD_WATER_QUICK"
    const val ACTION_MEDICINE_REMINDER = "com.example.ACTION_MEDICINE_REMINDER"
    const val ACTION_TAKE_MEDICINE_QUICK = "com.example.ACTION_TAKE_MEDICINE_QUICK"
    const val ACTION_NOTE_REMINDER = "com.example.ACTION_NOTE_REMINDER"
    const val ACTION_NOTE_DAILY_REFLECTION = "com.example.ACTION_NOTE_DAILY_REFLECTION"
    const val ACTION_SNOOZE_NOTE_REMINDER = "com.example.ACTION_SNOOZE_NOTE_REMINDER"
    const val ACTION_DISMISS_NOTE_REMINDER = "com.example.ACTION_DISMISS_NOTE_REMINDER"

    const val EXTRA_TARGET_SCREEN = "extra_target_screen"
    const val EXTRA_MEDICINE_ID = "extra_medicine_id"
    const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
    const val EXTRA_MEDICINE_DOSAGE = "extra_medicine_dosage"
    const val EXTRA_MEDICINE_CUSTOM_SOUND = "extra_medicine_custom_sound"
    const val EXTRA_MEDICINE_CUSTOM_MESSAGE = "extra_medicine_custom_message"
    const val EXTRA_NOTE_ID = "extra_note_id"
    const val EXTRA_NOTE_CONTENT = "extra_note_content"
    const val EXTRA_NOTE_TAG = "extra_note_tag"
    const val EXTRA_NOTE_TIMESTAMP = "extra_note_timestamp"

    const val TARGET_CYCLE = "CYCLE"
    const val TARGET_PERIODS = "PERIODS"
    const val TARGET_SHOPPING = "SHOPPING"
    const val TARGET_WATER = "WATER"
    const val TARGET_NOTES = "NOTES"
    const val TARGET_MEDICATIONS = "MEDICATIONS"

    const val NOTIFICATION_ID_WATER = 1001
    const val NOTIFICATION_ID_CYCLE = 1002
    const val NOTIFICATION_ID_CHECKLIST = 1003
    const val NOTIFICATION_ID_CHECKIN = 1004
    const val NOTIFICATION_ID_MEDICINE_BASE = 2000
    const val NOTIFICATION_ID_NOTE_BASE = 5000
    const val NOTIFICATION_ID_DAILY_NOTE = 5999
    const val NOTIFICATION_ID_TEST = 1099

    enum class TimeOfDay(val greeting: String, val emoji: String, val titlePrefix: String) {
        MORNING("Good morning", "☀️", "Morning"),
        AFTERNOON("Good afternoon", "🌤️", "Afternoon"),
        EVENING("Good evening", "🌆", "Evening"),
        NIGHT("Good night", "🌙", "Nighttime")
    }

    /**
     * Determines the current time-of-day category for adaptive greetings.
     * - 05:00 - 11:59: Morning
     * - 12:00 - 16:59: Afternoon
     * - 17:00 - 20:59: Evening
     * - 21:00 - 04:59: Night
     */
    fun getTimeOfDay(calendar: Calendar = Calendar.getInstance()): TimeOfDay {
        return when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> TimeOfDay.MORNING
            in 12..16 -> TimeOfDay.AFTERNOON
            in 17..20 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    /**
     * Returns a time-dependent greeting string (e.g. "Good morning", "Good afternoon", "Good evening", "Good night").
     * Optionally appends the user's name if supplied.
     */
    fun getTimeGreeting(calendar: Calendar = Calendar.getInstance(), name: String? = null): String {
        val greeting = getTimeOfDay(calendar).greeting
        return if (!name.isNullOrBlank()) {
            "$greeting, $name"
        } else {
            greeting
        }
    }

    /**
     * Dynamically adapts any message that contains a hardcoded greeting (such as "Good morning" or "good morning")
     * to match the current actual time of day.
     */
    fun formatTimeDependentGreeting(
        rawMessage: String,
        calendar: Calendar = Calendar.getInstance(),
        userName: String? = null
    ): String {
        if (rawMessage.isBlank()) return rawMessage
        val timeOfDay = getTimeOfDay(calendar)
        val targetGreeting = timeOfDay.greeting

        // Matches variations: "Good morning", "good morning", "Good afternoon", "Good evening", "Good night"
        val greetingRegex = Regex("(?i)\\bGood\\s+(morning|afternoon|evening|night)\\b")

        return if (greetingRegex.containsMatchIn(rawMessage)) {
            greetingRegex.replace(rawMessage) { matchResult ->
                val original = matchResult.value
                if (original.first().isUpperCase()) {
                    targetGreeting
                } else {
                    targetGreeting.lowercase()
                }
            }
        } else {
            rawMessage
        }
    }

    fun getNotificationSoundUri(context: Context): Uri {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + R.raw.cute_chime
        )
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Remove legacy channels so Android rebuilds channels with sound
            val oldChannels = listOf(
                "phases_cycle_channel", "phases_cycle_channel_v2", "phases_cycle_channel_v3",
                "phases_water_channel", "phases_water_channel_v2", "phases_water_channel_v3",
                "phases_checklist_channel", "phases_checklist_channel_v2", "phases_checklist_channel_v3",
                "phases_checkin_channel", "phases_checkin_channel_v2", "phases_checkin_channel_v3",
                "phases_medicine_channel", "phases_medicine_channel_v2", "phases_medicine_channel_v3"
            )
            for (oldId in oldChannels) {
                try {
                    notificationManager.deleteNotificationChannel(oldId)
                } catch (_: Exception) {}
            }

            val soundUri = getNotificationSoundUri(context)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()

            // 1. Medicine Channel (High importance, distinct cute sound)
            val medicineChannel = NotificationChannel(
                CHANNEL_MEDICINE,
                "Medicine & Pill Reminders 💊",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily alerts for vitamins, pills, and wellness medications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                setSound(soundUri, audioAttributes)
            }

            // 2. Cycle Channel (High importance, distinct cute sound)
            val cycleChannel = NotificationChannel(
                CHANNEL_CYCLE,
                "Cycle & Period Care 🌸",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Predictions for period start, ovulation, and cycle milestones"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                setSound(soundUri, audioAttributes)
            }

            // 3. Water Channel (High importance so it pops with sound)
            val waterChannel = NotificationChannel(
                CHANNEL_WATER,
                "Hydration Nudges 💧",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gentle daytime reminders to drink water and stay glowing"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                setSound(soundUri, audioAttributes)
            }

            // 4. Checklist Channel (High importance so it pops with sound)
            val checklistChannel = NotificationChannel(
                CHANNEL_CHECKLIST,
                "Phase Essentials & To-Get 🛍️",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts to check necessary supplies for upcoming phases"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                setSound(soundUri, audioAttributes)
            }

            // 5. Check-in Channel (High importance so it pops with sound)
            val checkinChannel = NotificationChannel(
                CHANNEL_CHECKIN,
                "Evening Reflections & Mood 🌙",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily evening check-in to log mood, symptoms, and thoughts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                setSound(soundUri, audioAttributes)
            }

            // 6. Notes & Thoughts Channel (High importance with sweet chime)
            val notesChannel = NotificationChannel(
                CHANNEL_NOTES,
                "Notes & Thoughts Reminders 📝",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for what you have written in your private notes"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                setSound(soundUri, audioAttributes)
            }

            notificationManager.createNotificationChannels(
                listOf(medicineChannel, cycleChannel, waterChannel, checklistChannel, checkinChannel, notesChannel)
            )
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Show notification with navigation PendingIntent and distinct sound
     */
    fun showNotification(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        message: String,
        targetScreen: String,
        quickAction: NotificationCompat.Action? = null
    ) {
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, targetScreen)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = getNotificationSoundUri(context)

        val formattedTitle = formatTimeDependentGreeting(title)
        val formattedMessage = formatTimeDependentGreeting(message)

        val publicVersion = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("KriMoon · Private Reminder 🔒")
            .setContentText("Unlock device to view your private wellness update.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(formattedTitle)
            .setContentText(formattedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(formattedMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (quickAction != null) {
            builder.addAction(quickAction)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Shows a cute & fun test notification for a given channel to confirm working setup and sound
     */
    fun sendTestNotification(
        context: Context,
        type: String = "water",
        customSound: String = "cute_chime",
        customMessage: String = "",
        medicineName: String = "Daily Multivitamin / Iron",
        medicineDosage: String = "1 capsule"
    ) {
        // Play the distinctive alert sound at clear alarm level
        SoundHelper.playAlarmOrPreview(context, customSound)

        val timeOfDay = getTimeOfDay()

        when (type) {
            "medicine" -> {
                val takeMedIntent = Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_TAKE_MEDICINE_QUICK
                    putExtra(EXTRA_MEDICINE_ID, -1L)
                    putExtra(EXTRA_MEDICINE_NAME, medicineName)
                }
                val takeMedPending = PendingIntent.getBroadcast(
                    context,
                    992,
                    takeMedIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val quickAction = NotificationCompat.Action.Builder(
                    android.R.drawable.checkbox_on_background,
                    "Taken! ✅",
                    takeMedPending
                ).build()

                val bodyMessage = if (customMessage.isNotBlank()) {
                    formatTimeDependentGreeting(customMessage)
                } else {
                    "${timeOfDay.greeting}! Time for your $medicineName ($medicineDosage)! Have a refreshing sip of water with it 💧💖 Staying healthy looks gorgeous on you!"
                }

                showNotification(
                    context = context,
                    notificationId = NOTIFICATION_ID_MEDICINE_BASE,
                    channelId = CHANNEL_MEDICINE,
                    title = "${timeOfDay.titlePrefix} Pill Care ${timeOfDay.emoji}💊",
                    message = bodyMessage,
                    targetScreen = TARGET_MEDICATIONS,
                    quickAction = quickAction
                )
            }
            "water" -> {
                val addWaterIntent = Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_ADD_WATER_QUICK
                }
                val addWaterPending = PendingIntent.getBroadcast(
                    context,
                    991,
                    addWaterIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val quickAction = NotificationCompat.Action.Builder(
                    android.R.drawable.ic_input_add,
                    "+1 Glass 💧",
                    addWaterPending
                ).build()

                val waterMessage = when (timeOfDay) {
                    TimeOfDay.MORNING -> "Good morning, bestie! ☀️ Quick glowing sip break! Your cells are doing a happy dance inside 🫧✨ Time for a fresh glass of water!"
                    TimeOfDay.AFTERNOON -> "Good afternoon, bestie! 🌤️ Midday glowing sip break! Keep your energy up and radiant with a crisp glass of water! 🫧✨"
                    TimeOfDay.EVENING -> "Good evening, bestie! 🌆 Wind down and hydrate! A soothing sip of fresh water keeps you glowing bright! 🫧✨"
                    TimeOfDay.NIGHT -> "Good night, bestie! 🌙 A gentle sip of water before sweet dreams! Sleep peacefully and wake up revitalized! 🫧✨"
                }

                showNotification(
                    context = context,
                    notificationId = NOTIFICATION_ID_WATER,
                    channelId = CHANNEL_WATER,
                    title = "Hydrated Bestie Alert! 🧚‍♀️💧",
                    message = waterMessage,
                    targetScreen = TARGET_WATER,
                    quickAction = quickAction
                )
            }
            "cycle" -> {
                showNotification(
                    context = context,
                    notificationId = NOTIFICATION_ID_CYCLE,
                    channelId = CHANNEL_CYCLE,
                    title = "${timeOfDay.greeting}, Angel! 🌸🧸",
                    message = "Your period is predicted in ~2 days! Fuzzy blankets, chamomile tea & cozy comfort treats on standby! 🍫🧦💕",
                    targetScreen = TARGET_CYCLE
                )
            }
            "checklist" -> {
                showNotification(
                    context = context,
                    notificationId = NOTIFICATION_ID_CHECKLIST,
                    channelId = CHANNEL_CHECKLIST,
                    title = "Luteal Nesting Time 🛍️✨",
                    message = "PMS approaching: Check your 'To Get' checklist for magnesium, dark chocolate & cozy essentials! ☕🧸",
                    targetScreen = TARGET_SHOPPING
                )
            }
            "checkin" -> {
                val (checkinTitle, checkinMsg) = when (timeOfDay) {
                    TimeOfDay.MORNING, TimeOfDay.AFTERNOON -> {
                        "${timeOfDay.titlePrefix} Check-in ${timeOfDay.emoji}✨" to
                        "${timeOfDay.greeting}! Take a peaceful pause to check in with yourself in your private scratchpad 🌸"
                    }
                    TimeOfDay.EVENING -> {
                        "Evening Reflection 🌆✨" to
                        "Good evening! You did wonderful today! Take a deep calming breath and jot down your thoughts in your private scratchpad 🌸💤"
                    }
                    TimeOfDay.NIGHT -> {
                        "Starry Night Reflection 🌙✨" to
                        "Good night! You did wonderful today! Take a deep calming breath and jot down your thoughts before sleep 🌸💤"
                    }
                }

                showNotification(
                    context = context,
                    notificationId = NOTIFICATION_ID_CHECKIN,
                    channelId = CHANNEL_CHECKIN,
                    title = checkinTitle,
                    message = checkinMsg,
                    targetScreen = TARGET_NOTES
                )
            }
            "note" -> {
                val sampleContent = if (customMessage.isNotBlank()) {
                    customMessage
                } else {
                    "Remember to drink a warm cup of herbal tea, do your evening stretch, and take pride in today's gentle progress 🌸"
                }
                val sampleNote = UserNote(
                    id = 9999L,
                    content = sampleContent,
                    tag = "thought",
                    timestampMillis = System.currentTimeMillis()
                )
                showNoteReminderNotification(context, sampleNote)
            }
        }
    }

    /**
     * Schedule recurring alarms using AlarmManager
     */
    fun scheduleAllReminders(
        context: Context,
        profile: UserProfile?,
        cycleStatus: CycleStatus?,
        medicines: List<MedicineReminder> = emptyList()
    ) {
        if (profile == null) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Cancel previous alarms before re-scheduling
        cancelAllReminders(context)

        if (!hasNotificationPermission(context)) return

        // 1. Water Reminders (e.g., 10:30 AM, 2:30 PM, 6:00 PM)
        if (profile.waterRemindersEnabled) {
            scheduleDailyAlarm(context, alarmManager, 10, 30, 201, ACTION_WATER_REMINDER)
            scheduleDailyAlarm(context, alarmManager, 14, 30, 202, ACTION_WATER_REMINDER)
            scheduleDailyAlarm(context, alarmManager, 18, 0, 203, ACTION_WATER_REMINDER)
        }

        // 2. Cycle Alert (Runs every morning at 9:00 AM)
        if (profile.cycleRemindersEnabled || profile.fertileRemindersEnabled || profile.shopRemindersEnabled) {
            scheduleDailyAlarm(context, alarmManager, 9, 0, 301, ACTION_CYCLE_REMINDER)
        }

        // 3. Evening Check-In (e.g. at reminderHour or 8:30 PM)
        if (profile.checkinRemindersEnabled) {
            val hour = profile.reminderHour.coerceIn(17, 23)
            scheduleDailyAlarm(context, alarmManager, hour, 0, 401, ACTION_CHECKIN_REMINDER)
        }

        // 4. Medicine Reminders (Scheduled at each user's chosen hour:minute)
        for (med in medicines) {
            if (med.isEnabled) {
                scheduleMedicineDailyAlarm(context, alarmManager, med)
            }
        }
    }

    fun scheduleMedicineDailyAlarm(
        context: Context,
        alarmManager: AlarmManager,
        med: MedicineReminder
    ) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, med.hour)
            set(Calendar.MINUTE, med.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            setPackage(context.packageName)
            action = ACTION_MEDICINE_REMINDER
            // Zero-plaintext IPC: pass only the database ID; receiver decrypts fresh from encrypted local vault
            putExtra(EXTRA_MEDICINE_ID, med.id)
        }

        val requestCode = (NOTIFICATION_ID_MEDICINE_BASE + med.id).toInt()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Alarm permissions constraint
        }
    }

    private fun scheduleDailyAlarm(
        context: Context,
        alarmManager: AlarmManager,
        hour: Int,
        minute: Int,
        requestCode: Int,
        action: String
    ) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            this.action = action
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Alarm permissions constraint
        }
    }

    fun cancelAllReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val actions = listOf(
            201 to ACTION_WATER_REMINDER,
            202 to ACTION_WATER_REMINDER,
            203 to ACTION_WATER_REMINDER,
            301 to ACTION_CYCLE_REMINDER,
            401 to ACTION_CHECKIN_REMINDER
        )

        for ((code, action) in actions) {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                this.action = action
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    /**
     * Posts a rich reminder notification for a specific user note
     */
    fun showNoteReminderNotification(context: Context, note: UserNote) {
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val tagDisplay = when (note.tag) {
            "gossip" -> "💬 Gossip"
            "question" -> "❓ Question"
            "symptom" -> "🩺 Symptom"
            "reminder" -> "⏰ Reminder"
            else -> "💭 Thought"
        }

        val noteId = note.id
        val content = note.content

        // Open Note in app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, TARGET_NOTES)
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        val openPending = PendingIntent.getActivity(
            context,
            (NOTIFICATION_ID_NOTE_BASE + noteId).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 1 hour Action (ID-only; never embed plaintext note content in broadcast extras)
        val snoozeIntent = Intent(context, NotificationReceiver::class.java).apply {
            setPackage(context.packageName)
            action = ACTION_SNOOZE_NOTE_REMINDER
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            (7000 + noteId).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_lock_idle_alarm,
            "Snooze 1h ⏰",
            snoozePending
        ).build()

        // Mark Done / Dismiss Action
        val dismissIntent = Intent(context, NotificationReceiver::class.java).apply {
            setPackage(context.packageName)
            action = ACTION_DISMISS_NOTE_REMINDER
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            (8000 + noteId).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "Mark Done ✨",
            dismissPending
        ).build()

        val soundUri = getNotificationSoundUri(context)

        val snippet = if (content.length > 70) content.take(70) + "..." else content
        val bigText = "📝 What you wrote:\n\n\"$content\"\n\n🌸 Private scratchpad reminder"

        val publicNoteVersion = NotificationCompat.Builder(context, CHANNEL_NOTES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("KriMoon · Private Note Reminder 🔒")
            .setContentText("Unlock device to read your private scratchpad note.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_NOTES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Remember What You Wrote • $tagDisplay")
            .setContentText(snippet)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNoteVersion)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .addAction(snoozeAction)
            .addAction(dismissAction)

        try {
            NotificationManagerCompat.from(context).notify(
                (NOTIFICATION_ID_NOTE_BASE + noteId).toInt(),
                builder.build()
            )
        } catch (_: SecurityException) {
        }
    }

    /**
     * Schedules an AlarmManager reminder for a user note at its specified reminderTimeMillis
     */
    fun scheduleNoteReminder(context: Context, note: UserNote) {
        val triggerMillis = note.reminderTimeMillis ?: return
        if (triggerMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            setPackage(context.packageName)
            action = ACTION_NOTE_REMINDER
            // Zero-plaintext IPC: pass only noteId; receiver decrypts fresh from encrypted local vault
            putExtra(EXTRA_NOTE_ID, note.id)
        }

        val requestCode = (NOTIFICATION_ID_NOTE_BASE + note.id).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Cancels any pending AlarmManager alarm and posted notifications for a given note
     */
    fun cancelNoteReminder(context: Context, noteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_NOTE_REMINDER
        }
        val requestCode = (NOTIFICATION_ID_NOTE_BASE + noteId).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(requestCode)
    }
}

