/*
 * firestore — المستودعات الحقيقية على فايربيز بـGitLive (OVERRIDES §52، ARCHITECTURE §31.3).
 * موديول لوحده عن `data` عشان اختبارات `data` تفضل تشتغل على محاكي الآيفون من غير مكتبات فايربيز للآيفون:
 * هنا الآيفون **بيتبني بس** (الربط بمكتبات فايربيز بيحصل في تطبيق الآيفون نفسه).
 *
 * ⚠️ **الاختبار على الكمبيوتر مش شغال** (اتجرب 2026-10-01): GitLive على JVM بيقعد فوق firebase-java-sdk، وقدام
 * Firestore Emulator حتى `enableNetwork()` بيعلّق — مهامه الداخلية ما بتشتغلش (ولا اتصال وصل للمحاكي في سجله).
 * ⇒ الاختبار الحقيقي للمستودعات يبقى على **محاكي أندرويد** (زي `firebase-trial` اللي GitLive اشتغل فيه فعلًا).
 * هدف JVM هنا عشان الكود المشترك يتبني على ويندوز بس.
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
            api(project(":data"))
            implementation("dev.gitlive:firebase-firestore:2.7.0")
        }
    }
}
