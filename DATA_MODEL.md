> ⚠️ **نسخة أقدم وأقل تفصيلاً.** نموذج البيانات المعتمد الآن: `design-source/masroofi-claude-code/spec/03-data-model.md` مع تعديلات `OVERRIDES.md`. هذا الملف يُقرأ للتاريخ فقط.

# مصروفي — نموذج البيانات (DATA MODEL)

> العمود الفقري للبرنامج. كل شاشة وكل حساب يتعلق بهذا النموذج.
> مشتق من ملفات البيانات الحقيقية في `files/` ومن `مواصفات-تصميم-مصروفي.md`.
> آخر تحديث: 2026-09-06

---

## قواعد عامة

- كل المبالغ **أعداد صحيحة بالهللة** (`Halalas = number`). 96.47 ر.س → 9647
- كل التواريخ نص بصيغة `YYYY-MM-DD`. التاريخ والوقت معاً بصيغة ISO 8601
- كل مستند له `id` نصي، و `createdAt` و `updatedAt`
- المسار الأساسي لكل شيء: `users/{uid}/...`

---

## خريطة الكيانات

```
                    ┌──────────┐
                    │ Account  │ بنكي أو كاش
                    └────┬─────┘
                         │
    ┌────────────────────▼────────────────────┐
    │            Transaction                  │  ← العمود الفقري
    │            العملية                       │
    └──┬──────┬──────┬──────┬──────┬──────────┘
       │      │      │      │      │
   ┌───▼──┐ ┌─▼────┐ ┌▼─────┐ ┌▼────────┐ ┌▼──────────┐
   │Merch.│ │Categ.│ │Import│ │Transfer │ │Counterpar.│
   │التاجر │ │التصنيف│ │Batch │ │التحويل  │ │الطرف      │
   └───▲──┘ └─▲──┬─┘ └──────┘ └─────────┘ └───────────┘
       │      │  │
    ┌──┴──┐   │  └────────┬──────────────┐
    │Rule │───┘      ┌────▼───┐    ┌─────▼──────┐
    │القاعدة│         │ Budget │    │ Commitment │
    └─────┘          │ السقف  │    │ الالتزام    │
                     └────────┘    └────────────┘

        ┌───────┐        ┌────────────┐
        │ Asset │────────│ AssetTrade │
        │ الأصل  │        │ عملية بيع/شراء│
        └───────┘        └────────────┘
```

---

## 1. Account — الحساب

`users/{uid}/accounts/{id}`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | معرّف |
| `name` | string | اسم يختاره المستخدم |
| `kind` | `'bank'` \| `'cash'` | **نوع الحساب** |
| `bank` | string \| null | البنك. `alrajhi` للبنكي، `null` للكاش |
| `last4` | string \| null | آخر أربعة أرقام فقط. **لا يُخزَّن رقم الحساب كاملاً**. `null` للكاش |
| `currency` | string | `SAR` |
| `openingBalance` | Halalas | الرصيد الافتتاحي — **إلزامي لمطابقة الرصيد** |
| `openingDate` | date | تاريخ الرصيد الافتتاحي |
| `active` | boolean | |

### حساب الكاش (Cash account)

حساب من نوع `cash` لتتبع الفلوس النقدية في الجيب. الفرق عن الحساب البنكي:

| | بنكي | كاش |
|---|---|---|
| مصدر العمليات | استيراد كشف | إدخال يدوي فقط |
| `statementBalance` | من الكشف | دائماً `null` |
| منع التكرار | ببصمة `dedupeKey` | لا ينطبق |
| المطابقة | مقارنة بالكشف بدقة الهللة | **جرد يدوي** (cash count) يسجّله المستخدم |

#### قاعدة إلزامية — منع الحساب المزدوج (double counting)

**سحب النقد من الصراف ليس مصروفاً. هو تحويل من الحساب البنكي إلى حساب الكاش.**

