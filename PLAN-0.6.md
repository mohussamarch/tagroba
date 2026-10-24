# خطة 0.6 — اتعملت 2026-10-17 · التنفيذ يبدأ الخميس الجاي بعد تجديد الحد الأسبوعي

> قرار المالك 2026-10-17: «متبدأش اي شغل الان لان قربنا نخلص الليمت الاسبوعي». اللي اختاره «دلوقتي» في صفحة المراجعة = **الجولة الأولى**. اللي اختاره «بعدين» = **الجولة التانية بعد الأولى**. ملاحظاته على الكروت = متطلبات. المصدر: صفحة «مراجعة مصروفي» (claude.ai/artifact/5AbiqgGKYcyp3SkPqDFyZr، مجموعة choices) — كل الكروت اتجاوبت.
> قبل التنفيذ: استنى تغييرات التصميم اللي المالك هيعملها في جلسة «نظام التصميم v0.4» واجمعها هنا (القسم ٤).

## ١. الجولة الأولى (اختيار «دلوقتي»)

### الأمان (14)
- **AUTH-1** (medium · medium) أي حد يقدر يعمل حسابات على مشروعنا ويخلّص الحصة المجانية
  - **الحالة (r06-security 2026-10-24): جزئي** — الكود جاهز و**مقفول** (`-Pmasroufy.appCheck=true`) + خطوات اللوحة في `tools/security/APP-CHECK-STEPS.md`. مستني المالك: اللوحة + موافقة تنزيل مكتبتين. الطريق المفتوح ما اتترجمش.
  - Merged AUTH-1 + SECRETS-3. FIREBASE.md:18-19 (Email/Password enabled), FIREBASE.md:45-53 (full web firebaseConfig incl. apiKey in public repo mohussamarch/tagroba); FirebaseAuthAdapter.kt:50-53 createUserWithEmailAndPassword; androidApp/build.gradle.kts:137-160 ships google-services in fast APK; firestore.rules:12-19 sharedMerchants read for any auth, create/update while confirmed==false; no AppCheck/PlayIntegrity in native-app (OVERRIDES.md:1805 still open). Scenario: identitytoolkit signUp with key -> ID token -> loop Firestore REST listDocuments on sharedMerchants until Spark 50k reads/day exhausted -> RESOURCE_EXHAUSTED for all users; same token rewrites unconfirmed entries. Fix: enable 
- **PRIVACY-4** (low · small) نسخ الإصلاح الاحتياطية بتتحفظ مكشوفة وما بتتمسحش
  - **الحالة (r06-security 2026-10-24): اتعمل** — `noBackupFilesDir/backups` · آخر ملفين · المجلد الخارجي القديم بيتمسح مرة واحدة · التصدير بـ«احفظ في» بس.
  - ملاحظة المالك: «النسخ الاحطياطي اخره ملفين بس وكل ما يعمل نسخ يمسح الزيادة»
  - Merged PRIVACY-4 + ANDROID-1. device/src/androidMain/kotlin/app/masroufy/device/AndroidLocalFiles.kt:23-41 writes plaintext JSON to getExternalFilesDir(null)/backups (KDoc 16-20 says USB-visible on purpose); usecase/VerifiedBackup.kt:17-21; callers MergeDuplicates.kt:99,133, EmployerRetroApply.kt:97,108, RepairOldSms.kt:106; wired for all flavors at androidApp AppContainer.kt:176; minSdk 24 (androidApp/build.gradle.kts:25) so API 24-28 READ_EXTERNAL_STORAGE apps can read it; no cleanup anywhere. Fix: write to context.filesDir/noBackupFilesDir, keep last N, delete after undo window; user-facing export only via CreateDocument.
- **ANDROID-3** (low · small) قفل البرنامج ما بيخبيش الأرصدة من شاشة التطبيقات الأخيرة
  - **الحالة (r06-security 2026-10-24): اتعمل** — القفل متشغّل ⇒ FLAG_SECURE + `setRecentsScreenshotEnabled(false)` (33+) · المحتوى ورا القفل من غير semantics. ما اتجربش على جهاز.
  - Merged AUTH-3 + ANDROID-3. No FLAG_SECURE / setRecentsScreenshotEnabled anywhere in native-app. MainActivity.kt:57-66: onStop only calls lock.onHidden(); LockGate.kt:34-36 just records hiddenAt; locked set only in onVisible (LockGate.kt:38-45) after 5-min check (Notifications.kt:101 LOCK_AFTER_BACKGROUND_MS), so the Recents snapshot is the unlocked UI. MasroufyApp.kt:82-90 keeps AppShell composed under LockScreen; LockScreen.kt:70 only blocks taps (pointerInput detectTapGestures), semantics not cleared -> TalkBack can read/act on ledger nodes. Fix: when lock enabled set WindowManager.LayoutParams.FLAG_SECURE (or setRecentsScreenshotEnabled(false) on API 33+); while locked don't compose AppSh
- **PRIVACY-1** (low · medium) بصمة النص الأصلي جنب النسخة المتقصوصة بتسمح بتخمين رقم الحساب
  - **الحالة (r06-security 2026-10-24): اتأجل** — البصمة بتستعملها 12 ملف (منع التكرار · المصادر الممسوحة · مقارنة المصادر · مراجع الرسايل). التغيير الآمن محتاج مطابقة البصمتين القديمة والجديدة في كلهم + اختبارات — أكبر من الجولة، وأي غلطة بتغيّر منع التكرار.
  - core/Dedupe.kt:154-167 hashContent = unsalted FNV-1a 64-bit over UTF-16. ImportStatement.kt:69-72 sourceHash = hashContent(line.row.raw) stored beside rawLine (masked only at write by DocCodec.kt:29 storeForm); same in SmsFeeTwins.kt:51, SmsFeeEffect.kt:118-120, MergeUndo.kt:40-42; BankSmsModel.kt:235 reference = "SMS:" + hashContent(sender|receivedAt|body) with raw body from AndroidBankSms.kt:57 / ReadBankSms.kt:62. Attacker with backup/export or admin access brute-forces masked digits (10^6-10^11) offline. Attacker model needs data access already, hence low. Fix: hash the redacted text (redactSms / SmsSafety.sanitize output), or HMAC-SHA256 with per-user key not stored with the data; migra
- **PRIVACY-3** (low · small) استرجاع النسخة الاحتياطية بيعدّي أرقام كاملة من غير تخبية
  - **الحالة (r06-security 2026-10-24): اتعمل** — `data/RestoreForm.kt`: المجموعات المحمية بتتكتب بشكل المحوّل (مفاتيحه بس) + `storeForm` + `redactSms` · حد طول النص (20,000) والتداخل (6) في `checkFullBackupData`.
  - firestore/FirestoreFullBackup.kt:64-66 addMissing writes protectBankText(group,row) via set(); :94 BANK_TEXT_FIELDS only accountIdentity/rawLine/rawDescription/rawMerchantName (+ title/body for alert inbox, :101-108); normal path data/StoreRules.kt:22-25 storeForm sanitizes every top-level string in transactions/sourceRecords/importBatches. core/BackupCheck.kt:138-157 no unknown-key or length/depth check; usecase/FullBackup.kt:204 profile restored as-is; checksum is unkeyed. Fix: apply storeForm(group,row) (or redactSms) to all strings in protectBankText; allowlist keys per group; cap string length and nesting depth.
- **AUTH-4** (low · medium) تسجيل الخروج ما بيمسحش البيانات ولا بيوقف قراءة رسايل البنك
  - **الحالة (r06-security 2026-10-24): اتعمل** — الخروج: قراية الرسايل بتقف ورسايل الحساب بتتمسح · نسخ الإصلاح · المسودات · Firestore (terminate + clearPersistence ثم نسخة جديدة) · سطر في لوحة الخروج باللهجتين. ما اتجربش على جهاز.
  - Sign-out: AccountSheets.kt:88 -> SignIn.kt:58 -> FirebaseAuthAdapter.kt:71 auth.signOut() only. AccountSession.kt:133-142 switchTo(null) only stops listeners. clearPersistence never called outside firebase-trial. SmsInboxStore.kt:42,49-56 keeps enabled=true/owner=uid; disable(uid) never called on sign-out, BankSmsReceiver.kt:37 keeps storing bodies. Backups in AndroidLocalFiles.kt:39-41 remain. Fix: on user sign-out terminate()+clearPersistence(), SmsInboxStore.disable(uid) (or wipe rows), delete backups dir and per-uid drafts; optionally ask in the sign-out sheet.
- **SECRETS-1** (low · medium) نسخة الموبايل بتتوقّع بمفتاح التجربة بتاع الكمبيوتر
  - **الحالة (r06-security 2026-10-24): اتأجل بقرار المالك** (OVERRIDES §118-2): المفتاح بيتغير مرة واحدة قبل النشر على المتجر.
  - Merged AUTH-5 + SECRETS-1 + ANDROID-4 + SUPPLY-1 (SUPPLY-1 disputed: debug keystore is per-machine, not literally shared). native-app/androidApp/build.gradle.kts:36-37 comment (debug key SHA-1 registered for Google sign-in), :52-58 fast buildType initWith(release) + signingConfig = signingConfigs.getByName("debug") (~/.android/debug.keystore, pw android/androiddebugkey), :44-46 release has no signing config; HANDOVER.md:17 publishes SHA-1 670c193e... Scenario: keystore copied from build PC -> attacker signs app.masroufy.mobile, installs over via adb -r / Drive zip, inherits refresh token + sms-inbox.db + SMS grants, passes SHA-1 key restriction. Loss risk: regenerated keystore breaks upgrade
- **SECRETS-2** (low · small) قايمة الملفات الممنوعة من الرفع ناقصة كام حاجة
  - **الحالة (r06-security 2026-10-24): اتعمل** — `/data/` (مربوط بالجذر) · `.env*` (غير `.env.example`) · `**/google-services.json` · `**/GoogleService-Info.plist` · `*.p12 *.pfx *.jks *.keystore` · `native-app/*.log`. مفيش ملف متتبّع اتخبى.
  - git check-ignore: not ignored -> native-app/androidApp/google-services.json, android/app/google-services.json (android/.gitignore:65 commented out), .env, .env.production (root .gitignore:8-9 only .env.local/.env.*.local), native-app/build*.log (11 untracked logs, contain secrets path), *.p12. data/ ignored only via E:/work/masroufy/.git/info/exclude (local, untracked). Nothing sensitive committed today. Fix: tracked root .gitignore add data/, .env and .env.* with !.env.example, **/google-services.json, **/GoogleService-Info.plist, *.p12, *.pfx, *.jks, *.keystore, native-app/*.log.
- **SECRETS-5** (low · small) أداة فحص الأسرار قبل الرفع ما بتشوفش الملفات المضغوطة وبتصرخ على حاجات وهمية
  - **الحالة (r06-security 2026-10-24): اتعمل** — `root..HEAD` نضيف (الـzip القديم في أول commit فمش «مضاف» في المدى ده).
  - scripts/secretscan.mjs:8 git diff -U0 text only (binary -> 'Binary files differ', 188 such in history incl. tracked masroofi-claude-code.zip); :28 file names only reported; :31 only '+' lines; :17 password regex needs ':'/'=' and >=8 chars, misses Groovy storePassword "android" (android/app/build.gradle:23,25); :23 ALLOW only FirebaseSetup.kt. Full root..HEAD run: 7 false positives (Emulator.kt:30,49, scripts/golden/documentsGolden.ts:83, 4 tests/integration files), exit 1. Fix: git diff --name-status fail on google-services.json, .env*, *.jks, *.keystore, *.p12, *.zip, *.apk; fail on added non-image/font binaries; ALLOW known-fake test files for the hardcoded-password pattern only.
- **LOGS-2** (low · small) تقارير الأعطال ممكن تتبعت باسم حساب خرج
  - **الحالة (r06-security 2026-10-24): اتعمل** — الخروج بيمسح رقم الحساب دايمًا، وقفل التقارير كمان.
  - native-app/androidApp/src/main/kotlin/app/masroufy/android/CrashReports.kt:53-58 session(): `if (!isOn()) return` before c.setUserId(uid.orEmpty()); sign-out via AppContainer.kt:95 problems.session(null,null) -> ProblemLogSetup.kt:46; apply() (41-50) on setOn only toggles collection and app_version. Fix: when uid == null always setUserId("") regardless of isOn(); clear user id in apply() when switching off.
- **PRIVACY-7** (low · small) اختيار ملف كبير للاسترجاع ممكن يهنّج البرنامج
  - **الحالة (r06-security 2026-10-24): اتعمل قبل كده** (`8b00ce03`): JSON بس · 50 م.ب · القراية على IO. اتراجع ومفيش تغيير.
  - ملاحظة المالك: «مش المفروض يكون في تحديد لنوعية فقط للملفات اللي يمكن رفعها او قراءتها ؟ ليه اصلا حاجة بصيغة mp4 ممكن البرنامج يقبل انه يشوفها ؟»
  - ui/src/androidMain/kotlin/app/masroufy/ui/screens/more/BackupFiles.android.kt:46-58 OpenDocument callback on main thread: openInputStream(uri).use { it.readBytes().decodeToString() } with no size check (OpenableColumns.SIZE not queried though DISPLAY_NAME is); :60 picker accepts */*; reached from BackupScreen.kt:87-89. Fix: reject > ~50 MB via OpenableColumns.SIZE or bounded streaming; read on Dispatchers.IO.
