package app.masroufy.ui.screens.character

import app.masroufy.core.CharacterLook
import app.masroufy.core.normalized
import app.masroufy.core.parseStoredCharacter
import app.masroufy.core.toStored

/**
 * «ابنِ/عدّل شخصيتك» من غير ما شغل نص ساعة يضيع (طلب المالك 2026-10-16 — «لما اطلع من غير قصدي يسألني تاني وينبهني انه مفيش حفظ اتعمل»):
 * - **أي خروج** (رجوع الجهاز · سهم الرجوع · «إلغاء») وفيه تعديلات ⇒ تأكيد جوه الصفحة: «احفظ» · «اخرج من غير حفظ» · «كمّل تعديل».
 * - **مسودة على الجهاز** (لكل حساب — [CharacterDraftStore]) بعد كل تغيير: لو البرنامج اتقفل، الفتح الجاي بيعرض «كمّل من آخر تعديل» أو «ابدأ من المحفوظ».
 */
interface CharacterDraftStore {
    /** آخر مسودة للحساب ده على الجهاز ده (null = مفيش). */
    fun read(): String?

    /** null = امسح (اتحفظ أو المالك خرج من غير حفظ). */
    fun write(value: String?)
}

/** فيه تعديلات ما اتحفظتش: الشكل غير اللي البنّاء بدأ بيه ([baseline] = المحفوظ أو البداية). */
fun isDirty(look: CharacterLook, baseline: CharacterLook): Boolean = look.normalized() != baseline.normalized()

/** طلب خروج: من غير تعديلات ⇒ يخرج على طول · بتعديلات ⇒ التأكيد. */
enum class ExitStep { LEAVE, CONFIRM }

fun exitStep(dirty: Boolean): ExitStep = if (dirty) ExitStep.CONFIRM else ExitStep.LEAVE

/** ردود التأكيد. */
enum class ExitChoice { SAVE, LEAVE, STAY }

/** اللي بيحصل للمسودة بعد الرد: «احفظ» ⇒ تتمسح بعد الحفظ · «اخرج من غير حفظ» ⇒ تتمسح · «كمّل» ⇒ تفضل. */
fun clearsDraft(choice: ExitChoice): Boolean = choice != ExitChoice.STAY

/** المسودة نص قصير على الجهاز: «حقل=رقم;…» بنفس أسماء التخزين (`toStored`). */
fun encodeDraft(look: CharacterLook): String = look.toStored().entries.joinToString(";") { "${it.key}=${it.value}" }

/** null لو مفيش أو النص بايظ. */
fun decodeDraft(raw: String?): CharacterLook? {
    if (raw.isNullOrBlank()) return null
    val map = raw.split(';').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else part.substring(0, i) to (part.substring(i + 1).toLongOrNull() ?: return null)
    }.toMap()
    if (map.isEmpty()) return null
    return runCatching { parseStoredCharacter(map) }.getOrNull()
}

/** عند الفتح: مسودة مختلفة عن اللي البنّاء هيبدأ بيه ⇒ عرض «كمّل من آخر تعديل»؛ غير كده null (والمسودة القديمة تتمسح). */
fun draftToOffer(draft: CharacterLook?, baseline: CharacterLook): CharacterLook? = draft?.takeIf { isDirty(it, baseline) }