```
سحب 500 من الصراف   →  Transfer  (بنكي 500− ، كاش 500+)   ← ليس مصروفاً
شراء بـ 120 كاش      →  مصروف حقيقي على حساب الكاش
```

- كل عملية سحب نقدي في الكشف تُعلَّم `isTransfer = true` تلقائياً
- المصروف الحقيقي يُسجَّل عند الصرف من الكاش لا عند السحب
- لو المستخدم لم يسجّل صرف الكاش، يظهر الفرق في **رصيد الكاش غير المُفسَّر** ولا يُخمَّن ولا يُوزَّع على تصنيفات

#### تسوية الجرد (Cash count reconciliation)

`users/{uid}/cashCounts/{id}`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `accountId` | string | → حساب الكاش |
| `date` | date | تاريخ الجرد |
| `countedAmount` | Halalas | المبلغ المعدود فعلياً |
| `expectedAmount` | Halalas | الرصيد المحسوب من التطبيق |
| `difference` | Halalas | الفرق |
| `adjustmentTxnId` | string \| null | عملية التسوية إن قبلها المستخدم |

الفرق يُعرض ولا يُخفى. المستخدم يقرر: يسجّل عملية ناقصة، أو يقبل تسوية بفرق معلوم.

---

## 2. Transaction — العملية

`users/{uid}/transactions/{id}` — **الكيان الأهم**

مشتق من `transactions_full.csv`: التاريخ، مدين، دائن، الرصيد، التاجر، التصنيف، نوع العملية، التفاصيل

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `accountId` | string | → Account |
| `date` | date | التاريخ |
| `seq` | number | ترتيب العملية داخل اليوم — **ضروري للمطابقة** |
| `debit` | Halalas | مدين |
| `credit` | Halalas | دائن |
| `statementBalance` | Halalas \| null | الرصيد كما ورد في الكشف |
| `rawMerchant` | string | اسم التاجر كما ورد في الكشف |
| `merchantId` | string \| null | → Merchant بعد التوحيد |
| `categoryId` | string \| null | → Category |
| `categorySource` | `'rule'` \| `'merchant'` \| `'user'` \| `'none'` | مصدر التصنيف |
| `categoryStatus` | `'tentative'` \| `'confirmed'` | مبدئي أو مؤكد |
| `txnType` | string | نوع العملية من الكشف (نقاط بيع، تحويل، سحب…) |
| `details` | string | النص الكامل من الكشف |
| `note` | string | ملاحظة المستخدم |
| `originalCurrency` | string \| null | العملة الأصلية |
| `originalAmount` | Halalas \| null | المبلغ بالعملة الأصلية |
| `fxRate` | number \| null | سعر الصرف |
| `isTransfer` | boolean | حركة أموال لا مصروف |
| `isIncome` | boolean | دخل حقيقي |
| `isExcluded` | boolean | استُبعد يدوياً |
| `isReviewed` | boolean | روجعت |
| `counterpartyId` | string \| null | → Counterparty |
| `commitmentId` | string \| null | → Commitment |
| `importBatchId` | string \| null | → ImportBatch. `null` للإدخال اليدوي |
| `dedupeKey` | string \| null | بصمة منع التكرار. `null` لعمليات الكاش اليدوية |
| `entrySource` | `'import'` \| `'manual'` | مصدر الإدخال |

### قاعدة الاستبعاد
مصروف الفترة = العمليات المدينة **ناقص** `isTransfer` **ناقص** `isExcluded`.
الدخل = العمليات الدائنة **ناقص** التسويات **وناقص** الوارد بين حسابات المستخدم نفسه.

### بصمة منع التكرار (dedupeKey)
`accountId + date + debit + credit + statementBalance + hash(details)`

عمليتان متطابقتان في اليوم نفسه بالمبلغ نفسه **ليستا بالضرورة تكراراً**. النظام يعرضهما للمستخدم ولا يحذف تلقائياً.

