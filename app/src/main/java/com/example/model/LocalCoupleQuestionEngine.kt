package com.example.model

import kotlin.math.abs

enum class QuestionTheme(val label: String, val emoji: String) {
    ROMANCE("Romance", "🌸"),
    TOUCH("Touch", "💋"),
    TEASE("Tease", "😏"),
    ADVENTURE("Adventure", "🌙"),
    CONFESSION("Confession", "💌"),
    MIDNIGHT("After-Dark", "🔥")
}

data class RoundOutcome(
    val roundIndex: Int,
    val theme: QuestionTheme,
    val matched: Boolean,
    val choiceSignature: Int, // order-independent signature so both phones compute the same seed
    val spiceLevel: SpiceLevel
)

data class GeneratedQuizRound(
    val question: CoupleQuizQuestion,
    val theme: QuestionTheme,
    val heatScore: Int, // 0..100
    val isAdaptiveFollowUp: Boolean,
    val adaptiveTag: String = ""
)

data class GeneratedWyrRound(
    val prompt: WouldYouRatherPrompt,
    val theme: QuestionTheme,
    val heatScore: Int, // 0..100
    val isAdaptiveFollowUp: Boolean,
    val adaptiveTag: String = ""
)

data class TaggedQuizItem(
    val question: CoupleQuizQuestion,
    val theme: QuestionTheme
)

data class TaggedWyrItem(
    val prompt: WouldYouRatherPrompt,
    val theme: QuestionTheme
)

/**
 * Pure on-device, zero-network local adaptive question engine.
 * Learns from the couple's previous picks, match streak, and selected spice level
 * to craft escalating follow-up questions that build upon prior answers.
 *
 * Completely deterministic per (coupleCode, roundIndex, spiceLevel, symmetric history)
 * so two phones playing over the internet independently produce the exact same question.
 */
object LocalCoupleQuestionEngine {

    val expandedDoodlePrompts: List<String> = listOf(
        "Us on our dream date 🌙✨",
        "Our future cozy apartment 🏡",
        "Draw what you want to kiss right now 💋",
        "Your partner as a cute animal 🐱",
        "Late-night snack craving 🍓🍫",
        "What we're doing next time we meet 🔥",
        "Our signature couple tattoo 💕",
        "Midnight stargazing picnic 🌌",
        "Our chaotic road-trip car 🚗💨",
        "Your favorite outfit on me 👗",
        "A secret love note in symbols 💌",
        "Our dream beach sunset spot 🌅",
        "How I look when I'm sleepy 😴",
        "Our weekend getaway cabin 🏔️",
        "A movie scene that reminds you of us 🎬",
        "Draw a midnight dare for me 😈"
    )

