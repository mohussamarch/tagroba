package app.masroufy.ui.screens.investment.holdings

import androidx.compose.ui.graphics.Color
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingType
import app.masroufy.core.InvestHoldKey
import app.masroufy.core.TextRef
import app.masroufy.core.uiText
import app.masroufy.core.withCurrency
import app.masroufy.ui.icons.Lucide

/**
 * أيقونات وألوان وأسامي «حاجاتي» — المسارات من `<svg>` النموذج نفسه (`InvestHoldings` · `AssetAddSheet` @design v0.4)، والألوان
 * من نفس الملفات. عرض بس (من غير أي حساب فلوس).
 */
internal object HoldIcons {
    val gold = Lucide("hold-gold", "M4 18l3-7h10l3 7zM8 11l2-5h4l2 5")
    val flat = Lucide("hold-flat", "M3 11l9-7 9 7M5 10v10h14V10M10 20v-6h4v6")
    val land = Lucide("hold-land", "M3 7l6-3 6 3 6-3v13l-6 3-6-3-6 3zM9 4v13M15 7v13")
    val shop = Lucide("hold-shop", "M4 9l1.5-5h13L20 9M4 9h16v11H4zM4 9a2.7 2.7 0 0 0 5.3 0 2.7 2.7 0 0 0 5.4 0 2.7 2.7 0 0 0 5.3 0M10 20v-5h4v5")
    val stk = Lucide("hold-stk", "M3 17l6-6 4 4 8-8M15 7h6v6")
    val co = Lucide("hold-co", "M4 8h16v12H4zM9 8V5h6v3M4 13h16")
    val car = Lucide("hold-car", "M5 16h14M6 16l1.5-5h9L18 16M6 16v3M18 16v3M8 13.5h.01M16 13.5h.01")
    val phone = Lucide("hold-phone", "M8 3h8v18H8zM11 18h2")
    val pc = Lucide("hold-pc", "M4 5h16v11H4zM2 19h20")
    val watch = Lucide("hold-watch", "M12 8v4l2 2M9 3h6l1 4H8zM9 21h6l1-4H8zM12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10")
    val oud = Lucide("hold-oud", "M10 3h4v3h-4zM8 6h8l1 4v10H7V10z")
    val art = Lucide("hold-art", "M4 5h16v14H4zM4 15l5-5 4 4 3-3 4 4")
    val other = Lucide("hold-other", "M5 12h.01M12 12h.01M19 12h.01")
    val plus = Lucide("hold-plus", "M12 5v14M5 12h14")
}

internal fun typeIcon(type: HoldingType): Lucide = when (type) {
    HoldingType.GOLD, HoldingType.SILVER -> HoldIcons.gold
    HoldingType.FLAT -> HoldIcons.flat
    HoldingType.LAND -> HoldIcons.land
    HoldingType.SHOP -> HoldIcons.shop
    HoldingType.STK -> HoldIcons.stk
    HoldingType.CO -> HoldIcons.co
    HoldingType.CAR -> HoldIcons.car
    HoldingType.PHONE -> HoldIcons.phone
    HoldingType.PC -> HoldIcons.pc
    HoldingType.WATCH -> HoldIcons.watch
    HoldingType.OUD -> HoldIcons.oud
    HoldingType.ART -> HoldIcons.art
    HoldingType.OTHER -> HoldIcons.other
}

/** أيقونة المربع (النموذج: دهب · شقة · أسهم · عربية · ساعة · نقط). */
internal fun groupIcon(group: HoldingGroup): Lucide = when (group) {
    HoldingGroup.METAL -> HoldIcons.gold
    HoldingGroup.PROPERTY -> HoldIcons.flat
    HoldingGroup.SHARES -> HoldIcons.stk
    HoldingGroup.PERSONAL -> HoldIcons.car
    HoldingGroup.COLLECTIBLES -> HoldIcons.watch
    HoldingGroup.OTHER -> HoldIcons.other
}

/** خلفية ولون صندوق الأيقونة لكل مجموعة (`BOX` في النموذج). */
internal fun groupBox(group: HoldingGroup): Pair<Color, Color> = when (group) {
    HoldingGroup.METAL -> Color(0x1F956000) to Color(0xFF956000)
    HoldingGroup.PROPERTY -> Color(0xFFDCEBD6) to Color(0xFF08634F)
    HoldingGroup.SHARES -> Color(0x1F2469BA) to Color(0xFF2469BA)
    HoldingGroup.COLLECTIBLES -> Color(0x14956000) to Color(0xFF7A5A1E)
    HoldingGroup.PERSONAL, HoldingGroup.OTHER -> Color(0x12193D33) to Color(0xFF4F625C)
}

