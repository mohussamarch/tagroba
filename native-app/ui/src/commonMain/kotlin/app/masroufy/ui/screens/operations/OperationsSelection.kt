package app.masroufy.ui.screens.operations

import app.masroufy.core.Id

/**
 * وضع «تحديد» في «العمليات» (قايمة المالك §6.5 بند 15) — **حالة شاشة بس، دوال نقية** (اختبار JVM). الدخول من «تحديد» في الرأس
 * أو من القايمة اللي بتطلع بالضغط المطوّل (والصف ده بيبقى محدد). وهو شغال: الضغط على صف بيحدده أو بيشيله، و«تحديد الكل» للظاهر
 * في الشهر، والخروج بالرجوع أو «إلغاء» أو تبديل الخانة أو بعد أي فعل. الترتيب محفوظ (بترتيب الضغط) عشان الكتابة تبقى بنفس الترتيب.
 */
data class OpsSelection(val active: Boolean = false, val ids: List<Id> = emptyList()) {
    val count: Int get() = ids.size

    fun isSelected(id: Id): Boolean = id in ids

    /** يدخل الوضع — ومعاه أول عملية لو جه من قايمة الصف. */
    fun start(first: Id? = null): OpsSelection = OpsSelection(true, listOfNotNull(first))

    /** برّه الوضع ما بيعملش حاجة (الضغط بيفتح التفاصيل زي الأول). */
    fun toggle(id: Id): OpsSelection = when {
        !active -> this
        id in ids -> copy(ids = ids - id)
        else -> copy(ids = ids + id)
    }

    /** كل الظاهر متحدد؟ (قايمة فاضية ⇒ لأ). */
    fun allSelected(visible: List<Id>): Boolean = visible.isNotEmpty() && ids.containsAll(visible)

    /** «تحديد الكل» ⇄ «إلغاء تحديد الكل» للظاهر بس — المحدد من برّه الظاهر بيفضل زي ما هو. */
    fun toggleAll(visible: List<Id>): OpsSelection = when {
        !active -> this
        allSelected(visible) -> copy(ids = ids - visible.toSet())
        else -> copy(ids = ids + visible.filter { it !in ids })
    }

    /** بعد إعادة التحميل: اللي اختفى من القايمة (اتمسح أو الشهر اتغير) ما يفضلش متحدد من غير ما يبان. */
    fun keepOnly(visible: List<Id>): OpsSelection {
        val shown = visible.toSet()
        return if (ids.all { it in shown }) this else copy(ids = ids.filter { it in shown })
    }

    fun cancel(): OpsSelection = OpsSelection()
}
