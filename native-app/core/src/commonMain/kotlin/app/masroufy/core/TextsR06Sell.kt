package app.masroufy.core

/**
 * r06-sell (OVERRIDES §112-4) — «بيع أصل»: «الفلوس راحت فين؟» إجباري · الربط بالتحويل اللي وصل · كارت المكسب أو الخسارة (مفاتيح SELL_
 * في [R06Key]). الكلام بالحرف من `AssetTradeSheet.dc.html` (وضع البيع — فرع التصميم 84113940). ملف لوحده زي `TextsR06Split.kt`.
 */
internal val MSA_R06_SELL_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.SELL_TO to "أين ذهب المال؟",
    R06Key.SELL_TO_BANK to "البنك",
    R06Key.SELL_TO_CASH to "النقد",
    R06Key.SELL_TO_OTHER to "محفظة أخرى",
    R06Key.SELL_NO_WALLETS to "لا محفظة بعملة هذا الأصل — أضف محفظة أولًا.",
    R06Key.SELL_LINK to "اربطه بالتحويل الوارد (إن وصل)",
    R06Key.SELL_NO_LINK to "بلا ربط",
    R06Key.SELL_NO_MATCH to "لا تحويل قريب من المبلغ والتاريخ.",
    R06Key.SELL_GAIN to "ربح",
    R06Key.SELL_LOSS to "خسارة",
    R06Key.SELL_COST_SHARE to "حصة التكلفة {0}",
    R06Key.SELL_NOTE to "مبلغ البيع ليس دخلًا — يدخل محفظتك كبيع أصل، ويظهر المكسب وحده.",
    R06Key.SELL_ERR_WALLET to "اختر أين ذهب المال.",
    R06Key.SELL_ERR_CURRENCY to "عملة المحفظة غير عملة الأصل.",
    R06Key.SELL_ERR_LINK to "هذا التحويل لا يناسب البيع أو مربوط بشيء آخر.",
    R06Key.SELL_ACTION to "بيع",
)

internal val EGYPTIAN_R06_SELL_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.SELL_TO to "الفلوس راحت فين؟",
    R06Key.SELL_TO_BANK to "البنك",
    R06Key.SELL_TO_CASH to "الكاش",
    R06Key.SELL_TO_OTHER to "محفظة تانية",
    R06Key.SELL_NO_WALLETS to "مفيش محفظة بعملة الأصل ده — ضيف محفظة الأول.",
    R06Key.SELL_LINK to "اربطه بالتحويل اللي وصل (لو وصل)",
    R06Key.SELL_NO_LINK to "من غير ربط",
    R06Key.SELL_NO_MATCH to "مفيش تحويل قريب من المبلغ والتاريخ.",
    R06Key.SELL_GAIN to "مكسب",
    R06Key.SELL_LOSS to "خسارة",
    R06Key.SELL_COST_SHARE to "حصة التكلفة {0}",
    R06Key.SELL_NOTE to "الفلوس اللي قبضتها مش دخل — بتدخل محفظتك كبيع أصل، والمكسب بس هو اللي بيبان.",
    R06Key.SELL_ERR_WALLET to "اختار الفلوس راحت فين.",
    R06Key.SELL_ERR_CURRENCY to "عملة المحفظة غير عملة الأصل.",
    R06Key.SELL_ERR_LINK to "التحويل ده مش مناسب للبيع أو مربوط بحاجة تانية.",
    R06Key.SELL_ACTION to "بيع",
)

internal val ENGLISH_R06_SELL_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.SELL_TO to "Where did the money go?",
    R06Key.SELL_TO_BANK to "Bank",
    R06Key.SELL_TO_CASH to "Cash",
    R06Key.SELL_TO_OTHER to "Other wallet",
    R06Key.SELL_NO_WALLETS to "No wallet in this asset's currency — add one first.",
    R06Key.SELL_LINK to "Link it to the transfer that arrived (if it did)",
    R06Key.SELL_NO_LINK to "No link",
    R06Key.SELL_NO_MATCH to "No transfer close to the amount and date.",
    R06Key.SELL_GAIN to "Gain",
    R06Key.SELL_LOSS to "Loss",
    R06Key.SELL_COST_SHARE to "Cost share {0}",
    R06Key.SELL_NOTE to "The proceeds aren't income — they enter your wallet as an asset sale, and only the gain shows.",
    R06Key.SELL_ERR_WALLET to "Choose where the money went.",
    R06Key.SELL_ERR_CURRENCY to "The wallet's currency differs from the asset's.",
    R06Key.SELL_ERR_LINK to "This transfer doesn't fit the sale or is linked to something else.",
    R06Key.SELL_ACTION to "Sell",
)
