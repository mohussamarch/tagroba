# محاكي الدخول + Firestore بقواعد المشروع **الحقيقية**

لاختبار `AuthOnEmulatorTest` (محاكي أندرويد): تسجيل الدخول على Auth Emulator، وFirestore بنفس `firestore.rules` اللي
منشورة على مشروع المالك (مش قواعد الاختبار المفتوحة اللي في `../emulator/`). مشروع `demo-masroufy-auth` — اسم `demo-…`
مش ممكن يبقى مشروع حقيقي، فمفيش حاجة بتوصل لفايربيز الحقيقي.

```powershell
cd native-app/firestore/emulator-auth
# المحاكي ما بيقبلش ملف قواعد برا مجلده ⇒ نسخة من الأصل كل مرة (مش في Git عشان ما تختلفش عنه)
Copy-Item ../../../firestore.rules firestore.rules
firebase emulators:start --only auth,firestore --project demo-masroufy-auth
```
(Java 21 في PATH). المنافذ 9099 و8089 — جنب محاكي `../emulator/` على 8088، فالاتنين يشتغلوا مع بعض.
