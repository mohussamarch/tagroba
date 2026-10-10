package app.masroufy.ui.screens.home

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.TextKey
import app.masroufy.core.UserProfile
import app.masroufy.ui.icons.Lucide

/**
 * «كمّل ملفك» (`ProfileQuestion` — §63): **سؤال واحد بس كل مرة**. البطاقات اللي ليها حقل في `UserProfile` وحالة استخدام بتحفظها
 * (`ManageProfile.saveWithQuestions`) بس: السيارة · العائلة · السكن · العمل الخاص.
 * ⚠️ **ناقص في المنطق** (مش متبني في كوتلن — مش بنخترعه): نسبة «ملفك X٪» وأوزانها · مين بيختار البطاقة (محرك الإشعارات §61 في النموذج) ·
 * بطاقة الراتب (نطاق مش رقم — مفيش حقل) · بطاقة الكاش (رصيد بداية جديد بعد أول تشغيل — `OnboardAccount.finish` بيشتغل مرة واحدة بس) ·
 * بطاقة الديون القديمة (مفيش علامة «اتجاوبت» ولا خطوة واحدة لشخص + دين). الترتيب هنا ترتيب بطاقات النموذج.
 */
enum class ProfileCard(val title: TextRef, val sub: TextRef?, val icon: Lucide) {
    CAR(UiKey.PROFILE_QUESTION_CAR, UiKey.PROFILE_QUESTION_CAR_SUB, Lucide.CAR),
    FAMILY(UiKey.PROFILE_QUESTION_FAMILY, UiKey.PROFILE_QUESTION_FAMILY_SUB, Lucide.USERS),
    HOME(UiKey.PROFILE_QUESTION_HOUSING, null, Lucide.HOUSE),
    BIZ(UiKey.PROFILE_QUESTION_BIZ, null, Lucide.BRIEFCASE),

    /** «هل تذهب بها إلى العمل؟» — بيطلع بعد «عندي سيارة» لو عنده شغل شغال (`IncomeFollowUp.CarToWork` — §64). */
    CAR_TO_WORK(UiKey.PROFILE_QUESTION_CAR_WORK, null, Lucide.CAR),
}

/** اختيار في البطاقة: [reply] الجملة اللي بتطلع بعد الحفظ (بتقول اللي اتغير فعلًا — التصنيفات المشروطة من `presentCategories`). */
data class ProfileOption(val id: String, val label: TextRef, val reply: TextRef)

/** أول سؤال لسه ما اتجاوبش (null = مفيش أسئلة ليها حفظ دلوقتي ⇒ كارت «ملفك» في الرئيسية بيختفي). */
fun nextProfileCard(profile: UserProfile?): ProfileCard? {
    val p = profile ?: return ProfileCard.CAR
    return when {
        p.hasCar == null -> ProfileCard.CAR
        p.supportsDependents == null -> ProfileCard.FAMILY
        p.renter == null -> ProfileCard.HOME
        p.business == null -> ProfileCard.BIZ
        else -> null
    }
}

fun optionsOf(card: ProfileCard): List<ProfileOption> = when (card) {
    ProfileCard.CAR -> listOf(
        ProfileOption("yes", UiKey.PROFILE_QUESTION_YES, UiKey.PROFILE_QUESTION_CAR_YES),
        ProfileOption("no", UiKey.PROFILE_QUESTION_NO, UiKey.PROFILE_QUESTION_CAR_NO),
    )
    ProfileCard.FAMILY -> listOf(
        ProfileOption("no", UiKey.PROFILE_QUESTION_NO, UiKey.PROFILE_QUESTION_FAMILY_NO),
        ProfileOption("spouse", UiKey.PROFILE_QUESTION_FAMILY_SPOUSE, UiKey.PROFILE_QUESTION_FAMILY_YES),
        ProfileOption("parents", UiKey.PROFILE_QUESTION_FAMILY_PARENTS, UiKey.PROFILE_QUESTION_SAVED),
    )
    ProfileCard.HOME -> listOf(
        ProfileOption("rent", UiKey.PROFILE_QUESTION_HOME_RENT, UiKey.PROFILE_QUESTION_HOME_RENT_REPLY),
        ProfileOption("own", UiKey.PROFILE_QUESTION_HOME_OWN, UiKey.PROFILE_QUESTION_HOME_NO_RENT),
        ProfileOption("family", UiKey.PROFILE_QUESTION_HOME_FAMILY, UiKey.PROFILE_QUESTION_HOME_NO_RENT),
    )
    ProfileCard.BIZ -> listOf(
        ProfileOption("yes", UiKey.PROFILE_QUESTION_YES, UiKey.PROFILE_QUESTION_BIZ_YES),
        ProfileOption("no", UiKey.PROFILE_QUESTION_NO, UiKey.PROFILE_QUESTION_BIZ_NO),
    )
    ProfileCard.CAR_TO_WORK -> listOf(
        ProfileOption("yes", UiKey.PROFILE_QUESTION_YES, UiKey.PROFILE_QUESTION_SAVED),
        ProfileOption("no", UiKey.PROFILE_QUESTION_NO, UiKey.PROFILE_QUESTION_SAVED),
    )
}

/**
 * الإجابة ⇒ حقول الملف (اللي `checkProfile` بيقبلها): «زوجة وأولاد» = `spouse` + `children` · «أهلي» = `parents` · السكن «ملك» و«مع أهلي» = مش مستأجر.
 * [ProfileCard.CAR_TO_WORK] مش هنا — ليه حالة استخدام لوحده (`answerCarToWork`).
 */
fun applyAnswer(profile: UserProfile, card: ProfileCard, optionId: String): UserProfile = when (card) {
    ProfileCard.CAR -> profile.copy(hasCar = optionId == "yes")
    ProfileCard.FAMILY -> when (optionId) {
        "spouse" -> profile.copy(supportsDependents = true, dependentKinds = listOf("spouse", "children"))
        "parents" -> profile.copy(supportsDependents = true, dependentKinds = listOf("parents"))
        else -> profile.copy(supportsDependents = false, dependentKinds = null)
    }
    ProfileCard.HOME -> profile.copy(renter = optionId == "rent")
    ProfileCard.BIZ -> profile.copy(business = optionId == "yes")
    ProfileCard.CAR_TO_WORK -> profile
}
