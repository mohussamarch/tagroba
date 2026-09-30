import java.util.Properties

/*
 * firestore — المستودعات الحقيقية على فايربيز بـGitLive (OVERRIDES §52، ARCHITECTURE §31.3).
 * موديول لوحده عن `data` عشان اختبارات `data` تفضل تشتغل على محاكي الآيفون من غير مكتبات فايربيز للآيفون:
 * هنا الآيفون **بيتبني بس** (الربط بمكتبات فايربيز بيحصل في تطبيق الآيفون نفسه).
 *
 * **الاختبار على محاكي أندرويد** (`androidInstrumentedTest`) قدام Firestore Emulator على الكمبيوتر (10.0.2.2:8088).
 * ⚠️ على الكمبيوتر (JVM) مش شغال (اتجرب 2026-10-01): GitLive هناك فوق firebase-java-sdk، وقدام المحاكي حتى
 * `enableNetwork()` بيعلّق. هدف JVM هنا عشان الكود المشترك يتبني على ويندوز بس.
 *
 * هدف أندرويد بيتفعّل **بس لو Android SDK موجود** (`ANDROID_HOME` أو `local.properties`) — عشان باقي الموديولات تتبني من غيره.
 */
plugins {
    kotlin("multiplatform")
}

val onMac = System.getProperty("os.name").lowercase().contains("mac")
val androidSdk: String? = System.getenv("ANDROID_HOME")
    ?: rootProject.file("local.properties").takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use(::load) }.getProperty("sdk.dir") }
val withAndroid = androidSdk != null
if (withAndroid) apply(plugin = "com.android.library")
else logger.lifecycle("⚠️ Android SDK مش موجود — هدف أندرويد في :firestore (واختباراته على المحاكي) مش هيتبني")

kotlin {
    jvm()
    if (withAndroid) androidTarget()
    if (onMac) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":data"))
            implementation("dev.gitlive:firebase-firestore:2.7.0")
        }
        if (withAndroid) {
            getByName("androidInstrumentedTest").dependencies {
                implementation(kotlin("test"))
                implementation("androidx.test:runner:1.7.0")
                implementation("androidx.test.ext:junit:1.3.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            }
        }
    }
}

if (withAndroid) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "app.masroufy.firestore"
        compileSdk = 36
        defaultConfig {
            minSdk = 24
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