internal fun purposeColor(p: HoldingPurpose): Color = when (p) {
    HoldingPurpose.INVEST -> Color(0xFF0E8A6E)
    HoldingPurpose.RESERVE -> Color(0xFFB7791F)
    HoldingPurpose.PERSONAL -> Color(0xFF8A9A95)
    HoldingPurpose.TRADE -> Color(0xFF3866A7)
}

internal fun purposeKey(p: HoldingPurpose): TextRef = when (p) {
    HoldingPurpose.INVEST -> InvestHoldKey.HOLD_PURPOSE_INVEST
    HoldingPurpose.RESERVE -> InvestHoldKey.HOLD_PURPOSE_RESERVE
    HoldingPurpose.PERSONAL -> InvestHoldKey.HOLD_PURPOSE_PERSONAL
    HoldingPurpose.TRADE -> InvestHoldKey.HOLD_PURPOSE_TRADE
}

/** اسم المربع («معادن») · اسم صفحة النوع («المعادن»). */
internal fun groupKey(g: HoldingGroup): TextRef = when (g) {
    HoldingGroup.METAL -> InvestHoldKey.HOLD_GROUP_METAL
    HoldingGroup.PROPERTY -> InvestHoldKey.HOLD_GROUP_PROPERTY
    HoldingGroup.SHARES -> InvestHoldKey.HOLD_GROUP_SHARES
    HoldingGroup.PERSONAL -> InvestHoldKey.HOLD_GROUP_PERSONAL
    HoldingGroup.COLLECTIBLES -> InvestHoldKey.HOLD_GROUP_COLLECTIBLES
    HoldingGroup.OTHER -> InvestHoldKey.HOLD_GROUP_OTHER
}

internal fun categoryKey(g: HoldingGroup): TextRef = when (g) {
    HoldingGroup.METAL -> InvestHoldKey.HOLD_CAT_METAL
    HoldingGroup.PROPERTY -> InvestHoldKey.HOLD_CAT_PROPERTY
    HoldingGroup.SHARES -> InvestHoldKey.HOLD_CAT_SHARES
    HoldingGroup.PERSONAL -> InvestHoldKey.HOLD_CAT_PERSONAL
    HoldingGroup.COLLECTIBLES -> InvestHoldKey.HOLD_CAT_COLLECTIBLES
    HoldingGroup.OTHER -> InvestHoldKey.HOLD_CAT_OTHER
}

internal fun typeKey(t: HoldingType): TextRef = when (t) {
    HoldingType.GOLD -> InvestHoldKey.HOLD_T_GOLD
    HoldingType.SILVER -> InvestHoldKey.HOLD_T_SILVER
    HoldingType.FLAT -> InvestHoldKey.HOLD_T_FLAT
    HoldingType.LAND -> InvestHoldKey.HOLD_T_LAND
    HoldingType.SHOP -> InvestHoldKey.HOLD_T_SHOP
    HoldingType.STK -> InvestHoldKey.HOLD_T_STK
    HoldingType.CO -> InvestHoldKey.HOLD_T_CO
    HoldingType.CAR -> InvestHoldKey.HOLD_T_CAR
    HoldingType.PHONE -> InvestHoldKey.HOLD_T_PHONE
    HoldingType.PC -> InvestHoldKey.HOLD_T_PC
    HoldingType.WATCH -> InvestHoldKey.HOLD_T_WATCH
    HoldingType.OUD -> InvestHoldKey.HOLD_T_OUD
    HoldingType.ART -> InvestHoldKey.HOLD_T_ART
    HoldingType.OTHER -> InvestHoldKey.HOLD_T_OTHER
}

/** «شيء واحد» · «شيئان» · «3 أشياء». */
internal fun countText(n: Int): String = when (n) {
    1 -> uiText(InvestHoldKey.HOLD_COUNT_1)
    2 -> uiText(InvestHoldKey.HOLD_COUNT_2)
    else -> uiText(InvestHoldKey.HOLD_COUNT_N, n.toString())
}

/** الرقم بالوحدة الكاملة من غير كسور (النموذج `fmt`: «52,560») — قسمة صحيحة للعرض بس، والسالب بإشارته. */
internal fun wholeText(minor: Halalas): String {
    val abs = if (minor < 0) -minor else minor
    val digits = ((abs - abs % 100) / 100).toString()
    val grouped = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) grouped.append(',')
        grouped.append(c)
    }
    return (if (minor < 0) "−" else "") + grouped
}

/** «⁦⃁ 52,560⁩» · «52,560 ج.م» — نفس شكل العملة في باقي التطبيق (§92). */
internal fun wholeMoney(minor: Halalas, currency: Currency): String = withCurrency(wholeText(minor), currency)