- **RULES-4** (low · small) خانة التصنيف في التجار المشتركين بتقبل أي كلام
  - **الحالة (r06-security 2026-10-24): مسودة — ما اتنشرتش.** التصنيف `cat-…` بحروف لاتيني **وعربي** (معرّفات الشجرة الجاهزة فيها عربي — النمط اللي في البند كان هيرفضها) · مفيش 4 أرقام ورا بعض. 14/14 على المحاكي. النشر بموافقة المالك.
  - firestore.rules:41 categoryId any string <= 120; :23 aliases any 1-80 char string incl. digits; 4+ digit block only client-side. Disputed: free text already possible via displayName (:39) and normalizedName/key (:37-38). Fix: categoryId == null || categoryId.matches('^cat-[a-z0-9-]{1,60}$'); !normalizedName.matches('.*[0-9]{4,}.*') and same for each alias/displayName.
- **PRIVACY-2** (low · small) استيراد كشف الراجحي والجداول ما بيخبيش الأرقام المقسّمة بمسافات
  - **الحالة (r06-security 2026-10-24): جزئي** — `storeForm` بقى شايف الأرقام المقسّمة بمسافات وشرط (12 رقم أو أكتر). `redactSms` وقت قراية الراجحي/الجداول **اتشال** لأنه كسر تحديد أطراف التحويل في كشف الراجحي الحقيقي (418 ⇒ 394) — النص بيتحمي وقت الكتابة.
  - core/AlrajhiPdf.kt:168-174 description = tidy(description,400) without redactSms (QnbPdf.kt:113 does call it); core/ImportSchemas.kt:87-91, :112-119 CSV description/raw as-is; data/StoreRules.kt:11-24 LONG_DIGITS = [0-9]{5,} only, so '1234 5678 ...' / '1234-5678-...' pass via FirestoreTransactionRepository.kt:70. Disputed: Rajhi raw is synthesized 'صفحة N · date' (AlrajhiPdf.kt:160), and real-world grouped numbers in descriptions unproven. Fix: redactSms on Rajhi/CSV description+raw; make storeForm use space/dash-aware redactSms.
- **SECRETS-6** (low · small) خطوة تحديث الأسعار التلقائية عندها صلاحية كتابة واسعة
  - **الحالة (r06-security 2026-10-24): اتعمل (ما اترفعش)** — الإضافات على commit كامل · `persist-credentials: false` · الصلاحية على المهمة · التوكن لخطوة الرفع بس.
  - .github/workflows/prices.yml:23-24 workflow-level permissions contents: write; :34 actions/checkout@v4, :36 actions/setup-node@v4 by mutable tag; checkout persist-credentials default true. App trusts raw.githubusercontent.com/mohussamarch/tagroba/main/public/prices.json and averages.json (LoadOnlineFeeds.kt:31) with shape-only validation. Disputed: single job so moving permissions changes nothing; upstream tag compromise is remote. Fix: pin actions to full commit SHAs; persist-credentials: false and pass token only to push step.

### الحسابات والفلوس (9)
- **money-1** (high · small) التراجع عن الاستيراد ناقصه خطوات الرجوع
  - wiring/src/commonMain/kotlin/app/masroufy/wiring/ImportsGraph.kt:50-58 builds RevertDeps without `undoers` (defaults to emptyList at app/.../usecase/RevertImportBatch.kt:107) and without RevertLinkDeps.spaceLegs. So RevertImportBatch.execute (line 206) never runs MergeUndo (MergeUndo.kt:71), LinkedUndo (LinkInflows.kt:171), ReversalUndo, or SubscriptionChargeUndo. ReturnsWiring.undoers (ReturnsWiring.kt:24) exists but is not referenced in wiring/ or androidApp/. Scenarios: (a) statement line merged into an SMS txn via recordMerges (MergeUndo.kt:49-57 overwrites occurredAt/sourceOrder/statedBalance) -> revert keeps the txn (KEPT_OTHER_SOURCE) but never restores those fields; (b) revert delete
- **money-2** (high · small) التحويلات الغامضة بتتحسب مصروف عكس قرارك
  - ملاحظة المالك: «بشكل عام اي تحويلات المفروض متتحسبش مشتريات غير لما صاحب الحساب يقول كدة ..غير كدة اي تحوي لازم يتراجع الاول او صاحب الحساب يحطها قاعدة مع حسااب معين»
  - core/src/commonMain/kotlin/app/masroufy/core/EstimatedKinds.kt:87-88: `t.copy(economicKind = suggestion.kind ?: fallback)` with fallback PURCHASE for OUT. For ambiguous suggestions kind is null (SuggestEconomicKind.kt:44-45) and `alternatives` is ignored, so "تحويلات" (alts INTERNAL_TRANSFER first, line 80-84), "محافظ رقمية" (74-78), BARQ (87-91) and "سداد" (DEBT_REPAID first, 67-72) all become PURCHASE => counted by computePeriodTotals as personal expense. OVERRIDES.md:294 (§18 owner amendment) says: high confidence => its kind; otherwise => suggested kind OR FIRST ALTERNATIVE; only if none => OUT expense. Inherited from src/domain/estimatedKinds.ts:51, so the old app had the same deviation
- **money-3** (medium · medium) أول «حدّث الرصيد» لبنك من غير رصيد بداية بيطلع فرق وهمي
  - UpdateBalance.preview/record (app/.../usecase/UpdateBalance.kt:64-81) call balanceAtCheckpoint which starts from wallet.openingBalanceMinor (a placeholder 0 when Wallet.openingUnknown, TransactionViews.kt:33). recordedDiffMinor = stated - computed => the whole balance; CheckpointEffect.needsReview (BalanceCheckpoint.kt:52) is true, so openDifferences (UpdateBalance.kt:93-95) lists it, explainWalletBalance adds it as an ADJUSTMENT line (BalanceWhy.kt:159-162). UI shows the fake computed value: ui/.../more/BalanceUpdateSheet.kt:124 and FixMyBalanceScreen via FixMyBalance.preview (FixMyBalance.kt:69,82 beforeMinor). Owner decision P4 (OVERRIDES.md:2151) says the balance is unknown until the fir
