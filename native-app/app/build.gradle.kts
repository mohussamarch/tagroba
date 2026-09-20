/*
 * app — طبقة الاستخدامات (application): واجهات المستودعات (ports) وحالات الاستخدام (use cases).
 * بتعتمد على `core` بس ومفيش حاجة بتعتمد عليها من جوه (CLAUDE.md #2) — الاتجاه للداخل، والمترجم بيمنع العكس.
 * ولا فايربيز ولا Room ولا شاشات هنا: دول في الطبقة اللي بره.
 */
plugins {
    kotlin("multiplatform")
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
            api(project(":core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest {
            // نفس أدوات ملفات المرجع بتاعة `core` — مصدر واحد، من غير نسخ
            kotlin.srcDir("../core/src/jvmTest/kotlin/app/masroufy/core/support")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                // `runBlocking` عشان حالات الاستخدام `suspend` — للاختبارات بس (ARCHITECTURE §31)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("com.ibm.icu:icu4j:78.3")
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    val golden = rootProject.file("golden")
    inputs.dir(golden).withPropertyName("golden")
    systemProperty("golden.dir", golden.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
