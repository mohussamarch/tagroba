package app.masroufy.wiring

import app.masroufy.core.SpaceTransfer
import app.masroufy.core.SpaceTransferError
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.SpaceLegWrite
import app.masroufy.port.SpaceTransferWriter
import app.masroufy.usecase.SpaceBook
import app.masroufy.usecase.TransactionLinkRepos
import app.masroufy.usecase.TransferBetweenSpaces
import app.masroufy.usecase.TransferBetweenSpacesDeps

/**
 * «التحويل لنفسك بين البلدين» لأي منطقة محتاجة رجوله (العمليات · الاستيراد): نفس الكاتب الذرّي ونفس دفاتر البلاد المفتوحة —
 * `legsIn(spaceId)` بيرفض ربط الرجل وبيفك زوجها قبل ما التراجع عن دفعة يمسحها (مراجعة r06 money-1).
 */
internal fun spaceTransfersOf(c: AreaContext): TransferBetweenSpaces = TransferBetweenSpaces(
    TransferBetweenSpacesDeps(c.repos.spaceTransfers, c.session.spaceTransferWriter() ?: RefusingWriter, { id -> bookOf(c, id) }, c.env.ids, c.env.clock),
)

/** البلاد المفتوحة في الجلسة — والبلد الشغالة دايمًا منهم (الاختبار ممكن ما يدّيهاش). */
internal fun AreaContext.openSpaces() = session.spaces().let { all -> if (all.any { it.first.id == space.id }) all else listOf(space to repos) + all }

private fun bookOf(c: AreaContext, spaceId: String): SpaceBook? = c.openSpaces().firstOrNull { it.first.id == spaceId }?.let { (s, repos) ->
    SpaceBook(
        s, repos.transactions, repos.wallets,
        TransactionLinkRepos(
            repos.roscaEntries, repos.installmentPayments, repos.installmentPlans, repos.zakatPayments, repos.eventLinks, repos.projectLinks,
            repos.allocations, repos.obligations, repos.settlements,
        ),
    )
}

/** الجلسة من غير كاتب البلدين ⇒ التسجيل والربط والفك بيرفضوا بـ«غير متاح» (ولا كتابة) بدل ما يتظاهروا إنهم نجحوا. */
private object RefusingWriter : SpaceTransferWriter {
    override suspend fun link(pair: SpaceTransfer, legs: List<SpaceLegWrite>) = throw SpaceTransferError(uiText(TextKey.NOT_AVAILABLE))

    override suspend fun unlink(pair: SpaceTransfer, legs: List<SpaceLegWrite>) = throw SpaceTransferError(uiText(TextKey.NOT_AVAILABLE))
}
