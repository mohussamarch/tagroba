package app.masroufy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * رموز نظام التصميم v0.4 (`design-source/design-system-v0.4/tokens.json` على فرع التصميم) — الفاتح بس.
 * الغامق: ألوانه في الجدول لكن وصفات الزجاج والظل الغامقة **لسه ما اتصممتش** (DESIGN-SYSTEM «ملاحظات للتنفيذ») ⇒ مش هنا.
 * أي لون أو مقاس في شاشة لازم ييجي من هنا — ممنوع رقم جديد من غير قرار مكتوب.
 */
object Ink {
    val text = Color(0xFF193D33)
    val muted = Color(0xFF637570)
    val primary = Color(0xFF08634F)
    val onPrimary = Color(0xFFFFFFFF)
    val heroStart = Color(0xFF064B40)
    val heroEnd = Color(0xFF08705A)
    val income = Color(0xFF13764D)
    val expense = Color(0xFFBE3D48)
    val transfer = Color(0xFF2469BA)
    /** الفواصل: `#CCD8CC` بشفافية 55%. */
    val line = Color(0x8CCCD8CC)
    val selected = Color(0xFFDCEBD6)
    val focus = Color(0xFF956000)
    val alertBg = Color(0xFFFBF0DD)
    val surface = Color(0xFFFFFDF9)
    /** نقطة الإشعار (KOTLIN-MAP §٣): ٦×٦ من غير إطار. */
    val dot = Color(0xFFD93A47)
    /** نص ثانوي على البطاقة البترولية. */
    val onHeroMuted = Color(0xFFDCEBD6)
    val heroProgress = Color(0xFFA6DEC1)
    val heroTrack = Color(0x2EFFFFFF)
    val lensInk = Color(0xFFDAF8E9)
    val mint = Color(0xFFA6DEC1)
    val sky = Color(0xFF9BC5FF)
    val amber = Color(0xFFF3C96B)
    val rose = Color(0xFFFFA8AC)
    /** نص ميت (يوم فات في التقويم). */
    val faded = Color(0xFF9AA8A3)
    /** حدود الحقول: `rgba(204,216,204,0.9)`. */
    val fieldEdge = Color(0xE6CCD8CC)
}

/** ألوان التصنيفات — للأيقونة وخلفيتها بشفافية 12% بس. **المبلغ ما بيورثش لون التصنيف.** */
object CategoryInk {
    val restaurants = Color(0xFFA36A21)
    val shopping = Color(0xFF3866A7)
    val savings = Color(0xFF27785B)
    val transport = Color(0xFF4D747C)
    val gifts = Color(0xFFA55060)
}

/** سلّم الزوايا (DESIGN-SYSTEM «المقاسات والزوايا») — ممنوع زاوية من برّه السلّم. */
object Radius {
    val iconTile = 12.dp
    val lensSmall = 16.dp
    val control = 18.dp
    val card = 22.dp
    val nav = 26.dp
    val lensLarge = 26.dp
    val hero = 28.dp
    val menu = 28.dp
    val sheet = 32.dp
}

/** المسافات والتخطيط (tokens.json `layout`). */
object Space {
    val gutter = 20.dp
    val block = 14.dp
    val listGap = 8.dp
    val listGapWide = 12.dp
    val touch = 48.dp
    val navHeight = 70.dp
    /** v0.4.1: الشريط على خط المحتوى (OVERRIDES §73) — مش 12. */
    val navInset = 20.dp
    val navBottom = 14.dp
    /** شريط «اسأل مصروفي»: ٤٤ مرئي جوه مساحة لمس ٤٨، على بعد ١٨ فوق شريط التنقل (KOTLIN-MAP §٣). */
    val askHeight = 44.dp
    val askGap = 18.dp
    /** المسافة تحت آخر عنصر في قايمة جوه تبويب (الشريطين تحت) ≈ 156. */
    val tabContentBottom = 156.dp
    /** رأس الأقسام الأربعة: صف أول ٤٨ والزراير ٤٨×٤٨ بزاوية ١٨. */
    val headerRow = 48.dp
}

/** مقاسات الخط (tokens.json `typography.size`). */
object TextSize {
    const val caption = 12
    const val body = 14
    const val section = 18
    const val title = 24
    const val amount = 38
    const val amountSmall = 34
}
