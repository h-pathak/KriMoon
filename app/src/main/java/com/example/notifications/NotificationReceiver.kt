package com.example.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.PhasesRepository
import com.example.model.CycleCalculator
import com.example.model.CyclePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!com.example.security.BackendSecurityFirewall.isAllowedReceiverAction(intent)) return
        val action = intent?.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repo = PhasesRepository(db)
                val profile = repo.getProfileOnce()

                when (action) {
                    NotificationHelper.ACTION_MEDICINE_REMINDER -> {
                        val medId = intent.getLongExtra(NotificationHelper.EXTRA_MEDICINE_ID, -1L)
                        handleMedicineReminder(context, repo, medId, profile?.name ?: "Krishi")
                    }

                    NotificationHelper.ACTION_TAKE_MEDICINE_QUICK -> {
                        val medId = intent.getLongExtra(NotificationHelper.EXTRA_MEDICINE_ID, -1L)
                        handleQuickMedicineTake(context, repo, medId, profile?.name ?: "Krishi")
                    }

                    NotificationHelper.ACTION_WATER_REMINDER -> {
                        if (profile?.waterRemindersEnabled == true) {
                            handleWaterReminder(context, repo, profile.waterGoal, profile.name)
                        }
                    }

                    NotificationHelper.ACTION_ADD_WATER_QUICK -> {
                        handleQuickWaterAdd(context, repo, profile?.waterGoal ?: 8, profile?.name ?: "Krishi")
                    }

                    NotificationHelper.ACTION_CYCLE_REMINDER -> {
                        handleCycleReminder(context, repo, profile)
                    }

                    NotificationHelper.ACTION_CHECKIN_REMINDER -> {
                        if (profile?.checkinRemindersEnabled == true) {
                            handleCheckinReminder(context, profile.name)
                        }
                    }

                    NotificationHelper.ACTION_NOTE_REMINDER -> {
                        val noteId = intent.getLongExtra(NotificationHelper.EXTRA_NOTE_ID, -1L)
                        handleNoteReminder(context, repo, noteId)
                    }

                    NotificationHelper.ACTION_SNOOZE_NOTE_REMINDER -> {
                        val noteId = intent.getLongExtra(NotificationHelper.EXTRA_NOTE_ID, -1L)
                        handleSnoozeNote(context, repo, noteId)
                    }

                    NotificationHelper.ACTION_DISMISS_NOTE_REMINDER -> {
                        val noteId = intent.getLongExtra(NotificationHelper.EXTRA_NOTE_ID, -1L)
                        handleDismissNote(context, repo, noteId)
                    }

                    NotificationHelper.ACTION_NOTE_DAILY_REFLECTION -> {
                        handleDailyNoteReflection(context, repo)
                    }

                    Intent.ACTION_BOOT_COMPLETED -> {
                        if (profile != null) {
                            val periods = repo.getPeriodLogsOnce()
                            val medicines = repo.getMedicationsOnce()
                            val status = CycleCalculator.compute(
                                periods = periods,
                                defaultCycleLength = profile.cycleLength,
                                defaultPeriodLength = profile.periodLength
                            )
                            NotificationHelper.scheduleAllReminders(context, profile, status, medicines)
                        }

                        try {
                            val activeNotes = repo.getActiveNoteRemindersOnce()
                            val now = System.currentTimeMillis()
                            for (note in activeNotes) {
                                if (note.reminderTimeMillis != null && note.reminderTimeMillis > now) {
                                    NotificationHelper.scheduleNoteReminder(context, note)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleMedicineReminder(
        context: Context,
        repo: PhasesRepository,
        medId: Long,
        userName: String
    ) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val med = if (medId > 0) repo.getMedicationById(medId) else null
        if (med == null || !med.isEnabled) return

        // If already taken today, do not nag
        if (med.lastTakenDate == todayStr) return

        val timeOfDay = NotificationHelper.getTimeOfDay()
        val displayName = med.name.ifBlank { "Daily Wellness Pill" }
        val displayDosage = med.dosage.ifBlank { "1 dose" }
        val soundToPlay = med.customSound.ifBlank { "cute_chime" }

        SoundHelper.playAlarmOrPreview(context, soundToPlay)

        val takeIntent = Intent(context, NotificationReceiver::class.java).apply {
            setPackage(context.packageName)
            action = NotificationHelper.ACTION_TAKE_MEDICINE_QUICK
            putExtra(NotificationHelper.EXTRA_MEDICINE_ID, medId)
        }
        val takePending = PendingIntent.getBroadcast(
            context,
            (NotificationHelper.NOTIFICATION_ID_MEDICINE_BASE + medId.toInt()),
            takeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val quickAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "Taken! ✅",
            takePending
        ).build()

        val greetingTarget = if (userName.isNotBlank()) userName else "gorgeous"
        val medCustomMsg = med.customReminderMessage.takeIf { it.isNotBlank() }
        val rawMessage = medCustomMsg
            ?: "${timeOfDay.greeting}, $greetingTarget! Time for your $displayName ($displayDosage) ✨ Have a fresh glass of water with it! 💧💕 Staying healthy looks beautiful on you!"
        val message = NotificationHelper.formatTimeDependentGreeting(rawMessage, userName = userName)

        NotificationHelper.showNotification(
            context = context,
            notificationId = (NotificationHelper.NOTIFICATION_ID_MEDICINE_BASE + medId.toInt()),
            channelId = NotificationHelper.CHANNEL_MEDICINE,
            title = "${timeOfDay.titlePrefix} Pill Care ${timeOfDay.emoji}💊",
            message = message,
            targetScreen = NotificationHelper.TARGET_MEDICATIONS,
            quickAction = quickAction
        )
    }

    private suspend fun handleQuickMedicineTake(
        context: Context,
        repo: PhasesRepository,
        medId: Long,
        userName: String
    ) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val nowTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val med = if (medId > 0) repo.getMedicationById(medId) else null
        if (med != null) {
            repo.markMedicationTakenToday(medId, todayStr, nowTimeStr)
        }

        val medName = med?.name?.ifBlank { "your pill" } ?: "your pill"
        val greeting = if (userName.isNotBlank()) userName else "Krishi"
        NotificationHelper.showNotification(
            context = context,
            notificationId = (NotificationHelper.NOTIFICATION_ID_MEDICINE_BASE + medId.toInt()),
            channelId = NotificationHelper.CHANNEL_MEDICINE,
            title = "Wellness Checked! 🌸✨",
            message = "Way to go, $greeting! Marked $medName as taken. Keep glowing and taking care of yourself! 💖💊",
            targetScreen = NotificationHelper.TARGET_MEDICATIONS
        )
    }

    private suspend fun handleWaterReminder(context: Context, repo: PhasesRepository, goal: Int, userName: String) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val waterLog = repo.getWaterLogForDate(todayStr).firstOrNull()
        val count = waterLog?.glassesCount ?: 0

        val addWaterIntent = Intent(context, NotificationReceiver::class.java).apply {
            setPackage(context.packageName)
            action = NotificationHelper.ACTION_ADD_WATER_QUICK
        }
        val addWaterPending = PendingIntent.getBroadcast(
            context,
            101,
            addWaterIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val quickAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "+1 Glass 💧",
            addWaterPending
        ).build()

        val timeOfDay = NotificationHelper.getTimeOfDay()
        val greetingTarget = if (userName.isNotBlank()) userName else "bestie"
        val message = if (count >= goal) {
            "Queen status unlocked! 👑 You've hit all $goal glasses today! Enjoy another refreshing sip if you'd like 🌸🫧"
        } else {
            when (timeOfDay) {
                NotificationHelper.TimeOfDay.MORNING -> "Good morning, $greetingTarget! ☀️ Quick glowing sip break: You've had $count of $goal glasses. Start your day with fresh hydration! 💧✨"
                NotificationHelper.TimeOfDay.AFTERNOON -> "Good afternoon, $greetingTarget! 🌤️ Midday hydration check: You've had $count of $goal glasses. Take a sparkling sip break right now! 🫧✨"
                NotificationHelper.TimeOfDay.EVENING -> "Good evening, $greetingTarget! 🌆 Evening hydration check: You've had $count of $goal glasses. Keep that radiance flowing! 💧✨"
                NotificationHelper.TimeOfDay.NIGHT -> "Good night, $greetingTarget! 🌙 You've had $count of $goal glasses. Take a soothing sip before cozy sleep! 🫧✨"
            }
        }

        NotificationHelper.showNotification(
            context = context,
            notificationId = NotificationHelper.NOTIFICATION_ID_WATER,
            channelId = NotificationHelper.CHANNEL_WATER,
            title = "Hydrated Bestie Alert! 🧚‍♀️💧",
            message = message,
            targetScreen = NotificationHelper.TARGET_WATER,
            quickAction = quickAction
        )
    }

    private suspend fun handleQuickWaterAdd(context: Context, repo: PhasesRepository, goal: Int, userName: String) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentCount = repo.getWaterLogForDate(todayStr).firstOrNull()?.glassesCount ?: 0
        val newCount = currentCount + 1
        repo.setWaterCount(todayStr, newCount)

        val greeting = if (userName.isNotBlank()) userName else "Krishi"
        val message = if (newCount >= goal) {
            "🎉 Hydration Royalty, $greeting! You hit your goal with glass $newCount of $goal! Glowing so bright! 💧✨"
        } else {
            "Yum, refreshing! 🫧 Glass $newCount / $goal logged. Keep that radiance flowing! 💧🌸"
        }

        NotificationHelper.showNotification(
            context = context,
            notificationId = NotificationHelper.NOTIFICATION_ID_WATER,
            channelId = NotificationHelper.CHANNEL_WATER,
            title = "Hydration Boost! 💧✨",
            message = message,
            targetScreen = NotificationHelper.TARGET_WATER
        )
    }

    private suspend fun handleCycleReminder(
        context: Context,
        repo: PhasesRepository,
        profile: com.example.data.UserProfile?
    ) {
        if (profile == null) return
        val periods = repo.getPeriodLogsOnce()
        val status = CycleCalculator.compute(
            periods = periods,
            defaultCycleLength = profile.cycleLength,
            defaultPeriodLength = profile.periodLength
        )

        val name = if (profile.name.isNotBlank()) profile.name else "Angel"

        if (profile.cycleRemindersEnabled && status.daysUntilNextPeriod == 2) {
            NotificationHelper.showNotification(
                context = context,
                notificationId = NotificationHelper.NOTIFICATION_ID_CYCLE,
                channelId = NotificationHelper.CHANNEL_CYCLE,
                title = "Cozy Heads-Up, $name! 🌸🧸",
                message = "Your period is predicted in ~2 days. Cozy blankets, herbal teas & favorite snacks ready for comfort! 🍫🧦💕",
                targetScreen = NotificationHelper.TARGET_CYCLE
            )
            return
        }

        if (profile.cycleRemindersEnabled && status.daysUntilNextPeriod == 0 && !status.isLate) {
            NotificationHelper.showNotification(
                context = context,
                notificationId = NotificationHelper.NOTIFICATION_ID_CYCLE,
                channelId = NotificationHelper.CHANNEL_CYCLE,
                title = "Day 1 Hugs: Take It Slow 🩸🌸",
                message = "Your period is expected around today. Be extra gentle with yourself, rest without apologies! 🍵💖",
                targetScreen = NotificationHelper.TARGET_PERIODS
            )
            return
        }

        if (profile.cycleRemindersEnabled && status.isLate && status.daysLate in 1..4) {
            NotificationHelper.showNotification(
                context = context,
                notificationId = NotificationHelper.NOTIFICATION_ID_CYCLE,
                channelId = NotificationHelper.CHANNEL_CYCLE,
                title = "Cycle Love Note (+${status.daysLate}d) 🌸✨",
                message = "Period is taking its time. Bodies aren't clocks—stress and weather can nudge things. Breathe easy, $name! 💕",
                targetScreen = NotificationHelper.TARGET_CYCLE
            )
            return
        }

        if (profile.fertileRemindersEnabled && (status.currentPhase == CyclePhase.FERTILE || status.currentPhase == CyclePhase.OVULATION)) {
            NotificationHelper.showNotification(
                context = context,
                notificationId = NotificationHelper.NOTIFICATION_ID_CYCLE,
                channelId = NotificationHelper.CHANNEL_CYCLE,
                title = "Radiance Blooming! 🌱✨",
                message = "${status.currentPhase.emoji} ${status.currentPhase.shortName}: Natural vitality & charm are at 100%. Shine bright today, $name! 🌸🌟",
                targetScreen = NotificationHelper.TARGET_CYCLE
            )
            return
        }

        if (profile.shopRemindersEnabled && (status.currentPhase == CyclePhase.LUTEAL || status.currentPhase == CyclePhase.LATE_LUTEAL)) {
            NotificationHelper.showNotification(
                context = context,
                notificationId = NotificationHelper.NOTIFICATION_ID_CHECKLIST,
                channelId = NotificationHelper.CHANNEL_CHECKLIST,
                title = "Luteal Nesting Checklist 🛍️🧸",
                message = "Approaching PMS days: check your 'To Get' list for soothing teas, magnesium, and pampering comfort items! ☕🛁✨",
                targetScreen = NotificationHelper.TARGET_SHOPPING
            )
        }
    }

    private suspend fun handleCheckinReminder(context: Context, userName: String) {
        val timeOfDay = NotificationHelper.getTimeOfDay()
        val name = if (userName.isNotBlank()) userName else "Krishi"
        val (checkinTitle, checkinMsg) = when (timeOfDay) {
            NotificationHelper.TimeOfDay.MORNING, NotificationHelper.TimeOfDay.AFTERNOON -> {
                "${timeOfDay.titlePrefix} Check-in ${timeOfDay.emoji}✨" to
                "${timeOfDay.greeting}, $name! Take a quiet cozy minute to check in with yourself and jot down your thoughts in your private scratchpad 🌸"
            }
            NotificationHelper.TimeOfDay.EVENING -> {
                "Evening Reflection 🌆✨" to
                "Good evening, $name! The day is winding down and you did great. Take a quiet cozy minute to jot down your thoughts in your private scratchpad 🌸💤"
            }
            NotificationHelper.TimeOfDay.NIGHT -> {
                "Starry Night Reflection 🌙✨" to
                "Good night, $name! The day is done and you did great. Take a soothing breath and jot down your thoughts in your private scratchpad 🌸💤"
            }
        }

        NotificationHelper.showNotification(
            context = context,
            notificationId = NotificationHelper.NOTIFICATION_ID_CHECKIN,
            channelId = NotificationHelper.CHANNEL_CHECKIN,
            title = checkinTitle,
            message = checkinMsg,
            targetScreen = NotificationHelper.TARGET_NOTES
        )
    }

    private suspend fun handleNoteReminder(
        context: Context,
        repo: PhasesRepository,
        noteId: Long
    ) {
        if (noteId <= 0L) return
        val note = repo.getNoteById(noteId) ?: return
        if (note.content.isBlank()) return

        repo.markNoteReminderFired(note.id, note.reminderTimeMillis, reminderFired = true)
        NotificationHelper.showNoteReminderNotification(context, note.copy(reminderFired = true))
    }

    private suspend fun handleSnoozeNote(
        context: Context,
        repo: PhasesRepository,
        noteId: Long
    ) {
        if (noteId <= 0L) return
        androidx.core.app.NotificationManagerCompat.from(context).cancel(
            (NotificationHelper.NOTIFICATION_ID_NOTE_BASE + noteId).toInt()
        )
        val snoozeMillis = System.currentTimeMillis() + 60 * 60 * 1000L
        repo.updateNoteReminder(noteId, snoozeMillis)
        val updated = repo.getNoteById(noteId) ?: return
        NotificationHelper.scheduleNoteReminder(context, updated)
    }

    private suspend fun handleDismissNote(
        context: Context,
        repo: PhasesRepository,
        noteId: Long
    ) {
        androidx.core.app.NotificationManagerCompat.from(context).cancel(
            (NotificationHelper.NOTIFICATION_ID_NOTE_BASE + noteId).toInt()
        )
        if (noteId > 0L) {
            repo.markNoteReminderFired(noteId, null, reminderFired = true)
        }
    }

    private suspend fun handleDailyNoteReflection(
        context: Context,
        repo: PhasesRepository
    ) {
        val notes = repo.getAllNotesOnce()
        if (notes.isEmpty()) return

        val selectedNote = notes.random()
        val tagDisplay = when (selectedNote.tag) {
            "gossip" -> "💬 Gossip"
            "question" -> "❓ Question"
            "symptom" -> "🩺 Symptom"
            "reminder" -> "⏰ Reminder"
            else -> "💭 Thought"
        }

        NotificationHelper.showNotification(
            context = context,
            notificationId = NotificationHelper.NOTIFICATION_ID_DAILY_NOTE,
            channelId = NotificationHelper.CHANNEL_NOTES,
            title = "💭 Remember What You Wrote • $tagDisplay",
            message = "\"${selectedNote.content.take(80)}\" — Take a mindful pause to reflect 🌸",
            targetScreen = NotificationHelper.TARGET_NOTES
        )
    }
}
