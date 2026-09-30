/*
 * data — طبقة التخزين (infrastructure): تحويل الكيانات ↔ مستندات فايربيز، وبعدين المستودعات الحقيقية بـGitLive (OVERRIDES §52).
 * بتعتمد على `app` (والـ`core` جواه) — ومفيش حاجة جوه بتعتمد عليها (CLAUDE.md #2).
 * **لسه من غير GitLive:** المحوّلات كوتلن نقي بتشتغل على `Map` عادي، فبتتختبر على الكمبيوتر من غير فايربيز.
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
            api(project(":app"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest {
            // نفس أدوات ملفات المرجع بتاعة `core` — مصدر واحد
            kotlin.srcDir("../core/src/jvmTest/kotlin/app/masroufy/core/support")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
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