---

## 3. Merchant — التاجر

`users/{uid}/merchants/{id}` — مشتق من `merchants_classified.csv` (319 تاجراً)

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `nameAr` | string | الاسم العربي |
| `nameEn` | string | الاسم اللاتيني |
| `aliases` | string[] | الأسماء البديلة وصيغ الفروع |
| `categoryId` | string \| null | التصنيف الافتراضي |
| `confidence` | `'confirmed'` \| `'needsReview'` | الثقة |
| `logoRef` | string \| null | مرجع الشعار داخل الحزمة |
| `domain` | string \| null | نطاق المتجر |

**قاعدة الشعارات:** لا يُخمَّن شعار من تشابه اسم، ولا يُعرض شعار مرسوم يقلّد شركة. البديل: أيقونة التصنيف أو اختصار الاسم.

---

## 4. Category — التصنيف

`users/{uid}/categories/{id}`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `parentId` | string \| null | `null` = رئيسي، وإلا فرعي. **مستويان فقط** |
| `name` | string | |
| `icon` | string | |
| `color` | string | hex |
| `isExpense` | boolean | تعريف المصروف |
| `active` | boolean | |
| `archived` | boolean | |
| `sortOrder` | number | |

- الفرع يرث درجة أفتح من لون الأصل ويمكن تخصيصها
- `#BA7517` و `#D85A30` **محجوزان للتنبيه** ولا يُستخدمان كألوان تصنيفات
- الأصل الذي له فروع يستقبل عملياته في فرع اسمه **«غير محدد»**
- أي دمج أو تحويل أو أرشفة يعرض عدد العمليات المتأثرة ويتيح التراجع

---

## 5. Rule — القاعدة

`users/{uid}/rules/{id}` — مشتق من `rules_categories.csv` (228 قاعدة)

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `keyword` | string | الكلمة المفتاحية |
| `categoryId` | string | التصنيف الناتج |
| `priority` | number | الترتيب. الأعلى يفوز |
| `active` | boolean | |

### ترتيب التصنيف (الأولوية من الأعلى للأسفل)

1. **تصنيف مؤكد من المستخدم** — لا يُستبدل آلياً أبداً
2. **خريطة التاجر الصريحة** (Merchant.categoryId)
3. **قواعد الكلمات مرتبة بالأولوية** — أول تطابق يفوز
4. **بلا تصنيف** → يدخل قائمة المراجعة

---

## 6. Budget — السقف

`users/{uid}/budgets/{id}`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `categoryId` | string \| null | `null` = سقف الفترة الكلي |
| `periodKey` | string | `YYYY-MM` أو `default` |
| `limit` | Halalas | سقف المستخدم |
| `source` | `'user'` \| `'suggested'` | |

- **لا يضع التطبيق سقفاً تلقائياً**
- المتوسط يُحسب من **ثلاث فترات مالية مكتملة فقط**، أو المتاح مع ذكر عدده. بلا تاريخ فلا متوسط
- المتوسط يُعرض كعلامة على الشريط، منفصل عن السقف. خارج المدى يثبت عند الطرف وتبقى التسمية
- مجموع سقوف التصنيفات يُقارن بسقف الفترة. الزيادة **تنبيه لا منع**
- تصنيف بلا سقف لا يتلقى إشعارات عتبة

---

## 7. Commitment — الالتزام والاشتراك

