# مصروفي — قواعد R8 لنسخة «السريعة» (fast / fastEmulator — HANDOVER §7، ARCHITECTURE §31.37).
# الهدف: تصغير وتحسين الكود (السرعة) **من غير ما حاجة تقع وقت التشغيل**.

# 1) من غير تغيير الأسماء: التسمية الجديدة بتكسب حجم بس مش سرعة، وبتكسر أي حاجة بتعتمد على اسم فئة
#    (`::class.simpleName` في رسايل الأخطاء ورموز الدخول · أسماء enum المكتوبة في فايربيز) — وسجل الأعطال بيفضل مقروء.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# 2) فايربيز (GitLive فوق مكتبة جوجل): مكتبات جوجل جاية بقواعدها. GitLive بيحوّل القيم بـkotlinx.serialization
#    والكود بيقرا المستندات خام (`getData()` ⇒ Map) — نحتفظ بطبقة GitLive كلها عشان أي تحويل بالانعكاس يفضل شغال.
-keep class dev.gitlive.firebase.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# 3) دخول جوجل (Credential Manager) — القاعدة الموصى بيها من جوجل: الكلاس بيتحمّل بالانعكاس
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *** *(...);
}
-keep class com.google.android.libraries.identity.googleid.** { *; }

# 4) قراية كشف PDF (`PdfBoxPages` — pdfbox-android): مكتبات اختيارية مش موجودة (صور JPEG2000 · التشفير)
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.bind.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
