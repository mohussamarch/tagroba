package app.masroufy.core

/**
 * تخزين المساعد «مصروفي» (OVERRIDES §78 «الذاكرة والأسئلة في حساب المالك وتتزامن وبتدخل في النسخة الاحتياطية» + الرد ٤ على فرع
 * التصميم: السجل كمان). **كله على مستوى الحساب** (`users/{uid}/…` جنب `alertSettings` و`savingsGoals`) ⇒ القاعدة العامة
 * `users/{uid}/{document=**}` بتغطيه ⇒ مفيش نشر قواعد ولا فهارس.
 *
 * **ليه مش في `profile/main`؟** التطبيق القديم بيحفظ الملف كله فوق بعضه (`setDoc(ref, {...profile})`) وكوتلن كمان (`set` من غير
 * merge) ⇒ أي حقل جديد هناك كان هيتمسح في صمت مع أول حفظ للملف. فالإعدادات الجديدة ليها مجموعتها ([USER_SETTINGS_GROUP]).
 */
const val USER_SETTINGS_GROUP = "userSettings"
const val ASSISTANT_CONVERSATIONS_GROUP = "assistantConversations"
const val ASSISTANT_MESSAGES_GROUP = "assistantMessages"
const val ASSISTANT_TOPICS_GROUP = "assistantTopics"
const val ASSISTANT_FORGOTTEN_GROUP = "assistantForgotten"
const val ASSISTANT_UNKNOWN_GROUP = "assistantUnknown"

/** مجموعات المساعد الخمسة (بتتكتب في النسخة الشاملة مع بعض لو أي واحدة فيها حاجة). */
val ASSISTANT_BACKUP_GROUPS = listOf(ASSISTANT_CONVERSATIONS_GROUP, ASSISTANT_MESSAGES_GROUP, ASSISTANT_TOPICS_GROUP, ASSISTANT_FORGOTTEN_GROUP, ASSISTANT_UNKNOWN_GROUP)

// ─── إعدادات المستخدم (`userSettings/{key}`) ───

/** مصدر اختيار المحفظة الأساسية: الشات · لوحة «+» · تفاصيل المحفظة («اجعلها الأساسية»). */
enum class MainWalletSource(val wire: String) { CHAT("chat"), ADD_SHEET("addSheet"), WALLET_DETAIL("walletDetail");

    companion object {
        fun fromWire(wire: String): MainWalletSource? = entries.firstOrNull { it.wire == wire }
    }
}

sealed interface UserSetting {
    /** معرّف المستند. */
    val key: String
    val updatedAt: String

    /**
     * المحفظة الأساسية لبلد (رد المالك ٢ — 2026-10-09): مستند لكل بلد (`mainWallet.<spaceId>`) — السعودية ومصر كل واحدة لوحدها.
     * المحفظة اللي اتشالت أو اتأرشفت ⇒ كأنها مش متحددة (بيسأل تاني) — عمره ما بيختار محفظة تانية في صمت.
     */
    data class MainWallet(val spaceId: String, val walletId: Id, val setFrom: MainWalletSource, override val updatedAt: String) : UserSetting {
        override val key: String get() = mainWalletKey(spaceId)
    }

    /** مفتاح «يتعلّم من أسئلتي» — بيتكتب `learningOn` بس لو false (من غيره = شغال). */
    data class Assistant(val learningOn: Boolean, override val updatedAt: String) : UserSetting {
        override val key: String get() = ASSISTANT_SETTINGS_KEY
    }

    /**
     * §83 (المالك 2026-10-10: «يقرأ كل تاريخ الرسايل ويحدث نفسه ويشوف ايه اللي ناقص»): **نقطة قراية رسايل البنك للبلد [spaceId]** — مستند لكل
     * بلد (`smsRead.<spaceId>`) في الحساب، فبيعيش بعد مسح البرنامج وعلى جوال جديد (الجهاز بيرجع يقرا من هنا — `SmsCatchUp`).
     * [point] = وقت (ISO): كل رسايل البنك قبله اتعاملت في البلد دي (اتسجلت · اتشالت · مكررة). [asked] = البنوك (المرسلين بعد `smsSenderKey`)
     * اللي اتسألت «اقرأ اللي فات؟» (أيوه أو لأ) — بنك مربوط مش فيها ⇒ يتسأل. أسامي مرسلين البنوك بس — ولا حرف من رسالة.
     */
    data class SmsReadPoint(val spaceId: String, val point: String, val asked: List<String>, override val updatedAt: String) : UserSetting {
        override val key: String get() = smsReadPointKey(spaceId)
    }

