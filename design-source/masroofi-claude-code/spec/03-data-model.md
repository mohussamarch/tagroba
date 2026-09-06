# 03 — نموذج بيانات مقترح

هذا نموذج تنفيذي مقترح يحتاج هجرات واختبارات، وليس قاعدة بيانات جاهزة. احتفظ بمعرفات مستقرة وسجل مصدر للتغييرات. الجداول المحلية المقترحة:

| الجدول | الحقول الأساسية والغرض |
|---|---|
| Wallet | id، name، currency، kind(bank/cash/own_abroad)، openingBalanceMinor، openingAt |
| Merchant | id، displayName، normalizedName، logoAsset، logoSource، verifiedCategoryId |
| MerchantAlias | merchantId، normalizedAlias؛ الربط المؤكد لا يُستنتج من تشابه مبهم |
| Category | id، parentId(nullable)، name، iconKey، lightColor، darkColor، active، order |
| ClassificationRule | id، priority، matchText، matchMode، categoryId، enabled |
| Transaction | id، occurredAt/datePrecision، economicKind، merchantId، categoryId، note، excludedFromBudget، reviewState، isCashTagged، createdAt، updatedAt |
| WalletPosting | id، transactionId، walletId، signedAmountMinor، currency، bookingAt؛ التحويل الداخلي له طرفان |
| FXDetail | transactionId، sourceAmount/currency، targetAmount/currency، decimalRate، rateAt، feeTransactionId؛ الناقص nullable وليس صفرًا |
| Tag | id، normalizedName، displayName |
| TransactionTag | transactionId، tagId؛ مفتاح مركب فريد |
| Person | id، name، archived؛ لا حذف للحساب ذي سجل |
| PersonAllocation | id، transactionId، personId، allocationKind(receivable/gift)، amountMinor، currency |
| Obligation | id، personId، originTransactionId، kind(receivable/loan_payable/custody_payable)، originalMinor، currency؛ المتبقي مشتق من التسويات |
| Settlement | id، transactionId، obligationId، amountMinor؛ مجموعها لا يتجاوز الحركة أو الرصيد |
| ImportBatch | id، sourceType، fileHash، importedAt، state(staged/committed/reverted)، counts |
| SourceRecord | id، batchId، accountIdentity، sourceReference(nullable)، sourceHash، originalRowIndex، normalizedPayload، transactionId(nullable)، matchingState |
| Budget | id، periodStart، periodEnd، totalLimitMinor؛ حدود صريحة |
| CategoryBudget | budgetId، categoryId، limitMinor، notifyEnabled، thresholdPercent |
| RecurringItem | id، merchant/service، kind(subscription/bill/installment)، confirmed، cycle، expectedMinor، nextDueAt، active |
| Asset / AssetEvent | نوع الأصل، الرمز، كمية عشرية، عيار الذهب، التكلفة والرسوم، شراء/بيع، الحصيلة، التاريخ |
| PriceQuote | assetId، decimalPrice، currency، fetchedAt، provider، staleState |
| NotificationReceipt | eventKey، threshold، category/recurringId، periodStart، sentAt؛ لمنع تكرار التنبيه |
| AuditEntry | entity، action، before/after أو عكس محاسبي، timestamp، batchId |

## القيود
- المبالغ الموجبة في أصل الشراء أو الالتزام؛ اتجاه السيولة في WalletPosting. لا تخزن إشارة مكررة متعارضة في مكانين.
- مجموع التخصيصات للشخص لا يتجاوز قيمة الشراء. العملة متوافقة، ولا يُسدد التزام بعملة أخرى دون تحويل موثق.
- تغيير العملية وتخصيص الشخص وتسويته وتحديث حالة الاستيراد عملية ذرية واحدة.
- category.parentId لا يكون نفسه أو من نسله؛ لا دورات. إلغاء التصنيف لا يمس السجل التاريخي.
- اقتراح فرع جديد للأصل الذي يحتوي عمليات مباشرة: أضف فرع «غير محدد» وانقل إليه المباشر بعد بيان الأثر.
- المرجع البنكي الفريد مقيد بالمصدر والحساب؛ بعض الجهات قد تعيد استخدام مرجع، لذلك يُحدد نطاقه بحسب المصدر. تعارض التفاصيل لا يُبتلع بـ upsert.
- عدة SourceRecords يمكن أن تشير إلى Transaction واحدة (رسالة + كشف)، فلا يفقد إثبات المصدر.
- batchId ليس ملكية مطلقة للعملية: عند التراجع عن دفعة لا تحذف عملية تؤيدها مصادر أخرى أو دخلت عليها تسوية لاحقة. اعرض الأثر وطبّق عكسًا متسقًا.
- فهرسة التاريخ والمصدر والتاجر والتصنيف والشخص والوسم؛ البحث لا يعتمد على تحميل كل السجل إلى الواجهة.

## الحفظ والاستعادة
استخدم هجرات غير مدمرة واختبار ترقية قاعدة قديمة. النسخة الاحتياطية لها schemaVersion وتحقق سلامة، وتحفظ المعرفات وعلاقات المصادر والتسويات. الاستعادة لا تكرر العمليات أو تخلط العملات. اختبر إغلاق التطبيق قسريًا أثناء الاستيراد، ثم استئنافه دون نصف دفعة.

لا تنسخ قاعدة بيانات حقيقية إلى APK. ابدأ بقاعدة فارغة؛ العينة تستورد في وضع عرض منفصل بإجراء واضح. إعدادات المظهر والخصوصية وبداية الفترة قابلة للحفظ محليًا.
