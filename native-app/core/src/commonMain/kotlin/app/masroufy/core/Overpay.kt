package app.masroufy.core

/**
 * §82-19 (المالك 2026-10-10 بكلامه: «يسأله انت دفعت اكتر و يظهرله اوبشنز يختار ما بينهم»): دفعت لحد **أكتر** من اللي عليك ⇒ سؤال
 * «دفعت أكتر من اللي عليك» بأربع اختيارات بدل الرفض. spec/02: «أي زيادة تُسجّل … بإجراء صريح» — الاختيار هو الإجراء الصريح، والزيادة
 * عمرها ما بتتبلع ولا بتتسحب من دين تاني في الصمت.
 */
enum class OverpayChoice(val wire: String) {
    /** «أمانة ليك عنده» ⇒ دين ليك عليه بالزيادة، نوعه «لك عنده» ومعلّم أمانة (`Obligation.custodyHeld`). */
    HELD_FOR_YOU("held"),

    /** «دعم» ⇒ الزيادة هدية ليه (نصيبه من العملية `gift` لو فيه عملية — من غير دين). */
    SUPPORT("support"),

    /** «سلفة جديدة ليه» ⇒ دين ليك عليه بالزيادة (سلفة عادية). */
    NEW_LOAN("loan"),

    /** «غلطة في المبلغ» ⇒ **ولا كتابة**: ارجع عدّل المبلغ. */
    WRONG_AMOUNT("wrong"),
    ;

    companion object {
        fun fromWire(wire: String): OverpayChoice? = entries.firstOrNull { it.wire == wire }
    }
}

/** الدفعة أكبر من المفتوح: [openMinor] اللي كان عليك · [paidMinor] اللي دفعته · [surplusMinor] الزيادة — بالوحدة الصغرى. */
data class Overpay(val openMinor: Halalas, val paidMinor: Halalas, val surplusMinor: Halalas)

/** الزيادة لو [paidMinor] أكبر من [openMinor] (والمفتوح أكبر من صفر — من غير دين مفتوح مفيش «أكتر من اللي عليك»). غير كده null. */
fun overpayOf(paidMinor: Halalas, openMinor: Halalas): Overpay? {
    assertHalalas(paidMinor)
    assertHalalas(openMinor)
    if (openMinor <= 0 || paidMinor <= openMinor) return null
    return Overpay(openMinor, paidMinor, subtractMoney(paidMinor, openMinor))
}

/** الديون اللي «دفعت أكتر» بتتسأل عليها: اللي **عليك** (قرض أو أمانة معاك) — الفلوس خارجة منك. */
fun overpayable(o: Obligation): Boolean = o.kind == ObligationKind.LOAN_PAYABLE || o.kind == ObligationKind.CUSTODY_PAYABLE