`users/{uid}/commitments/{id}` — مشتق من `subscriptions.csv` و `recurring_savings.csv` و `variable_bills.csv`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `name` | string | الخدمة |
| `kind` | `'subscription'` \| `'installment'` \| `'saving'` \| `'variableBill'` | |
| `categoryId` | string | |
| `merchantId` | string \| null | |
| `cycleAmount` | Halalas | مبلغ الدورة — **حقل مستقل** |
| `medianAmount` | Halalas \| null | للفواتير المتغيرة |
| `minAmount` / `maxAmount` | Halalas \| null | |
| `chargeCount` | number | عدد الخصومات |
| `monthCount` | number | عدد الشهور |
| `firstCharge` / `lastCharge` | date | |
| `dayStability` | number | ثبات يوم الخصم |
| `paidTotal` | Halalas | الإجمالي المدفوع تاريخياً — **حقل مستقل** |
| `paidLast12Months` | Halalas | المدفوع خلال 12 شهراً — **حقل مستقل ثالث** |
| `estimatedAnnual` | Halalas | المتوقع للسنة القادمة على السعر الحالي |
| `active` | boolean | |

**قاعدة صارمة:** `cycleAmount` و `estimatedAnnual` و `paidLast12Months` و `paidTotal` **أربعة حقول مختلفة لا يحل أحدها محل الآخر.** لا يُعرض إجمالي تاريخ غير محدد مكان المدفوع خلال السنة.

مثال Google One: `cycleAmount` = 8599 هللة، `estimatedAnnual` = 103188 هللة على السعر الحالي قبل الرسوم.

---

## 8. Transfer — التحويل

`users/{uid}/transfers/{id}`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `outTxnId` | string | العملية الصادرة |
| `inTxnId` | string \| null | العملية الواردة إن وُجدت |
| `counterpartyId` | string \| null | |
| `isInternal` | boolean | بين حسابات المستخدم نفسه |

التحويلات **لا تُحتسب مصروفاً ولا دخلاً**. لا يُجمع شراء السلعة مع دفعاتها مرتين.

---

## 9. Counterparty — الطرف

`users/{uid}/counterparties/{id}` — مشتق من `counterparties.csv`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `accountLast4` | string | **آخر أربعة أرقام فقط** |
| `nameFromStatement` | string | الاسم كما ورد |
| `displayName` | string | اسم يختاره المستخدم |
| `kind` | `'income'` \| `'expense'` \| `'settlement'` \| `'self'` | |

---

## 10. Asset — الأصل الاستثماري

`users/{uid}/assets/{id}`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `kind` | `'gold'` \| `'stock'` \| `'fund'` \| `'crypto'` \| `'manual'` | |
| `name` | string | |
| `symbol` | string \| null | مثل `2222.SR` أو `BTC` |
| `quantity` | number | الكمية |
| `unit` | string \| null | جرام، سهم، وحدة |
| `purity` | string \| null | العيار للذهب |
| `costBasis` | Halalas | التكلفة |
| `costDate` | date | |
| `manualPrice` | Halalas \| null | سعر مدخل يدوياً |
| `priceSource` | `'auto'` \| `'manual'` | |

`users/{uid}/assetTrades/{id}` — عمليات البيع الجزئي والكامل. **مسك سجل لا تداول.**

**حالات إلزامية:** ربح، خسارة، سعر قديم مع توقيته، نقص بيانات، بيع جزئي.
الذهب بلا وزن أو عيار يظل **اقتراحاً ناقصاً** ولا يُحسب.

---

## 11. ImportBatch — دفعة الاستيراد

`users/{uid}/importBatches/{id}`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | |
| `accountId` | string | |
| `fileName` | string | |
| `importedAt` | datetime | |
| `rowCount` | number | صفوف الملف |
| `insertedCount` | number | أُدخلت |
| `duplicateCount` | number | مكررة |
| `ambiguousCount` | number | غامضة |
| `status` | `'applied'` \| `'reverted'` | |

**كل دفعة قابلة للتراجع الكامل.** الاستيراد يعرض المكرر والغامض ولا يخفي اختلاف الرصيد.

---

## 12. PeriodSummary — ملخص الفترة

`users/{uid}/periodSummaries/{YYYY-MM}` — **مستند محسوب مسبقاً لضبط تكلفة القراءة**

