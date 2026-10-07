/* الأنواع المشتركة بين النسختين (من غير أي مكتبة فايربيز) — بتتبني لأندرويد والآيفون. */
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val onMac = System.getProperty("os.name").lowercase().contains("mac")

kotlin {
    jvm()
    if (onMac) {
        iosArm64()
        iosSimulatorArm64()
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
        }
    }
}
