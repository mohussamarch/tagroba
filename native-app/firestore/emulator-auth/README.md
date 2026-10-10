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

## تجربة التطبيق الجديد نفسه (نوع البناء `emulator` — ARCHITECTURE §31.31)

نفس المحاكيين بمشروع **`demo-masroufy-kt`** (اللي `androidApp` بنوع `emulator` بيكلمه على `10.0.2.2`):

```powershell
firebase emulators:start --only auth,firestore --project demo-masroufy-kt
```

ثم من `native-app`: `./gradlew :androidApp:assembleEmulator` ⇒ `F:/masroufy-build/<النسخة>/androidApp/outputs/apk/emulator/androidApp-emulator.apk`.
**حساب تجربة للمحاكي بس** (بيتمسح لما المحاكي يتقفل — اعمله من «حساب جديد» في التطبيق): `shell-check@example.com` / `Emu-only-7391`.

## قياس السرعة (HANDOVER §7 — 2026-10-10)

1. المحاكيين شغالين (فوق) ⇒ `node perf-seed.mjs` (3,500 عملية **مخترعة** + محافظ وتصنيفات وأشخاص وديون واشتراكات لحساب `perf-user`).
2. نسخة المحاكي (`assembleEmulator`) أو «السريعة» على المحاكي (`assembleFastEmulator`) ⇒ `adb install -r …`.
3. الدخول من غير كلمة سر (مفتاح غير موقّع — محاكي الدخول بس بيقبله):
   `adb shell am start -n app.masroufy.mobile/app.masroufy.android.MainActivity --es perf_uid perf-user`
4. `bash perf/flow.sh <ملف-السجل>` ⇒ جدول: من اللمسة لحد ما الشاشة اتملت · قراءات السيرفر · القراءات من الذاكرة. `perf/gfx.sh` = الإطارات (التمويه).
   سطور `MPERF` في logcat بتطلع **في نسخ المحاكي بس**. أماكن اللمس في `flow.sh` لشاشة 1080×2400 (`masroufy-fbtrial`).
- ⚠️ لو التطبيق مش شايف `10.0.2.2` («Network is unreachable»): واي فاي المحاكي من غير مسار ⇒ `adb shell svc wifi disable` (بيمشي على بيانات الجوال).
ممنوع حسابات تجربة على مشروع المالك الحقيقي.
