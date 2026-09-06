# مصروفي — إعدادات فايربيز

> أُنشئ في 2026-09-06. هذا الملف مرجع الجلسة لكل ما يخص فايربيز.

## المشروع

| البند | القيمة |
|---|---|
| اسم المشروع | masroufy |
| Project ID | `masroufy-d4b96` |
| Project number | `618279498271` |
| الباقة | **Spark (مجانية — بلا بطاقة)** |
| الاسم العام للمستخدمين | Masroufy |
| بريد الدعم | atgs03@gmail.com |

## تسجيل الدخول (Authentication) — مفعّل

- ✅ **Google** — Sign in with Google
- ✅ **Email/Password**
- ❌ Email link (passwordless) — معطّل عمداً
- ❌ Phone — معطّل (له تكلفة لكل رسالة)

## قاعدة البيانات (Cloud Firestore)

| البند | القيمة |
|---|---|
| الإصدار | Standard edition |
| Database ID | `(default)` |
| **الموقع** | **`me-central2` (الدمام، السعودية)** |
| وضع البدء | **Production mode** — كل القراءة والكتابة مرفوضة حتى تُنشر القواعد |

⚠️ الموقع **غير قابل للتغيير**. تغييره يتطلب مشروعاً جديداً من الصفر.

## الاستضافة (Hosting)

موقع الاستضافة `masroufy-d4b96` موجود ولم يُنشر عليه شيء بعد.
النطاقات المتوقعة: `masroufy-d4b96.web.app` و `masroufy-d4b96.firebaseapp.com`

## إعدادات تطبيق الويب

App nickname: `masroufy-web`
App ID: `1:618279498271:web:d6c9539ebee0f869219b2a`

```js
const firebaseConfig = {
  apiKey: "AIzaSyAdGNaIlkdMghIaAXZoFBDSLU5c7HSXz1o",
  authDomain: "masroufy-d4b96.firebaseapp.com",
  projectId: "masroufy-d4b96",
  storageBucket: "masroufy-d4b96.firebasestorage.app",
  messagingSenderId: "618279498271",
  appId: "1:618279498271:web:d6c9539ebee0f869219b2a"
};
```

**ملاحظة أمنية:** هذه الإعدادات **علنية بطبيعتها** ولا تُعد سراً — أي زائر للتطبيق يستطيع رؤيتها في كود الصفحة. الذي يحمي البيانات هو **قواعد الحماية** وحدها. مع ذلك تُوضع في ملف `.env.local` كعادة تنظيمية لا أمنية.

## قواعد الحماية — لم تُنشر بعد ⚠️

أول مهمة في المرحلة 0: إنشاء `firestore.rules` بهذا المحتوى ونشره بـ
`firebase deploy --only firestore:rules`

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null
                         && request.auth.uid == uid;
    }
  }
}
```

حتى ذلك الحين قاعدة البيانات ترفض كل شيء (Production mode)، وهذا آمن.

## حدود الباقة المجانية (Spark)

| المورد | الحد اليومي |
|---|---|
| قراءات المستندات | 50,000 |
| كتابات المستندات | 20,000 |
| حذف المستندات | 20,000 |
| التخزين | 1 جيجابايت |

**الحد المقيِّد هو عدد القراءات لا الحجم.** التزم بالقواعد الثلاث في `ARCHITECTURE.md` §5.6:
العمل بدون إنترنت، ومستندات `periodSummaries` الجاهزة، وكل استعلام محدود بفترة.

**Cloud Functions غير متاحة على Spark.** الأسعار عبر GitHub Actions كما في `ARCHITECTURE.md` §6.

## روابط

- [كونسول المشروع](https://console.firebase.google.com/project/masroufy-d4b96/overview)
- [Authentication](https://console.firebase.google.com/project/masroufy-d4b96/authentication/providers)
- [Firestore](https://console.firebase.google.com/project/masroufy-d4b96/firestore)
