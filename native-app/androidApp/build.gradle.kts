/*
 * androidApp — تطبيق أندرويد الجديد (`app.masroufy.mobile` — مسجّل في فايربيز ببصمة مفتاح التطوير، HANDOVER §2124/§2226) جنب التطبيق الحالي.
 * التجميع (composition root): `AppContainer` بيبني مستودعات فايربيز (`:firestore`) ⇒ `SpaceRepositories` ⇒ `SpaceGraph` (`:wiring`) ⇒ الشاشات (`:ui`).
 *
 * **إعدادات فايربيز (`google-services.json`) برا المستودع العام** في `secrets/` (جنب المشروع — مستبعد من Git، بندوّر لفوق زي `files/`):
 * موجود ⇒ بيتنسخ وقت البناء لـ`build/secrets/` (مستبعد) والإضافة بتقراه من هناك. مش موجود ⇒ الإضافة ما بتتطبقش خالص (البناء ينجح، والنسخة
 * الحقيقية من غير فايربيز — استعمل `emulator`).
 *
 * نوعين بناء: `debug` (مشروع المالك الحقيقي) · `emulator` (محاكي فايربيز على الكمبيوتر، مشروع `demo-masroufy-kt` — للتجربة بحسابات وهمية بس).
 */
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

val firebaseSettings = rootProject.extra["firebaseSettings"] as File?

/** AUTH-1: App Check — الافتراضي مقفول (`-Pmasroufy.appCheck=true` يفتحه بعد خطوات المالك في `tools/security/APP-CHECK-STEPS.md`). */
val appCheckOn = providers.gradleProperty("masroufy.appCheck").orNull == "true"

android {
    namespace = "app.masroufy.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.masroufy.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "kotlin-0.5.4"
        buildConfigField("String", "FIREBASE_EMULATOR_PROJECT", "\"\"")
        buildConfigField("boolean", "HAS_FIREBASE_SETTINGS", (firebaseSettings != null).toString())
        // تقارير الأعطال (OVERRIDES §90): شغالة في نسخ المالك الحقيقية (debug · fast)، ومقفولة في نسخ المحاكي (emulator · fastEmulator)
        buildConfigField("boolean", "CRASH_REPORTS", "true")
        // بيانات الاستخدام (OVERRIDES §114): نفس قاعدة تقارير الأعطال — شغالة في نسخ المالك، مقفولة في نسخ المحاكي
        buildConfigField("boolean", "USAGE_ANALYTICS", "true")
    }

    buildTypes {
        // مفتاح التطوير الافتراضي (~/.android/debug.keystore) — بصمته SHA-1 مسجلة في مشروع المالك (دخول جوجل)
        getByName("debug") { }
        create("emulator") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            buildConfigField("String", "FIREBASE_EMULATOR_PROJECT", "\"demo-masroufy-kt\"")
            buildConfigField("boolean", "CRASH_REPORTS", "false")
            buildConfigField("boolean", "USAGE_ANALYTICS", "false")
        }
        getByName("release") {
            isMinifyEnabled = false
        }
        /*
         * «السريعة» (HANDOVER §7 «السرعة»): نسخة المالك على الجوال — **مش debug**: R8 (تصغير + تحسين، من غير تغيير أسماء — `proguard-rules.pro`)
         * + تصغير الموارد + من غير `debuggable` (Compose والمكتبات بتشتغل بسرعتها) + ملف الكود السريع (`baseline-prof.txt` + `profileinstaller`).
         * موقّعة بنفس مفتاح التطوير ⇒ دخول جوجل بيفضل شغال (البصمة مسجلة) وبتتثبت فوق نسخة 0.1 من غير مسح البيانات.
         */
        create("fast") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-fast"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // نفس «السريعة» بالظبط بس على محاكي فايربيز (بيانات وهمية) — عشان نجرّبها ونقيسها قبل ما توصل للمالك
        create("fastEmulator") {
            initWith(getByName("fast"))
            matchingFallbacks += "release"
            buildConfigField("String", "FIREBASE_EMULATOR_PROJECT", "\"demo-masroufy-kt\"")
            buildConfigField("boolean", "CRASH_REPORTS", "false")
            buildConfigField("boolean", "USAGE_ANALYTICS", "false")
        }
    }

    // AUTH-1 (PLAN-0.6 §1 · `tools/security/APP-CHECK-STEPS.md`): App Check **مقفول** لحد ما المالك يسجّل البرنامج في Play Integrity ويوافق.
    // مقفول ⇒ ملف فاضي (`src/appCheckOff`) ومفيش مكتبة جديدة ولا تنزيل. مفتوح بـ`-Pmasroufy.appCheck=true` ⇒ `src/appCheckOn` + المكتبات تحت.
    sourceSets.getByName("main").java.srcDir(if (appCheckOn) "src/appCheckOn/kotlin" else "src/appCheckOff/kotlin")
    // «السريعة» على المحاكي محتاجة نفس إذن HTTP لـ10.0.2.2 بتاع نسخة المحاكي (محاكي الدخول من غير TLS) — نفس الملفات، من غير نسخ
    sourceSets.getByName("fastEmulator") {
        manifest.srcFile("src/emulator/AndroidManifest.xml")
        res.srcDirs("src/emulator/res")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // فحص lint بتاع الإصدار بيقرا كوتلن بنسخة أقدم وبيطلع أخطاء وهمية (زي `firebase-trial`)
    lint { checkReleaseBuilds = false }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":wiring"))
    implementation(project(":firestore"))
    implementation(project(":device"))
    // نفس GitLive اللي `:firestore` بيستعملها (ARCHITECTURE §31.3 · §31.6) — التطبيق بيعمل `Firebase.initialize` بنفسه (الحقيقي أو المحاكي)
    implementation("dev.gitlive:firebase-firestore:2.7.0")
    implementation("dev.gitlive:firebase-auth:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // `setContent` + `enableEdgeToEdge` (ARCHITECTURE §31.31)
    implementation("androidx.activity:activity-compose:1.11.0")
    // نافذة البصمة محتاجة `FragmentActivity` (`AndroidDeviceLock` — §31.9)
    implementation("androidx.fragment:fragment:1.8.9")
    // دخول جوجل بنافذة النظام (Credential Manager) — ARCHITECTURE §31.31
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // ملف الكود السريع (baseline profile) بيتسجّل على الجوال حتى لو التثبيت من ملف مش من المتجر (ARCHITECTURE §31.37) —
    // كان جاي أصلًا مع Compose بشكل غير مباشر، هنا صريح عشان «السريعة» بتعتمد عليه
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // تقارير الأعطال (OVERRIDES §90 — اختيار المالك «الاتنين»، السبب ARCHITECTURE — القاعدة 8): نفس BoM فايربيز اللي GitLive 2.7.0
    // جايبها (34.18.0 ⇒ crashlytics 20.1.0) عشان كل مكتبات جوجل تفضل نفس العيلة. الجمع مقفول في الملف (`AndroidManifest.xml`) والتطبيق
    // بيفتحه بنفسه حسب نوع البناء ومفتاح المستخدم (`CrashReports.kt`)
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-crashlytics:20.1.0")
    // بيانات الاستخدام (OVERRIDES §114 — السبب في ARCHITECTURE «بيانات الاستخدام»): من نفس BoM (من غير رقم نسخة — الـBoM بيحدده).
    // الجمع مقفول في المانيفست والتطبيق بيفتحه في نسخ المالك بس (`FirebaseUsage.kt`)؛ معرّف الإعلانات والإعلانات المخصصة مقفولين
    implementation("com.google.firebase:firebase-analytics")
    // AUTH-1 — App Check (السبب في ARCHITECTURE «App Check»): من نفس BoM. مزوّد Play Integrity في كل النسخ، ومزوّد التجربة (debug)
    // في نسخ debug والمحاكي بس (`AppCheckSetup` بيلاقيه بالاسم — نسخة الإصدار مالهاش مزوّد تجربة خالص)
    if (appCheckOn) {
        implementation("com.google.firebase:firebase-appcheck-playintegrity")
        for (config in listOf("debugImplementation", "emulatorImplementation", "fastEmulatorImplementation")) add(config, "com.google.firebase:firebase-appcheck-debug")
    }
}

