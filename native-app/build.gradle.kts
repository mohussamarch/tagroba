plugins {
    kotlin("multiplatform") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    // أندرويد لموديول `:firestore` بس (اختبار المستودعات على محاكي أندرويد) — نفس نسخة التطبيق الحالي (android/build.gradle)
    id("com.android.library") version "8.13.0" apply false
}

/*
 * مكان ملفات البناء: لو متغير البيئة `MASROUFY_BUILD_ROOT` (أو خاصية `masroufy.buildRoot` في إعدادات جريدل بتاعة الجهاز) موجود، ملفات البناء بتروح هناك (مثلًا قرص تاني فيه مساحة)
 * في فولدر باسم النسخة (worktree) — عشان قرص المشروع اتملا من بناء نسخ كتير بالتوازي (2026-10-09). من غيره: زي الأول جوه المشروع.
 */
(System.getenv("MASROUFY_BUILD_ROOT") ?: providers.gradleProperty("masroufy.buildRoot").orNull)?.takeIf { it.isNotBlank() }?.let { root ->
    val checkout = rootDir.parentFile.name
    allprojects {
        layout.buildDirectory.set(File(root, "$checkout/${path.trim(':').replace(':', '/').ifEmpty { "root" }}"))
    }
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
