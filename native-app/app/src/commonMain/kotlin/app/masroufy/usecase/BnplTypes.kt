package app.masroufy.usecase

import app.masroufy.core.BnplCycle
import app.masroufy.core.BnplMatch
import app.masroufy.core.BnplProvider
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.IsoDate
import app.masroufy.core.Transaction
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * خطة مفتوحة في «ده قسط من أنهي مشترى؟» (r051 — `BnplSheet` الحالة التانية): «فاضل [leftCount] من [count] · الجاية [nextDueAt]» والقسط،
 * و[diffMinor] = الخصم − القسط (موجب = أكبر · سالب = أقل · صفر = زيه) ⇒ السطر الأصفر لو اتختارت. كله محسوب هنا — الشاشة بتعرض بس.
 */
data class BnplOption(
    val planId: Id,
    val name: String,
    val installmentMinor: Halalas,
    val currency: Currency,
    val leftCount: Int,
    val count: Int,
    val nextDueAt: IsoDate?,
    val diffMinor: Halalas,
)

/**
 * خصم من مزوّد «ادفع بعدين» مستني رد المالك: مشترى جديد ([BnplMatch.NewPurchase]) أو «أنهي مشترى؟» ([BnplMatch.AskWhich] — [plans]
 * المفتوحة و[options] بأرقامها). [walletName] = محفظة الخصم (null = مش معروفة ⇒ ما بتتكتبش).
 */
data class BnplAsk(
    val transaction: Transaction,
    val provider: BnplProvider,
    val match: BnplMatch,
    val plans: List<InstallmentPlan>,
    val options: List<BnplOption> = emptyList(),
    val walletName: String? = null,
)

/** خصم اتربط لوحده بالدفعة رقم [number] من [count] في خطة [planName] — «تراجع» بيفكّه (`BnplCharges.undoAutoLink`). */
data class BnplLinked(val transactionId: Id, val planId: Id, val planName: String, val number: Int, val count: Int)

/** [linked] = الخصومات اللي اتربطت لوحدها (عملية ⇒ خطة) · [autoLinked] = نفسها بالتفاصيل · [asks] = اللي مستني المالك، الأقدم الأول. */
data class BnplSettle(val linked: Map<Id, Id>, val asks: List<BnplAsk>, val autoLinked: List<BnplLinked> = emptyList())

/** رد «اشتريت إيه؟ قسمتها على كام؟ كل قد إيه؟». [what] فاضي ⇒ اسم المزوّد · [remind] = «فكّرني قبل كل دفعة بيوم». */
data class BnplPurchase(val what: String, val categoryId: Id?, val count: Int, val cycle: BnplCycle = BnplCycle.MONTHLY, val remind: Boolean = true)

data class BnplChargesDeps(
    val txns: TransactionRepository,
    val plans: InstallmentPlanRepository,
    val payments: InstallmentPaymentRepository,
    val installments: ManageInstallments,
    val clock: Clock,
    /** النهارده بتوقيت البلد (§88). */
    val today: () -> IsoDate,
    /** r051: اسم محفظة الخصم في اللوحة (null = ما بيتكتبش). */
    val wallets: WalletRepository? = null,
    /** r051: اسم تصنيف المشترى في كارت «مشتريات بالتقسيط» (null = «تقسيط مشتريات»). */
    val categories: CategoryRepository? = null,
)

/** الخصومات الأقدم من كده ما بتتسألش (تاريخ قديم اتسجل قبل الميزة). */
const val BNPL_LOOKBACK_DAYS = 180

/**
 * r051: عملية **نوعها متأكد** (سجّلتها بإيدك من «+» · رسالة شكلها متعلّم) بتتسأل لو اتسجلت في آخر اليومين دول بس — اللوحة بتطلع ساعة
 * التسجيل، والقديم اللي اتصنّف قبل الميزة ما بيتسألش فجأة. اللي نوعه لسه ما اتأكدش بيتسأل زي الأول ([BNPL_LOOKBACK_DAYS]).
 */
const val BNPL_RECENT_DAYS = 2

/** رد المالك على لوحة «ادفع بعدين» (الشاشة بتبعته زي ما هو). */
sealed interface BnplAnswer {
    val transactionId: Id

    data class New(override val transactionId: Id, val purchase: BnplPurchase) : BnplAnswer

    data class Of(override val transactionId: Id, val planId: Id) : BnplAnswer

    /** «تخطّي» في «أول خصم» ⇒ قسط عادي تحت «المستحقات ← تقسيط مشتريات». */
    data class Skip(override val transactionId: Id) : BnplAnswer

    /** r051 «مش قسط — عملية عادية» ⇒ العملية زي ما هي (تصنيفها بيتسأل زي أي عملية) وما بتتسألش تاني. */
    data class NotInstallment(override val transactionId: Id) : BnplAnswer
}

/** بعد الرد: اسم الخطة والباقي عليها (محسوب هنا — الشاشة بتعرضه بس). «تخطّي» و«مش قسط» ⇒ الاتنين null. */
data class BnplDone(val planName: String?, val remainingMinor: Halalas?, val currency: Currency?)
