// تطبيق مصروفي الجديد للجهازين — KOTLIN_PLAN.md و OVERRIDES §38
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "masroufy-native"
include(":core")
include(":app")
include(":data")
include(":firestore")
include(":device")
// الواجهة (Compose Multiplatform — نظام التصميم v0.4 + هيكل التنقل + الشاشات) · التجميع (من المستودعات لحالات الاستخدام لكل منطقة) · تطبيق أندرويد
include(":ui")
include(":wiring")
include(":androidApp")
