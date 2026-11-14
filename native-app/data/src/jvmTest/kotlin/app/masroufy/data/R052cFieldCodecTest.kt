package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * §120-6 «ما تسألنيش تاني» (`transferParties.autoKind`) و§120-7 «مرتب دايمًا من الحساب ده» (`incomeSources.alwaysSalaryKeys`): حقول
 * اختيارية جديدة — المقفول ما بيتكتبش (المستند القديم هو هو)، والمتعلّم بيرجع زي ما اتكتب. كل البيانات مخترعة.
 */
class R052cFieldCodecTest {
    private val party = TransferParty("#7788", "••••7788", "7788", TransferVerdict.COMPANY, null, "2026-11-01", kind = EconomicKind.REFUND_RECEIVED)
    private val job = IncomeSource("inc-1", "شركة تجربة", "شركة تجربة", IncomeSourceKind.JOB, Currency.SAR, "2026-01-01", createdAt = "x", payerKeys = listOf("#7788"))

    @Test fun offIsNotWrittenAndOnRoundTrips() {
        val codec = TransferCodecs.transferParties
        assertFalse("autoKind" in codec.encode(party))
        assertEquals(party, codec.decode(codec.encode(party)))
        val on = party.copy(autoKind = true)
        assertEquals(on, codec.decode(codec.encode(on)))

        val sources = IncomeCodecs.incomeSources
        assertFalse("alwaysSalaryKeys" in sources.encode(job))
        assertEquals(job, sources.decode(sources.encode(job)))
        val always = job.copy(alwaysSalaryKeys = listOf("#7788"))
        assertEquals(always, sources.decode(sources.encode(always)))
    }
}
