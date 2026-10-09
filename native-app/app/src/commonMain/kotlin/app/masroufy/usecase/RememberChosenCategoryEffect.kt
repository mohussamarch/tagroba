package app.masroufy.usecase

/**
 * §75-16 وقت التسجيل: التصنيف اللي المالك **اختاره بإيده** لسطر في «سجّل الكل» (رسايل البنك) أو في مراجعة الكشف
 * ([RecordContext.chosenCategories]) بيتحفظ لمحل السطر **من غير سؤال «نفتكره؟»** ([MerchantMemory]).
 * - بعد ما الدفعة تتقفل ([afterCommit]) — فشله ما بيرجّعش التسجيل (التصنيف نفسه اتحفظ على العملية مؤكد في كل الأحوال).
 * - التسجيل التلقائي (`byOwner = false`) مش اختيار من المالك ⇒ ولا حاجة.
 * - نفس المحل بكذا تصنيف في نفس الضغطة ⇒ **الأحدث** (بتاريخ العملية، وبعده رقم السطر) يكسب.
 */
class RememberChosenCategoryEffect(private val memory: MerchantMemory) : RecordEffect {
    override suspend fun afterCommit(ctx: RecordContext) {
        if (!ctx.byOwner || ctx.chosenCategories.isEmpty()) return
        val picks = ctx.lines
            .mapNotNull { line -> ctx.chosenCategories[line.line.row.lineNumber]?.let { line to it } }
            .sortedWith(compareBy({ it.first.transaction.occurredAt }, { it.first.line.row.lineNumber }))
            .map { (line, categoryId) -> MerchantPick.of(line.transaction, categoryId) }
        memory.rememberAll(picks)
    }
}
