package app.masroufy.core

import platform.Foundation.NSString
import platform.Foundation.create

// خصائص NSString أعضاء في الكلاس — مش استيراد مستقل (أول بناء على ماك رفض الاستيراد ده)
internal actual fun nfkc(text: String): String =
    NSString.create(string = text).precomposedStringWithCompatibilityMapping
