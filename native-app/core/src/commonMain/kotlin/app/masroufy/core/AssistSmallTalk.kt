package app.masroufy.core

/**
 * الكلام العادي (تحية · شكر · سلام · «مين انت؟» · «تقدر تعمل إيه؟» …) والتحكم في المساعد (محادثة جديدة · السجل · اللي اتعلمه · التعلّم).
 * التحية جوه سؤال حقيقي بتبقى **أول الرد بس** («السلام عليكم، صرفت كام؟» ⇒ «وعليكم السلام…» + الرقم).
 */
enum class GreetingKind { SALAM, MORNING, EVENING, PLAIN }

internal object AssistTalkWords {
    // «السلام» كلمة كاملة بس («مع السلامة» وداع مش سلام)
    val SALAM = vocab("السلام عليكم", "سلام عليكم", "السلام", "salam", "assalamu alaikum", fuzzy = false, exact = true)
    val MORNING = vocab("صباح الخير", "صباح النور", "صباحو", "good morning")
    val EVENING = vocab("مساء الخير", "مساء النور", "مساكم", "good evening")
    // «إزيك» و«كيفك» تحية (التصميم) — «كيف حالك» و«عامل إيه» سؤال عن الحال
    val HELLO = vocab("اهلا", "اهلين", "هلا", "هلا والله", "مرحبا", "مرحبتين", "هاي", "ازيك", "كيفك", "hi", "hello", "hey", "يا هلا", fuzzy = false, exact = true)
    val HOW_ARE_YOU = vocab("عامل ايه", "عامله ايه", "كيف حالك", "كيف الحال", "شخبارك", "اخبارك", "how are you", "how r u", "شلونك", fuzzy = false, exact = true)
    val THANKS = vocab("شكرا", "شكر", "تسلم", "تسلمي", "مشكور", "يعطيك العافيه", "الله يعطيك العافيه", "ميرسي", "جزاك الله خير", "thanks", "thank you", "thx", "متشكر")
    val BYE = vocab("باي", "مع السلامه", "تصبح على خير", "تصبح علي خير", "في امان الله", "bye", "goodbye", "good night", "سلام", fuzzy = false, exact = true)
    val WHO = vocab("مين انت", "من انت", "انت مين", "وش انت", "انت ذكاء اصطناعي", "انت بوت", "انت روبوت", "who are you", "are you ai", "are you a bot", "what are you")
    val HELP = vocab("تقدر تعمل ايه", "ساعدني", "ماذا يمكنك", "وش تقدر تسوي", "ايش تقدر", "تقدر تساعدني", "help", "what can you do", "بتعمل ايه")
    val PRAISE = vocab("شاطر", "حلو كده", "ممتاز", "برافو", "جميل", "روعه", "good job", "great", "nice", "well done", "احسنت", fuzzy = false, exact = true)
    val FRUSTRATION = vocab(
        "مش فاهم", "مو فاهم", "ما فهمت", "مفيش فايده", "مافي فايده", "غبي", "useless", "stupid", "you don't understand", "you dont understand",
        "ما تفهم", "مبتفهمش",
    )
    val ADVICE = vocab(
        "استثمر في", "استثمر فين", "اشتري ذهب", "اشتري اسهم", "اشتري ولا", "هل اشتري", "اشتري ايه", "ابيع ولا", "should i buy", "should i invest",
        "invest in", "should i sell", "انصحني اشتري",
    )
    val NEW_CONVERSATION = vocab("محادثه جديده", "ابدا من جديد", "نبدا من الاول", "نبدا من جديد", "ابدا محادثه", "new chat", "new conversation", "start over")
    val HISTORY = vocab("المحادثات القديمه", "سجل المحادثات", "كلامنا اللي فات", "المحادثات السابقه", "محادثاتي", "history", "old chats", "chat history")

    /** «import history» = دفعات الاستيراد مش سجل المحادثات. */
    val NOT_HISTORY = vocab("import", "استيراد", "الاستيرادات", fuzzy = false)
    val MEMORY = vocab("اتعلمته عني", "اتعلمت عني", "تعرف عني", "تعرفه عني", "وش تعرف عني", "ماذا تعرف عني", "what do you know about me", "what have you learned")
    val LEARN_OFF = vocab("بطل تتعلم", "اوقف التعلم", "وقف التعلم", "لا تتعلم", "ما تتعلمش", "stop learning", "اقفل التعلم")
    val LEARN_ON = vocab("ارجع اتعلم", "رجع التعلم", "شغل التعلم", "ابدا التعلم", "start learning", "resume learning", "ارجع تعلم", "اتعلم من اسئلتي")
    val FORGET = vocab("انسى اللي اتعلمته", "انسي اللي اتعلمته", "امسح ما تعلمته", "امسح اللي اتعلمته", "امسح كل اللي تعرفه", "forget everything", "forget what you learned")
    val CONFIRM = setOf(
        "ايوه", "ايوا", "اه", "نعم", "اي", "تمام", "اوكي", "اوك", "ok", "okay", "yes", "yep", "yeah", "احفظ", "احفظها", "سجل", "سجلها", "سجله", "اكيد", "ماشي",
        "طيب", "صح", "يلا", "تم", "confirm", "save", "اكد", "اكدها", "موافق", "زين", "sure",
    ).map(::assistNormalize).toSet()
    val CANCEL = setOf("لا", "لاء", "لع", "no", "nope", "cancel", "الغي", "الغيها", "الغاء", "بلاش", "بلاها", "سيبك", "انسى", "انسي", "مش", "عايز", "عاوز", "ما", "ابي", "ابغي", "مابي")
        .map(::assistNormalize).toSet()
    val CANCEL_CORE = setOf("لا", "لاء", "لع", "no", "nope", "cancel", "الغي", "الغيها", "الغاء", "بلاش", "بلاها", "مابي").map(::assistNormalize).toSet()
    val FILLER = setOf("يا", "مصروفي", "لو", "سمحت", "من", "فضلك", "please", "pls", "plz", "خلاص", "كده", "هيك", "هكذا", "و", "شكرا", "thanks", "بس", "حبيبي")
        .map(::assistNormalize).toSet()
}

