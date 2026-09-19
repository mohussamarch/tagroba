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
