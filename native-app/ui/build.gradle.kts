import java.util.Properties

/*
 * ui — الواجهة بـCompose Multiplatform (اختيار المالك، KOTLIN_PLAN §2 · ARCHITECTURE §31.31):
 * نظام التصميم v0.4 (المكوّنات) + الهيكل (التنقل والشرايط تحت) + الشاشات، كل منطقة في `screens/<المنطقة>/`.
 * **الشاشة ما بتحسبش مبلغ ولا بتكلّم مستودع** (CLAUDE.md #4): بتاخد حالات استخدام من واجهة منطقتها (`<Area>Deps`)،
 * والتنفيذ في `:wiring` (التجميع). بتعتمد على `app` (حالات الاستخدام وأنواع نتايجها) بس — مفيش فايربيز هنا.
 * أندرويد بيتفعّل **بس لو Android SDK موجود** (زي `:firestore`)؛ هدف JVM لاختبارات المتنقّل على الكمبيوتر.
 */
plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val androidSdk: String? = System.getenv("ANDROID_HOME")
    ?: rootProject.file("local.properties").takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use(::load) }.getProperty("sdk.dir") }
val withAndroid = androidSdk != null
val onMac = System.getProperty("os.name").lowercase().contains("mac")
if (withAndroid) apply(plugin = "com.android.library")
else logger.lifecycle("⚠️ Android SDK مش موجود — هدف أندرويد في :ui مش هيتبني")

// 1.11.1 = androidx Compose 1.11 (compileSdk 35+ · AGP 8.6+). 1.12 محتاج compileSdk 37 وAGP 9.1 — مش متاحين هنا لسه
val composeVersion = "1.11.1"
val androidxComposeVersion = "1.11.0"

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
            // Compose Multiplatform بس (من غير Material) — نظام التصميم مكتوب بإيدنا (DESIGN-SYSTEM v0.4)
            api("org.jetbrains.compose.runtime:runtime:$composeVersion")
            api("org.jetbrains.compose.foundation:foundation:$composeVersion")
            api("org.jetbrains.compose.ui:ui:$composeVersion")
            api("org.jetbrains.compose.animation:animation:$composeVersion")
            // نفس النسخة اللي في باقي الموديولات — `StateFlow` بين التجميع والشاشات
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        if (withAndroid) {
            getByName("androidMain").dependencies {
                // خط Noto Sans Arabic من خدمة خطوط جوجل على الجهاز (من غير ملف خط في المستودع) — ARCHITECTURE §31.31
                implementation("androidx.compose.ui:ui-text-google-fonts:$androidxComposeVersion")
                // منتقي ملفات الجوال لـ«كشف الحساب» (`rememberLauncherForActivityResult` + `OpenDocument`) — نفس المكتبة اللي `:androidApp` بيستعملها (ARCHITECTURE §31.31)
                // نافذة النظام لحفظ ملف واختياره (النسخة الاحتياطية والتصدير — `CreateDocument`/`OpenDocument`) — نفس نسخة `:androidApp` (ARCHITECTURE §31.32)
                implementation("androidx.activity:activity-compose:1.11.0")
            }
        }
    }
}

if (withAndroid) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "app.masroufy.ui"
        compileSdk = 36
        defaultConfig { minSdk = 24 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