/*
 * ملف الأسماء (mapping) بتاع «السريعة»: R8 بيكتبه في `outputs/mapping/fast/mapping.txt` وبيكتب فوقه كل بناء ⇒ نسخة محفوظة باسم النسخة
 * في `outputs/mapping-archive/` (عشان أي تقرير قديم يتقري بعدين حتى لو الرفع ما حصلش).
 */
val fastMappingName = "mapping-${android.defaultConfig.versionName}-fast-${android.defaultConfig.versionCode}.txt"
val keepFastMapping = tasks.register<Copy>("keepFastMapping") {
    from(layout.buildDirectory.file("outputs/mapping/fast/mapping.txt"))
    into(layout.buildDirectory.dir("outputs/mapping-archive"))
    rename { fastMappingName }
    // إضافة Compose بتكتب فوق نفس الملف بعد R8 ⇒ النسخ يستناها (وإلا Gradle بيوقف البناء: implicit dependency)
    mustRunAfter(tasks.matching { it.name == "mergeFastComposeMapping" })
}
tasks.matching { it.name == "minifyFastWithR8" }.configureEach { finalizedBy(keepFastMapping) }

if (firebaseSettings != null) {
    apply(plugin = "com.google.gms.google-services")
    /*
     * تقارير الأعطال (OVERRIDES §90): الإضافة بتحط رقم البناء اللي المكتبة بتطلبه، وبترفع ملف الأسماء لنسخة «fast» بس (مشروع المالك).
     * ⚠️ الرفع محتاج نت وقت البناء: لو فشل، البناء بيفشل برسالة `uploadCrashlyticsMappingFileFast`. من غير نت:
     * `-Pmasroufy.crashlyticsUpload=false` (النسخة المحلية في `outputs/mapping-archive/` بتتعمل برضه). نسخ المحاكي وdebug ما بترفعش.
     */
    apply(plugin = "com.google.firebase.crashlytics")
    val uploadMapping = providers.gradleProperty("masroufy.crashlyticsUpload").orNull != "false"
    android.buildTypes.configureEach {
        extensions.configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
            mappingFileUploadEnabled = name == "fast" && uploadMapping
            nativeSymbolUploadEnabled = false
        }
    }
    val copied = layout.buildDirectory.file("secrets/google-services.json")
    val copyFirebaseSettings = tasks.register<Copy>("copyFirebaseSettings") {
        from(firebaseSettings)
        into(layout.buildDirectory.dir("secrets"))
    }
    afterEvaluate {
        tasks.withType<com.google.gms.googleservices.GoogleServicesTask>().configureEach {
            dependsOn(copyFirebaseSettings)
            googleServicesJsonFiles.set(listOf(copied.get().asFile))
        }
    }
    logger.lifecycle("إعدادات فايربيز لأندرويد: ${firebaseSettings.absolutePath} ⇒ build/secrets/")
} else {
    logger.lifecycle("⚠️ secrets/google-services.json مش موجود — التطبيق بيتبني من غير فايربيز الحقيقي (استعمل assembleEmulator)")
}