    /**
     * صف «أهم 5» في «الأشخاص» بترتيبك (OVERRIDES §84 — `core/PeopleRow.kt`): مستند واحد للحساب (الشخص مشترك بين البلاد). مفيش مستند =
     * لسه ما رتبتش بإيدك ⇒ الافتراضي. التطبيق القديم ما بيقراش `userSettings` ⇒ ما بيتأثرش (زي طريقة الفضة §69.9).
     */
    data class PeopleRow(val personIds: List<Id>, override val updatedAt: String) : UserSetting {
        override val key: String get() = PEOPLE_ROW_KEY
    }

    /**
     * كروت الرئيسية بترتيبك (اختيار المالك 2026-10-15 — `core/HomeCards.kt`): مستند واحد للحساب. [order] أسماء الكروت (`HomeCardKind.wire`)
     * بالترتيب و[hidden] المخفيين. مفيش مستند = لسه ما رتبتش ⇒ الترتيب الأول.
     */
    data class HomeCards(val order: List<String>, val hidden: List<String>, override val updatedAt: String) : UserSetting {
        override val key: String get() = HOME_CARDS_KEY
    }

    /** كارت النصيحة وكارت «ملفك» اللي اتقفلوا بـ«×» (رد المالك 2026-10-16 — `core/HomeTips.kt`): بصمات بس، مش نص ولا أرقام. */
    data class HomeTips(val advisorKey: String?, val profileKey: String?, val profileClosedOn: String?, override val updatedAt: String) : UserSetting {
        override val key: String get() = HOME_TIPS_KEY
    }

    /**
     * «هتبقى معاك كام» لكل بلد (مواصفة الاستثمار §3.2): علامات السنين ([marks] سنين تقويمية — بتفضل لحد ما تتشال بـ«×») والطريق والمبلغ
     * و«بتستثمرها في» (حفظهم سؤال Q8). المدة و«بفلوس النهارده» مش بيتحفظوا (بيفتح دايمًا على 10 سنين بفلوس النهارده — §87-5).
     */
    data class InvestView(
        val spaceId: String, val marks: List<Int>, val path: String?, val amountMinor: Halalas?, val mix: String?, override val updatedAt: String,
    ) : UserSetting {
        override val key: String get() = investViewKey(spaceId)
    }

    /** تاريخ الميلاد (مستوى الحساب — شخص واحد): بيتكتب من حاسبة التقاعد (§87-8) وبيطلع منه «عمرك وقتها». */
    data class BirthDate(val date: IsoDate, override val updatedAt: String) : UserSetting {
        override val key: String get() = BIRTH_DATE_KEY
    }

    /**
     * «حاجاتي» (رد المالك 2026-10-16 ٢أ): الحاجات الشخصية (العربية · الدهب الملبوس) **تدخل في المجموع وفي «هيبقى معاك كام»؟** — اختيار
     * المستخدم، والافتراضي **من غيرها**. مستند واحد على الحساب (`holdingsPrefs`). الحاجات الشخصية بتظهر في «حاجاتي» في الحالتين.
     */
    data class HoldingsPrefs(val includePersonal: Boolean, override val updatedAt: String) : UserSetting {
        override val key: String get() = HOLDINGS_PREFS_KEY
    }

    /** نصايح «الاستثمار» اللي اتقفلت بـ«×» (مواصفة النقل §3.2 — `core/InvestTips.kt`): معرّفات النصايح بس، وعمرها ما ترجع. */
    data class InvestTips(val closed: List<String>, override val updatedAt: String) : UserSetting {
        override val key: String get() = INVEST_TIPS_KEY
    }

