/*
 * تطبيق تجربة بنسختين (flavors) من نفس الكود — الفرق الوحيد المكتبة اللي بتقرا من فايربيز:
 *  - direct  = مكتبة جوجل الرسمية لأندرويد مباشرة (والآيفون هيحتاج كود لوحده بـSwift)
 *  - gitlive = GitLive (مكتبة كوتلن لكل الأجهزة بتلف مكتبات جوجل الأصلية)
 * **نفس نسخة فايربيز في الاتنين (BoM 34.18.0 — اللي GitLive 2.7.0 مبنية عليها)** عشان المقارنة تبقى عادلة.
 * بيقرا من Firestore Emulator بس (مشروع demo) — مفيش اتصال بحساب المالك.
 */
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.masroufy.trial"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.masroufy.trial"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "trial"
    }

    flavorDimensions += "sdk"
    productFlavors {
        create("direct") {
            dimension = "sdk"
            applicationIdSuffix = ".direct"
        }
        create("gitlive") {
            dimension = "sdk"
            applicationIdSuffix = ".gitlive"
        }
    }

    buildTypes {
        release {
            // من غير تصغير (R8): المقارنة على الحجم الخام للمكتبة. موقّع بمفتاح التطوير عشان يتنصّب على المحاكي بس
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // فحص lint بتاع الإصدار بيقرا كوتلن بنسخة أقدم وبيطلع أخطاء وهمية — مشروع تجربة، مش لازم
    lint { checkReleaseBuilds = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(project(":model"))

    "directImplementation"(platform("com.google.firebase:firebase-bom:34.18.0"))
    "directImplementation"("com.google.firebase:firebase-firestore")
    "directImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    "gitliveImplementation"(project(":gitlive"))
}
