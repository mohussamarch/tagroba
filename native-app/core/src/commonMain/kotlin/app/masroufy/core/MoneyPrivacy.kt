package app.masroufy.core

/**
 * إخفاء الأرقام (زرار العين — تصميم `NightPrivacy`، OVERRIDES «المظهر الليلي + زرار إخفاء الأرقام»): **مكان واحد** — [formatMoney]
 * بيسأل [isHidden] لما ما يتحددش `hidden` صراحة، فكل مبلغ في كل شاشة بيبقى «••••» من غير ما أي شاشة تتعدّل.
 * الواجهة بتركّب [reader] على حالة Compose (فالقراية جوه الرسم بتعمل إعادة رسم لما العين تتدوس). العملة بتفضل ظاهرة.
 */
object MoneyPrivacy {
    /** القراية — الافتراضي «مش مخفي»؛ الواجهة بتبدّله بقارئ حالة مراقَبة. */
    var reader: () -> Boolean = { false }

    val isHidden: Boolean get() = reader()

    /** النقط اللي بتحل محل الرقم. */
    const val MASK: String = "••••"
}