    /**
     * كارت الرئيسية «استثماراتك النهارده» (§3.2 — `core/InvestToday.kt`): قيمة كل حاجة (غير الشخصي، القيمة معروفة) آخر يوم اتشاف [date]
     * واليوم اللي قبله [prevDate] — قوايم متوازية (معرّف ↔ قيمة بالهللة) لكل بلد (`investDaily.<spaceId>`).
     */
    data class InvestDaily(
        val spaceId: String,
        val date: IsoDate,
        val ids: List<Id>,
        val values: List<Halalas>,
        val prevDate: IsoDate?,
        val prevIds: List<Id>,
        val prevValues: List<Halalas>,
        override val updatedAt: String,
    ) : UserSetting {
        override val key: String get() = investDailyKey(spaceId)
    }
}

fun investViewKey(spaceId: String) = "investView.$spaceId"

const val BIRTH_DATE_KEY = "birthDate"

const val HOLDINGS_PREFS_KEY = "holdingsPrefs"

const val INVEST_TIPS_KEY = "investTips"

fun investDailyKey(spaceId: String) = "investDaily.$spaceId"

const val ASSISTANT_SETTINGS_KEY = "assistant"

fun mainWalletKey(spaceId: String) = "mainWallet.$spaceId"

fun smsReadPointKey(spaceId: String) = "smsRead.$spaceId"

// ─── «اللي اتعلمته عنك» والأسئلة ───

/**
 * موضوع اتسأل عنه (§78-٥): [topic] من القايمة المقفولة (نية التصميم — مش نص حر) + الاسم الأساسي لو فيه. [key] = معرّف المستند.
 * مرتين أو أكتر ⇒ اقتراح أول ما المحادثة تفتح تحت «بتسأل عنها كتير».
 */
data class AssistTopic(val topic: String, val subjectId: Id?, val askCount: Int, val lastAskedAt: String) {
    val key: String get() = topicKey(topic, subjectId)
}

fun topicKey(topic: String, subjectId: Id?): String = if (subjectId == null) topic else "$topic:$subjectId"

/**
 * علامة «امسح دي» على حاجة **متحسبة** من بيانات المستخدم ومش متخزنة: حاجة من «اللي اتعلمته عنك» (`fact:…`) أو كارت من «أمور لم
 * تُنجزها بعد» اتقفل بـ«×» (`card:…` — رد المالك في النافذة التانية). المعرّف = بصمة المفتاح. المفتاح فيه قيمة الحاجة نفسها، فلو
 * اتغيرت (محل أكتر جديد · فاتورة الشهر الجاي) بترجع تظهر.
 */
data class ForgottenMark(val factKey: String, val createdAt: String) {
    val id: String get() = hashContent(factKey)
}

/**
 * سؤال ما اتفهمش (§78-٧ «السؤال يتحفظ عشان نعلّمه بعدين»): النص لحد ٢٠٠ حرف بعد قص أي ٥ أرقام ورا بعض أو أكتر (CLAUDE.md #11)،
 * بالشكل الموحّد ([normalized] — نفس السؤال بصيغتين كتابة = سطر واحد)، وعدد المرات، والشاشة والبلد اللي اتسأل فيهم.
 */
data class UnknownQuestion(
    val text: String,
    val normalized: String,
    val askCount: Int,
    val firstAskedAt: String,
    val lastAskedAt: String,
    val screen: String,
    val spaceId: String,
) {
    val id: String get() = hashContent(normalized)
}

/** أقصى عدد للأسئلة اللي ما اتفهمتش (نفس النموذج) — الأقدم سؤالًا بيتشال. */
const val MAX_UNKNOWN_QUESTIONS = 30
const val UNKNOWN_TEXT_MAX = 200

private val LONG_DIGITS = Regex("\\d{5,}")

/** أي ٥ أرقام ورا بعض أو أكتر (بعد تحويل الأرقام العربي) ⇒ «••••» + آخر ٤ (CLAUDE.md #11). */
fun maskLongDigits(text: String): String = LONG_DIGITS.replace(latinizeDigits(text)) { "••••" + it.value.takeLast(4) }

