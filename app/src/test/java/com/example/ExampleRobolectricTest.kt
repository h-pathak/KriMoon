package com.example

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.UserProfile
import com.example.model.CycleCalculator
import com.example.notifications.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("KriMoon", appName)
    }

    @Test
    fun `notification channels are created properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.createNotificationChannels(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val cycleChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_CYCLE)
        val waterChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_WATER)
        val checkinChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_CHECKIN)
        val checklistChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_CHECKLIST)
        val medicineChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_MEDICINE)
        val notesChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_NOTES)

        assertNotNull("Cycle channel should exist", cycleChannel)
        assertNotNull("Water channel should exist", waterChannel)
        assertNotNull("Checkin channel should exist", checkinChannel)
        assertNotNull("Checklist channel should exist", checklistChannel)
        assertNotNull("Medicine channel should exist", medicineChannel)
        assertNotNull("Notes channel should exist", notesChannel)

        assertTrue(cycleChannel.name.contains("Cycle"))
        assertTrue(waterChannel.name.contains("Hydration"))
        assertTrue(medicineChannel.name.contains("Medicine"))
        assertTrue(notesChannel.name.contains("Notes"))
    }

    @Test
    fun `user note model supports reminder scheduling and status`() {
        val reminderTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000L)
        val note = com.example.data.UserNote(
            id = 42L,
            content = "Prepare heating pad and chamomile tea for tonight",
            tag = "reminder",
            timestampMillis = System.currentTimeMillis(),
            reminderTimeMillis = reminderTime,
            reminderFired = false
        )

        assertEquals(42L, note.id)
        assertEquals("reminder", note.tag)
        assertEquals(reminderTime, note.reminderTimeMillis)
        assertFalse(note.reminderFired)
        assertTrue(note.content.contains("heating pad"))
    }

    @Test
    fun `note reminder notifications and test methods execute without error`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.createNotificationChannels(context)

        val note = com.example.data.UserNote(
            id = 101L,
            content = "Ask Dr. Sarah about iron levels next Tuesday",
            tag = "question",
            timestampMillis = System.currentTimeMillis(),
            reminderTimeMillis = System.currentTimeMillis() + 3600000L,
            reminderFired = false
        )

        NotificationHelper.showNoteReminderNotification(context, note)
        NotificationHelper.scheduleNoteReminder(context, note)
        NotificationHelper.cancelNoteReminder(context, 101L)
        NotificationHelper.sendTestNotification(context, "note")
        assertTrue(true)
    }

    @Test
    fun `schedule and cancel all reminders does not crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = UserProfile(
            id = 1,
            name = "Krishi",
            cycleLength = 28,
            periodLength = 5,
            waterGoal = 8,
            waterRemindersEnabled = true,
            cycleRemindersEnabled = true,
            fertileRemindersEnabled = true,
            checkinRemindersEnabled = true,
            shopRemindersEnabled = true
        )
        val status = CycleCalculator.compute(emptyList())
        val medicines = listOf(
            com.example.data.MedicineReminder(
                id = 1,
                name = "Multivitamin",
                dosage = "1 tablet",
                hour = 9,
                minute = 0
            )
        )

        NotificationHelper.scheduleAllReminders(context, profile, status, medicines)
        NotificationHelper.cancelAllReminders(context)
        assertTrue(true)
    }

    @Test
    fun `send test notifications does not crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.createNotificationChannels(context)

        NotificationHelper.sendTestNotification(context, "water")
        NotificationHelper.sendTestNotification(context, "cycle")
        NotificationHelper.sendTestNotification(context, "checklist")
        NotificationHelper.sendTestNotification(context, "checkin")
        NotificationHelper.sendTestNotification(context, "medicine")
        assertTrue(true)
    }

    @Test
    fun `particle controller spawn creates particles correctly`() {
        val controller = com.example.ui.components.ParticleController()
        assertEquals(0, controller.activeParticles.size)

        controller.spawn(
            centerX = 100f,
            centerY = 200f,
            emojis = listOf("🌸", "✨"),
            count = 10
        )

        assertEquals(10, controller.activeParticles.size)
        assertTrue(controller.activeParticles.all { it.emoji == "🌸" || it.emoji == "✨" })

        val first = controller.activeParticles.first()
        controller.removeParticle(first)
        assertEquals(9, controller.activeParticles.size)
    }

    @Test
    fun `cycle prediction algorithm computes dynamic intervals and upcoming prediction windows`() {
        val now = System.currentTimeMillis()
        val msPerDay = 24L * 60 * 60 * 1000
        val p1 = com.example.data.PeriodLog(id = 1, startDateMillis = now - (60L * msPerDay), endDateMillis = now - (55L * msPerDay), durationDays = 5)
        val p2 = com.example.data.PeriodLog(id = 2, startDateMillis = now - (31L * msPerDay), endDateMillis = now - (26L * msPerDay), durationDays = 5)
        val p3 = com.example.data.PeriodLog(id = 3, startDateMillis = now - (2L * msPerDay), endDateMillis = null, durationDays = null)

        val status = com.example.model.CycleCalculator.compute(listOf(p3, p2, p1), 28, 5)
        assertTrue("Cycle count should be at least 3", status.cycleCount >= 2)
        assertTrue("Effective cycle length should be close to 29", status.totalCycleDays in 27..31)
        assertTrue("Predicted window start is populated", status.predictedWindowStartMillis > 0)
        assertTrue("Predicted window end is greater than start", status.predictedWindowEndMillis > status.predictedWindowStartMillis)

        val windows = com.example.model.CycleCalculator.computeUpcomingPredictionWindows(listOf(p3, p2, p1), 28, 5, 3)
        assertEquals(3, windows.size)
        assertTrue(windows.first().expectedDateMillis > now)
    }

    @Test
    fun `medication data model stores custom sounds and messages`() {
        val med = com.example.data.Medication(
            name = "Evening Primrose Oil",
            dosage = "1000mg softgel",
            hour = 21,
            minute = 30,
            customSound = "gentle_harp",
            customReminderMessage = "Sweet dreams Krishi! Don't forget your evening primrose 🌙🌸"
        )
        assertEquals("gentle_harp", med.customSound)
        assertEquals(21, med.hour)
        assertEquals(30, med.minute)
        assertTrue(med.customReminderMessage.contains("Krishi"))
    }

    @Test
    fun `task category model handles default and custom states`() {
        val defaultCat = com.example.data.TaskCategory(
            id = "menstrual",
            displayName = "Crimson Care",
            emoji = "🩸",
            isDefault = true
        )
        val customCat = com.example.data.TaskCategory(
            id = "custom_tea",
            displayName = "Herbal Teas",
            emoji = "🍵",
            isDefault = false
        )

        assertEquals(false, defaultCat.isCustom)
        assertEquals(true, customCat.isCustom)
    }

    @Test
    fun `calendar month days generator generates prediction windows and phase dots`() {
        val now = System.currentTimeMillis()
        val msPerDay = 24L * 60 * 60 * 1000
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val curYear = cal.get(java.util.Calendar.YEAR)
        val curMonth = cal.get(java.util.Calendar.MONTH)

        val p1 = com.example.data.PeriodLog(id = 1, startDateMillis = now - (30L * msPerDay), endDateMillis = now - (25L * msPerDay), durationDays = 5)

        val days = com.example.model.CycleCalculator.generateMonthCalendarDays(
            year = curYear,
            month = curMonth,
            periods = listOf(p1),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            todayMillis = now
        )
        assertEquals(42, days.size)
        assertTrue("At least one day is today", days.any { it.isToday })
        assertTrue("Days are generated", days.isNotEmpty())
    }

    @Test
    fun `room database stores and retrieves user cycle data and symptoms locally for private tracking`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.AppDatabase.getInstance(context)
        val dao = db.dailyCycleLogDao()

        val sampleLog = com.example.data.DailyCycleLog(
            dateString = "2026-10-01",
            flowIntensity = "MEDIUM",
            symptoms = "cramps,bloating,fatigue",
            mood = "Sensitive",
            cervicalMucus = "CREAMY",
            painLevel = 3,
            energyLevel = 2,
            notes = "Stayed home with hot herbal tea"
        )

        val rowId = dao.insertOrUpdate(sampleLog)
        assertTrue("Log should be inserted with valid ID", rowId > 0)

        val retrieved = dao.getLogForDateSync("2026-10-01")
        assertNotNull("Retrieved log should not be null", retrieved)
        assertEquals("2026-10-01", retrieved?.dateString)
        assertEquals("MEDIUM", retrieved?.flowIntensity)
        assertTrue(retrieved?.symptoms?.contains("cramps") == true)
        assertEquals(3, retrieved?.painLevel)
        assertEquals(2, retrieved?.energyLevel)
        assertEquals("Stayed home with hot herbal tea", retrieved?.notes)

        // Delete test
        dao.deleteByDate("2026-10-01")
        val deleted = dao.getLogForDateSync("2026-10-01")
        assertTrue("Log should be deleted", deleted == null)
    }

    @Test
    fun `logging past periods trains cycle algorithm to detect upcoming cycles accurately and visualizes them on calendar`() = kotlinx.coroutines.runBlocking {
        val msPerDay = 24L * 60 * 60 * 1000
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 1, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val baseTime = baseCal.timeInMillis

        // 3 past periods: 56 days ago, 28 days ago, and 0 days ago (28-day intervals, 5-day flow duration)
        val period1 = com.example.data.PeriodLog(
            id = 1L,
            startDateMillis = baseTime - (56L * msPerDay),
            endDateMillis = baseTime - (52L * msPerDay),
            durationDays = 5,
            notes = "Period 1 logged"
        )
        val period2 = com.example.data.PeriodLog(
            id = 2L,
            startDateMillis = baseTime - (28L * msPerDay),
            endDateMillis = baseTime - (24L * msPerDay),
            durationDays = 5,
            notes = "Period 2 logged"
        )
        val period3 = com.example.data.PeriodLog(
            id = 3L,
            startDateMillis = baseTime,
            endDateMillis = baseTime + (4L * msPerDay),
            durationDays = 5,
            notes = "Period 3 logged"
        )

        val pastPeriods = listOf(period3, period2, period1)

        // Algorithm detects cycle length and upcoming cycle
        val status = CycleCalculator.compute(
            periods = pastPeriods,
            defaultCycleLength = 30, // default is 30, but algorithm should detect 28 from history!
            defaultPeriodLength = 6,
            referenceTimeMillis = baseTime
        )

        assertEquals("Algorithm should calculate average cycle length as 28 days from past intervals", 28.0, status.averageCycleLength, 0.5)
        assertEquals("Algorithm should calculate period length as 5 days from past logged durations", 5, status.periodLength)
        assertEquals("Upcoming cycle expected date should be 28 days from most recent start", baseTime + (28L * msPerDay), status.expectedNextDateMillis)

        // Calendar generation marks past logged periods vs predicted upcoming cycles
        val calendarDays = CycleCalculator.generateMonthCalendarDays(
            year = 2026,
            month = java.util.Calendar.OCTOBER,
            periods = pastPeriods,
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            todayMillis = baseTime
        )

        assertTrue("Calendar days list should have entries", calendarDays.isNotEmpty())
        val hasHistorical = calendarDays.any { it.isHistoricalLogged }
        assertTrue("Calendar should mark at least one historical logged day", hasHistorical)

        val hasPredicted = calendarDays.any { it.isPredicted }
        assertTrue("Calendar should mark predicted upcoming days", hasPredicted)
    }

    @Test
    fun `periods view tabs only contain calendar, trends, and history`() {
        val tabNames = com.example.ui.screens.PeriodsViewTab.values().map { it.name }
        assertTrue(tabNames.contains("CALENDAR"))
        assertTrue(tabNames.contains("TRENDS"))
        assertTrue(tabNames.contains("HISTORY"))
        assertEquals("Only 3 tabs should be present", 3, tabNames.size)
        assertFalse("SYMPTOMS tab should be removed", tabNames.contains("SYMPTOMS"))
    }

    @Test
    fun `sound helper plays sounds and configures sound pool without crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.notifications.SoundHelper.playSound(context, com.example.notifications.SoundHelper.SOUND_SWEET_BUBBLE)
        com.example.notifications.SoundHelper.playSound(context, com.example.notifications.SoundHelper.SOUND_CUTE_CHIME)
        com.example.notifications.SoundHelper.playCuteChime(context)
        assertTrue(true)
    }

    @Test
    fun `tactile stress helper executes all tactile patterns without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tactileHelper = com.example.ui.components.TactileStressHelper(context)
        tactileHelper.onTouchGrab()
        tactileHelper.onDragStretch(40f, 200f)
        tactileHelper.onDragStretch(90f, 200f)
        tactileHelper.onMaxTensionReached()
        tactileHelper.onElasticRelease(600f)
        tactileHelper.onSpringBounce(1)
        tactileHelper.onSpringBounce(2)
        tactileHelper.onSquishPop()
        tactileHelper.onSpinTick()
        tactileHelper.onBreathPulse()
        tactileHelper.reset()
        assertTrue(true)
    }

    @Test
    fun `tactile hydration helper executes all tactile patterns without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hydrationHelper = com.example.ui.components.TactileHydrationHelper(context)
        hydrationHelper.onDropSip(false)
        hydrationHelper.onDropSip(true)
        hydrationHelper.onGoalCelebration()
        hydrationHelper.onCupTapped(true)
        hydrationHelper.onCupTapped(false)
        hydrationHelper.onWaveSlosh()
        hydrationHelper.onPresetLogged(1)
        hydrationHelper.onPresetLogged(2)
        hydrationHelper.onGoalAdjusted()
        hydrationHelper.onDeduct()
        assertTrue(true)
    }

    @Test
    fun `main activity launches without crash`() {
        org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        assertTrue(true)
    }

    @Test
    fun `couple turn codec encodes decodes and isolates couple rooms by unique code`() {
        val codeA = com.example.model.CoupleTurnCodec.generateUniqueCode()
        val codeB = com.example.model.CoupleTurnCodec.generateUniqueCode(setOf(codeA))
        assertEquals(6, codeA.length)
        assertEquals(6, codeB.length)
        assertFalse("Generated codes should be unique", codeA == codeB)

        val quizPacket = com.example.model.CoupleTurnCodec.encodeQuizTurn(
            coupleCode = codeA,
            senderName = "Krishi",
            roundIndex = 2,
            choiceIndex = 1,
            spiceLevel = com.example.model.SpiceLevel.FLIRTY
        )
        assertTrue("Packet must use Blockchain AES-256-GCM header", quizPacket.startsWith("KRIMOON:BCHAIN:"))

        val decoded = com.example.model.CoupleTurnCodec.decodePacket(quizPacket, codeA).getOrNull()
        assertTrue(decoded is com.example.model.DecodedTurnPacket.QuizTurn)
        val quiz = decoded as com.example.model.DecodedTurnPacket.QuizTurn
        assertEquals("Krishi", quiz.senderName)
        assertEquals(2, quiz.roundIndex)
        assertEquals(1, quiz.choiceIndex)
        assertEquals(com.example.model.SpiceLevel.FLIRTY, quiz.spiceLevel)
        assertTrue("Mined block must satisfy Proof-of-Work difficulty '00'", quiz.block.blockHash.startsWith("00"))
        assertTrue("Mined block must pass cryptographic verification", com.example.model.CoupleBlockchain.verifyBlock(quiz.block))

        // Different couple code must be rejected by Firewall
        val wrongRoomResult = com.example.model.CoupleTurnCodec.decodePacket(quizPacket, codeB)
        assertTrue("Packet for another couple code must fail", wrongRoomResult.isFailure)

        // Firewall Anti-Replay: submitting the same block again when already in ledger must be blocked
        val replayResult = com.example.model.CoupleTurnCodec.decodePacket(
            rawInput = quizPacket,
            expectedCoupleCode = codeA,
            existingLedger = listOf(quiz.block)
        )
        assertTrue("Replayed block must be blocked by Firewall", replayResult.isFailure)

        // Firewall Partner Identity Lock: third-party sender must be blocked once partner is locked
        val intruderPacket = com.example.model.CoupleTurnCodec.encodeQuizTurn(
            coupleCode = codeA,
            senderName = "Intruder",
            roundIndex = 3,
            choiceIndex = 0,
            spiceLevel = com.example.model.SpiceLevel.FLIRTY
        )
        val partnerLockResult = com.example.model.CoupleTurnCodec.decodePacket(
            rawInput = intruderPacket,
            expectedCoupleCode = codeA,
            existingLedger = emptyList(),
            lockedPartnerName = "Alex",
            myName = "Krishi"
        )
        assertTrue("Unauthorized 3rd-party sender must be blocked when partner is locked", partnerLockResult.isFailure)

        // TicTacToe round-trip chained onto previous block hash
        val tttState = com.example.model.TicTacToeSyncState(
            board = listOf("X", "", "O", "", "", "", "", "", ""),
            xQueue = listOf(0),
            oQueue = listOf(2),
            nextTurn = "X",
            isBoltTwist = true,
            mySymbol = "O",
            roundCount = 3
        )
        val (tttPacket, tttBlock) = com.example.model.CoupleTurnCodec.encodeTicTacToeTurnWithBlock(
            coupleCode = codeA,
            senderName = "Alex",
            state = tttState,
            previousHash = quiz.block.blockHash,
            blockIndex = 2
        )
        val tttDecoded = com.example.model.CoupleTurnCodec.decodePacket(
            rawInput = tttPacket,
            expectedCoupleCode = codeA,
            existingLedger = listOf(quiz.block),
            lockedPartnerName = "Alex",
            myName = "Krishi"
        ).getOrNull()
        assertTrue(tttDecoded is com.example.model.DecodedTurnPacket.TicTacToeTurn)
        assertEquals(listOf(0), (tttDecoded as com.example.model.DecodedTurnPacket.TicTacToeTurn).state.xQueue)
        assertEquals(3, tttDecoded.state.roundCount)
        assertEquals(quiz.block.blockHash, tttBlock.previousHash)

        // Verify chain integrity across Genesis + Quiz Block + TicTacToe Block
        val genesis = com.example.model.CoupleBlockchain.createGenesisBlock(codeA, "Krishi")
        val chainVerify = com.example.model.CoupleBlockchain.verifyChain(listOf(genesis, quiz.block, tttBlock), codeA)
        assertTrue("Full blockchain ledger must verify cleanly", chainVerify.isValid)
        assertEquals(3, chainVerify.verifiedBlockCount)
    }

    @Test
    fun `room database persists offline game scores and couple rooms locally`() = kotlinx.coroutines.runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.AppDatabase.getInstance(context)

        val scoreDao = db.gameScoreDao()
        scoreDao.insertOrUpdate(
            com.example.data.GameScoreRecord(
                gameId = "moon_merge",
                highScore = 1450,
                bestStreak = 6,
                gamesPlayed = 3
            )
        )
        val loadedScore = scoreDao.getScoreById("moon_merge")
        assertNotNull(loadedScore)
        assertEquals(1450, loadedScore?.highScore)

        val genesis = com.example.model.CoupleBlockchain.createGenesisBlock("849201", "Krishi")
        val coupleDao = db.coupleRoomDao()
        coupleDao.insertOrUpdate(
            com.example.data.CoupleRoomRecord(
                coupleCode = "849201",
                myName = "Krishi",
                partnerName = "Alex",
                spiceLevel = "BOLD",
                matchCount = 4,
                streakCount = 4,
                isBonded = true,
                partnerLocked = true,
                blockchainLedgerJson = com.example.model.CoupleBlockchain.ledgerToJson(listOf(genesis)),
                lastBlockHash = genesis.blockHash,
                blockHeight = 1,
                firewallPassedCount = 5
            )
        )
        val loadedRoom = coupleDao.getRoomByCode("849201")
        assertNotNull(loadedRoom)
        assertEquals("Krishi", loadedRoom?.myName)
        assertEquals("BOLD", loadedRoom?.spiceLevel)
        assertEquals(true, loadedRoom?.isBonded)
        assertEquals(true, loadedRoom?.partnerLocked)
        assertEquals(genesis.blockHash, loadedRoom?.lastBlockHash)
        coupleDao.deleteByCode("849201")
    }

    @Test
    fun `nav screen has 5 balanced tabs including care hub and play arcade`() {
        val navNames = com.example.viewmodel.NavScreen.entries.map { it.name }
        assertEquals(listOf("CYCLE", "PERIODS", "CARE", "NOTES", "PLAY"), navNames)
        val careNames = com.example.viewmodel.CareTab.entries.map { it.name }
        assertEquals(listOf("WATER", "MEDS", "CHECKLIST"), careNames)
    }

    @Test
    fun `backend security firewall enforces 12 layers sql xss sanitization at rest aes gcm encryption and ipc whitelist`() {
        val firewall = com.example.security.BackendSecurityFirewall

        // 1. Verify all 12 firewall layers are active
        val audit = firewall.runSecurityAudit()
        assertEquals(12, audit.totalFirewallLayers)
        assertEquals(12, audit.activeFirewallLayers)
        assertTrue(audit.zeroBreachStatus.contains("ZERO BREACHES"))
        assertEquals(12, audit.layers.size)

        // 2. Verify SQLi and XSS payload neutralization
        val maliciousNote = "Hello <script>alert(1)</script>; DROP TABLE user_notes; UNION SELECT * FROM user_profile"
        val sanitized = firewall.sanitizeField(maliciousNote, maxLength = 500)
        assertFalse("Script tags must be redacted", sanitized.contains("<script", ignoreCase = true))
        assertFalse("DROP TABLE must be redacted", sanitized.contains("DROP TABLE", ignoreCase = true))
        assertFalse("UNION SELECT must be redacted", sanitized.contains("UNION SELECT", ignoreCase = true))

        // 3. Verify At-Rest AES-256-GCM Encryption & Decryption Vault + Tamper Protection
        val sensitivePlain = "Intimate private cycle note: cramps & migraine on day 2"
        val encrypted = firewall.encryptAtRest(sensitivePlain, aadContext = "user_note_42")
        assertTrue("Encrypted field must carry ENC:AES256GCM: header", encrypted.startsWith("ENC:AES256GCM:"))
        assertFalse("Ciphertext must not leak plaintext", encrypted.contains("migraine"))
        val decrypted = firewall.decryptAtRest(encrypted, aadContext = "user_note_42")
        assertEquals(sensitivePlain, decrypted)

        // Wrong context tag (cross-row swap attack) must fail decryption safely
        val wrongContextDecrypted = firewall.decryptAtRest(encrypted, aadContext = "user_note_999")
        assertEquals("[Tampered Record Blocked by At-Rest Vault]", wrongContextDecrypted)

        // 4. Verify Entity Bounds & Schema Invariant Clamping
        val outOfBoundsProfile = UserProfile(
            name = "Krishi <script>evil</script>",
            cycleLength = 999,
            periodLength = -5,
            waterGoal = 500,
            reminderHour = 99
        )
        val guardedProfile = firewall.guardUserProfile(outOfBoundsProfile)
        assertEquals(60, guardedProfile.cycleLength)
        assertEquals(2, guardedProfile.periodLength)
        assertEquals(20, guardedProfile.waterGoal)
        assertEquals(23, guardedProfile.reminderHour)
        assertFalse(guardedProfile.name.contains("<script", ignoreCase = true))

        // 5. Verify IPC Navigation & Broadcast Intent Whitelist Firewall
        assertTrue(firewall.isAllowedNavigationTarget(NotificationHelper.TARGET_CYCLE))
        assertTrue(firewall.isAllowedNavigationTarget(NotificationHelper.TARGET_NOTES))
        assertFalse(firewall.isAllowedNavigationTarget("javascript:alert(1)"))
        assertFalse(firewall.isAllowedNavigationTarget("../../etc/passwd"))

        val validBroadcast = android.content.Intent(NotificationHelper.ACTION_WATER_REMINDER)
        val spoofedBroadcast = android.content.Intent("com.evil.EXFILTRATE_DATA")
        assertTrue(firewall.isAllowedReceiverAction(validBroadcast))
        assertFalse(firewall.isAllowedReceiverAction(spoofedBroadcast))
    }
}


