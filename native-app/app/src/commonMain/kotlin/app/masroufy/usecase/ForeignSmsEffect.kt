package app.masroufy.usecase

/**
 * §75-12: الصف اللي اتبنى من إجابة «المبلغ كام بعملتك؟» (`ForeignSmsAsks.answerForeign`) فيه العملة الأجنبية ومبلغها ⇒ بيتنسخوا على
 * العملية وهي بتتسجل (المبلغ نفسه = المبلغ المحلي اللي المالك كتبه، بعملة المحفظة). لازم يبقى في `ImportStatementDeps.effects` بتاعة
 * كل بلد فيها رسايل بنك — من غيره الإجابة بتتسجل بالمبلغ المحلي بس.
 */
class ForeignSmsEffect : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        for (line in ctx.lines) {
            val currency = line.sms?.foreignCurrency ?: continue
            line.transaction = line.transaction.copy(foreignCurrency = currency, foreignAmountMinor = line.sms.foreignAmountMinor)
        }
    }
}
