plugins {
    kotlin("multiplatform") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    // أندرويد لموديول `:firestore` بس (اختبار المستودعات على محاكي أندرويد) — نفس نسخة التطبيق الحالي (android/build.gradle)
    id("com.android.library") version "8.13.0" apply false
    // تطبيق أندرويد الجديد (`:androidApp`) — نفس نسخة أندرويد (ARCHITECTURE §31.31)
    id("com.android.application") version "8.13.0" apply false
    kotlin("android") version "2.4.20" apply false
    // الواجهة: Compose Multiplatform (اختيار المالك — KOTLIN_PLAN §2) + مترجم Compose بنفس نسخة كوتلن
    id("org.jetbrains.compose") version "1.11.1" apply false
    kotlin("plugin.compose") version "2.4.20" apply false
    // إعدادات فايربيز لأندرويد — بيتطبق **بس** لو ملف المالك موجود برا المستودع (ARCHITECTURE §31.31)
    id("com.google.gms.google-services") version "4.4.4" apply false
}

/*
 * ملفات المالك الحقيقية (`files/` — كشف الحساب، **مستبعدة من Git**). بندوّر لفوق من المشروع عشان النسخة الفرعية
 * (git worktree جوه `.claude/worktrees/`) تلاقيها في المشروع الأصلي — قبل كده الاختبار كان بيتشال **في صمت** هناك.
 * لو مش موجودة (GitHub مثلًا) بيتطبع سطر واضح، والاختبارات الحقيقية ما بتشتغلش.
 */
val ownerFiles: File? = generateSequence(rootDir) { it.parentFile }
    .map { File(it, "files") }
    .firstOrNull { File(it, "transactions_full.csv").isFile }
extra["ownerFiles"] = ownerFiles
if (ownerFiles == null) logger.lifecycle("⚠️ ملفات المالك (files/transactions_full.csv) مش موجودة — اختبارات الكشف الحقيقي مش هتشتغل")
else logger.lifecycle("ملفات المالك الحقيقية: ${ownerFiles.absolutePath}")

/*
 * ملف إعدادات فايربيز لأندرويد (`google-services.json`) — **برا المستودع** في `secrets/` (مستبعد من Git). بندوّر لفوق زي `files/`.
 * `-Pmasroufy.firebaseSettings=<مسار>` بيغلب. مش موجود ⇒ التطبيق بيتبني من غير فايربيز الحقيقي (المحاكي بس).
 */
val firebaseSettings: File? = (providers.gradleProperty("masroufy.firebaseSettings").orNull?.let(::File)?.takeIf { it.isFile })
    ?: generateSequence(rootDir) { it.parentFile }.map { File(it, "secrets/google-services.json") }.firstOrNull { it.isFile }
extra["firebaseSettings"] = firebaseSettings