- **money-4** (medium · small) دمج رسالة وكشف باتجاه عكسي بيسيب نوع العملية غلط
  - app/.../usecase/MergeDuplicates.kt:112-115 copies observedDirection from the statement when `opposite`, but keeps keep.economicKind/economicKindConfirmed (keptAfter only pulls the drop's kind when keep is unconfirmed). The SMS is normally the kept one (created first). RepairOldSms.applyAll (RepairOldSms.kt:116-118) handles the same flip correctly: INTERNAL_TRANSFER for own-transfer SMS, otherwise economicKindConfirmed only if direction unchanged. computePeriodTotals/countsAsIncome ignore direction (Ledger.kt:59, EconomicKind.kt:144), so a confirmed IN kind (e.g. SALARY set by SmsSalaryEffect, SUPPORT_RECEIVED from transfer parties) stays income after becoming OUT. Fix: when the direction cha
- **money-5** (medium · small) الاسترداد بينقّص المصروف الكلي بس مش التصنيفات
  - computePeriodTotals subtracts REFUND_RECEIVED from expense (core/.../Ledger.kt:64-66), but categoryDistribution (core/.../Analytics.kt:18-31) only iterates countsAsPersonalSpend rows, so refunds never reduce a slice. Consumers: HomeScreenData.distribution -> rankSpend (TopSpend.kt) / LoadTopCategories, and LoadBudgetScreen spendByCategory (LoadBudgetScreen.kt:167-171) + per-category history. Result: sum(slices) != expenseMinor whenever a refund exists. Fix: in categoryDistribution subtract non-excluded refund rows from their categoryId slice (floor at 0 or keep negative consistently with totals), and add a test asserting sum(slices.amountMinor) == totals.personalExpenseMinor.
- **money-6** (medium · medium) أرقام القبول المرجعية بتتفحص بطريق غير طريق الشاشات
  - core/src/jvmTest/.../RealStatementTest.kt:23-31 checks 302,171.45 / 201,703.18 / 100,468.27 / 300,040.87 via computeExpenseBreakdown (TransactionViews.kt:178), which classifies by sourceCategory and is not used by any screen (only KeepRegressionTest). data/src/jvmTest/.../RealDataFlowTest.kt:115-133 runs LoadHomeScreen/LoadBudgetScreen per period but only asserts that 1,912 rows are shown, and line 137-138 checks the closing balance. Nothing asserts that the economic-kind path (import -> withEstimatedKinds -> computePeriodTotals) reproduces the reference totals, so money-2 is invisible to CI and to the owner-machine test. Fix: sum HomeScreenData.expenseMinor/incomeMinor over all periods and 
- **money-7** (low · small) تقدير النوع وتأكيده الجماعي بيبصوا على أدلة مختلفة
  - core/.../EstimatedKinds.kt:69-76 builds SuggestionInput without sourceCategory/sourceOperationType, while app/.../usecase/SetEconomicKind.kt:69-78 passes sourceCategory first (suggestEconomicKind prefers it, SuggestEconomicKind.kt:49). Diverges when the owner recategorizes an imported line (e.g. source "تحويلات" moved to a spending category) or when the seed category name differs. Fix: one helper `suggestionInputOf(t, names)` used by both.
- **money-8** (low · small) المتوسط بيتحسب بأرقام عشرية
  - core/.../Budget.kt:75 `JsText.round(total.toDouble() / used.size)` and Budget.kt:107 median `(a+b).toDouble()/2`. CLAUDE.md rule 1 forbids floating point in any money calculation. Values stay below 2^53 so results are exact today, but replace with rateOfMoney(total, 1, used.size) (Money.kt:136, half-away rounding) and integer median.
- **money-9** (low · medium) التراجع عن الدمج بيضيع لو قفلت الشاشة
  - ui/.../more/BalanceWhyScreen.kt:69 `var lastTicket by remember { mutableStateOf<MergeTicket?>(null) }` — only the latest MergeTicket, lost on navigation/process death; FixUndo (FixMyBalance.kt:51) is likewise in-memory. MergeDuplicates.undo needs the ticket (MergeDuplicates.kt:125). Fix: persist tickets (they already map to the verified backup docs) or rebuild undo from the backup file.

### رسايل البنك (6)
- **sms-1** (high · medium) التحويل بين حسابك في بنكين مختلفين بيتسجل خارج وداخل في نفس المحفظة
  - smsLegVerdict returns Between when both from/to last4 are in ownDigits(all), and ownDigits covers every wallet kind (SmsRouting.kt:66). Routing then resolves each leg with accountWallet(...) ?: base (SmsRouting.kt:91, legWallet :75-78). accountWallet returns null when the digits belong to a non-bank wallet (kind filter at :57; AnswerSmsAccount.mine at AnswerSmsAccount.kt:28 lets the owner put last4 on a digital_wallet or own_abroad wallet), or to a bank wallet mapped to another sender (:59-60). Example: Rajhi→wA mapped, SNB→wB mapped, and the SNB SMS reads 'من حساب: ..1111 / الى: 2222'. The OUT leg's accountWallet is null (wA is owned by alrajhi), so it falls back to base wB. The IN leg also
- **sms-3** (medium · small) «سجّلها بإيدك» بيسجّل مصروف بتاريخ النهارده دايمًا
  - wiring SmsGraph.kt:223-229: recordByHand builds AddOperationDraft(AddKind.OUT, amountText, walletId). AddOperationDraft (QuickAddOperation.kt:39-47) has no date field, so the operation is dated today and is always OUT. The message is then dismissed with no SourceRecord or SMS:<hash> reference, so a later readFrom on a new device can bring it back. The UI routes foreign-currency messages to this button (TextsAreaImportsSms.kt:151 'سجّلها بإيدك', SmsWaiting.kt:238-245), and FailedUi already carries the receive date (BankSmsModel.kt:134). Fix: pass the message day (FailedUi.date), let the owner pick the direction (or default it from the rejected parse when known), and record through the SMS pip
- **sms-4** (medium · small) عدد «محتاجة تأكيد» بيقف عند أحدث ٢٠٠ رسالة
  - ملاحظة المالك: «تقربيا المفروض انت حلتها بالفعل»
  - SmsRouteWalk.waiting() (SmsRouteWalk.kt:84) reads only window(null), the newest page of at most 200. WaitingState.waiting.messageIds is page-only, and only SmsWaiting.total (:112) covers the whole inbox. AutoRecordSms.asksOf (AutoRecordSms.kt:274) maps messageIds only, so SmsAskSource.pending (SmsAskSource.kt:13), and through it CountNeedsConfirmation.total (CountNeedsConfirmation.kt:61), is capped at 200 SMS asks. UnmappedSender.messages (SmsRouteWalk.kt:65 via routeSender) is page-only in the same way. Fix: let CountNeedsConfirmation use WaitingState.waiting.total for the SMS share (one aggregate ask carrying a count, or the asks plus the remainder), and flag the per-sender counts as parti
- **sms-5** (medium · medium) اختيار «هي دي» بين احتمالات الكشف مش موجود في الشاشة
  - CrossSourcePreview.kt:172-175 turns CrossSourceVerdict.Ambiguous into a SIMILAR line with mergeCandidates. ReviewSmsInbox.recordAll(…, mergeChoices) (ReviewSmsInbox.kt:144) and ImportStatement.commit apply the choice. But wiring SmsGraph.record (SmsGraph.kt:139-150) calls one.recordAll(chosen, similar) without mergeChoices, and there is no reference to mergeCandidates/mergeChoices anywhere under ui/ or wiring/ (grep is empty). Fix: expose SmsReviewLine.mergeCandidates in BankSmsModel/SmsWaiting as a pick list, add mergeChoices to SmsDeps.record, and pass it through; add a wiring test that an Ambiguous line merges into the chosen statement transaction.
- **sms-6** (low · small) «سجّل الكل» في الشاشة بيشيل الرسالة بعد أول رِجل
  - ReviewSmsInbox.recordLocked (ReviewSmsInbox.kt:195-200): when ackLater == null (the screen path via recordAll), each part acknowledges done = recorded + DUPLICATE lines of that part right away through deps.inbox.imported. A two-leg message whose legs sit in different parts (ScreenRouter) is therefore removed as soon as part A finishes, even when part B's leg is CONFLICT or an unchosen SIMILAR. SmsRouteWalk.forEachRoute (:53,:73) counts expected legs, but only in the background path. Fix: inside recordLocked, collect done message ids across all parts and acknowledge a message only when every leg line of it is done, mirroring the expected-count logic.
- **sms-7** (low · small) دورة الخلفية بتلف الصندوق كله مع كل رسالة جديدة ومن غير حد وقت
  - RunBackgroundCycle.runNow calls deps.sms.run() (RunBackgroundCycle.kt:68), and backgroundCycle wires it (BackgroundWiring.kt:59) with no SmsRunMemo and no deadline. AutoRecordSms.run → record(memo=null) → walk(newOnly=false) visits every page. Each wallet preview runs loadLedger → sources.listByAccountIdentity + findByIds over the whole ledger (CrossSourcePreview.kt:40-43). After that, routes.waiting() re-parses the newest page. MasroufyBackground.requestRun enqueues a run after every received SMS (BankSmsReceiver.kt:195, APPEND_OR_REPLACE). The cost is not measured here; correctness holds because each part commits atomically and acknowledges after commit. Fix: keep one process-wide SmsRunMe

### الأشخاص والديون (13)
- **people-1** (high · small) الدين القديم بيتسجل بالريال حتى في مصر
  - app/.../usecase/ManagePeople.kt:194-206 addOpeningDebt hard-codes `currency = Currency.SAR`. ui/.../dues/DebtSheets.kt OpeningDebtBody parses the amount with `space.space.currency` (EGP in the Egypt space) but calls addOpeningDebt(personId, kind, minor) without a currency. Consequences: PersonLedger groups it under SAR; settleOldestOf (PersonLinkWrites.kt:58) and openDebtsFor filter `currency == t.currency` so EGP transfers never settle it; ZakatHoldingsReader filters obligations by space currency so it is excluded from Egypt zakat; SettleOverpay creates the surplus debt in o.currency (SAR). Fix: add a currency parameter (from the space / ManagePeopleDeps), pass it from DebtSheets and Onboar
- **people-2** (high · medium) الزكاة بتاخد الديون بحالة النهارده مش بحالة يوم الميعاد
  - app/.../usecase/ZakatHoldings.kt (people loop): obligations are not filtered by origin date <= asOf and `remainingOfObligation(o, settled)` uses every settlement, including ones dated after asOf. ManageZakat.assess uses asOf = minOf(today, year.dueAt), so any assessment after the due date mixes cash-as-of-dueAt with debts-as-of-today. Wallet balances and asset lots are already cut at asOf, and the rosca loop was fixed for exactly this (comment about session 11/13). Fix: drop obligations whose origin transaction date > asOf (keep opening debts), and drop settlements whose transaction date > asOf (manual settlements have no date — keep them, as the rosca loop keeps undated entries). Add a test
- **people-3** (medium · small) نفس العملية ممكن تتربط بشخص وبقسط أو جمعية في نفس الوقت
  - ManagePersonLinks.kt linkLocked only rejects existing PersonLink and ledgerLinkedIn (obligations/settlements/allocations) — no check of roscaEntries / installmentPayments / installmentPlans.receivedTransactionId. DueLinks.kt:62-68 check() only rejects other dues/zakat/event-gift links — no check of personLinks/obligations/settlements/allocations. The operation detail shows LinkPersonCard for INSTALLMENT_PAID/ROSCA_CONTRIBUTION (linkFlowOf returns non-NONE), so REPAID on an installment payment changes the kind to DEBT_REPAID while the payment still counts toward the plan. Fix: add the due checks to linkLocked (and ManagePeople.linkToPerson / AnswerTransferAsks pending) and the ledger/person-l
- **people-4** (medium · small) مسح نص تحويل «ده حسابي» بيسيب النص التاني
  - ManageTransfers.otherLegs creates `<id>#own-leg`. DeleteTransaction.kt preview/delete has no OWN_LEG_SUFFIX handling (only EraseBlock.SPACE_LEG for cross-space legs), so erasing the base leaves the leg and erasing the leg leaves the base INTERNAL_TRANSFER with no counterpart — wallet balances diverge. ManageTransfers.forget also deletes legs via txns.deleteMany without removing their transactionTags/projectLinks/eventLinks, which would leave rows that fail BACKUP_RELATIONS in checkFullBackupData. OWN_LEG_SUFFIX is unknown outside the people/transfer use cases (grep). Fix: add an OWN_LEG block (or erase both legs atomically with restore) in DeleteTransaction; route forget's leg removal throug
- **people-5** (medium · small) شيل «السلفة» بعد «ده حسابي التاني» بيسيب ميعاد الدين ووقائع الزكاة معلقة
  - TransferAskLedger.kt PersonLedgerRepos.remove deletes settlements, obligations and allocations only. Callers: ManageTransfers.decide (markOwnAccount), AnswerTransferAsks.answerLoanOrSupport/answerDebtRepayment/answerOverpay (stale writes). debtTerms.obligationId and zakatFacts.obligationId are hard BACKUP_RELATIONS (Backup.kt:88,91), checked in checkFullBackupData (BackupCheck.kt:131-135) which FullBackup.create calls (FullBackup.kt:151). DeleteDebt and ManagePersonLinks.unlink already remove terms/facts. Fix: give PersonLedgerRepos optional DebtTermsRepository/ZakatFactRepository and remove them for `writes.loans`; test markOwnAccount after setDebtTerms then FullBackup.create.
- **people-6** (medium · medium) قسط «ادفع بعدين» اللي اتربط لوحده غلط مالوش فك ولا مسح
  - ملاحظة المالك: «كمان حط في اعتبارك انه ممكن ادفع اكثر من قسط مجموعين مع بعض في تابي ... بيجي رسالة اني دفعت كذا مرة واحدة لتابي و هما اصلا بيكونو مجموع كذا عملية مع بعض ..محتاجين حل او اقتراح للنقطة دي»
  - BnplCharges.settle (called from SmsScreenExtras.kt:52 bnplAsks(), i.e. a read path that writes) auto-links any unconfirmed provider charge whose amount equals an open plan's installment (Bnpl.kt:61-68 bnplMatch). ManageInstallments.unlink exists but the UI only calls it for receivedTransactionId (InstallmentDetailScreen.kt:165-173); there is no UI to unlink an installment payment and InstallmentPlanRepository has no delete (DuesPorts.kt). Fix: expose unlink for payments (and add the txn to plan.dismissedTxnIds so settle() does not re-link), add plan delete with restore, and return AskWhich instead of NextInstallment when a same-provider NewPurchase is plausible (e.g. the charge date is far f
- **people-7** (medium · small) السداد اللي بتكتبه بإيدك مالوش تراجع
  - DebtSheets.kt:145 deps.people.settle(...) then toaster.show(settledToast(...)) with no undo; no use case deletes a single settlement (DeleteDebt comment: «مفيش شاشة تمسح تسوية لوحدها»). SettleOverpay.resolve likewise has no undo. Fix: ManagePeople.removeSettlement(id) restricted to settlements with transactionId == "" (and not referenced by a PersonLink), plus an UndoWindow on the toast; Firestore path should bump concurrency/settlements.
- **people-8** (medium · medium) سداد من الربط ممكن يتكتب فوق سداد تاني في نفس اللحظة
  - ManagePersonLinks.linkLocked writes settlements with deps.settlements.saveMany (ManagePersonLinks.kt:138), computed by settleOldestOf from a plain read; it does not bump `concurrency/settlements`, so FirestoreSettlementWriter (used by ManagePeople.settle / AnswerTransferAsks) cannot detect it, and LINK_LOCK does not cover the writer path. PersonRulesEffect runs in the background cycle. An over-settled obligation makes remainingOfObligation throw LedgerError (Ledger.kt:100-104), which propagates from ManagePeople.listWithBalances, personLedger and LoadDues. Fix: write link settlements inside a Firestore transaction that bumps the revision (or route through SettlementWriter), and make list vie
- **people-9** (medium · medium) دمج نسخة قديمة ممكن يبلع دين قديم ويخلط وقائع الزكاة
  - Backup.kt:205 NATURAL_KEYS obligations = [personId, originTransactionId, kind, currency] — no originalMinor; for opening debts originTransactionId is null so any two same-person/kind/currency opening debts collide in mergeFullBackupDetailed and the incoming one is skipped with its id remapped; its settlements (natural key [obligationId, transactionId=""] also without amount) are then remapped onto the existing debt or dropped. Separately, zakatFacts rows keep row["id"] = old obligation id while the relation remap rewrites obligationId (BackupRemap.kt handles only personLinks), and FirestoreFullBackup.docIdOf uses backupRowId ("id"), so the fact lands under the old doc id: a duplicate fact fo
- **people-10** (low · small) صفحة الشخص بتخبي البلد التانية لو قرايتها وقعت
  - PersonPage.kt LoadPersonPage.load: `others.mapNotNull { runCatching { o.page.ledgerHere(personId).abroad() }.getOrNull() }` silently drops a failing space; PersonPageData has no flag. Rule 10. Fix: return the failed space ids in PersonPageData and render an «غير متاح» line in PersonLedgerSection.
- **people-11** (low · small) تعديل مبلغ العملية (لما يتوصل) هيسيب الدين بالمبلغ القديم
  - EditTransaction.kt:102-115 setAmount + core/AmountEdit.kt:15-34 planAmountEdit only consider allocations and settlements; obligations with originTransactionId == id, PersonLink.amountMinor, installment payments and rosca entries are ignored. setAmount is not called from ui/ or wiring/ in 0.5.1 (grep), and AnswerTransferAsks.FixAmount expects this screen — fix before wiring §32.
- **people-12** (low · small) البحث عن عملية لربطها بشخص بيتقل الشاشة
  - ui/.../people/PersonLinkOps.kt:152 `links.page.search(query)` runs in LaunchedEffect (Main) without withContext; LoadPersonPage.search calls owner.everything() (all transactions), ownLegBases and normalizeText/logicalArabic per transaction. The suggestions call at line 79 already uses Dispatchers.Default. Fix: wrap in withContext(Dispatchers.Default).
- **people-13** (low · small) تحويلات الحسابات المربوطة اللي رجعت من نسخة ما بتظهرش في صفحة الشخص
  - PersonRules.kt PartyTxnIndex.idsFor: after the first full scan it only reads txns.listCreatedAfter(cursor) (strict createdAt > cursor, MemoryRepositories.kt:34). Rows added by FullBackup merge keep their original createdAt, so they are never indexed in this process. Fix: invalidate the index (cursor = null) after FullBackup.apply / any restore, e.g. via a change counter.

### الاستثمار والبروفايل (7)
- **invest-1** (high · small) المساعد بيرد على «أقدر أصرف كام في اليوم» برقم غير كارت الرئيسية
  - AssistantAnswersMoney.kt:47-55 (dailyAllowance) and :26-27 (ASSIST_PER_DAY inside remaining()) read home(current).allowance = core Analytics.kt:45 dailyAllowance(budgetLimit - spent)/days, built in LoadHomeScreen.kt:189. The home card uses LoadDailyAllowance (LoadHomeCardData.kt:62-82: onHand - duesUntil(payday) / days, owner reply 2026-10-16). Same question, two formulas; BudgetsPresenter.kt:150 also shows the old one as 'about X a day until payday'. Fix: inject LoadDailyAllowance into AssistantSources and answer DAILY_ALLOWANCE from it (keep 'not available' when onHand is null, mention unpricedDues); relabel or retire the budget-based per-day line. Add a test asserting assistant == home ca
- **invest-2** (medium · small) إيجار الشقة اللي كتبته في «ضيف حاجة» مش داخل في «الدخل من غير شغل»
  - ملاحظة المالك: «في صفحة مصادر الدخل يظهرله مقترح انه تم اضافة ايجار في ممتلكاتك تحب نضيفها ك دخل ؟ و يظهر له تنبيه بده في الصفحة الرئيسية . كمان عايزين سيستم يربط عملية بتيجي تحويل من شخص معين او كاش انه ده دفع الايجار بتاع الاصل الفلاني و يكون في جدول بيحسبله بالشهور ما تم دفعه و هل متأجرة اصلا ولا لا (مع الاخذ بالاعتبار ان الايجار ممكن يكون يومي او شهري او سنوي )»
  - AddHolding.kt:94-98 saves rent via ManageAssetGrowth.setProfile -> Asset.monthlyRentMinor. LoadFutureInputs.kt:108-111 computes passiveKnownMinor only from IncomeSource kind RENT/INVESTMENT with MONTHLY expectedMinor; asset rent is never read. Scenario: add FLAT, rented=true, rent 3,000 -> PassiveIncome.knownMinor stays 0. Fix: add sum of monthlyRentMinor of non-archived, same-currency rented holdings (respecting vacantMonthsPerYear if desired), deduplicated against RENT sources linked to the asset; needs an owner decision on dedupe -> write as open question. No test covers rented holding -> passive income.
- **invest-4** (medium · small) المساعد بيقول «قيمة أصولك» برقم غير «حاجاتي»
  - ملاحظة المالك: «كمان عايزك تفرق بين الفلوس اللي انا محوشها عن الكاش اللي معايا ... (ممكن نسمي الفلوس دي السيولة وملهاش علاقة انها للاستثمار اصلا غير لما انا اقول و ساعتها تتمسح من الفوس اللي ممكن اصرفها في الشهر . كمان محتاج ممكان واضح للتخطيط في الفلوس العادية مش الاستثمار واعرف منه ايه المحجوز لباقي الشهر و ايه اللي ممكن احجزه للدفع بالترتيب .»
  - AssistantAnswersPlans.kt:167-182 uses ManageAssets.listPortfolio().owned (Assets.kt:180-192 computePortfolioTotals): includes personal/losesValue items, does not filter asset.currency, and returns null if any asset lacks a price, which also makes the ASSIST_ASSETS_PARTIAL branch (line 176) unreachable. «حاجاتي» = LoadHoldings + holdingsView (HoldingsView.kt:67-105). Fix: answer from LoadHoldings snapshot + holdingsView totalMinor/unknownCount; this also matches OVERRIDES assistant rule (d) 'same functions as the screens'.
- **invest-5** (medium · small) إصدار أحدث يضيف إعداد جديد هيوقّع قراية الإعدادات في النسخة دي
  - data/AssistantCodecs.kt:90 'else -> throw DocumentError' for unknown userSettings type; FirestoreSpace decodeAll (FirestoreSpace.kt:98) maps every doc through decode with no per-doc catch. LoadInvestTip.closedIds (LoadInvestTip.kt:95-96), LoadInvestToday.kt:119, ManageInvestView.stored, ManageBirthDate.load have no runCatching (only LoadHoldings.readIncludePersonal does). Already in OVERRIDES 101 'deferred'; raising because 0.5.1 is the first build users keep while newer builds add types. Fix: decode unknown type to null and mapNotNull, log once.
- **invest-6** (low · small) المساعد بيقول مرتبك من جهة واحدة لو عندك وظيفتين وواحدة من غير مبلغ
  - AssistantAnswersPlans.kt:48-51: salaryState(...).amountMinor ?: sourcesActiveOn(...).firstOrNull(::isWage)?.expectedMinor. With 2 active employers where one has expectedMinor = null, Salary.kt:51 returns null (rule 10), then the fallback returns the first wage's amount as 'your salary'. Fix: only fall back when salaryState.employers is empty (pension case).
- **invest-7** (low · small) اختيار «تدخل الحاجات الشخصية» مش متطبق في كل الكروت
  - LoadInvestReturns.kt:104 and LoadInvestTip.kt:115 call investableSplit(it.items) with default includePersonal=false; LoadFutureInputs.kt:106 passes holdings.includePersonal. LoadInvestToday.kt:117 filters purpose != PERSONAL instead of HoldingValue.personalLike (InvestHoldingModel.kt:112) + includePersonal. Fix: pass snapshot.includePersonal and use personalLike everywhere.
- **invest-8** (low · small) شاشة صفحة المجموعة بتجمع فلوس بنفسها
  - ui/.../investment/holdings/AssetCategoryState.kt:45-47 sums valueMinor with addMoney in the UI layer (CLAUDE.md rule 4). Move to a core/usecase function (e.g. groupStart(snapshot, group)) next to ProjectHoldingGroup.

### بناء البرنامج والشاشات (6)
- **arch-1** (high · small) النسخة الاحتياطية الشاملة بتعلّق الشاشة وممكن تقفل البرنامج
  - BackupScreen.kt:113-117 runs `b.create(more.nowIso()).toJsonText()` inside `scope.launch` where scope = rememberCoroutineScope() (BackupScreen.kt:55) => Main dispatcher; FullBackup.create/plan/apply (app/usecase/FullBackup.kt:147,166,193) never switch context and FirestoreSpace reads do not either, so JSON building, checksum, checkFullBackupData/checkBackupFinance and merge all run on the UI thread. Restore does the same: RestorePreviewScreen.kt:77-80 (`backup.plan(text)` in LaunchedEffect) and :144-145 (`b.apply(plan.file)`). BackupScreen.kt:116 also does `text.encodeToByteArray().size` only to get the size => a second full copy of the backup in memory. HANDOVER already measured 41 dropped 
- **arch-3** (medium · small) حفظ بيتعمل ومحدش بيعرف إنه اترفض
  - FirestoreSpace.kt:53-64 `write {}` launches the commit on writeScope and on failure only does `failures.tryEmit(e)` + Problems.report; `writeFailures` (FirestoreSpace.kt:43-47) has no collector anywhere in the repo (grep `writeFailures` hits only its declaration and a comment in FirestoreSync.kt:68). Callers (saveAll FirestoreSpace.kt:101-108, deleteAll, updateDoc, profile save FirestoreAccountAdapters.kt:54-56) also apply the change to LocalMirror optimistically, so the UI shows it; when the server rejects (rules/App Check denial, update() on a missing doc, invalid value) the SDK rolls back the local cache and the listener removes it from the mirror — the item silently disappears. The KDoc 
- **arch-4** (medium · small) ملفك الشخصي ممكن يتمسح منه حاجات لو نسخة أقدم حفظته
  - FirestoreAccountAdapters.kt:36-56 FirestoreProfileRepository.save builds the full doc and calls `ref().set(doc)` without merge, then mirror.applyReplace. Every other entity uses set(merge = true) + explicit FieldValue.delete for known-empty fields (mergeForm, FirestoreSpace.kt:92-96) precisely so fields written by another version survive. Version 0.4.1 (commit 4dc031d6) writes no salaryCurrency/salaryDeclined/salaryRange, so a 0.4.1 device saving the profile (ManageIncomeSources.applyMonthStart, ManageProfile, ProfileSalary, ManagePersonCircles all call profiles.save) erases 0.5 fields; the frozen TS app's setDoc does the same to character/lookIndex/duesInBudget etc. Also a read-modify-write
- **arch-5** (medium · medium) حساب «معك الآن» بيحصل على خيط الشاشة
  - LoadWithYouNow.kt:36-48 reads `txns.listByDateRange(from = earliest wallet openingAt, today)` (the whole history, 4000+ rows for the owner) and runs walletBalancesOn with no withContext; ShellGraph.kt:61 calls it directly; HomeScreen.kt:76-87 calls loadHome (HomeScreen.kt:119-132: withYouNow, profile, LoadHomeScreen.load, calendar.summary, inbox) from LaunchedEffect => Main dispatcher. The Firestore repositories do no dispatcher switch either, and LocalMirror decodes each document lazily on first read (LocalMirror.kt:29-37), so the first home load decodes all transactions on the UI thread. ARCHITECTURE 31.42 requires any use case looping over all operations to take `work` and withContext(wor
- **arch-6** (low · medium) لو المزامنة وقفت لمجموعة ما بترجعش تاني
  - FirestoreSync.kt:92-99: on a listener exception the group is marked stale (LocalMirror.markStale) and the job ends; nothing restarts it. `errors` (FirestoreSync.kt:43-50) has no collector (grep `sync.errors`/`.errors.collect` finds none). Afterwards reads for that group go to Firestore directly and remoteChanges never fires for it, so screens do not refresh on other-device edits. Fix: retry the collect with backoff while the session is alive (stop on sign-out), and clear the failed mark when a snapshot arrives again; unit-test with a fake snapshots flow that throws once.
- **arch-7** (low · small) الرئيسية ممكن تومض برسالة خطأ وهي بتتحدث
  - HomeScreen.kt:78-86 and loadHome HomeScreen.kt:120-131 wrap suspend calls in runCatching, which catches CancellationException. When LaunchedEffect(deps, tick, changes.version) restarts (remote change during opening, REMOTE_SETTLE_MS bump), the cancelled run resumes, every later suspend call fails fast, and it assigns `me = null`, `load = HomeLoad.Failed` (if cancelled in withYouNow) or a Ready with month = null. The repo already has cancellation-safe helpers (readState in home/ReadState.kt:24-31, attempt in OperationsParts.kt:46-52); ui/ has 273 runCatching uses, so the pattern recurs. Fix: use readState/attempt (or rethrow CancellationException) in loadHome and the effect.

## ٢. الجولة التانية (اختيار «بعدين»)
- **sms-2** (medium · small) رسوم التحويل بين حساباتك بتتسجل مرتين
  - SmsAccountLegs.kt:79 onLeg() copies the whole SmsRow, fee included, onto both legs. smsFeeToRecord (SmsFeeEffect.kt:142-146) only checks direction when fee.includedInAmount, so an external fee on the IN leg (reference ends with '#in') passes. SmsFeeEffect.prepare (:51,:70) then splits it again, and split() writes a second 'رسوم بنكية' OUT transaction in the receiving wallet with reference '<ref>#in#fee', which the dedupe doesn't link to the first. Fix: in smsLegRows set fee = null on the IN leg (or in smsFeeToRecord return null when direction == IN). Add a Between test whose message carries a separate fee line.
- **invest-3** (medium · small) صفحة «معادن» بتكبّر الفضة بمتوسط الدهب، والتوقع الكبير بيسيبها ثابتة
  - ملاحظة المالك: «يتم التنويه ان الفضة بالذات مش باسعارها الحقيقية»
  - HoldingsView.kt:130-135 groupBucket(METAL) = GOLD; PreviewHoldingValue.kt:59-69 ProjectHoldingGroup grows the whole METAL group start (AssetCategoryState.kt:45-47 sums gold+silver) at r.goldBp. InvestHoldingModel.kt:72-77 bucket(SILVER) = NONE, used by investableSplit for the main projection. Fix: project per bucket (gold part at goldBp, silver flat) or split the start by item.bucket; keep a single source of truth tied to Q4.
- **arch-2** (medium · small) الاسترجاع بيقف في النص لو رجعت من الشاشة
  - RestorePreviewScreen.kt:74 `val scope = rememberCoroutineScope()`; :143-145 sets Phase.Applying then `scope.launch { b.apply(plan.file) }`. There is no BackHandler/Overlay blocking back while Phase.Applying (grep shows none). Leaving the screen cancels the scope; FullBackup.apply (FullBackup.kt:193-210) writes root groups via port.addMissing in transactional pages (FirestoreFullBackup.kt:57), then profile, then other spaces and spaceTransfers last — cancellation leaves a partially restored account with no Done/Error phase shown. A re-run is mostly idempotent (add-missing), but the owner is never told to re-run. Fix: block back during Applying (Overlay(onBack = {}) like OperationsScreen selec
- **RULES-2** (low · small) أي مستخدم يقدر يغيّر اسم تاجر مشترك ضافه غيره
  - firestore.rules:15-17 update = resource.data.confirmed == false && validSharedMerchant(key); validSharedMerchant (:33-44) never compares with resource.data (only normalizedName == key); :39 displayName size <= 80 with no minimum. App only changes categoryId (SharedMerchants.kt:97 contributionFor keeps displayName/aliases); confirmed entries' displayName+aliases are copied into every account (SharedMerchants.kt:117-122, planAccountMerchantSync). Fix: on update add request.resource.data.diff(resource.data).affectedKeys().hasOnly(['categoryId','updatedAt']); on create add d.displayName.size() > 0; add rules tests denying displayName/aliases change on update and empty displayName.
- **ANDROID-2** (low · small) نقل البيانات من موبايل لموبايل بياخد الدخول ورسايل البنك
  - Merged AUTH-2 + ANDROID-2 + PRIVACY-5. native-app/androidApp/src/main/AndroidManifest.xml:15-22 has only android:allowBackup="false", no android:dataExtractionRules / fullBackupContent, no res/xml rules; targetSdk 36 (androidApp/build.gradle.kts:26); no flavor manifest override, so fast build affected. On API 31+, allowBackup=false does not block D2D transfer. Data at risk: Firebase Auth refresh token (com.google.firebase.auth.api.Store shared_prefs), sms-inbox.db (SmsInboxStore.kt:18, enabled=true + owner uid), Firestore offline cache, lock/sync prefs, problems.log. Fix: android:dataExtractionRules="@xml/data_extraction_rules" excluding root/file/database/sharedpref/external under both <clo
- **RULES-6** (low · medium) اختبارات قواعد الحماية بتتشغل بالإيد بس
  - native-app/firestore/rules-test/rules.test.mjs:80-140 covers two users + signed-out but only create-time size checks for displayName/aliases (107-113) and confirmed lock (127-132); no update-field-pinning or empty displayName tests. .github/workflows only native-core.yml (gradle jvmTest) and prices.yml; firebase.json:3 has no predeploy test. Fix: CI job on firestore.rules changes running firebase emulators:exec --only firestore --project demo-masroufy-rules "node --test rules.test.mjs"; add tests for RULES-2.
- **SUPPLY-3** (low · small) أدوات البناء والمكتبات ما بتتفحصش بصمتها
  - native-app/gradle/wrapper/gradle-wrapper.properties:3-7 no distributionSha256Sum (validateDistributionUrl only checks URL); no gradle/verification-metadata.xml; .github/workflows/native-core.yml:24-30 runs ./gradlew without wrapper-validation. Disputed: defense-in-depth, HTTPS from official repos. Fix: add distributionSha256Sum for gradle-8.14.3-all.zip; ./gradlew --write-verification-metadata sha256 help and commit; add gradle/actions/wrapper-validation step.

## ٣. طلبات جديدة من المالك (2026-10-17) — محتاجة تصميم أو قرار قبل البنا
1. **مراجعة منطق البلدين (أولوية عالية في الجولة الأولى).** المالك: «وانا بجرب البرنامج اكتشفت مشاكل لو انا حولت لمصر (من ضمنها ان فجأة مصدر الدخل هو هو ولكن مكتوب بالمصري ) ..ف محتاج مراجعة منك لمنطق حجات كتير لو حولت من بلد لتانية و خصوصا في الاشخاص (لو شخص معين بتعامل معاه في مصر و السعودية في نفس الوقت )». الخطوة الأولى: مراجعة قراية بس لكل حاجة بتتغير بتغيير البلد (مصادر الدخل، الأشخاص والديون عبر البلدين، المحافظ، العملة، النصوص، الإعدادات المشتركة)، وبعدين خطة إصلاح. مرتبط بـ people-1 (الدين القديم بالريال في مصر).
2. **علامة البلد.** المالك: «لو حد عمل البلدين لازم حجات كتير تتعلم عليها بعلامة سواء علم البلدو او اختصار البلد بالانجليزي او حاجة معينة». شكل العلامة يتقرر في جلسة التصميم (علم/اختصار/لون) — يتبعتلها.
3. **الإيجار كدخل** (من ملاحظة invest-2): اقتراح «ضفت إيجار في ممتلكاتك، تحب نضيفه كدخل؟» في مصادر الدخل + تنبيه في الرئيسية + ربط تحويل من شخص أو كاش بإيجار أصل معيّن + جدول شهور مدفوع/مش مدفوع + هل الأصل متأجر + إيجار يومي/شهري/سنوي. محتاج تصميم.
4. **السيولة مقابل التحويش** (من ملاحظة invest-4): الكاش اللي معاه مش استثمار ولا تحويش إلا لما يقول، ولما يقول يتشال من اللي يقدر يصرفه في الشهر + مكان واضح لتخطيط الفلوس العادية: إيه المحجوز لباقي الشهر وإيه اللي ممكن يتحجز للدفع بالترتيب. محتاج تصميم.
5. **أقساط تابي المجمّعة** (من ملاحظة people-6): خصم واحد لتابي ممكن يكون مجموع كذا قسط — محتاج اقتراح حل (مثلًا: لو المبلغ = مجموع أقساط مستحقة لنفس المزوّد، يتوزع عليهم ويسأل للتأكيد). يتبعت لجلسة التصميم مع لوحة «أنهي مشترى».
6. **قاعدة التحويلات** (من ملاحظة money-2): أي تحويل ما يتحسبش مشتريات/مصروف إلا لما صاحب الحساب يقول، أو يحط قاعدة لحساب معيّن.
7. **أنواع الملفات** (من ملاحظة PRIVACY-7): الاسترجاع يقبل نوع ملف النسخة بس.
8. **النسخ الاحتياطية الداخلية** (من ملاحظة PRIVACY-4): آخر ملفين بس، وكل نسخة جديدة تمسح الزيادة.

## ٤. شغل جاهز أو واقف من قبل (يدخل الجولة الأولى)
- فروع [READY] لسه ما اندمجتش: `r051-topspend2-f` (تصميم أكتر ما صرفت عليه) · `r051-bnpl2-f` (لوحة التقسيط وقسم مشتريات بالتقسيط).
- فروع وقفت في النص (تتكمل على فرع جديد `X-2`): `r051-balance2` («افهم رصيدك» + «صلّح رصيدي» من التصميم) · `r052-people` (تقسيم مشترى على أكتر من شخص) · `r052-sms` («أكّد الأشكال» في الخلفية بإشعار).
- مشاكل فحص 0.5.1 (HANDOVER §7): «مَن هذا؟» على التحويل الخارج · «ممكن تكون مربوطة بـ» بيقترح استرداد ملوش علاقة · حفظ النسخة الشاملة على خيط الواجهة (= arch-1).
- إجابات المالك في OVERRIDES §105.
- تغييرات التصميم الجاية من جلسة «نظام التصميم v0.4» (المالك قال إنه هيغيّر تصميم حاجات): تتجمع هنا قبل الخميس.
- رفع الكود: كود 0.5.1 ما اترفعش — يترفع مع 0.5.2/0.6 بموافقة المالك.

## ٥. ترتيب التنفيذ المقترح (من الخميس)
1. حماية البيانات والفلوس: AUTH-1 (خطوات لوحة فايربيز للمالك) · money-1 · money-2 · sms-1 · people-1 · people-2 · arch-1 · مراجعة البلدين.
2. باقي «دلوقتي» حسب المنطقة، كل منطقة في فرع لوحده، 2-3 فروع بالكتير في نفس الوقت (حد الاستخدام).
3. دمج الفروع الجاهزة والتصاميم، نسخة، تجربة على المحاكي ببيانات كبيرة، رفع على درايف.
4. الجولة التانية.

## ٦. تصاميم اتوافق عليها ومستنية النقل (من جلسة «نظام التصميم v0.4»)

### «الرسايل والتصنيف» — موافقة المالك 2026-10-17 («موافق — اكتبها للبرنامج»)
- **الفرع:** claude/design-system-v0-4-90a69a، من ed999fbf لحد d279aeb7. القرارات في OVERRIDES.md بتاع فرع التصميم، قسم «✅ «الرسايل والتصنيف»»، وصفوف KOTLIN-MAP.
- **CategorySheet (جديد):**
  - بحث ثابت، و«آخر اللي استخدمته»، ومربعات 4 أعمدة.
  - التصنيف الرئيسي يرتفع مكانه، والفرعيات تطلع جنبه بخيوط بلونه، وورا كده تغبيش خفيف. أول عقدة «عام».
  - اختيار الفرعي بيسجّل على طول، ويطلع شريط «اتصنّفت» ومعاه «تراجع».
  - فرعيات الدين بتفتح WithWho.
  - شجرة «ديون وسلف» الجديدة: سلفة ادّيتها · سلفة أخدتها · سداد دين · تحصيل دين · مستحقات.
- **WithWho (جديد):**
  - الناس وأرصدتهم، و«شخص جديد».
  - المعاني كلها ظاهرة دايمًا: دين ليا عنده · دين عليا ليه · سلفة رجعتلي · سددتله · مستحقات له · هدية · دعم · مع الشخص ده بس.
  - المعنى بيتحدد مقدمًا من التصنيف، وفيه سطر يوضح الأثر، و«اربط»، و«مش عارف دلوقتي» (تروح «محتاجة تربطها بشخص»).
  - بيستخدم كمان جوه «شخص أعرفه».
- **TransferParty (اتعاد رسمه):**
  - ردود في عمودين: 6 للداخل و4 للخارج.
  - «تجاهل الحساب ده» بعداد 8 ثواني و«تراجع»، وبعدها كارت رمادي فيه «رجّعه».
  - لوحات فرعية: أنواع الجهة · المستحقات و«احفظ القاعدة» · اسم وملاحظة.
- **BankSmsSettings:** قسم «حسابات متجاهَلة».
- **SmsPending (جديد):**
  - تجميع حسب الشكل: الشرح مرة واحدة، و«فهمناها كده»، وشارة النوع، و3 سطور و«اعرض الأقدم».
  - «أكّد الشكل ده»، ونتيجته «اتسجلت N، ومعاها M بنفس الشكل» ومعاها «تراجع».
  - صفوف التحويل فيها «سجّلها وحدد مين» و«حسابي التاني».
  - **كارت الراتب بيتأكد لوحده:** «أكّد الأشكال» ما بيلمسوش (قرار المالك).
  - «أكّد الأشكال» فيه شريط تقدم وأعداد بتتحرك، و«اخرج — هيكمّل لوحده».
- **SmsAway (جديد):** شاشة القفل أثناء التشغيل في الخلفية وبعده، بنصوص إشعار 0.5.2 ومن غير أرقام.
- **BankSms:** رابط لـSmsPending.
- **OperationDetail:** بيبعت مبلغ التحويل لـTransferParty.
- **أسئلة مفتوحة (ما تتحسمش لوحدك):**
  - (أ) «حركة فلوس مش مصروف» مخفية في «شراء».
  - (ج) أنواع الجهة.
  - (د) «حاجة تانية» على الخارج = مصروف باسم.
  - (و) التحويلات المؤكدة تستنى «مين ده؟» في تفاصيلها.
- **مرتبط بالجولة الأولى:** r052-people (التقسيم) · r052-sms (الخلفية) · إصلاح «مَن هذا؟» على الخارج (التصميم ده بيحله).

### اتجاه المالك للأسبوع الجاي (⏭ في OVERRIDES بتاع فرع التصميم)
- صفحة أسئلة شائعة أكبر، متقسمة لأقسام.
- كل تنبيه أصفر أو شرح طويل (إلا لو مهم جدًا) يبقى شريط نصيحة فوق الصفحة، يتقفل بـX ويظهر أول مرة بس.
- نوافذ منبثقة فوق تغبيش أكتر بدل اللوحات اللي بتطلع من تحت.

### إجابات المالك على أسئلة «الرسايل والتصنيف» المفتوحة (2026-10-17 · كروت اختيار)
- (أ) «حركة فلوس مش مصروف» **تتخفى** من لوحة التصنيف بعد «شراء».
- (ج) «شركة أو جهة» **حسب الاتجاه**:
  - الداخل: استرداد · تعويض · دخل تاني.
  - الخارج: فاتورة · رسوم · مشتريات · قسط تمويل.
- (د) «حاجة تانية» على التحويل الخارج = **مصروف بالاسم ده**، والتصنيف اختياري.
- (و) التحويل المؤكد اللي طرفه مش معروف **يسأل «مين ده؟» جوه تفاصيله**. بيظهر في «محتاجة مراجعة»، وما يتحسبش مصروف لحد ما يتحدد (زي ملاحظة money-2).

### تعديلات بعد الموافقة (فرع التصميم aca34498 · fe9b3b78 · 7a116738 — OVERRIDES بتاع فرع التصميم «✅ ملاحظات المالك بعد الموافقة»)
1. **التصنيف من تفاصيل العملية:**
   - أي اختيار تصنيف بيفتح CategorySheet كاملة بدل 6 اختيارات و«أخرى» وهمية. العنوان «فيمَ صُرف؟» لفرق جرد الكاش، و«تصنيف «X»» لغيره.
   - قيمة التصنيف بتتعرض بلونه.
   - «غيّر» بيفضل موجود بعد الاختيار، والنقطة الحمرا بتختفي.
   - منطق: فرق الكاش يستخدم شجرة التصنيفات الحقيقية ويفضل قابل للتعديل. **مهمة في الجولة الأولى:** دوّر على أي قايمة 6 اختيارات فيها «أخرى» وهمية (جرد الكاش · الإضافة السريعة) وشيلها.
2. **مفيش عقدة «عام» في الفرعيات.** الدوسة التانية على التصنيف الرئيسي اللي اترفع = يتسجل عام. ولـ«ديون وسلف» الدوسة التانية بتفتح «مع مين؟».
3. **أي دفعة مربوطة بعملية متسجلة أو كاش** (SettleSheet في DebtDetail). كلام المالك: «لو انا سجلت اني دفعت حاجة المفروض يربط ده بعملية معينة متسجلة عنده بالفعل ويشاور عليها او يختار كاش».
   - الأساسي «عملية متسجلة»: مرشحين بنفس الشخص ونفس الاتجاه وأقرب مبلغ والأحدث. أحسن واحد فوق ومختار، عليه شارة «غالبًا دي» وسطر سبب.
   - تحتهم «مش لاقيها؟ دوّر في العمليات».
   - «كاش»: مبلغ، وبيتسجل داخل أو خارج من محفظة الكاش.
   - **«من غير عملية» اتشال.** منطق الأمانة لما الدفعة أكبر من المتبقي زي ما هو.
   - **مهمة في الجولة الأولى (قراية بس الأول):** اعمل قايمة بكل مكان في البرنامج بيسجّل دفعة من غير عملية: سداد الديون · الأقساط والتقسيط · الاشتراكات · الزكاة · المستحقات · الإيجار. نفس اللوحة تتطبق عليهم، وابعت القايمة لجلسة التصميم.

### وقفة التصميم
المالك وقّف كل شغل التصميم دلوقتي («وقف كل عمليات التصميم الان هنأجلها»). الحاجات اللي متأجلة ومالهاش ترتيب (⏸ في OVERRIDES بتاع فرع التصميم):
- علامة البلد · الإيجار كدخل · السيولة والتحويش · خصم تابي المجمّع.
- الأشخاص والعمليات، ومعاها تقسيم المشترى.
- الاستثمار.
- الأسئلة الشائعة وشرايط النصايح.

ما تتبنيش بشكل نهائي من غير رسمها.
- **ملحوظة نقل:** النموذج لسه مختلف في حاجتين، والمرجع OVERRIDES، مش النموذج (فرع التصميم 2c7c3c29):
  - (ج) TransferParty لسه بيعرض اختيارات أنواع الجهة القديمة. استخدم قايمة المالك.
  - (و) مدخل «محتاجة مراجعة» وقاعدة «ما يتحسبش مصروف» لسه ما اترسموش. ابنيهم بستايل البرنامج.

## ٧. الراتب ومصاريف الشغل من نفس الحساب (قرار المالك 2026-10-17 · كروت اختيار)
المالك: «نفس الشخص اللي بيحولي فلوس معينة علي مدار الشهر اشتري حجات ممكن هو نفسه يكون بيحول الراتب ..ازاي نظبط ده ؟». النهارده ربط الحساب بجهة الشغل بيخلّي أي تحويل منه يتسجل راتب (employerByPayer · applyKnownPayerSalary).
1. **إمتى يتسجل راتب لوحده:** لو الرسالة فيها «راتب/رواتب/مرتب»، أو لو المبلغ قريب من الراتب المكتوب على الجهة والميعاد في حدود أيام من يوم الراتب. غير كدة يسأل.
2. **التحويلات التانية من نفس الحساب:** بيسأل باختيارات جاهزة: استرداد مصاريف دفعتها لهم · مكافأة أو بدل · سلفة · حاجة تانية. وبيفتكر الاختيار للمرات الجاية (قاعدة على الحساب، ومعاها نفس شروط المبلغ والميعاد).
3. **مصاريف الشغل اللي بتترجع:** المشترى الأصلي بيتربط بالاسترداد وما يتحسبش من مصروفه. البرنامج بيقترح المشترى اللي مبلغه قريب، ولحد ما الفلوس ترجع المشترى بيتعلّم «مستني استرداد».
- **مرتبط بـ:** invest-1 و sms (السالاري) و money-2 وتقسيم المشترى. **الجولة الأولى**، و**محتاج رسم** لكارت «مستني استرداد» ولاختيارات التحويل التانية لما التصميم يرجع.

## ٨. خصم تابي/تمارا المجمّع (قرار المالك 2026-10-17 · كروت اختيار)
المشكلة: نسخة 0.5.1 بتسأل عن أي خصم من تابي بلوحة فيها اختيارين بس، «احفظ الخطة» و«تخطّي». الرقم ممكن يكون مجموع كذا قسط. **الحل المؤقت للمالك:** «تخطّي». العملية بتتسجل قسط تحت «تقسيط مشتريات» من غير خطة.
1. **زرار «مجموع أكتر من قسط»** في اللوحة. بيفتح صفحة الأقساط، والمالك بيكتب بإيده الخطط اللي الخصم ده بيدفعها (كام خطة وقسط كل واحدة). والمجموع لازم يساوي الخصم، أو الفرق يبان.
2. **أول قسط من مشترى جديد جوه الخصم المجمّع:** «+ مشترى جديد» من نفس الصفحة (اشتريت إيه، وقسمتها على كام).
3. **صورة الشاشة (اختياري، بعد دراسة التكلفة):** المالك يحط صورة من تطبيق تابي أو تمارا، والبرنامج يقرا الخطط منها ويمسح الصورة بعدها. **لو قراية الصورة كبيرة أو مكلفة، نفضل يدوي** (كلام المالك). ده محتاج دراسة الأول: قراية النص على الموبايل نفسه من غير سيرفر، ومكتبة جديدة لازم يتكتب سببها في ARCHITECTURE. ومش بنبنيه من غير موافقة.
4. **تمييز بالرسايل:** المالك بيقول إن رسايل أول دفعة غير رسايل الدفعات المجمّعة. **مهمة قراية:** راجع أشكال رسايل تابي/تمارا (رسايل البنك ورسايل تابي نفسها لو بتوصل) على النسخة المحلية بتاعة المالك، بنفس طريقة القياس اللي ما بتتحفظش في المستودع. لو فيه فرق ثابت، نستخدمه عشان نقترح «مجموع أكتر من قسط» لوحده.
- **محتاج رسم** لزرار «مجموع أكتر من قسط» وصفحة التوزيع لما التصميم يرجع. مرتبط بـpeople-6 و r051-bnpl2-f.

## ٩. شكوى المالك على 0.5.1 في الموبايل الحقيقي (2026-10-17) — أولوية أولى في الجولة الأولى
المالك: «حاليا مش بيجيلي اي اشعارات من البرنامج و ملاحظ انه مش بيقرأ رسايل البنك غير لما اخش البرنامج مش بيقرأ في الخلفية و لما اعمل تحديث في العمليات بيعلق و يتاخر جدا و البرنامج مش بيكون سلس + شكل علامة التحديث الدايرة اللي بتلف بدائية اوي و بتاعت الاندرويد العامة».
1. **مفيش إشعارات خالص، والقراية في الخلفية مش شغالة.** شخّص الأول: إذن الإشعارات على أندرويد 13+، مستقبل الرسايل، WorkManager والشغلة الدورية، تحسين البطارية، والتشغيل التلقائي عند الشركات المصنّعة. واتأكد إن «القراءة التلقائية» مقفولة ولا لأ (اتنصح يقفلها قبل 0.5.1). محتاج لوج من موبايله، والمالك هو اللي بيبعته.
2. **التحديث في العمليات بيعلّق ويتأخر على الموبايل الحقيقي**، رغم إن المحاكي عدّى. قيس على بيانات بحجم بيانات المالك، وشوف هل فيه شغل على خيط الواجهة أو قراية القاعدة كلها لسه في مسار التحديث (overview / recordNew / الأشخاص / PersonRulesEffect / quick links).
3. **البرنامج مش سلس بشكل عام:** قيس الإطارات في الشاشات الرئيسية على موبايل حقيقي.
4. **شكل علامة التحديث:** دايرة أندرويد العادية، ومحتاجة شكل خاص بالبرنامج. يتبعت لجلسة التصميم لما ترجع.
- **معلومة جديدة من المالك (2026-10-17):** كل حاجة مفتوحة: القراءة التلقائية، وإذن الإشعارات، والبطارية «غير مقيّد». وصفحة الرسايل مكتوب فيها إن آخر مرة البرنامج قرا فيها كانت من 24 ساعة. يعني **الشغلة في الخلفية مش بتشتغل خالص** على موبايله، مش بس متأخرة. اتطلب منه صورة من «سجل المشاكل» (إعدادات البرنامج ← «الأعطال والمشاكل»). أول خطوة يوم الخميس: اقرا جدولة WorkManager (الشغلة الدورية بتتسجل؟ بتتلغي مع التحديث؟ القيود؟)، ومستقبل الرسايل، وأي استثناء في MasroufyCycleWorker، وجرّب على المحاكي إن الشغلة بتشتغل بعد تحديث النسخة من 0.5 لـ0.5.1.
- **تشخيص بقراية الكود بس (2026-10-22 · من غير بنا ولا جهاز):**
  - **الإشعارات مش متوصلة أصلًا من الخلفية.** في BackgroundWiring.kt حوالي سطر 50-59 مكتوب candidates = null و engine = null، فالدورة بترجع من غير ما تبعت أي إشعار (RunBackgroundCycle.kt:83). ده جزء ما اتبناش، مش عطل في موبايل المالك.
  - **مفيش في الكود جملة «آخر قراية من كذا».** نقطة القراية بتتحرك من الخلفية ومن فتح البرنامج، بس ولا شاشة بتعرضها. فالـ«24 ساعة» اللي المالك شافها مش دليل لوحده.
  - **عطل اللغة العربي اتشال من الاحتمالات** عند المالك: لغة موبايله إنجليزي. عند أخوه (لغته عربي) البرنامج كان بيقفل، واتصلح في 0.5.1.1.
  - **الاحتمالات بالترتيب:**
    1. الخلفية بتشتغل بس شكلها ميت. البرنامج بيسجل لوحده بس الأشكال اللي المالك أكّدها قبل كده (SmsLearning.kt). الباقي بيستنى، ومفيش إشعار.
    2. العامل بيستسلم من غير صوت. الجلسة ما بتجهزش في 30 ثانية (AppContainer.kt:186-188)، فبيرجع «نجاح» ومفيش حاجة بتتكتب في سجل المشاكل.
    3. قيود الشركة المصنّعة. عند شاومي مثلًا «التشغيل التلقائي» مفتاح لوحده غير البطارية.
    4. أعطال بتتسجل فعلًا في سجل المشاكل.
  - **الخطوات بالترتيب، الأرخص الأول:**
    - (أ) سطر في سجل المشاكل لما العامل يستسلم.
    - (ب) «آخر مرة اتقرت في الخلفية» تظهر في شاشة الرسايل.
    - (ج) تجربة على المحاكي: رسالة تيجي والبرنامج مقفول.
    - (د) توصيل الإشعارات من الخلفية. ده الكبير: لازم ياخد نفس المرشحين بتوع الشاشة (GatherAllSpaceAlerts).
  - **مطلوب من المالك:** ماركة الموبايل، ومفتاح «التشغيل التلقائي» إن وجد. ولما يفتح البرنامج: الرسايل الجديدة بتظهر «مستنية تأكيد» ولا «اتسجلت لوحدها»؟ وصورة سجل المشاكل.
- **0.5.1.1 اترفعت على درايف (2026-10-22):** «مصروفي-0.5.1.1.apk» و«مصروفي-0.5.1.1-للكمبيوتر.zip».
  - sha256 للنسخة 1920795007a84153ece2bb2d4ae67de599a2f45888ce95141211820e79904bc4، وهو نفسه بعد الرفع ونفسه جوه الزيب.
  - التوقيع 670c193e…7631، ورقم النسخة 8.
  - **ما اتجربتش على أي جهاز بلغة عربي.** أخو المالك هو أول تجربة.

## ١٠. خطة توفير التوكنز (طلب المالك 2026-10-22: «خطة لعدم صرف التوكنز»)
1. **فرع تقيل واحد في نفس الوقت، واتنين بالكتير.** سبعة فروع مع بعض خلّصت الحد مرتين.
2. **الموديل على قد الشغلة.**
   - نقل تصميم جاهز أو تعديل ميكانيكي أو بحث في الكود: وكيل بموديل أرخص (Sonnet، وHaiku للبحث).
   - التشخيص والقرارات المالية والمراجعة: الموديل الكبير.
3. **كل وكيل ليه سقف أدوات (40-80) وملفات محددة بأرقام السطور.** ممنوع «اقرا المشروع كله». CODEMAP.md هو الخريطة، والوكيل ما يقراش ملف مرتين.
4. **الفحص على قد التعديل.**
   - أثناء الشغل: اختبارات الوحدة اللي اتغيرت بس (مثلًا :core:jvmTest).
   - البنا الكامل والـ8 مهام: قبل النسخة بس.
5. **مراجعة واحدة على الفرق (diff) لكل فرع.** من غير جولات مراجعة عدائية متكررة ولا ناقدين إضافيين، إلا في الحسابات المالية.
6. **ما نعيدش اللي اتعمل.**
   - نتايج المراجعة محفوظة (review-code/*.json و security-findings.json). نشتغل منها مش نراجع تاني.
   - commit بعد كل جزء، عشان وقوف الحد ما يضيعش شغل.
7. **المحاكي مرة واحدة لكل نسخة، بسكربت جاهز.** مش بعد كل تعديل.
8. **القياس:** بعد كل فرع نشوف اتصرف قد إيه (spend.mjs)، ولو فرع عدّى الحد اللي متوقع له نوقف ونسأل.
9. **الأسئلة للمالك تتجمع في كروت مرة واحدة**، بدل ما نوقف الشغل كذا مرة.
10. **ترتيب الجولة الأولى بالتكلفة:**
    - رخيص وسريع: §9 (أ)(ب) · دمج الفرعين الجاهزين · PRIVACY-4 · PRIVACY-7.
    - متوسط: money-1 · money-2 · sms-1 · people-1 · people-2 · arch-1.
    - تقيل، بعد ما الرخيص يخلص: مراجعة البلدين · توصيل الإشعارات · الفروع الواقفة.

## ١١. متابعة المشاكل على فايربيز (طلب المالك 2026-10-22)
- **اللي شغال (من الكود · CrashReports.kt):**
  - الوقعات بتتبعت لتقارير أعطال فايربيز (Crashlytics). ده اتأكد يوم 2026-10-20: ظهرت وقعات اللغة العربي من 7 مستخدمين.
  - أخطاء «سجل المشاكل» من نوع خطأ (ERROR) بتتبعت تقرير من غير وقعة، بس لو معاها كائن الخطأ.
- **اللي ناقص:**
  1. التحذيرات (WARN) بتفضل على الموبايل بس.
  2. في 4 أماكن الخطأ بيتسجل من غير كائن خطأ، منهم SMS_AUTO_RECORD_FAILED في عامل الخلفية، والأماكن دي ما بتوصلش فايربيز خالص.
  3. عامل الخلفية لما يستسلم بصمت ما بيسجلش أي حاجة (§9 أ).
  4. مفيش متابعة دورية: مفيش تنبيه بإيميل لما تظهر مشكلة جديدة، ومفيش حد بيبص على اللوحة.
- **الإصلاح المقترح (رخيص):**
  - كل خطأ وكل تحذير مهم يتبعت تقرير من غير وقعة، بالكود بس ومن غير أي بيانات.
  - كود جديد BACKGROUND_GAVE_UP.
  - المالك يفعّل تنبيهات الإيميل في لوحة فايربيز. ده إعداد في حسابه، يعمله بنفسه بخطوات مكتوبة.
  - مراجعة اللوحة مع كل نسخة جديدة.
- **التأكد من اللوحة نفسها:** محتاج دخول بحساب المالك، وأنا ممنوع أكتب كلمة سر. المالك يدخل بنفسه في المتصفح، وبعدها أقدر أقرا اللوحة.

## ١٢. ملفات دراهم (المالك 2026-10-22 · كروت اختيار)
المالك جاب تصدير من برنامج دراهم في `E:/work/masroufy/ملفات دراهم/` (6 ملفات CSV · 263 عملية صرف). **دي بيانات المالك الحقيقية: ممنوع أي اسم محل أو مبلغ منها يدخل المستودع.**
- **الأعمدة:** المعرّف · الحساب · اسم المحل بالعربي · اسم المحل بالإنجليزي · المبلغ · النوع · التصنيف بالعربي والإنجليزي · الفرعي · ملاحظة · التاريخ · الوقت · برّه الميزانية · برّه التقارير.

**اختيارات المالك («دلوقتي»):**
1. **قاعدة كبيرة للمحلات بتصنيفها.** كلامه: «نحفظ اسماء المحلات بتصنيفها خلاص بقا عندنا قاعدة كبيرة».
   - **الطريقة اللي ما بتحطش أسامي في المستودع:** «استيراد من دراهم» جوه البرنامج.
   - المالك يرفع الملف، والبرنامج يكتب «ذاكرة التاجر» في حسابه هو بس، تحت `users/{uid}`: الاسم المنضّف والاسم العربي والتصنيف المقابل في شجرتنا.
   - تصنيفات دراهم بتتطابق مع شجرتنا بجدول ثابت، والجدول ده فيه أسامي تصنيفات بس، من غير محلات.
   - المشاركة في `sharedMerchants` قرار تاني للمالك (❓).
2. **الوجبة حسب الساعة:** المطعم يتقترح له فطور أو غدا أو عشا من ساعة العملية، والمالك يقدر يغيّره. الحدود الافتراضية ❓ مقترحة: فطور قبل 11 · غدا 11–17 · عشا بعد 17.
3. **علامتين منفصلين** على العملية: «برّه الميزانية» و«برّه التقارير».
4. **تنضيف أسامي الكارت:** شيل «المدينة + البلد» من آخر الاسم، والكلمات المقطوعة تتعرض نضيفة. الاسم الأصلي يفضل محفوظ.
5. **تنبيه الخصم المكرر:** خصمين من نفس المحل، والمبلغ متقارب، في خلال دقيقة ⇒ يسأل «مكرر؟». ما بيتشالش لوحده.
- **الفرعيات الناقصة عندنا** (من دراهم): فطور/غدا/عشا/حلا · قهوة الصباح/عصير · صيدلية/صالون/مستشفيات وعيادات/مغسلة وكوي · كهربا/مية/غاز/إيجار/صيانة البيت · سينما/هواية/فعاليات · طيران. قارنها بالشجرة وضيف الناقص بس.
- **مكانها:** الجولة ١-ج، بعد رفع ١-أ و١-ب. الأشكال الجديدة (شاشة الاستيراد · العلامتين · سؤال المكرر) تتبعت لجلسة التصميم.

## ١٣. تصاميم اتوافق عليها من DESIGN-BRIEF-0.6 (2026-10-22)
- **بند 1: علامة التحميل «عملات بتترص».**
  - فرع التصميم 0d54d37b · 8f51846c · 5a7c2540.
  - بيتنقل في فرع `r06-1a2`.
- **بند 2: القراية في الخلفية.**
  - فرع التصميم 8e6d0f87 · 8c31e0e4، والملفات BankSms.dc.html و SmsAway.dc.html. المرجع OVERRIDES بتاع فرع التصميم «✅ القراية في الخلفية».
  - **(أ) كارت الحالة في «رسايل البنك»:** سطر بساعة: «آخر مرة قرا لوحده: من …». لو ما قراش خالص: «لسه ما قراش لوحده». لو الإذن اتسحب: يفضل يعرض وقت آخر قراية.
  - **(ب) حالة «فيه رسايل فاتته»:**
    - **إمتى تظهر:** وقت فتح البرنامج، لو فيه رسايل بنك في الصندوق أحدث من آخر قراية في الخلفية وما اتقرتش.
    - **الكارت:** أصفر، وفيه العدد محسوب.
    - **زرار «اقراهم دلوقتي»:** العملات جوه الزرار، وبعدها رسالة «اتقرت الـN رسايل واتسجلت».
    - **زرار «خلّيه يشتغل في الخلفية»:** بيفتح إعداد البطارية.
    - **بعد القراية باليد:** السطر يفضل على وقت آخر قراية في الخلفية.
    - ده يتبني في `r06-1a3` بعد `r06-1a2`، وقبل رفع 0.5.2.
  - **(ج) الإشعارات:** العملية اللي اتسجلت لوحدها ما لهاش إشعار.
    - الإشعار لتلات حالات بس، ومن غير أرقام ولا أسامي:
      - شكل جديد مستني تأكيدك.
      - إذن الرسايل اتسحب.
      - رسايل وصلت وما اتقرتش.
    - ده محتاج توصيل إشعارات الخلفية، فهو في الجولة ١-ج التقيلة.
- **ردود المالك على كروت دراهم (2026-10-22):**
  - **الوجبة:** علامة على العملية (فطور/غدا/عشا). التصنيف يفضل «مطاعم».
  - **تصنيفات جديدة:** «مغسلة وكوي» تحت المنزل · «هوايات» تحت ترفيه · «عصير» تحت مطاعم وقهوة.
  - **الحفظ:** «حسابي + القاعدة المشتركة». أسامي المحلات وتصنيفها بس، من غير مبالغ ولا تواريخ، بتتكتب في `sharedMerchants` بحالة «مش مؤكد». التأكيد من لوحة فايربيز (§25). ده كتابة على المشروع الحقيقي من موبايل المالك، مش من Claude.
- **بند 3: «الفلوس دي إيه؟» + «مستني استرداد»** (فرع التصميم 75a9b4c4 · c51e7861 · 70516f66).
  - **تعديلين من المالك بيغيّروا اللي اتبنى في `r06-1b2`:**
    1. القاعدة بقت **اختيارية**: مربع «خليها قاعدة للمرات الجاية» بيبدأ فاضي.
    2. «حاجة تانية» = اسم + إيموجي، وبتفضل اختيار ثابت للحساب اللي بيحوّل ده بس.
  - **وكمان:**
    - استرداد مصاريف بقى يتربط بأكتر من مشترى، وتحت القايمة سطر المجموع.
    - زرار «مصروف شغل؟ هيترد لي» على المشترى.
  - بيتنقل في `r06-1b3`.
- **بند 4: خصم تابي/تمارا المجمّع** (فرع التصميم 64c428e0 · e880ae7e · 448ad147).
  - اقتراح «غالبًا: أ + ب = المبلغ بالظبط».
  - لوحة «الخصم ده فيه كام قسط؟»:
    - قيمة القسط تتعدّل بإيدك.
    - «+ مشترى جديد».
    - سطر المجموع.
    - زرار «وزّع» شغال بس لو المجموع مظبوط.
  - «الدفعة دي رقم كام؟»: الدفعات اللي قبلها بتتعلّم «اتدفعت قبل كده» من غير ما تتحسب مصروف.
  - بيتنقل في `r06-1b4`.
- **قاعدة كتابة من المالك (2026-10-22، فرع التصميم fb6d9629):** «احنا بنرغي كتير في البرنامج وانا عايز اسلوب يكون اقصر».
  - العلامة أو الحالة كلمة أو كلمتين.
  - الشرح سطر واحد، وبس لو لازم.
  - بتتطبق على كل نص جديد، والقديم يتقصّر لما الشاشة تتلمس.
  - مساحة مصر بالمصري، ومساحة السعودية بعربي بسيط.
- **بند 5 (علامة البلد):** المالك اختار علم فوق لوجو الحاجة. **ما يتبنيش لسه.** المالك هيرسمها بنفسه.
- **بند 5: علامة البلد، اتوافق عليه** (فرع التصميم 1c4eba9f، الملف CountryMark.dc.html، المرجع OVERRIDES بتاع فرع التصميم «✅ علامة البلد»).
  - **الشكل:**
    - علم مدوّر صغير 13 بكسل، بإطار أبيض 1.5، في ركن أيقونة الحاجة من فوق ناحية الاسم.
    - مرسوم رسم متجه، مش إيموجي.
    - علم مصر: أحمر/أبيض/أسود ونقطة دهبي.
    - علم السعودية: أخضر عليه سطرين أبيض وسيف.
  - **بيظهر لما البلدين شغالين بس**، على: المحافظ والبطاقات · الديون · مصادر الدخل · العمليات.
  - مكانه الجولة ١-ج، مع مراجعة منطق البلدين.
- **بند 6 + «المحجوز للخطط»، اتوافق عليهم** (فرع التصميم 6d8c9d34 · 03c1fa2a + كومت الموافقة · LinkPay.dc.html · HeroReserve.dc.html · Main.dc.html · OVERRIDES بتاع فرع التصميم «✅ اربطها بعملية أو كاش» و«✅ المحجوز للخطط»).
  - **لوحة LinkPay واحدة على 4 أماكن:** سداد دين · دفع زيادة · زكاة كاش · إيداع ادخار.
    - «عملية متسجلة»: مقترحة بـ«غالبًا دي».
    - «كاش»: عملية حقيقية في محفظة الكاش، ومعاها «إمتى؟».
    - مفيش «من غير عملية».
  - **المحجوز للخطط:**
    - إيداع الادخار الافتراضي «حجز من رصيدي»: من غير عملية.
    - سطر في الكارت الرئيسي: «تقدر تصرف X · محجوز Y للخطط».
    - «تقدر تصرف النهارده» يتحسب من المتاح.
    - مفتاحين: إظهار السطر، والتنبيه لو صرفت من المحجوز.
  - **ده منطق فلوس تقيل، مكانه الجولة ١-ج بعد رفع 0.5.2**، في فرع `r06-1f`.
- **الجاي من التصميم:** المظهر الليلي وزرار إخفاء الأرقام (المالك: «ضروري»).
- **القاعدة المشتركة للمحلات (سؤال المالك 2026-10-22):** في نسخة كوتلن التوصيل لسه مش متعمل. `SharedMerchants.sync` و`contribute` موجودين في الكود، بس محدش بينادي عليهم (RulesScreen.kt:56).
  - **رد المالك:**
    - التوصيل في **الجولة التقيلة**.
    - المحل الجديد يظهر عند الناس **«علطول كاقتراح»** في خلال دقايق، وصاحب الحساب يقدر يغيّره.
    - التأكيد بيفضل من لوحة فايربيز (§25).
  - **التنفيذ:**
    - `contribute` لما المستخدم يأكد تصنيف شراء.
    - `sync` بالتغييرات من آخر مؤشر (`changes since cursor`) لما البرنامج يتفتح، وفي كل دورة خلفية.
    - المؤكدين مرة في اليوم، عشان حد القراية المجانية.
    - أسامي الناس وأطراف التحويل ممنوع تتبعت (§80).
- **المظهر الليلي + زرار إخفاء الأرقام، اتوافق عليهم** (فرع التصميم c0b22309 + الموافقة · NightPrivacy.dc.html · OVERRIDES بتاع فرع التصميم «✅ المظهر الليلي + زرار إخفاء الأرقام»):
  - فاتح/غامق/زي الجوال، بألوان الغامق المحددة، وانتقال 300 مللي.
  - زرار عين يخفي كل المبالغ «••••».
  - مفتاح «اخفي الأرقام لما أفتح البرنامج».
  - **ده واجهة بس، من غير منطق فلوس، فممكن يدخل 0.5.2** لو خلص بدري. لو لأ، يروح للجولة التقيلة.
- **بند 7 + «اختر الشهر»، اتوافق عليهم.**
  - **المرجع:** فرع التصميم 4d19ae4c · d616dfd3 · a17624ee + كومت الموافقة. الملفات: ReviewEntry.dc.html · TransferParty.dc.html · PeriodPicker.dc.html. القرارات في OVERRIDES بتاع فرع التصميم «✅ بند 7» و«✅ اختر الشهر».
  - **(1) «محتاجة مراجعة»:** كارت واحد في العمليات.
    - العدد كله فوق، وتحته 3 صفوف بعددها: رسايل مستنية تأكيد · نوعها مش أكيد · مين ده؟
    - سطر أصفر: «مش محسوب صرف لحد ما ترد».
  - **(2) «شركة أو جهة»:** اختيارين وبعدهم «حاجة تانية…» بتفتح قايمة كاملة. كل اختيار بيشغّل منطقه الموجود.
    - **الداخل:** استرداد · تعويض. القايمة: مرتب · مكافأة أو بدل · سلفة · دين رجعلي · جمعية · إيجار · أرباح · دخل تاني.
    - **الخارج:** فاتورة · مشتريات. القايمة: رسوم · قسط تمويل · اشتراك · تقسيط · زكاة · إيداع ادخار · جمعية · سداد دين · إيجار · تبرع · مصروف تاني.
    - **أنواع جديدة بمنطق:**
      - إيجار داخل ⇒ مصدر دخل «إيجار …»، والشهر يتعلّم «تم الدفع».
      - إيجار خارج ⇒ إيجار المستحقات يتعلّم «تم الدفع».
      - أرباح ⇒ عائد على استثمار تختاره.
      - تبرع ⇒ يسأل «زكاة ولا صدقة؟». الزكاة تتخصم من زكاة السنة، والصدقة مصروف.
    - **مرفوض:** تأمين وضرايب.
  - **(3) «اختر الشهر»:** تحت كل شهر بدأ سطرين: «دخل …» أخضر و«مصروف …» وردي.
    - المصروف هنا هو المصروف الحقيقي.
    - الشهر الحالي لحد النهارده.
    - الشهور اللي ما بدأتش من غير سطور.
  - **المكان:** الجولة التقيلة.
- **«الإيجار كدخل»: الرسم اتلغى** بقرار المالك (2026-10-23، فرع التصميم 7259ad7d).
  - كلامه: «اكتشفت ان في جزء كبير محتاج تصميم لوجيك له الأول وربطه صح لاني تهت جدا في استخدام البرنامج و معرفتش اصلا اوصل لصفحة الايجار دي».
  - **ما تتنقلش لوحة RentIncome.**
  - منطق «إيجار» جوه «حاجة تانية…» (بند 7) لسه عليه موافقة.
  - **المشكلة الأكبر عند المالك:** التنقّل جوه البرنامج، وربط الحاجات ببعض.
  - ❓ مستني المالك يحدد نبدأ بإيه، وده ممكن يغيّر ترتيب الجولة التقيلة.

## ١٥. قايمة المالك وهو بيجرب 0.5.2 (2026-10-23)
> المالك: «بدأت احس كتير اني تايه في البرنامج وفي حجات مش كاملة مش بتتوصل ببعض صح … اكتشافها بسيط بردو نسبة للحقيقة».
1. **تعديل رحلة البداية:**
   - إضافة أكتر من بنك في رحلة البداية.
   - طلب إذن قراية الرسايل والإشعارات في البداية.
2. **مراجعة أمان (سيكيورتي ريفيو).**
3. **شكل اختيار التاريخ يتغير.**
4. **إظهار أو إخفاء حساباتي في الرئيسية.**
5. **تسجيل الديون والتحصيل رخم ومشتّت.**
6. **التحويل بين حساباتي فيه حاجة غلط.**
7. **الأصل: مش عارف أبيعه أو أحذفه.**
8. **الرئيسية فاضية** لما مفيش عمليات.
9. **لما أروح بلد تانية** يجيلي رسايل البلد دي بس.
10. **مصادر الدخل في البلد التانية** ممكن تظهر بالعلم (علامة البلد — §13 بند 5، شغالة في `r06-countries`).
11. **مصادر الدخل محتاجة مراجعة.**
12. **البنوك اللي بتبعت رسايل:** ليه آخرها 10 بس؟
- **الخطوة:** مراجعة ربط شاملة، قراية بس، لكل حاجة في البرنامج. لكل حاجة: بتتعمل منين، وبتتشاف فين، وبتتعدل وتتمسح إزاي، وبتتربط بإيه. الغرض نلاقي الطرق المقفولة. وفيها تشخيص كل بند من الـ12. النتيجة تتعرض على المالك كصفحة مرسومة، وتتبعت لجلسة التصميم مع الخريطة.
- **قرارات المالك على الخريطة (2026-10-23 · فرع التصميم fc0682a6 · AppMap.dc.html و LinkMap.dc.html · آخر OVERRIDES بتاع فرع التصميم):**
  1. **خطط الادخار نوعين:**
     - الكبيرة في الاستثمار.
     - الشهرية جوه الميزانيات، لحجز تمن حاجة أو تقسيط قصير لحد سنة.
     - ⏸ منطق الشهرية ما اتصممش، **ما يتبنيش**.
  2. **مصادر الدخل:**
     - بتتفتح من سطر «دخل الشهر» في الرئيسية.
     - مكانها الأساسي جوه الملف («ملفي / حسابك»).
     - الإيجار نوع من أنواعها.
     - ⏸ المالك هيعيد تصميم صفحة الإعدادات كلها، **استنى**.
  3. **المحافظ** و**التصنيفات والقواعد** و**الاستيراد والنسخ الاحتياطي** بيطلعوا لأول مستوى في «المزيد»، كل واحد بضغطة واحدة. ✅ **ينفع يتبني دلوقتي.**
  4. **الزكاة بتتنقل لـ«المستحقات»** وبتخرج من الاستثمار. ✅ **ينفع يتبني دلوقتي.**
  - ❓ لسه مفتوح: التحويلات · المشاريع · السؤال بيروح لمين.

## ١٦. مستودع الملاحظات + بيانات الاستخدام من غير بيانات شخصية (المالك 2026-10-23)

**المستودع الخاص:** `mohussamarch/masroufy-feedback`، **خاص**، واتعمل 2026-10-23 بطلب المالك («اعمل انت المستودع وهنقسمه من جوا»). كل اللي بييجي من البرنامج بيتحط فيه كمشكلة (issue) عليها علامة:
- **النوع:** سؤال · اقتراح · مشكلة (اسمها في السعودية «شكوى») · تنبيه تصنيف · تقرير استخدام · عطل تلقائي.
- **البلد:** مصر · السعودية.

**الطريق، من غير سيرفر ومن غير وظائف سحابية (باقة سبارك):**
1. البرنامج بيكتب مستند في مجموعة كتابة بس، `inbox/{auto}`، بشروط:
   - مستخدم داخل؛
   - الحقول متحددة بالاسم والطول؛
   - لا قراية ولا تعديل من البرنامج؛
   - حد يومي على الجهاز.
2. الشروط دي تغيير في القواعد، فمحتاجة اختبار على المحاكي بمستخدمين اتنين، ونشرها بموافقة المالك.
3. فيه أكشن مجدول في المستودع الخاص بيشتغل كل 3 ساعات (تقريبًا 240 دقيقة في الشهر من 2,000 مجانية):
   - بيقرا `inbox` بحساب خدمة (service account)؛
   - بيفتح مشكلة لكل مستند، بالعلامة المناسبة؛
   - بيمسح المستند.
4. **المفتاح:** المالك هو اللي بيعمل مفتاح حساب الخدمة من لوحة فايربيز، ويحطه في أسرار المستودع الخاص (Secrets). مفتاح فايربيز ما يتحطش في المستودع العام.

**تنبيه التصنيف (§113):**
- لما **3 أشخاص أو أكتر** يصنّفوا نفس المحل بعكس قاعدة جاهزة، بتتفتح مشكلة «تنبيه تصنيف» فيها: اسم المحل، القاعدة، والتصنيف اللي اختاروه، والعدد.
- مفيش فيها مين اللي اختار.
- الأكشن نفسه بيحسبها من مجموعة `sharedMerchants` (مساهمات مش مؤكدة)، والبرنامج مش محتاج يتغيّر علشانها.

**بيانات الاستخدام: نجمع إيه؟** القاعدة: أعداد ونِسب وفئات بس. ممنوع: مبالغ · أرقام حسابات · نص الرسايل · أسامي ناس · أسامي محلات مربوطة بمستخدم · تواريخ عمليات · مكان.

| المجموعة | البند | ليه |
|---|---|---|
| الصحة | نسبة الجلسات من غير وقعة · عدد كل كود في سجل المشاكل · مرات «القراية في الخلفية استسلمت» · تجميد أكتر من 700 م.ث · وقت فتح البرنامج | نلاقي العطل قبل الشكوى |
| الرسايل | نسبة قراية رسايل كل بنك (رقم المرسل عام مش شخصي) · عدد «شكل جديد مستني» لكل بنك · وقت من وصول الرسالة لتسجيلها (فئات) | نعرف أنهي بنك غيّر شكل رسايله |
| التصنيف | نسبة العمليات اللي اتصنفت لوحدها · نسبة اللي المستخدم غيّر تصنيفها · لكل قاعدة جاهزة: كام مرة اتغيرت | نصلّح القواعد الغلط |
| البداية | كل خطوة في رحلة البداية: كام واحد وصلها وكام كمّل · وافق على إذن الرسايل/الإشعارات ولا لأ | نعرف الناس بتقف فين |
| الاستخدام | الشاشات اللي اتفتحت (عدد في الأسبوع) · المزايا المفعّلة (أقساط · تابي/تمارا · جمعيات · زكاة · ادخار) · عدد الحسابات (فئات: 1 · 2-3 · 4+) · بلد ولا بلدين · الوضع الداكن | نعرف نكبّر إيه ونسيب إيه |
| الاحتكاك | فتح «+» ورجع من غير ما يسجّل · بحث في الأسئلة من غير نتيجة (العدد بس، مش النص) · «مالقيتش إجابتك» | نلاقي الحتة اللي بتوه الناس |
| الاستمرار | كام يوم فتح البرنامج في الأسبوع (فئات) · رقم النسخة · نوع الجوال وإصدار أندرويد | نعرف الناس بتكمّل ولا لأ |

**إزاي بيتبعت:** تقرير واحد في الأسبوع من كل جهاز، أرقام بس، على `inbox` بنوع «usage». الأكشن بيجمّع التقارير كلها في **مشكلة واحدة في الأسبوع** («تقرير استخدام»)، ومن غير أي معرّف للمستخدم.

❓ **قرارات مستنية المالك:**
1. تقريرنا الخاص ده، ولا مكتبة تحليلات فايربيز الجاهزة (بتجمع حاجات أكتر لوحدها ومحتاجة نقفل معرّف الإعلانات)؟
2. الإرسال شغال لوحده ويقدر المستخدم يقفله، ولا مقفول لحد ما المستخدم يوافق؟
3. لازم سطر في سياسة الخصوصية وصفحة أمان البيانات في جوجل بلاي.