fun detectGreeting(s: AssistSignals): GreetingKind? = when {
    s.has(AssistTalkWords.SALAM) -> GreetingKind.SALAM
    s.has(AssistTalkWords.MORNING) -> GreetingKind.MORNING
    s.has(AssistTalkWords.EVENING) -> GreetingKind.EVENING
    s.has(AssistTalkWords.HELLO) -> GreetingKind.PLAIN
    else -> null
}

/** الكلام العادي اللي مالوش علاقة بالبيانات — بعد ما كل حاجة تانية فشلت (إلا طلب النصيحة: قبل الشاشات عشان «أشتري ذهب؟» ما يفتحش الاستثمار). */
internal fun smallTalkOf(s: AssistSignals): AssistIntent? = when {
    s.has(AssistTalkWords.WHO) -> AssistIntent.WHO_ARE_YOU
    s.has(AssistTalkWords.HELP) -> AssistIntent.HELP
    s.has(AssistTalkWords.FRUSTRATION) -> AssistIntent.FRUSTRATION
    s.has(AssistTalkWords.HOW_ARE_YOU) -> AssistIntent.HOW_ARE_YOU
    s.has(AssistTalkWords.THANKS) -> AssistIntent.THANKS
    s.has(AssistTalkWords.BYE) && !s.has(AssistTalkWords.SALAM) -> AssistIntent.BYE
    s.has(AssistTalkWords.PRAISE) -> AssistIntent.PRAISE
    detectGreeting(s) != null -> AssistIntent.GREETING
    else -> null
}

/** التحكم في المساعد نفسه. `true/false` في [Pair.second] = التعلّم يشتغل/يقف (لـ[AssistIntent.LEARNING_SWITCH] بس). */
internal fun controlOf(s: AssistSignals): Pair<AssistIntent, Boolean?>? = when {
    s.has(AssistTalkWords.FORGET) -> AssistIntent.FORGET_ALL to null
    s.has(AssistTalkWords.LEARN_OFF) -> AssistIntent.LEARNING_SWITCH to false
    s.has(AssistTalkWords.LEARN_ON) -> AssistIntent.LEARNING_SWITCH to true
    s.has(AssistTalkWords.MEMORY) -> AssistIntent.MEMORY to null
    s.has(AssistTalkWords.NEW_CONVERSATION) -> AssistIntent.NEW_CONVERSATION to null
    s.has(AssistTalkWords.HISTORY) && !s.has(AssistTalkWords.NOT_HISTORY) -> AssistIntent.HISTORY to null
    else -> null
}

/** «أيوه» / «تمام سجّلها» وكارت مستني ⇒ تأكيد. كلام قصير كله تأكيد أو حشو. */
internal fun isConfirm(s: AssistSignals): Boolean {
    if (s.tokens.isEmpty() || s.tokens.size > 4 || s.hasAmount) return false
    val forms = s.tokens.map { cliticForms(it) }
    return forms.any { f -> f.any { it in AssistTalkWords.CONFIRM } } &&
        forms.all { f -> f.any { it in AssistTalkWords.CONFIRM || it in AssistTalkWords.FILLER } }
}

/** «لا» / «لأ الغيها» / «ما أبي» وكارت مستني ⇒ إلغاء. من غير مبلغ ولا محفظة ولا يوم (دول تعديل: «لا خليها ٢٠»). */
internal fun isCancel(s: AssistSignals): Boolean {
    if (s.tokens.isEmpty() || s.tokens.size > 5 || s.hasAmount || s.dayOffset != null) return false
    if (s.entities.any { it.type == AssistEntityType.WALLET || it.type == AssistEntityType.CATEGORY }) return false
    val forms = s.tokens.map { cliticForms(it) }
    val core = forms.any { f -> f.any { it in AssistTalkWords.CANCEL_CORE } } || containsPhrase(s.tokens, listOf("مش", "عايز")) ||
        containsPhrase(s.tokens, listOf("ما", "ابي"))
    return core && forms.all { f -> f.any { it in AssistTalkWords.CANCEL || it in AssistTalkWords.FILLER } }
}
