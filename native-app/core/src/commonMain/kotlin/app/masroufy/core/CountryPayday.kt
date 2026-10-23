package app.masroufy.core

/**
 * بداية الشهر المالي **لكل بلد** (قرار المالك — OVERRIDES §111-3): يوم القبض في مصر ما يغيّرش شهر السعودية.
 *
 * التخزين: [UserProfile.payday] = يوم السعودية (البلد الأصلية — زي ما كان، فالملف القديم ما بيتغيرش)، و[UserProfile.paydays] = يوم كل
 * بلد تانية بمعرّفها. بلد مالهاش يوم ⇒ **بتاخد يوم السعودية** (الافتراضي — رد المراجعة cty-5) لحد ما يتحدد ليها يوم.
 * الشاشات وحالات الاستخدام بتقرا `profile.payday` زي ما هي: مستودع الملف في البلد ([inSpace]/[savedFromSpace]) هو اللي بيبدّل.
 */
fun UserProfile.paydayIn(spaceId: String): Int = if (spaceId == DEFAULT_SPACE_ID) payday else paydays[spaceId] ?: payday

/** الملف زي ما بتشوفه البلد [spaceId]: `payday` = يوم البلد دي. */
fun UserProfile.inSpace(spaceId: String): UserProfile = if (spaceId == DEFAULT_SPACE_ID) this else copy(payday = paydayIn(spaceId))

/**
 * الملف اللي اتعدّل من البلد [spaceId] ⇒ زي ما بيتخزن على الحساب: يوم السعودية بيفضل زي ما هو المتخزن ([stored])، ويوم البلد دي بيتكتب في
 * خانتها **بس لو اتغيّر** (عشان أي حفظ تاني للملف من مصر ما يثبّتش يوم السعودية على مصر من غير قصد).
 */
fun UserProfile.savedFromSpace(spaceId: String, stored: UserProfile?): UserProfile {
    if (spaceId == DEFAULT_SPACE_ID) return this
    val base = stored ?: emptyProfile()
    val days = if (payday != base.paydayIn(spaceId)) base.paydays + (spaceId to payday) else base.paydays
    return copy(payday = base.payday, paydays = days)
}
