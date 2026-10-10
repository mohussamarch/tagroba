package app.masroufy.ui.screens.operations

import androidx.compose.ui.graphics.Color
import app.masroufy.core.Category
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.shell.parseHex

/**
 * أيقونات منطقة «العمليات» اللي مش في المشتركة — المسارات من `<svg>` لوحات النموذج (`Operations` · `OperationDetail`) بالحرف،
 * والدايرة والمستطيل اتحولوا لمسار بنفس الشكل.
 */
internal object OperationsIcons {
    /** «⋮» على صف العملية (قايمة الإجراءات). */
    val DOTS_VERTICAL = Lucide(
        "DOTS_VERTICAL",
        "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
    )

    /** صفحة التاجر (الكلمة والسهم بقوا رمز محل — KOTLIN-MAP §٣). */
    val STORE = Lucide("STORE", "M3 9l1.5-5h15L21 9", "M3 9h18v2a3 3 0 0 1-6 0a3 3 0 0 1-6 0a3 3 0 0 1-6 0z", "M5 13v8h14v-8", "M10 21v-5h4v5")

    /** دخل (محفظة وسهم لتحت). */
    val INCOME = Lucide("INCOME", "M6 6h12a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z", "M12 10v5M9.5 12.5L12 15l2.5-2.5")

    /** تحويل بين محافظك (سهمين). */
    val MOVE = Lucide("MOVE", "M4 8h14l-3-3", "M20 16H6l3 3")
}

/** رمز التصنيف المتخزن (`iconKey` من لوسيد) ⇒ أقرب أيقونة متاحة؛ المجهول ⇒ وسم. عرض بس. */
internal fun categoryIcon(iconKey: String?): Lucide = when (iconKey) {
    "utensils", "chef-hat", "soup", "croissant", "milk" -> Lucide.UTENSILS
    "coffee" -> Lucide.COFFEE
    "shopping-basket", "shopping-cart", "shopping-bag", "store", "package", "shirt" -> Lucide.SHOPPING_BAG
    "car", "fuel", "route", "train-front", "square-parking", "wrench" -> Lucide.CAR
    "house", "home", "house-heart", "key-round", "building-complex", "hotel" -> Lucide.HOUSE
    "gift", "party-popper" -> Lucide.GIFT
    "piggy-bank" -> Lucide.PIGGY_BANK
    "trending-up" -> Lucide.TRENDING_UP
    "arrow-left-right", "repeat", "send" -> Lucide.ARROW_LEFT_RIGHT
    "banknote" -> Lucide.BANKNOTE
    "wallet", "wallet-cards" -> Lucide.WALLET
    "users", "heart-handshake", "hand-heart", "baby" -> Lucide.USERS
    "hand-coins" -> Lucide.HAND_COINS
    "briefcase-business" -> Lucide.BRIEFCASE
    "sparkles", "wand-sparkles" -> Lucide.SPARKLES
    "calendar-clock" -> Lucide.CALENDAR
    "graduation-cap", "school", "library" -> Lucide.FILE_TEXT
    else -> Lucide.TAG
}

/** لون التصنيف الفاتح (`#A36A21`) ⇒ لون الأيقونة وخلفيتها؛ من غير تصنيف ⇒ [fallback]. */
internal fun categoryColor(category: Category?, fallback: Color): Color = category?.lightColor?.let(::parseHex) ?: fallback
