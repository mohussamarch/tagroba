package app.masroufy.core

/**
 * كارت النصيحة (القهوة) وكارت «ملفك» في الرئيسية (رد المالك 2026-10-16): ثابتين فوق برّه «عدّل الكروت»، وعلى كل واحد ×.
 * «ايوة ثابتين و معاهم زرار اكس بحيث اقفلهم ويرجعو تاني لو البرنامج شاف انه يرجع تاني». القفل بيتحفظ في الحساب
 * (`userSettings/homeTips` — [UserSetting.HomeTips]).
 * ❓ قاعدة الرجوع اختيار Claude مستني المالك:
 * - النصيحة بترجع لما تظهر نصيحة جديدة (العنوان أو الكلام اتغير) — [advisorTipKey].
 * - «ملفك» بيرجع لما الاكتمال يتغير أو بعد [PROFILE_TIP_RETURN_DAYS] أيام والملف لسه ناقص. ⚠️ نسبة الاكتمال مالهاش حسبة في كوتلن لسه ⇒
 *   «الاكتمال اتغير» = **السؤال الجاي اتغير** (اتجاوب سؤال) — [profileTipKey].
 */
const val HOME_TIPS_KEY = "homeTips"

const val PROFILE_TIP_RETURN_DAYS = 7

/** اللي اتقفل بـ«×»: بصمة النصيحة · بصمة حالة الملف ويوم القفل. null = ما اتقفلش. */
data class HomeTipsClosed(val advisorKey: String?, val profileKey: String?, val profileClosedOn: IsoDate?) {
    companion object {
        val NONE = HomeTipsClosed(null, null, null)
    }
}

/** بصمة النصيحة (مش النص نفسه — ما بنخزنش أرقام النصيحة في الإعدادات). نص جديد ⇒ بصمة جديدة ⇒ الكارت يرجع. */
fun advisorTipKey(title: String, body: String): String = hashContent("advisor\n$title\n$body")

/** بصمة حالة الملف = السؤال الجاي (`ProfileCard.name` في الواجهة). */
fun profileTipKey(nextQuestion: String): String = "profile:$nextQuestion"

fun advisorTipShown(key: String, closed: HomeTipsClosed): Boolean = closed.advisorKey != key

fun profileTipShown(key: String, closed: HomeTipsClosed, today: IsoDate): Boolean {
    val at = closed.profileClosedOn
    return closed.profileKey != key || at == null || daysBetween(at, today) >= PROFILE_TIP_RETURN_DAYS
}
