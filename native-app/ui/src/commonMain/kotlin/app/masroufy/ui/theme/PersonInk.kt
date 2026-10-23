package app.masroufy.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ألوان صفحة الشخص (r06-person — `PersonHero.dc.html` · `PersonProfile.dc.html`، فرع التصميم 59eac9b8).
 * خلفية الرأس ثابتة في المظهرين زي البطاقة البترولية (أول لون = الأخضر الأصلي — بيتبدل مع الغامق من `Glass.heroBase`).
 */
object PersonInk {
    /** 5 ألوان «لون الخلفية» (من → لـ، زاوية 125). */
    val heroColors: List<Pair<Color, Color>> = listOf(
        Color(0xFF064B40) to Color(0xFF08705A),
        Color(0xFF1F3D6B) to Color(0xFF2469BA),
        Color(0xFF5A2340) to Color(0xFFA55060),
        Color(0xFF5C3B0A) to Color(0xFF956000),
        Color(0xFF2B2F3A) to Color(0xFF4B5566),
    )

    /** دايرة الصورة من غير صورة (`#E6F2EA → #CFE6D8`) ورمز الشخص (`#8DB8A3`). */
    val avatarTop get() = mix(0xFFE6F2EA, 0xFF22352D)
    val avatarBottom get() = mix(0xFFCFE6D8, 0xFF1B2B25)
    val avatarFigure get() = mix(0xFF8DB8A3, 0xFF5E8A75)

    /** الحلقة حوالين الصورة والعلامات (`#FAF9F3` — لون الخلفية). */
    val ring get() = mix(0xFFFAF9F3, 0xFF101A16)

    /** مناسبة الفرح (وردي) والباقي (أزرق) في شريط «مرة واحدة» والتنبيه. */
    val weddingInk get() = mix(0xFFA55060, 0xFFF2A3B3)
    val weddingBg get() = mix(0xFFFBE9EE, 0xFF3A2129)

    /** كارت المناسبة على الأخضر (`rgba(255,255,255,0.12)`) والزراير عليه (`0.16`). */
    val onHeroCard = Color(0x1FFFFFFF)
    val onHeroButton = Color(0x29FFFFFF)
    val handle = Color(0x73FFFFFF)

    /** لوحة «خصّص» البيضا فوق الأخضر (`rgba(255,255,255,0.94)`). */
    val editPanel get() = mix(0xF0FFFFFF, 0xF0182420)
}
