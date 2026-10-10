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
}

const val ASSISTANT_SETTINGS_KEY = "assistant"

fun mainWalletKey(spaceId: String) = "mainWallet.$spaceId"

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
