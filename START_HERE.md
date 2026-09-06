# مصروفي — ابدأ من هنا (SETUP)

> خطوات التجهيز مرة واحدة، ثم بدء جلسة Claude Code.
> نفّذها بالترتيب. كل أمر جاهز للنسخ.

---

## الجزء 1 — تجهيز المجلد

مسار المشروع الحالي فيه حروف عربية ومسافات، وده بيسبب أخطاء غريبة مع أدوات Node على ويندوز.

افتح **PowerShell** ونفّذ:

```powershell
mkdir E:\work\masroufy
cd E:\work\masroufy
```

ثم انسخ إلى `E:\work\masroufy` هذه الملفات من `E:\work\me\برامج\تطبيق دراهم`:

- `ARCHITECTURE.md`
- `DATA_MODEL.md`
- `SPEC.md`
- `CLAUDE.md`
- `START_HERE.md`
- `مواصفات-تصميم-مصروفي.md`
- `واجهة-التطبيق-ودليل-التصميم.md`
- `تعديلات-على-مواصفات-مصروفي.md`
- مجلد `files` كامل

المجلد القديم يفضل كما هو كأرشيف.

---

## الجزء 2 — البرامج المطلوبة

### 2.1 Node.js

نزّل نسخة **LTS** من [nodejs.org](https://nodejs.org) وثبّتها بالإعدادات الافتراضية.

للتأكد:
```powershell
node -v
npm -v
```
لازم يطلع رقمين. لو ظهر خطأ، اقفل PowerShell وافتحه من جديد.

### 2.2 Git

نزّل من [git-scm.com](https://git-scm.com/download/win) وثبّت بالإعدادات الافتراضية.

```powershell
git --version
```

عرّف نفسك مرة واحدة:
```powershell
git config --global user.name "Mohamed Hossam"
git config --global user.email "atgs03@gmail.com"
```

### 2.3 VS Code

نزّل من [code.visualstudio.com](https://code.visualstudio.com)

### 2.4 Claude Code

```powershell
npm install -g @anthropic-ai/claude-code
```

للتأكد:
```powershell
claude --version
```

---

## الجزء 3 — الحسابات

### 3.1 GitHub

اعمل حساب مجاني على [github.com](https://github.com) لو ما عندكش.
لا تعمل مستودع (repository) الآن — الجلسة هتعمله.

### 3.2 مشروع فايربيز

روح [console.firebase.google.com](https://console.firebase.google.com)

1. **Add project** → الاسم `masroufy`
2. **Google Analytics: عطّلها** (Disable) — مش محتاجينها وبتزود تعقيد
3. من القائمة اليسار: **Build → Authentication → Get started**
   - تبويب **Sign-in method**
   - فعّل **Google**
   - فعّل **Email/Password**
4. من القائمة اليسار: **Build → Firestore Database → Create database**
   - اختر **Production mode** (مش Test mode — ده بيخلي البيانات مفتوحة للكل)
   - **الموقع (location):** اختر الأقرب للسعودية. لو `me-central2` (الدمام) متاح خده، وإلا `europe-west1`
   - ⚠️ **الموقع لا يتغير بعد كده إطلاقاً**
5. **⚙️ Project settings** (الترس فوق) → انزل لتحت لـ **Your apps**
   - دوس أيقونة الويب **`</>`**
   - App nickname: `masroufy-web`
   - **لا تفعّل** Firebase Hosting دلوقتي
   - **Register app**
   - هيظهر لك كود فيه `firebaseConfig` — **انسخه كله واحتفظ به**، هتحتاجه في الجلسة

---

## الجزء 4 — الإجابتان المطلوبتان

جهّزهم قبل ما تبدأ الجلسة:

**1. الرصيد الافتتاحي**
الرصيد في حسابك بالراجحي قبل أول عملية في الكشف، وتاريخه.
من `transactions_full.csv`: أول عملية في 2025/01/01 مدين 96.47 والرصيد بعدها 4741.36
يعني الرصيد الافتتاحي = **4,837.83 ر.س بتاريخ 2025/01/01** — أكّدها أو صححها.

**2. الشهر المالي بيبدأ يوم كام؟**
الأول من الشهر الميلادي؟ ولا يوم نزول الراتب؟ (لو كده، يوم كام)

---

## الجزء 5 — بدء الجلسة

في PowerShell:

```powershell
cd E:\work\masroufy
claude
```

كلود كود هيقرأ `CLAUDE.md` تلقائياً ويعرف القواعد.

### الرسالة الأولى — انسخها كما هي وعدّل المكانين المعلّمين

```
اقرأ ARCHITECTURE.md و DATA_MODEL.md و SPEC.md و FIREBASE.md كاملة قبل أي شيء.

نبدأ المرحلة 0 والمرحلة 1 من مراحل البناء في ARCHITECTURE.md.

معلومات ناقصة كنت طلبتها:
- الرصيد الافتتاحي البنكي: 4,837.83 ر.س بتاريخ 2025-01-01
- الرصيد الافتتاحي للكاش: 1,000.00 ر.س بتاريخ 2026-09-06
- الشهر المالي يبدأ يوم: 28
- إعدادات فايربيز: موجودة كلها في FIREBASE.md

المرحلة 0 — التجهيز:
1. أنشئ مشروع React + TypeScript + Vite مع vite-plugin-pwa
2. جهّز هيكل المجلدات domain/ و application/ و infrastructure/ و presentation/ و app/ كما في قسم Clean Architecture في ARCHITECTURE.md، واحترم قاعدة الاعتماد للداخل
3. اربط فايربيز: Authentication و Firestore مع تفعيل العمل بدون إنترنت
4. اكتب ملف قواعد الحماية firestore.rules بالنص الموجود في FIREBASE.md وانشره بـ firebase deploy --only firestore:rules — ده أول حاجة تتعمل لأن قاعدة البيانات دلوقتي رافضة كل حاجة
5. اعمل شاشة تسجيل دخول بجوجل وبالإيميل وكلمة السر، وشاشة فاضية بعد الدخول
6. git init و commit أول
معيار الاكتمال: أسجل دخول وأخرج بنجاح

المرحلة 1 — الشريحة الأولى:
1. عرّف واجهات المستودعات (ports) في application/ports/ حسب DATA_MODEL.md
2. حوّل files/parse_alrajhi.py إلى محلل بـ TypeScript في infrastructure/import/
3. نفّذ منع التكرار ببصمة dedupeKey كما في DATA_MODEL.md
4. استورد rules_categories.csv و merchants_classified.csv كبيانات أولية
5. نفّذ محرك التصنيف في domain/categorize.ts بترتيب الأولوية الموجود في DATA_MODEL.md — دوال نقية بلا أي اعتماد على فايربيز
6. اكتب حالتي الاستخدام ImportStatement و CategorizeTransactions في application/useCases/
7. نفّذ المستودعات مرتين: infrastructure/firestore/ للتشغيل و infrastructure/memory/ للاختبار من CSV
8. اعمل شاشة العمليات مع البحث النصي والبحث بالمبلغ ±5%
9. نفّذ ImportBatch مع إمكانية التراجع الكامل

معيار القبول قبل ما تكمل: شغّل حالة الاستخدام على files/transactions_full.csv باستخدام مستودعات الذاكرة **من غير فايربيز خالص**، ولازم تطلع بالضبط:
المدين 302,171.45 — حركات ليست مصروفاً 201,703.18 — المصروف الحقيقي 100,468.27 — الدائن 300,040.87
لو الأرقام مش مطابقة، اوقف وحلّل سبب الفرق قبل ما تكمل.

اشتغل خطوة خطوة واسألني لو فيه أي غموض. اشرح لي بالعربي المصرية إيه اللي بتعمله وليه، وحط المصطلح الإنجليزي جنب أي مصطلح عربي.
```

---

## ملاحظات

- **كل شريحة تنتهي بـ commit.** لو حاجة بوظت، ترجع بأمر واحد
- **الجلسة الواحدة تخلّص شريحة واحدة.** متحاولش تعمل كل حاجة في جلسة
- **بعد كل شريحة:** حدّث سجل القرارات في `SPEC.md`
- **`files/counterparties.csv` فيه أرقام حسابات كاملة.** يفضل على جهازك ولا يرفع لفايربيز بهذا الشكل
