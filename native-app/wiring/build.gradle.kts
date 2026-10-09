import java.util.Properties

/*
 * wiring — **التجميع** (composition): من مستودعات البلد (`SpaceRepositories` — واجهات بس) لحالات الاستخدام اللي كل منطقة محتاجاها
 * (`SpaceGraph` بينفّذ `SpaceDeps` بتاع `:ui`). يدوي من غير مكتبة حقن (CLAUDE.md #6) — زي `app/container.ts` في التطبيق الحالي.
 * مفيش فايربيز ولا أندرويد هنا: `:androidApp` بيحوّل `FirestoreContainer` لـ`SpaceRepositories`، والاختبار على الكمبيوتر بيدّي مستودعات
 * الذاكرة — فالتجميع نفسه بيتختبر على JVM (`WiringSmokeTest`).
 */
plugins {
    kotlin("multiplatform")
}

val androidSdk: String? = System.getenv("ANDROID_HOME")
    ?: rootProject.file("local.properties").takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use(::load) }.getProperty("sdk.dir") }
val withAndroid = androidSdk != null
val onMac = System.getProperty("os.name").lowercase().contains("mac")
if (withAndroid) apply(plugin = "com.android.library")

kotlin {
    jvm()
    if (withAndroid) androidTarget()
    if (onMac) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":ui"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            // ترتيب الأسماء العربي في `core` على JVM (نفس اختبارات `app`)
            implementation("com.ibm.icu:icu4j:78.3")
        }
    }
}

if (withAndroid) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "app.masroufy.wiring"
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
