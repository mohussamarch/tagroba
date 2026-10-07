import java.util.Properties

/*
 * device — منافذ الجهاز نفسه (طبقة infrastructure): قراية كشف PDF دلوقتي، ورسايل البنك والتنبيهات بعدين.
 * أندرويد: PdfBox-Android (KOTLIN_PLAN §2، ARCHITECTURE §31.7). الآيفون: PDFKit وLocalAuthentication من النظام (مفيش مكتبة)
 * — هدف الآيفون **على ماك بس** (زي باقي الموديولات)، فبيتبني ويتختبر على GitHub.
 * هدف أندرويد بيتفعّل **بس لو Android SDK موجود** (زي `:firestore`)، وهدف JVM فاضي عشان الموديول يتبني من غيره.
 * ⚠️ اختبار الكشف الحقيقي بياخد الملفات من `files/` (برا Git) لـ`build/ownerTestAssets` وقت البناء — على جهاز المالك بس.
 */
plugins {
    kotlin("multiplatform")
}

val androidSdk: String? = System.getenv("ANDROID_HOME")
    ?: rootProject.file("local.properties").takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use(::load) }.getProperty("sdk.dir") }
val withAndroid = androidSdk != null
val onMac = System.getProperty("os.name").lowercase().contains("mac")
if (withAndroid) apply(plugin = "com.android.library")
else logger.lifecycle("⚠️ Android SDK مش موجود — هدف أندرويد في :device مش هيتبني")

kotlin {
    jvm()
    if (withAndroid) androidTarget()
    if (onMac) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":app"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // الكشف المخترع (`SyntheticStatement`) — مصدر واحد لاختبارات الكمبيوتر وأندرويد والآيفون
        jvmTest { kotlin.srcDir("src/sharedTest/kotlin") }
        if (onMac) {
            // `iosMain`/`iosTest` بيتعملوا من القالب الافتراضي — بالاسم (`getByName`) مش موجودين لسه هنا، فبالـaccessor
            iosMain.dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
            iosTest { kotlin.srcDir("src/sharedTest/kotlin") }
        }
        if (withAndroid) {
            getByName("androidMain").dependencies {
                // قارئ PDF على أندرويد (Apache 2.0) — ARCHITECTURE §31.7
                implementation("com.tom-roush:pdfbox-android:2.0.27.0")
                // القراية في الخلفية (`withContext`) — نفس النسخة اللي في باقي الموديولات
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                // قفل التطبيق بالبصمة أو رمز الجوال — نفس مكتبة التطبيق الحالي (ARCHITECTURE §27 و§31.9)
                implementation("androidx.biometric:biometric:1.1.0")
                // رسايل البنك بتتسجل لوحدها في الخلفية + الدورة الدورية + الإشعار المتأجل (OVERRIDES §72) — السبب ARCHITECTURE §31.29
                implementation("androidx.work:work-runtime-ktx:2.10.5")
                // `NotificationCompat` (القناة · شاشة القفل · أندرويد 13) — جاية أصلًا مع WorkManager والبصمة؛ مكتوبة صريح لأننا بنستعملها
                implementation("androidx.core:core:1.12.0")
            }
            getByName("androidInstrumentedTest").kotlin.srcDir("src/sharedTest/kotlin")
            getByName("androidInstrumentedTest").dependencies {
                implementation(kotlin("test"))
                implementation("androidx.test:runner:1.7.0")
                implementation("androidx.test.ext:junit:1.3.0")
                implementation("androidx.test:rules:1.7.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                // تشغيل العامل في الاختبار (TestListenableWorkerBuilder) — نفس نسخة WorkManager
                implementation("androidx.work:work-testing:2.10.5")
            }
        }
    }
}

if (withAndroid) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "app.masroufy.device"
        compileSdk = 36
        defaultConfig {
            minSdk = 24
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        if (providers.gradleProperty("masroufy.releaseTests").isPresent) testBuildType = "release"
    }

    // كشفين المالك الحقيقيين + كلماتهم من قارئ الكمبيوتر (pdfjs) للمقارنة — مش في Git ولا في APK غير الاختبار
    val owner = rootProject.extra["ownerFiles"] as File?
    val names = listOf("alrajhi-statement.pdf", "alrajhi-pdf-pages.json", "qnb-statement.pdf", "qnb-pdf-pages.json")
    val ownerAssets = layout.buildDirectory.dir("ownerTestAssets")
    val copyOwnerStatements = tasks.register<Copy>("copyOwnerStatements") {
        if (owner != null) from(owner) { include(names); into("owner") }
        into(ownerAssets)
    }
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        sourceSets.getByName("androidTest").assets.srcDir(ownerAssets)
    }
    tasks.matching { it.name.startsWith("merge") && it.name.contains("AndroidTestAssets") }.configureEach { dependsOn(copyOwnerStatements) }
}