    val taggedQuizBank: List<TaggedQuizItem> = listOf(
        // ==================== SWEET (16) ====================
        TaggedQuizItem(
            CoupleQuizQuestion("sq1", "What is our ideal zero-effort Sunday morning together?",
                listOf("Cuddling under blankets", "Coffee + pancakes in bed", "Binge-watching in hoodies", "Long sleepy voice call"), SpiceLevel.SWEET),
            QuestionTheme.ROMANCE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq2", "Which small gesture melts my heart fastest?",
                listOf("Random 'miss you' text", "Forehead kiss", "Remembering tiny details", "Surprise snack delivery"), SpiceLevel.SWEET),
            QuestionTheme.ROMANCE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq3", "When we reunite after time apart, what happens in the first 10 seconds?",
                listOf("Lift-off bear hug", "Non-stop kisses", "Happy tears & giggles", "Freeze and stare in awe"), SpiceLevel.SWEET),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq4", "What song or vibe screams 'us'?",
                listOf("Late-night R&B slow jam", "Dreamy indie pop", "90s nostalgic love ballad", "Upbeat chaotic dance track"), SpiceLevel.SWEET),
            QuestionTheme.ROMANCE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq5", "Which cozy date night would we repeat every single week?",
                listOf("Cooking dinner barefoot", "Drive-in movie with blankets", "Midnight ice cream run", "Building a living room fort"), SpiceLevel.SWEET),
            QuestionTheme.ADVENTURE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq6", "When one of us has a rough day, what fixes it fastest?",
                listOf("Silent tight hug for 2 minutes", "Ordering favorite comfort food", "Venting over voice call", "Head scratches & soft music"), SpiceLevel.SWEET),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq7", "What tiny habit of mine do you secretly adore?",
                listOf("Sleepy voice at night", "The way you laugh mid-story", "Random excited rambling", "Stealing my hoodie"), SpiceLevel.SWEET),
            QuestionTheme.CONFESSION
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq8", "If we booked a spontaneous weekend trip tomorrow, where are we going?",
                listOf("Foggy mountain cabin", "Warm hidden beach cove", "Cozy café-hopping city", "Stargazing desert dome"), SpiceLevel.SWEET),
            QuestionTheme.ADVENTURE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq9", "How do we usually end up falling asleep together?",
                listOf("Tangled legs & whispers", "Movie still playing in background", "One talking till the other snoozes", "Forehead-to-forehead"), SpiceLevel.SWEET),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq10", "What's our funniest couple superpower?",
                listOf("Reading each other's eyes in a crowd", "Inside jokes nobody gets", "Turning errands into dates", "Syncing cravings at the exact minute"), SpiceLevel.SWEET),
            QuestionTheme.TEASE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq11", "Which photo of us belongs in a frame first?",
                listOf("Messy golden-hour selfie", "Candid mid-laugh shot", "Mirror pic dressed up", "Sleepy morning coffee pic"), SpiceLevel.SWEET),
            QuestionTheme.ROMANCE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq12", "If I leave a surprise note in your pocket, what should it say?",
                listOf("A cheesy compliment", "Tonight's secret date plan", "A tiny doodle of us", "One reason I'm obsessed with you"), SpiceLevel.SWEET),
            QuestionTheme.CONFESSION
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq13", "What's the most romantic rainy-day plan for us?",
                listOf("Hot cocoa & window watching", "Slow dancing in the kitchen", "Long nap while thunder rolls", "Board games & playful bets"), SpiceLevel.SWEET),
            QuestionTheme.ROMANCE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq14", "Which everyday moment with me feels secretly magical?",
                listOf("Holding hands while walking", "Grocery shopping together", "Late-night kitchen talks", "Goodnight texts before sleep"), SpiceLevel.SWEET),
            QuestionTheme.CONFESSION
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq15", "What pet or future home detail would we agree on instantly?",
                listOf("A fluffy clingy cat or dog", "Fairy lights everywhere", "A huge cozy reading couch", "A rooftop balcony for two"), SpiceLevel.SWEET),
            QuestionTheme.ADVENTURE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("sq16", "What makes you smile first thing when you think of me?",
                listOf("Our last voice call", "The way you look at me", "Your dramatic text replies", "Planning our next meet"), SpiceLevel.SWEET),
            QuestionTheme.ROMANCE
        ),

        // ==================== FLIRTY (16) ====================
        TaggedQuizItem(
            CoupleQuizQuestion("fq1", "Where should my next unexpected kiss land?",
                listOf("Right on the neck", "Corner of the lips", "Slow lip lock", "Collarbone tease"), SpiceLevel.FLIRTY),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq2", "What outfit or look on me drives you the wildest?",
                listOf("Oversized tee & nothing else", "Dressed up for date night", "Fresh out of the shower", "Cozy fitted loungewear"), SpiceLevel.FLIRTY),
            QuestionTheme.TEASE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq3", "If I whisper something teasing during a boring call, how do you react?",
                listOf("Blush & lose train of thought", "Tease right back harder", "Demand a private replay later", "Smirk and bite my lip"), SpiceLevel.FLIRTY),
            QuestionTheme.TEASE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq4", "Which romantic tension trope fits us best?",
                listOf("Hands wandering under the table", "Rain-soaked slow kiss", "Pinned gently against the wall", "Stolen glances across the room"), SpiceLevel.FLIRTY),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq5", "What kind of subtle touch gives you instant butterflies?",
                listOf("Hand on the lower back", "Fingers tangled in hair", "Thumb brushing across lips", "Pulling me in by the waist"), SpiceLevel.FLIRTY),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq6", "If we're stuck in an elevator together for 10 minutes, what happens?",
                listOf("Instant makeout session", "Playful teasing until we cave", "Slow hug & whispered confessions", "Taking secret cute selfies"), SpiceLevel.FLIRTY),
            QuestionTheme.ADVENTURE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq7", "Which flirty text from me breaks your focus fastest?",
                listOf("'Wish you were here right now'", "A surprise mirror selfie", "'Wait till I see you tonight'", "A 10-second whispered voice note"), SpiceLevel.FLIRTY),
            QuestionTheme.TEASE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq8", "Who breaks eye contact first during a close-up stare-down?",
                listOf("Me — I blush way too fast", "You — with a guilty smirk", "Neither — it turns into a kiss", "We both burst out laughing"), SpiceLevel.FLIRTY),
            QuestionTheme.TEASE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq9", "What's the hottest way to end a date night?",
                listOf("Kissing in the doorway for 10 mins", "Slow dancing to one last song", "Pulling each other straight to the couch", "Whispering what we wanted all night"), SpiceLevel.FLIRTY),
            QuestionTheme.ROMANCE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq10", "Which playful bet should the winner of our next game collect?",
                listOf("5-minute shoulder & neck massage", "Winner chooses the next kiss", "A 30-second bold confession", "Breakfast in bed on demand"), SpiceLevel.FLIRTY),
            QuestionTheme.TEASE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq11", "When I'm sitting right beside you on the couch, where do your hands go?",
                listOf("Resting warm on your thigh", "Playing with your fingers", "Pulling you onto my lap", "Tracing circles on your arm"), SpiceLevel.FLIRTY),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq12", "What compliment from me makes your heart skip a beat?",
                listOf("Calling you irresistibly gorgeous", "Whispering how good you smell", "Saying I couldn't stop thinking of you", "Telling you I love your mind & body"), SpiceLevel.FLIRTY),
            QuestionTheme.CONFESSION
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq13", "If I show up at your door at midnight unannounced, what's our first move?",
                listOf("Pull you inside & lock the door", "Long breathless hallway kiss", "Late-night rooftop hangout", "Cuddle straight into bed"), SpiceLevel.FLIRTY),
            QuestionTheme.ADVENTURE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq14", "Which kiss style do we never get tired of?",
                listOf("Slow, teasing, almost-pulling-away", "Deep & breathless against the wall", "Playful lip-bite between laughs", "Soft sleepy morning kisses"), SpiceLevel.FLIRTY),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq15", "What secret thought pops into your head when I dress up?",
                listOf("'How do I steal them away early?'", "'I'm literally obsessed with them'", "'Counting down till we're alone'", "'Can't take my eyes off their lips'"), SpiceLevel.FLIRTY),
            QuestionTheme.CONFESSION
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("fq16", "What sets the mood faster than anything else?",
                listOf("Dim warm lights & soft R&B", "Playful wrestling & laughs", "Slow neck kisses from behind", "Deep late-night confessions"), SpiceLevel.FLIRTY),
            QuestionTheme.MIDNIGHT
        ),

        // ==================== BOLD / AFTER-DARK (16) ====================
        TaggedQuizItem(
            CoupleQuizQuestion("bq1", "On our next private weekend away, what's the #1 rule?",
                listOf("Phones on DND all weekend", "Zero rush, pure chemistry", "Room service & candlelight only", "You're in charge tonight"), SpiceLevel.BOLD),
            QuestionTheme.MIDNIGHT
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq2", "What kind of late-night voice note from me makes your pulse race?",
                listOf("Soft sleepy whispers", "Confessing what I crave next", "Playful teasing dare", "Describing our reunion kiss"), SpiceLevel.BOLD),
            QuestionTheme.CONFESSION
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq3", "Which midnight energy describes our chemistry best?",
                listOf("Slow, intense eye contact", "Playful biting & laughs", "Can't-keep-hands-off-you", "Whispered secrets in the dark"), SpiceLevel.BOLD),
            QuestionTheme.MIDNIGHT
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq4", "First 30 minutes behind closed doors when we finally see each other?",
                listOf("Make out like we're in a movie", "Take turns spoiling each other", "Shower together & decompress", "Lock the door & forget time"), SpiceLevel.BOLD),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq5", "Tonight's pace: how do you want the tension to build?",
                listOf("Slow-burn teasing for hours", "Zero patience — straight in", "Switching between sweet & fiery", "Guided purely by eye contact"), SpiceLevel.BOLD),
            QuestionTheme.MIDNIGHT
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq6", "Which daring move from me catches you off guard in the best way?",
                listOf("Taking control without warning", "Whispering a bold dare in your ear", "Tracing kisses down your spine", "Holding your gaze & not blinking"), SpiceLevel.BOLD),
            QuestionTheme.TEASE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq7", "What makes a late-night call feel like we're in the same room?",
                listOf("Lights off, headphones in, soft voice", "Playing 20 bold questions", "Describing every detail we miss", "Falling asleep breathing on the line"), SpiceLevel.BOLD),
            QuestionTheme.CONFESSION
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq8", "If we had a hotel suite for 24 hours with zero plans, what's the highlight?",
                listOf("Candlelit warm bath together", "Never even leaving the sheets", "Midnight balcony drinks & kisses", "Spin-the-bottle dares for two"), SpiceLevel.BOLD),
            QuestionTheme.ADVENTURE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq9", "Which sensation drives you the craziest when my hands are on you?",
                listOf("Soft fingertips grazing skin", "Firm grip pulling you closer", "Warm breath right by your ear", "Slow kisses that linger forever"), SpiceLevel.BOLD),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq10", "Who loves taking the lead more once the lights go dim?",
                listOf("Definitely me tonight", "Definitely you — surprise me", "We love fighting for control", "Depends on who teases first"), SpiceLevel.BOLD),
            QuestionTheme.MIDNIGHT
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq11", "What's one confession you've been waiting for the right night to tell me?",
                listOf("A daydream I've had about us", "My favorite memory of your touch", "A dare I really want to try", "How much you actually undo me"), SpiceLevel.BOLD),
            QuestionTheme.CONFESSION
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq12", "If we pick one after-dark theme for tonight's date, what wins?",
                listOf("Blindfolded taste & touch game", "Truth or Dare — no skips", "Candlelit slow massage night", "Dress up just to undress"), SpiceLevel.BOLD),
            QuestionTheme.MIDNIGHT
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq13", "Where is your favorite place for my hands to linger?",
                listOf("Tangled right in your hair", "Held tight around your waist", "Tracing along your jawline", "Pinned softly beside you"), SpiceLevel.BOLD),
            QuestionTheme.TOUCH
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq14", "How should I wake you up after a late night together?",
                listOf("Slow trail of kisses on the neck", "Pulling you closer under the sheets", "Whispering something wickedly sweet", "Coffee waiting + one more round"), SpiceLevel.BOLD),
            QuestionTheme.ROMANCE
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq15", "What turns a normal quiet night into an unforgettable one?",
                listOf("Eye contact that says everything", "Playful teasing that escalates", "Music low & zero distractions", "Trying something brand new together"), SpiceLevel.BOLD),
            QuestionTheme.MIDNIGHT
        ),
        TaggedQuizItem(
            CoupleQuizQuestion("bq16", "Next time we're alone and I say 'don't move', what do you hope comes next?",
                listOf("A slow breathless kiss", "Warm hands exploring gently", "A whispered secret right in my ear", "Total surprise — don't tell me"), SpiceLevel.BOLD),
            QuestionTheme.TEASE
        )
    )

    val taggedWyrBank: List<TaggedWyrItem> = listOf(
        // ==================== SWEET (15) ====================
        TaggedWyrItem(WouldYouRatherPrompt("sw1", "Fall asleep on video call every night", "Write each other weekly love letters", SpiceLevel.SWEET), QuestionTheme.ROMANCE),
        TaggedWyrItem(WouldYouRatherPrompt("sw2", "Cook chaotic dinner together at 1 AM", "Stargaze from a cozy rooftop", SpiceLevel.SWEET), QuestionTheme.ADVENTURE),
        TaggedWyrItem(WouldYouRatherPrompt("sw3", "Wear matching hoodies in public", "Have a secret handshake only we know", SpiceLevel.SWEET), QuestionTheme.TEASE),
        TaggedWyrItem(WouldYouRatherPrompt("sw4", "Wake up to breakfast in bed every Sunday", "Get a surprise forehead kiss every morning", SpiceLevel.SWEET), QuestionTheme.TOUCH),
        TaggedWyrItem(WouldYouRatherPrompt("sw5", "Spend a whole snowed-in day under one blanket", "Take a spontaneous sunrise road trip", SpiceLevel.SWEET), QuestionTheme.ADVENTURE),
        TaggedWyrItem(WouldYouRatherPrompt("sw6", "Always know when the other is thinking of you", "Always hear their laugh when you miss them", SpiceLevel.SWEET), QuestionTheme.CONFESSION),
        TaggedWyrItem(WouldYouRatherPrompt("sw7", "Slow dance in an empty parking lot at midnight", "Share headphones on a long train ride", SpiceLevel.SWEET), QuestionTheme.ROMANCE),
        TaggedWyrItem(WouldYouRatherPrompt("sw8", "Have a scrapbook of every ticket & photo of us", "Have a playlist of every song that reminds us of each other", SpiceLevel.SWEET), QuestionTheme.ROMANCE),
        TaggedWyrItem(WouldYouRatherPrompt("sw9", "Spend 3 hours talking in a parked car", "Spend 3 hours wandering a night market holding hands", SpiceLevel.SWEET), QuestionTheme.ADVENTURE),
        TaggedWyrItem(WouldYouRatherPrompt("sw10", "Get a 2-minute tight hug whenever you want", "Get endless head scratches while watching movies", SpiceLevel.SWEET), QuestionTheme.TOUCH),
        TaggedWyrItem(WouldYouRatherPrompt("sw11", "Relive our very first favorite memory together", "Fast-forward for a 5-minute sneak peek of our future home", SpiceLevel.SWEET), QuestionTheme.CONFESSION),
        TaggedWyrItem(WouldYouRatherPrompt("sw12", "Bake messy midnight brownies together", "Order 4 different desserts and rate them in bed", SpiceLevel.SWEET), QuestionTheme.ADVENTURE),
        TaggedWyrItem(WouldYouRatherPrompt("sw13", "Get a surprise bouquet on a random Tuesday", "Get a 3-minute rambling voice note about why you love me", SpiceLevel.SWEET), QuestionTheme.ROMANCE),
        TaggedWyrItem(WouldYouRatherPrompt("sw14", "Always hold hands while falling asleep", "Always steal the warm spot in the bed", SpiceLevel.SWEET), QuestionTheme.TOUCH),
        TaggedWyrItem(WouldYouRatherPrompt("sw15", "Read each other's minds for 10 minutes", "Never have to spend a weekend apart again", SpiceLevel.SWEET), QuestionTheme.CONFESSION),

        // ==================== FLIRTY (15) ====================
        TaggedWyrItem(WouldYouRatherPrompt("fw1", "Receive a spicy surprise voice note mid-day", "Get pulled in for a sudden doorway kiss", SpiceLevel.FLIRTY), QuestionTheme.TEASE),
        TaggedWyrItem(WouldYouRatherPrompt("fw2", "Slow dance in the kitchen at midnight", "Steamy bubble bath with candles", SpiceLevel.FLIRTY), QuestionTheme.ROMANCE),
        TaggedWyrItem(WouldYouRatherPrompt("fw3", "Tease each other in public without touching", "Sneak away from a party for 10 minutes", SpiceLevel.FLIRTY), QuestionTheme.TEASE),
        TaggedWyrItem(WouldYouRatherPrompt("fw4", "Get kissed slowly along the neck", "Get pulled onto my lap mid-conversation", SpiceLevel.FLIRTY), QuestionTheme.TOUCH),
        TaggedWyrItem(WouldYouRatherPrompt("fw5", "Have me whisper bold compliments all evening", "Have me steal kisses every time nobody looks", SpiceLevel.FLIRTY), QuestionTheme.TEASE),
        TaggedWyrItem(WouldYouRatherPrompt("fw6", "Kiss in the pouring rain like a movie", "Make out in the backseat overlooking city lights", SpiceLevel.FLIRTY), QuestionTheme.ADVENTURE),
        TaggedWyrItem(WouldYouRatherPrompt("fw7", "Wear only my oversized shirt all morning", "Dress to kill for a candlelit dinner for two", SpiceLevel.FLIRTY), QuestionTheme.TEASE),
        TaggedWyrItem(WouldYouRatherPrompt("fw8", "Play a staring contest where the loser owes a kiss", "Play 20 flirty questions where you can't dodge", SpiceLevel.FLIRTY), QuestionTheme.CONFESSION),
        TaggedWyrItem(WouldYouRatherPrompt("fw9", "Get a slow, teasing back massage with warm oil", "Get breathless kisses pinned softly against the door", SpiceLevel.FLIRTY), QuestionTheme.TOUCH),
        TaggedWyrItem(WouldYouRatherPrompt("fw10", "Send one bold secret photo you delete after 10 seconds", "Leave a whispered 30-second after-hours voice note", SpiceLevel.FLIRTY), QuestionTheme.CONFESSION),
        TaggedWyrItem(WouldYouRatherPrompt("fw11", "Have our hands tangled under a restaurant table", "Skip dessert and head straight home early", SpiceLevel.FLIRTY), QuestionTheme.ADVENTURE),
        TaggedWyrItem(WouldYouRatherPrompt("fw12", "Be woken up with soft kisses on your collarbone", "Stay up till 3 AM laughing and kissing in the dark", SpiceLevel.FLIRTY), QuestionTheme.TOUCH),
        TaggedWyrItem(WouldYouRatherPrompt("fw13", "Let me pick your outfit for tonight's date", "Let me plan the entire night as a total mystery", SpiceLevel.FLIRTY), QuestionTheme.ADVENTURE),
        TaggedWyrItem(WouldYouRatherPrompt("fw14", "Hear the exact moment you knew you wanted me", "Hear what you daydream about when we're apart", SpiceLevel.FLIRTY), QuestionTheme.CONFESSION),
        TaggedWyrItem(WouldYouRatherPrompt("fw15", "One long agonizingly slow kiss", "Ten playful impatient kisses in a row", SpiceLevel.FLIRTY), QuestionTheme.TOUCH),

        // ==================== BOLD / AFTER-DARK (15) ====================
        TaggedWyrItem(WouldYouRatherPrompt("bw1", "Play Truth or Dare with zero skips tonight", "Let your partner call every shot on date night", SpiceLevel.BOLD), QuestionTheme.MIDNIGHT),
        TaggedWyrItem(WouldYouRatherPrompt("bw2", "Weekend locked in a secluded mountain cabin", "Luxury suite with a private rooftop hot tub", SpiceLevel.BOLD), QuestionTheme.ADVENTURE),
        TaggedWyrItem(WouldYouRatherPrompt("bw3", "Slow teasing buildup all evening", "Immediate 'drop everything' reunion", SpiceLevel.BOLD), QuestionTheme.MIDNIGHT),
        TaggedWyrItem(WouldYouRatherPrompt("bw4", "Be blindfolded while I trace kisses anywhere", "Be the one in complete control for 20 minutes", SpiceLevel.BOLD), QuestionTheme.TOUCH),
        TaggedWyrItem(WouldYouRatherPrompt("bw5", "Confess your wildest secret daydream about us", "Act out a daring prompt without saying a word", SpiceLevel.BOLD), QuestionTheme.CONFESSION),
        TaggedWyrItem(WouldYouRatherPrompt("bw6", "Spend the night with only candlelight & soft R&B", "Spend the night in pitch darkness relying only on touch", SpiceLevel.BOLD), QuestionTheme.MIDNIGHT),
        TaggedWyrItem(WouldYouRatherPrompt("bw7", "Have me tease you until you beg for a kiss", "Take charge and pull me in first without warning", SpiceLevel.BOLD), QuestionTheme.TEASE),
        TaggedWyrItem(WouldYouRatherPrompt("bw8", "Late-night steamy shower together after a long day", "Lazy morning tangled in sheets until noon", SpiceLevel.BOLD), QuestionTheme.TOUCH),
        TaggedWyrItem(WouldYouRatherPrompt("bw9", "Whisper every bold thought straight into my ear", "Text me what you want from across a crowded room", SpiceLevel.BOLD), QuestionTheme.TEASE),
        TaggedWyrItem(WouldYouRatherPrompt("bw10", "Try a brand-new spicy dare neither of us has done", "Perfect our favorite after-dark move all night", SpiceLevel.BOLD), QuestionTheme.MIDNIGHT),
        TaggedWyrItem(WouldYouRatherPrompt("bw11", "Hands pinned softly while I kiss your neck", "Fingers tangled in my hair guiding every move", SpiceLevel.BOLD), QuestionTheme.TOUCH),
        TaggedWyrItem(WouldYouRatherPrompt("bw12", "Truth: answer 3 unfiltered midnight questions", "Dare: 60 seconds of nonstop kissing with zero talking", SpiceLevel.BOLD), QuestionTheme.CONFESSION),
        TaggedWyrItem(WouldYouRatherPrompt("bw13", "A spontaneous night in a hotel with room service only", "A private beach night under the stars alone", SpiceLevel.BOLD), QuestionTheme.ADVENTURE),
        TaggedWyrItem(WouldYouRatherPrompt("bw14", "Keep eye contact the entire time tonight", "Close your eyes and focus on every touch & whisper", SpiceLevel.BOLD), QuestionTheme.MIDNIGHT),
        TaggedWyrItem(WouldYouRatherPrompt("bw15", "Leave a subtle mark only we know about", "Leave a secret message that has me counting the hours", SpiceLevel.BOLD), QuestionTheme.TEASE)
    )

    /**
     * Computes Chemistry Heat (0..100) from base SpiceLevel + recent match/clash history.
     * Matching accelerates heat; clashes add playful tension; heat naturally escalates across rounds.
     */
    fun computeHeatScore(baseSpice: SpiceLevel, history: List<RoundOutcome>): Int {
        val base = when (baseSpice) {
            SpiceLevel.SWEET -> 24
            SpiceLevel.FLIRTY -> 52
            SpiceLevel.BOLD -> 74
        }
        var delta = 0
        history.takeLast(8).forEach { entry ->
            delta += if (entry.matched) 8 else 4
        }
        return (base + delta).coerceIn(10, 100)
    }

    /**
     * Finds the couple's dominant theme based on their recent rounds (matching weights 2x).
     */
    fun dominantTheme(history: List<RoundOutcome>, fallback: QuestionTheme): QuestionTheme {
        if (history.isEmpty()) return fallback
        val weights = mutableMapOf<QuestionTheme, Int>()
        history.takeLast(6).forEachIndexed { idx, entry ->
            val recencyBoost = idx + 1
            val matchBoost = if (entry.matched) 2 else 1
            weights[entry.theme] = (weights[entry.theme] ?: 0) + (recencyBoost * matchBoost)
        }
        return weights.maxByOrNull { it.value }?.key ?: fallback
    }

    /**
     * Generates the question for any Couple Quiz roundIndex.
     * - Base rounds pull deterministically from the expanded 48-question bank (shuffled per coupleCode).
     * - Every 3rd round after round 1 (round 2, 5, 8, ...) OR once past the base deck,
     *   our local offline engine synthesizes a tailored follow-up that builds directly on
     *   the couple's previous answers and escalates the spice!
     */
    fun generateQuizRound(
        coupleCode: String,
        roundIndex: Int,
        baseSpice: SpiceLevel,
        history: List<RoundOutcome>
    ): GeneratedQuizRound {
        val safeRound = roundIndex.coerceAtLeast(0)
        val heat = computeHeatScore(baseSpice, history)
        val lastOutcome = history.lastOrNull()

        val shouldSynthesizeFollowUp = safeRound >= 2 && (safeRound % 3 == 2 || safeRound >= 16)
        if (shouldSynthesizeFollowUp && lastOutcome != null) {
            return synthesizeAdaptiveQuizFollowUp(
                coupleCode = coupleCode,
                roundIndex = safeRound,
                baseSpice = baseSpice,
                heat = heat,
                lastOutcome = lastOutcome,
                history = history
            )
        }

        // Pull deterministically from expanded bank, letting high heat unlock spicier cards smoothly
        val effectiveSpice = when {
            heat >= 82 -> SpiceLevel.BOLD
            heat >= 50 && baseSpice == SpiceLevel.SWEET && safeRound >= 6 -> SpiceLevel.FLIRTY
            else -> baseSpice
        }
        val pool = taggedQuizBank.filter { it.question.spiceLevel == effectiveSpice }
            .ifEmpty { taggedQuizBank }
        val shuffled = deterministicOrder(pool, seedFor(coupleCode, effectiveSpice.ordinal * 37 + 11))
        val picked = shuffled[safeRound % shuffled.size]
        val escalatedBadge = if (effectiveSpice != baseSpice) "🔥 Heat escalated to ${effectiveSpice.title}" else ""

        return GeneratedQuizRound(
            question = picked.question,
            theme = picked.theme,
            heatScore = heat,
            isAdaptiveFollowUp = escalatedBadge.isNotEmpty(),
            adaptiveTag = escalatedBadge
        )
    }

    /**
     * Generates the dilemma for any Would You Rather roundIndex.
     * Synthesizes answer-aware follow-up dilemmas every 3rd round (2, 5, 8, ...) and beyond the base deck.
     */
    fun generateWyrRound(
        coupleCode: String,
        roundIndex: Int,
        baseSpice: SpiceLevel,
        history: List<RoundOutcome>
    ): GeneratedWyrRound {
        val safeRound = roundIndex.coerceAtLeast(0)
        val heat = computeHeatScore(baseSpice, history)
        val lastOutcome = history.lastOrNull()

        val shouldSynthesizeFollowUp = safeRound >= 2 && (safeRound % 3 == 2 || safeRound >= 15)
        if (shouldSynthesizeFollowUp && lastOutcome != null) {
            return synthesizeAdaptiveWyrFollowUp(
                coupleCode = coupleCode,
                roundIndex = safeRound,
                baseSpice = baseSpice,
                heat = heat,
                lastOutcome = lastOutcome,
                history = history
            )
        }

        val effectiveSpice = when {
            heat >= 82 -> SpiceLevel.BOLD
            heat >= 50 && baseSpice == SpiceLevel.SWEET && safeRound >= 6 -> SpiceLevel.FLIRTY
            else -> baseSpice
        }
        val pool = taggedWyrBank.filter { it.prompt.spiceLevel == effectiveSpice }
            .ifEmpty { taggedWyrBank }
        val shuffled = deterministicOrder(pool, seedFor(coupleCode, effectiveSpice.ordinal * 53 + 29))
        val picked = shuffled[safeRound % shuffled.size]
        val escalatedBadge = if (effectiveSpice != baseSpice) "🔥 Heat escalated to ${effectiveSpice.title}" else ""

        return GeneratedWyrRound(
            prompt = picked.prompt,
            theme = picked.theme,
            heatScore = heat,
            isAdaptiveFollowUp = escalatedBadge.isNotEmpty(),
            adaptiveTag = escalatedBadge
        )
    }

    private fun synthesizeAdaptiveQuizFollowUp(
        coupleCode: String,
        roundIndex: Int,
        baseSpice: SpiceLevel,
        heat: Int,
        lastOutcome: RoundOutcome,
        history: List<RoundOutcome>
    ): GeneratedQuizRound {
        val focusTheme = dominantTheme(history, lastOutcome.theme)
        val seed = seedFor(coupleCode, roundIndex * 997 + lastOutcome.choiceSignature * 31 + focusTheme.ordinal * 7)
        val hotTier = heat >= 70 || baseSpice == SpiceLevel.BOLD
        val midTier = heat >= 45 || baseSpice == SpiceLevel.FLIRTY

        val leadIn = if (lastOutcome.matched) {
            pick(
                listOf(
                    "You two synced on ${lastOutcome.theme.label.lowercase()} — let's turn it up:",
                    "Matched vibes! Taking ${focusTheme.label.lowercase()} one level deeper:",
                    "Chemistry check passed 🔥 Building on your last pick:"
                ),
                seed
            )
        } else {
            pick(
                listOf(
                    "Opposite picks last round — settle the tension:",
                    "Clashing vibes make it hotter 😏 Compromise check:",
                    "You two picked differently — meet halfway on this:"
                ),
                seed
            )
        }

        val (stem, options) = when (focusTheme) {
            QuestionTheme.TOUCH -> if (hotTier) {
                pick(
                    listOf(
                        "How long should the slow-burn touch last before we give in?" to listOf(
                            "Tease slowly until neither can wait",
                            "Zero delay — pull me in right away",
                            "Hands exploring with eyes locked",
                            "Whispered countdown from 10 to 0"
                        ),
                        "Next time our hands find each other in the dark, what's the move?" to listOf(
                            "Trace kisses from jawline to collarbone",
                            "Pull you onto my lap without a word",
                            "Fingers laced & pinned gently above you",
                            "Slow warm massage that turns breathless"
                        )
                    ),
                    seed + 3
                )
            } else {
                pick(
                    listOf(
                        "What's the sweetest way to turn a simple hug into butterflies?" to listOf(
                            "Hold on 5 seconds longer than usual",
                            "Soft kiss right beneath the ear",
                            "Resting foreheads together in silence",
                            "Spinning me around & pulling me back"
                        ),
                        "Which closeness moment do you crave most right now?" to listOf(
                            "Warm hand resting on your waist",
                            "Head on your chest listening to your heartbeat",
                            "Fingers tangled under a blanket",
                            "A slow unexpected doorway kiss"
                        )
                    ),
                    seed + 5
                )
            }

            QuestionTheme.TEASE -> if (hotTier || midTier) {
                pick(
                    listOf(
                        "Your partner owes you one bold teasing dare tonight — what do you claim?" to listOf(
                            "A 15-second whispered voice note right now",
                            "No-hands kiss challenge for 30 seconds",
                            "Confess the hottest thought you had today",
                            "Winner controls tonight's entire vibe"
                        ),
                        "How do we keep the playful tension electric all week?" to listOf(
                            "Secret coded flirty texts during the day",
                            "Surprise midnight photo drops",
                            "Stealing kisses when we have 10 seconds alone",
                            "Leaving teasing hints about our next meet"
                        )
                    ),
                    seed + 7
                )
            } else {
                pick(
                    listOf(
                        "If we turn tonight into a playful competition, what's the prize?" to listOf(
                            "Loser plans the entire next date",
                            "Winner gets a 10-minute back rub",
                            "Loser records a dramatic love speech",
                            "Winner gets three wishes anytime"
                        )
                    ),
                    seed + 9
                )
            }

            QuestionTheme.CONFESSION -> if (hotTier) {
                pick(
                    listOf(
                        "No holding back — what's one thing you secretly want me to do more?" to listOf(
                            "Take charge and surprise me unexpectedly",
                            "Whisper exactly what you're thinking",
                            "Linger longer during every single kiss",
                            "Text me the unfiltered version of your cravings"
                        ),
                        "If we unlock one late-night confession right now, which topic goes first?" to listOf(
                            "A fantasy scenario we haven't tried yet",
                            "The exact moment you wanted me most",
                            "What outfit or gesture completely undoes you",
                            "One dare you're shy to ask for"
                        )
                    ),
                    seed + 11
                )
            } else {
                pick(
                    listOf(
                        "Which unspoken truth about us makes your heart warmest?" to listOf(
                            "How safe & electric it feels together",
                            "Still getting butterflies every single time",
                            "Thinking of you at random quiet moments",
                            "Knowing nobody else gets us like this"
                        )
                    ),
                    seed + 13
                )
            }

            QuestionTheme.ADVENTURE -> if (hotTier || midTier) {
                pick(
                    listOf(
                        "Upgrade our next getaway — what makes it unforgettable?" to listOf(
                            "Private midnight swim under the stars",
                            "Checking in early & locking out the world",
                            "Late-night rooftop drinks & slow kisses",
                            "Packing a secret bag of date-night dares"
                        ),
                        "Spontaneous midnight escape — pick our secret hideout:" to listOf(
                            "Parked overlooking city lights, seats reclined",
                            "Boutique hotel suite with candles & rain",
                            "Quiet beach blanket with only the waves",
                            "Cozy loft with music low & phones off"
                        )
                    ),
                    seed + 17
                )
            } else {
                pick(
                    listOf(
                        "Build our dream 24-hour couple itinerary:" to listOf(
                            "Sunrise coffee + hidden scenic drive",
                            "Bookstore crawl + cozy rainy café",
                            "Street food tour + sunset picnic",
                            "Late-night drive + singing off-key"
                        )
                    ),
                    seed + 19
                )
            }

            QuestionTheme.MIDNIGHT -> pick(
                listOf(
                    "Heat is climbing 🔥 What's the next move once the door clicks shut?" to listOf(
                        "Slow eye contact before a single word",
                        "Pull me in breathless against the wall",
                        "Dim lights, warm touch, zero rush",
                        "Take turns giving one dare at a time"
                    ),
                    "Midnight rulebook — which energy wins tonight?" to listOf(
                        "Soft whispers & lingering fingertips",
                        "Bold, fearless, zero hesitation",
                        "Playful teasing until someone caves",
                        "Pure slow-burn chemistry all night"
                    )
                ),
                seed + 23
            )

            QuestionTheme.ROMANCE -> if (midTier || hotTier) {
                pick(
                    listOf(
                        "Let's mix romance with heat — what's the ultimate scene?" to listOf(
                            "Slow dancing barefoot that turns into kissing",
                            "Candlelit bath with soft music playing",
                            "Whispering secrets tangled under silk sheets",
                            "Stolen kisses while cooking dinner together"
                        )
                    ),
                    seed + 29
                )
            } else {
                pick(
                    listOf(
                        "Build on that sweet energy — what's our signature couple ritual?" to listOf(
                            "Goodnight voice notes when apart",
                            "Sunday slow mornings with zero alarms",
                            "Long hugs every time we say hello",
                            "Monthly surprise mini-dates"
                        )
                    ),
                    seed + 31
                )
            }
        }

        val escalatedSpice = when {
            heat >= 75 -> SpiceLevel.BOLD
            heat >= 45 -> SpiceLevel.FLIRTY
            else -> baseSpice
        }
        val tag = if (lastOutcome.matched) {
            "✨ Built from your match · ${focusTheme.emoji} ${focusTheme.label}"
        } else {
            "✨ Built from your clash · ${focusTheme.emoji} ${focusTheme.label}"
        }

        return GeneratedQuizRound(
            question = CoupleQuizQuestion(
                id = "adaptive_q_${roundIndex}_${abs(seed % 10000)}",
                prompt = "$leadIn $stem",
                options = options,
                spiceLevel = escalatedSpice
            ),
            theme = focusTheme,
            heatScore = heat,
            isAdaptiveFollowUp = true,
            adaptiveTag = tag
        )
    }

    private fun synthesizeAdaptiveWyrFollowUp(
        coupleCode: String,
        roundIndex: Int,
        baseSpice: SpiceLevel,
        heat: Int,
        lastOutcome: RoundOutcome,
        history: List<RoundOutcome>
    ): GeneratedWyrRound {
        val focusTheme = dominantTheme(history, lastOutcome.theme)
        val seed = seedFor(coupleCode, roundIndex * 1013 + lastOutcome.choiceSignature * 41 + focusTheme.ordinal * 13)
        val hotTier = heat >= 70 || baseSpice == SpiceLevel.BOLD
        val midTier = heat >= 45 || baseSpice == SpiceLevel.FLIRTY

        val pair = when (focusTheme) {
            QuestionTheme.TOUCH -> if (hotTier) {
                pick(
                    listOf(
                        "10 minutes of slow teasing kisses with zero rushing" to "Skip the buildup and pull each other straight into bed",
                        "Be blindfolded while I kiss wherever I choose" to "Pin my wrists gently and set the pace yourself",
                        "Warm fingertips tracing slowly along your back" to "Breathless neck kisses from behind"
                    ),
                    seed
                )
            } else {
                pick(
                    listOf(
                        "Get pulled into a surprise slow hug from behind" to "Fall asleep tangled with fingers intertwined",
                        "Get soft kisses on your forehead & cheeks all night" to "Have me play with your hair until you drift off"
                    ),
                    seed + 3
                )
            }

            QuestionTheme.TEASE -> if (hotTier || midTier) {
                pick(
                    listOf(
                        "Trade one daring dare right now on this call/chat" to "Save up 3 wild dares for the next time we lock the door",
                        "Whisper something bold in my ear in a crowded room" to "Sneak away together to a quiet corner for 5 minutes",
                        "Send a teasing hint every hour until tonight" to "Show up completely unannounced and surprise me"
                    ),
                    seed + 5
                )
            } else {
                pick(
                    listOf(
                        "Winner of our next game gets a custom love voice note" to "Loser has to cook or treat the winner on our next date"
                    ),
                    seed + 7
                )
            }

            QuestionTheme.CONFESSION -> if (hotTier) {
                pick(
                    listOf(
                        "Voice-note a raw 20-second after-dark confession right now" to "Answer 3 unfiltered truth questions with zero skips",
                        "Reveal the hottest daydream you've had about us" to "Tell me the one move that makes you lose all composure"
                    ),
                    seed + 11
                )
            } else {
                pick(
                    listOf(
                        "Tell me the exact moment you first fell for me" to "Tell me your favorite tiny memory nobody else knows"
                    ),
                    seed + 13
                )
            }

            QuestionTheme.ADVENTURE -> if (hotTier || midTier) {
                pick(
                    listOf(
                        "Private midnight hot tub under the stars" to "Candlelit hotel suite with Do Not Disturb on all weekend",
                        "Make out overlooking the city lights at 2 AM" to "Rain-soaked cozy cabin with a fireplace & zero Wi-Fi"
                    ),
                    seed + 17
                )
            } else {
                pick(
                    listOf(
                        "Spontaneous midnight drive with snacks & our playlist" to "Secret sunset picnic with blankets & fairy lights"
                    ),
                    seed + 19
                )
            }

            QuestionTheme.MIDNIGHT -> pick(
                listOf(
                    "Total slow-burn teasing for an hour before kissing" to "Instant breathless reunion the second the door shuts",
                    "You're in complete control of tonight's after-dark script" to "Surrender control and let me surprise you completely",
                    "Candlelight & soft R&B with intense eye contact" to "Pitch dark room focusing only on whispers & touch"
                ),
                seed + 23
            )

            QuestionTheme.ROMANCE -> if (midTier || hotTier) {
                pick(
                    listOf(
                        "Slow dance barefoot in the kitchen till it turns into kissing" to "Steamy candlelit bath with whispered confessions",
                        "Morning coffee in bed wearing nothing but my shirt" to "Midnight balcony stargazing wrapped in one blanket"
                    ),
                    seed + 29
                )
            } else {
                pick(
                    listOf(
                        "Write each other a secret letter to open next month" to "Spend an entire Sunday with phones off just cuddling"
                    ),
                    seed + 31
                )
            }
        }

        val escalatedSpice = when {
            heat >= 75 -> SpiceLevel.BOLD
            heat >= 45 -> SpiceLevel.FLIRTY
            else -> baseSpice
        }
        val tag = if (lastOutcome.matched) {
            "✨ Spiced up from your match · ${focusTheme.emoji} ${focusTheme.label}"
        } else {
            "✨ Tension twist from your picks · ${focusTheme.emoji} ${focusTheme.label}"
        }

        return GeneratedWyrRound(
            prompt = WouldYouRatherPrompt(
                id = "adaptive_wyr_${roundIndex}_${abs(seed % 10000)}",
                optionA = pair.first,
                optionB = pair.second,
                spiceLevel = escalatedSpice
            ),
            theme = focusTheme,
            heatScore = heat,
            isAdaptiveFollowUp = true,
            adaptiveTag = tag
        )
    }

    private fun seedFor(coupleCode: String, salt: Int): Int {
        var h = coupleCode.hashCode() * 31 + salt * 17
        h = h xor (h ushr 16)
        h *= 0x45d9f3b
        h = h xor (h ushr 16)
        return h
    }

    private fun <T> pick(items: List<T>, seed: Int): T {
        val idx = ((seed % items.size) + items.size) % items.size
        return items[idx]
    }

    private fun <T> deterministicOrder(items: List<T>, seed: Int): List<T> {
        val copy = items.toMutableList()
        var s = seed.toLong() and 0xFFFFFFFFL
        for (i in copy.lastIndex downTo 1) {
            s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val j = (s % (i + 1)).toInt()
            val tmp = copy[i]
            copy[i] = copy[j]
            copy[j] = tmp
        }
        return copy
    }
}
