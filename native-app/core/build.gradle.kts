/*
 * core — قلب التطبيق بكوتلن نقي (KOTLIN_PLAN §1-3): الفلوس والفترات والدفتر والتصنيف ومنع التكرار…
 * ولا أندرويد ولا آيفون ولا فايربيز. الاختبارات بتقارن بملفات المرجع في `../golden/` بالحرف والهللة.
 */
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val onMac = System.getProperty("os.name").lowercase().contains("mac")

kotlin {
    jvm()
    // الآيفون: بيتبني ويتختبر على ماك بس (على ويندوز Kotlin/Native ما بيبنيش للآيفون)
    if (onMac) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            // ترتيب الحروف العربي بنفس ICU اللي في التطبيق الحالي وأندرويد والآيفون (ARCHITECTURE §31)
            implementation("com.ibm.icu:icu4j:78.3")
        }
        jvmTest.dependencies {
            // قراية ملفات المرجع JSON — للاختبارات بس (ARCHITECTURE §31)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
    }
}

tasks.withType<Test>().configureEach {
    val golden = rootProject.file("golden")
    // ملفات المرجع مدخل للاختبار: لو اتولدت تاني، الاختبارات لازم تشتغل تاني (مش «up-to-date»)
    inputs.dir(golden).withPropertyName("golden")
    systemProperty("golden.dir", golden.absolutePath)
    // الكشف الحقيقي على جهاز المالك بس (مش في Git): الاختبار بيشتغل لما الملف موجود، وإلا ما بيتشغلش خالص
    // مكان الملف بيتدوّر عليه في `build.gradle.kts` الرئيسي (بيطلع لفوق للمشروع الأصلي من أي worktree)
    val ownerFiles = rootProject.extra["ownerFiles"] as File?
    val realStatement = ownerFiles?.let { File(it, "transactions_full.csv") }
    if (realStatement != null) {
        systemProperty("masroufy.realStatement", realStatement.absolutePath)
        systemProperty("masroufy.ownerFiles", ownerFiles.absolutePath)
    }
    else filter { excludeTestsMatching("app.masroufy.core.RealStatementTest") }
    // كلمات الـPDF الحقيقي بتتطلع بـ`scripts/real/exportPdfPages.mjs` — لو مش موجودة الاختبار ما بيشتغلش
    if (ownerFiles == null || !File(ownerFiles, "alrajhi-pdf-pages.json").isFile) {
        filter { excludeTestsMatching("app.masroufy.core.RealPdfStatementTest") }
        logger.lifecycle("⚠️ كلمات كشف الـPDF (files/alrajhi-pdf-pages.json) مش موجودة — اختبار قارئ الـPDF الحقيقي مش هيشتغل")
    }
    // كشف QNB مصر الحقيقي (نفس السكربت ⇒ files/qnb-pdf-pages.json)
    if (ownerFiles == null || !File(ownerFiles, "qnb-pdf-pages.json").isFile) {
        filter { excludeTestsMatching("app.masroufy.core.RealQnbPdfTest") }
        logger.lifecycle("⚠️ كلمات كشف QNB (files/qnb-pdf-pages.json) مش موجودة — اختبار قارئ QNB الحقيقي مش هيشتغل")
    }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
