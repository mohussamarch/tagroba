package app.masroufy.ui.glass

import android.os.Build

/** `RenderEffect` (التمويه) من أندرويد 12 (API 31) — أقل من كده الزجاج بيبقى تدرج شبه معتم (DESIGN-SYSTEM «ملاحظات للتنفيذ»). */
actual fun blurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
