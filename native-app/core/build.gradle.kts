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
        jvmTest.dependencies {
            // قراية ملفات المرجع JSON — للاختبارات بس (ARCHITECTURE §31)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("golden.dir", rootProject.file("golden").absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
