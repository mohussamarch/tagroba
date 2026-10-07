// تجربة مكتبة فايربيز (KOTLIN_PLAN §4-أ4) — مشروع **لوحده** برا بناء التطبيق، فما بيأثرش على `core` و`app` ولا على الـworkflow.
// التشغيل: من `native-app/`: ./gradlew -p firebase-trial assembleDebug
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "masroufy-firebase-trial"
include(":app", ":model", ":gitlive")
