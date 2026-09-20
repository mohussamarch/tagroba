package app.masroufy.core

/**
 * تعديل مبلغ العملية — نقل `src/domain/amountEdit.ts` (OVERRIDES §32).
 * الجديد بيتحسب في كل حاجة، والأصلي من المصدر بيتحفظ **مرة واحدة** لمطابقة التكرار،
 * والمبلغ ما ينزلش تحت اللي متوزع على أشخاص أو متسوّى بيه دين.
 */
class AmountEditError(message: String) : IllegalArgumentException(message)

data class AmountEdit(val amountMinor: Halalas, val originalAmountMinor: Halalas)

/** المبلغ اللي مطابقة التكرار بتستعمله: الأصلي من المصدر لو اتعدل. */
fun sourceAmountMinor(amountMinor: Halalas, originalAmountMinor: Halalas?): Halalas = originalAmountMinor ?: amountMinor

fun planAmountEdit(
    amountMinor: Halalas,
    originalAmountMinor: Halalas?,
    newAmountMinor: Halalas,
    allocated: List<Halalas>,
    settled: List<Halalas>,
): AmountEdit {
    if (newAmountMinor > MAX_SAFE_HALALAS || newAmountMinor <= 0) {
        throw AmountEditError(uiText(TextKey.AMOUNT_EDIT_INVALID))
    }
    val allocatedTotal = allocated.fold(0L) { sum, a -> addMoney(sum, a) }
    if (newAmountMinor < allocatedTotal) {
        throw AmountEditError(uiText(TextKey.AMOUNT_EDIT_BELOW_ALLOCATIONS, formatMoney(allocatedTotal)))
    }
    val settledTotal = settled.fold(0L) { sum, s -> addMoney(sum, s) }
    if (newAmountMinor < settledTotal) {
        throw AmountEditError(uiText(TextKey.AMOUNT_EDIT_BELOW_SETTLEMENTS, formatMoney(settledTotal)))
    }
    return AmountEdit(newAmountMinor, sourceAmountMinor(amountMinor, originalAmountMinor))
}
