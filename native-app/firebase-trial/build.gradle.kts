plugins {
    // نفس نسخة أندرويد اللي التطبيق الحالي بيتبني بيها (android/build.gradle)
    id("com.android.application") version "8.13.0" apply false
    kotlin("android") version "2.4.20" apply false
    kotlin("multiplatform") version "2.4.20" apply false
    id("com.android.library") version "8.13.0" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
}
