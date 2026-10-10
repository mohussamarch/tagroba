/*
 * androidApp — تطبيق أندرويد الجديد (`app.masroufy.mobile` — مسجّل في فايربيز ببصمة مفتاح التطوير، HANDOVER §2124/§2226) جنب التطبيق الحالي.
 * التجميع (composition root): `AppContainer` بيبني مستودعات فايربيز (`:firestore`) ⇒ `SpaceRepositories` ⇒ `SpaceGraph` (`:wiring`) ⇒ الشاشات (`:ui`).
 *
 * **إعدادات فايربيز (`google-services.json`) برا المستودع العام** في `secrets/` (جنب المشروع — مستبعد من Git، بندوّر لفوق زي `files/`):
 * موجود ⇒ بيتنسخ وقت البناء لـ`build/secrets/` (مستبعد) والإضافة بتقراه من هناك. مش موجود ⇒ الإضافة ما بتتطبقش خالص (البناء ينجح، والنسخة
 * الحقيقية من غير فايربيز — استعمل `emulator`).
 *
 * نوعين بناء: `debug` (مشروع المالك الحقيقي) · `emulator` (محاكي فايربيز على الكمبيوتر، مشروع `demo-masroufy-kt` — للتجربة بحسابات وهمية بس).
 */
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

val firebaseSettings = rootProject.extra["firebaseSettings"] as File?

android {
    namespace = "app.masroufy.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.masroufy.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "kotlin-0.2"
        buildConfigField("String", "FIREBASE_EMULATOR_PROJECT", "\"\"")
        buildConfigField("boolean", "HAS_FIREBASE_SETTINGS", (firebaseSettings != null).toString())
    }

    buildTypes {
        // مفتاح التطوير الافتراضي (~/.android/debug.keystore) — بصمته SHA-1 مسجلة في مشروع المالك (دخول جوجل)
        getByName("debug") { }
        create("emulator") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            buildConfigField("String", "FIREBASE_EMULATOR_PROJECT", "\"demo-masroufy-kt\"")
        }
        getByName("release") {
            isMinifyEnabled = false
        }
        /*
         * «السريعة» (HANDOVER §7 «السرعة»): نسخة المالك على الجوال — **مش debug**: R8 (تصغير + تحسين، من غير تغيير أسماء — `proguard-rules.pro`)
         * + تصغير الموارد + من غير `debuggable` (Compose والمكتبات بتشتغل بسرعتها) + ملف الكود السريع (`baseline-prof.txt` + `profileinstaller`).
         * موقّعة بنفس مفتاح التطوير ⇒ دخول جوجل بيفضل شغال (البصمة مسجلة) وبتتثبت فوق نسخة 0.1 من غير مسح البيانات.
         */
        create("fast") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-fast"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // نفس «السريعة» بالظبط بس على محاكي فايربيز (بيانات وهمية) — عشان نجرّبها ونقيسها قبل ما توصل للمالك
        create("fastEmulator") {
            initWith(getByName("fast"))
            matchingFallbacks += "release"
            buildConfigField("String", "FIREBASE_EMULATOR_PROJECT", "\"demo-masroufy-kt\"")
        }
    }

    // «السريعة» على المحاكي محتاجة نفس إذن HTTP لـ10.0.2.2 بتاع نسخة المحاكي (محاكي الدخول من غير TLS) — نفس الملفات، من غير نسخ
    sourceSets.getByName("fastEmulator") {
        manifest.srcFile("src/emulator/AndroidManifest.xml")
        res.srcDirs("src/emulator/res")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // فحص lint بتاع الإصدار بيقرا كوتلن بنسخة أقدم وبيطلع أخطاء وهمية (زي `firebase-trial`)
    lint { checkReleaseBuilds = false }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":wiring"))
    implementation(project(":firestore"))
    implementation(project(":device"))
    // نفس GitLive اللي `:firestore` بيستعملها (ARCHITECTURE §31.3 · §31.6) — التطبيق بيعمل `Firebase.initialize` بنفسه (الحقيقي أو المحاكي)
    implementation("dev.gitlive:firebase-firestore:2.7.0")
    implementation("dev.gitlive:firebase-auth:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // `setContent` + `enableEdgeToEdge` (ARCHITECTURE §31.31)
    implementation("androidx.activity:activity-compose:1.11.0")
    // نافذة البصمة محتاجة `FragmentActivity` (`AndroidDeviceLock` — §31.9)
    implementation("androidx.fragment:fragment:1.8.9")
    // دخول جوجل بنافذة النظام (Credential Manager) — ARCHITECTURE §31.31
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // ملف الكود السريع (baseline profile) بيتسجّل على الجوال حتى لو التثبيت من ملف مش من المتجر (ARCHITECTURE §31.37) —
    // كان جاي أصلًا مع Compose بشكل غير مباشر، هنا صريح عشان «السريعة» بتعتمد عليه
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}

if (firebaseSettings != null) {
    apply(plugin = "com.google.gms.google-services")
    val copied = layout.buildDirectory.file("secrets/google-services.json")
    val copyFirebaseSettings = tasks.register<Copy>("copyFirebaseSettings") {
        from(firebaseSettings)
        into(layout.buildDirectory.dir("secrets"))
    }
    afterEvaluate {
        tasks.withType<com.google.gms.googleservices.GoogleServicesTask>().configureEach {
            dependsOn(copyFirebaseSettings)
            googleServicesJsonFiles.set(listOf(copied.get().asFile))
        }
    }
    logger.lifecycle("إعدادات فايربيز لأندرويد: ${firebaseSettings.absolutePath} ⇒ build/secrets/")
} else {
    logger.lifecycle("⚠️ secrets/google-services.json مش موجود — التطبيق بيتبني من غير فايربيز الحقيقي (استعمل assembleEmulator)")
}