| الحقل | النوع |
|---|---|
| `periodKey` | string |
| `expenseTotal` / `incomeTotal` / `net` | Halalas |
| `savingsRate` | number \| null |
| `coverage` | number \| null |
| `projectedClose` | Halalas \| null |
| `categoryTotals` | { categoryId: Halalas }[] |
| `computedAt` | datetime |

يُعاد حسابه عند أي تغيير يمس الفترة. الشاشة الرئيسية تقرأ **مستنداً واحداً** لا آلاف العمليات.

---

## 13. NotificationState — حالة الإشعار

`users/{uid}/notificationState/{id}`

| الحقل | النوع | الوصف |
|---|---|---|
| `id` | string | `periodKey + categoryId + threshold` |
| `firedAt` | datetime \| null | |
| `armed` | boolean | |

**قواعد آلة الحالات:** مرة واحدة لكل عتبة وتصنيف وفترة. رفع السقف يعيد التسليح. خفضه ينتظر العملية التالية. هدوء 23:00–08:00. حد ثلاثة إشعارات يومياً. لا عتبات في أول ثلاثة أيام من الفترة.

---

## 14. Settings — الإعدادات

`users/{uid}/settings/app` — مستند واحد

| الحقل | النوع | الوصف |
|---|---|---|
| `fiscalMonthStartDay` | number | **يوم بداية الشهر المالي = 28** (يوم نزول الراتب). الفترة تمتد من 28 إلى 27 من الشهر التالي |
| `currency` | string | `SAR` |
| `notifications` | object | العتبات العامة وساعات الهدوء |
| `theme` | `'light'` \| `'dark'` \| `'system'` | |
| `lastBackupAt` | datetime | |

---

## معادلات محفوظة في النموذج

| المقدار | المعادلة | الحالة الاستثنائية |
|---|---|---|
| الصافي | الدخل − المصروف | ليس رصيداً |
| معدل الادخار | الصافي ÷ الدخل | دخل صفر → **غير متاح**، لا قسمة على صفر |
| التوقع | مصروف حتى اليوم ÷ أيام مضت × أيام الفترة | يظهر بعد **سبعة أيام فقط** |
| التغطية | مصروف الفترة ÷ (مصروف الفترة + المتاح) | البسط والمقام **للفترة نفسها** |
| الرصيد الجاري | السابق + الدائن − المدين | يُقارن بالكشف بعد كل عملية بدقة الهللة |
| نسب التصنيفات | من مصروف الفترة بعد الاستبعادات | أكبر خمس شرائح والباقي «غيرها»، المجموع 100% |

- فرق النسبة عن الفترة السابقة **بالنقاط المئوية** لا بالنسبة
- أول خمسة أيام من الفترة تحمل تنويه «الفترة في أولها»
- صفر مصروف له حالة فارغة صريحة

---

## ملاحظة على الشهر المالي

`fiscalMonthStartDay = 28`. الفترة المالية تمتد من 28 إلى 27 من الشهر التالي.

- اليوم 28 آمن: موجود في كل الشهور بما فيها فبراير
- **هذا لا يغيّر أرقام معيار القبول** في `SPEC.md` — تلك مجاميع كل الفترة لا مجاميع شهر. تغيّر حدود الفترات لا يغيّر الإجمالي
- تسمية الفترة: الفترة التي تبدأ 28 يناير تُسمّى بشهر فبراير (الشهر الذي تقع فيه أغلبها)

---

## ما ينقص لبدء التنفيذ

1. ✅ **الحساب البنكي (الراجحي):** رصيد افتتاحي **4,837.83 ر.س بتاريخ 2025-01-01**
   مشتق من الكشف: أول عملية مدين 96.47 والرصيد بعدها 4,741.36
2. ✅ **حساب الكاش:** رصيد افتتاحي **1,000.00 ر.س بتاريخ 2026-09-06**
3. تأكيد أن `parse_alrajhi.py` هو المحلل المعتمد لصيغة الكشف
