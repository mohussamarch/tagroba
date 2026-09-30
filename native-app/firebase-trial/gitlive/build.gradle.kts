/*
 * قارئ GitLive في **الكود المشترك** — نفس الملف بيتبني لأندرويد (والتطبيق بيشغّله على المحاكي)
 * وللآيفون (بيتبني على ماك GitHub بس: `:gitlive:compileKotlinIosSimulatorArm64`). ده اللي بيثبت إن الكود واحد للجهازين.
 */
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

val onMac = System.getProperty("os.name").lowercase().contains("mac")

kotlin {
    androidTarget()
    if (onMac) {
        iosArm64()
        iosSimulatorArm64()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":model"))
            implementation("dev.gitlive:firebase-firestore:2.7.0")
        }
    }
}

android {
    namespace = "app.masroufy.trial.gitlive"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