/**
 * تسجيل سؤال ما اتفهمش: نفس الشكل الموحّد ⇒ العدد +١ وآخر وقت. فوق [MAX_UNKNOWN_QUESTIONS] ⇒ الأقدم سؤالًا بيتشال.
 * بيرجع (اللي يتحفظ، معرّفات اللي تتشال).
 */
fun recordUnknown(
    existing: List<UnknownQuestion>,
    rawText: String,
    nowIso: String,
    screen: String,
    spaceId: String,
): Pair<UnknownQuestion, List<String>> {
    val normalized = assistNormalize(maskLongDigits(rawText)).take(UNKNOWN_TEXT_MAX)
    val text = clipText(maskLongDigits(rawText.trim()), UNKNOWN_TEXT_MAX)
    val id = hashContent(normalized)
    val old = existing.firstOrNull { it.id == id }
    val saved = old?.copy(text = text, askCount = old.askCount + 1, lastAskedAt = nowIso, screen = screen, spaceId = spaceId)
        ?: UnknownQuestion(text, normalized, 1, nowIso, nowIso, screen, spaceId)
    val all = existing.filter { it.id != id } + saved
    val drop = if (all.size <= MAX_UNKNOWN_QUESTIONS) emptyList()
    else all.filter { it.id != id }.sortedWith(compareBy<UnknownQuestion> { isoInstantMillis(it.lastAskedAt) ?: Long.MIN_VALUE }.thenBy { it.id })
        .take(all.size - MAX_UNKNOWN_QUESTIONS).map { it.id }
    return saved to drop
}

/** «انسخ القايمة»: الأسئلة كنص عادي (المالك بيبعته بنفسه — مفيش حاجة بتتبعت لوحدها). */
fun unknownAsText(questions: List<UnknownQuestion>): String =
    questions.sortedByDescending { isoInstantMillis(it.lastAskedAt) ?: Long.MIN_VALUE }.joinToString("\n") { "${it.text} (${it.askCount})" }

// ─── إشعارات الجرس الممسوحة (رد المالك ٣ — 2026-10-09) ───

/**
 * إشعار اتمسح بـ«×» من الجرس أو صفحة الإشعارات: **على الحساب ويتزامن ويدخل النسخة الشاملة** (`alertDismissals/{id}`) — المحرك ما
 * بيرجّعوش، وكارته في «أمور لم تُنجزها بعد» بيختفي، والنقطة الحمرا على التبويب بتروح. «رجّعه» (٤ ثواني في الشاشة) بيشيل العلامة.
 * المعرّف = بصمة موضوع التنبيه (فيه `|` و`:`). الموضوع لما يخلص (الحاجة اتعملت) العلامة بتتشال لوحدها.
 * [eventKey] = الدرجة اللي اتمسحت (`threadKey|النوع` — §79.2-1): لو التنبيه صعّد لدرجة جديدة (من «قرّبت» لـ«عدّيت» مثلًا) بيرجع **بالدرجة الجديدة
 * بس**. null = كارت بداية من غير إشعار، أو علامة قديمة ⇒ الموضوع كله.
 */
data class AlertDismissal(val threadKey: String, val dismissedAt: String, val eventKey: String? = null) {
    val id: String get() = hashContent(threadKey)
}

const val ALERT_DISMISSALS_GROUP = "alertDismissals"

/**
 * مجموعات المساعد والإعدادات الجديدة في النسخة الشاملة (`BACKUP_GROUPS`): كل واحدة بتتكتب في الملف **لوحدها لو فيها حاجة** ⇒ ملف حساب
 * ما استخدمش المساعد هو هو حرف بحرف زي قبل (ونفس البصمة)، والتطبيق القديم بيقرا مجموعاته بس ويتجاهل الباقي (`src/domain/checkFullBackup.ts`).
 */
val ASSISTANT_ALL_BACKUP_GROUPS: List<String> = listOf(
    USER_SETTINGS_GROUP, ASSISTANT_CONVERSATIONS_GROUP, ASSISTANT_MESSAGES_GROUP, ASSISTANT_TOPICS_GROUP, ASSISTANT_FORGOTTEN_GROUP,
    ASSISTANT_UNKNOWN_GROUP, ALERT_DISMISSALS_GROUP,
)
