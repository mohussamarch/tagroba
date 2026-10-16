package app.masroufy.ui.screens.character

import app.masroufy.core.CharacterLook

/**
 * نقطة ربط «شخصيتك» (r041 — character-spec §5.2): البنّاء بيقرا ويحفظ من هنا بس (CLAUDE.md #4 — مفيش مستودع في الشاشة).
 * التنفيذ في `:wiring` (`MoreGraph.character` ⇒ `ManageProfile.character` · `saveCharacter`). null = المعاينة من غير ملف.
 */
interface CharacterSetting {
    /** الشخصية المحفوظة — `null` = لسه ما اتبنتش. */
    suspend fun current(): CharacterLook?

    /** النوع اللي في ملفك ("male"/"female"/null) — البنّاء ما بيسألوش، وبيعرض قوايمه بس. */
    suspend fun profileGender(): String?

    /** «احفظ» — بيرمي لو الحفظ ما نفعش (الشاشة بتفضل مفتوحة برسالة). */
    suspend fun save(look: CharacterLook)

    /** المسودة على الجهاز (2026-10-16) — null = من غير مسودة (الاختبار · المعاينة). */
    val draft: CharacterDraftStore? get() = null
}
