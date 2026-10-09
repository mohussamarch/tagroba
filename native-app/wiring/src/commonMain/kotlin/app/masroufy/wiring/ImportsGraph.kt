package app.masroufy.wiring

import app.masroufy.ui.screens.imports.ImportsDeps

/**
 * «الاستيراد» — `ReviewSmsInbox` · `ImportStatement` (`importDeps(c.repos, c.env)`) · `ReadPdfStatement` · `RevertImportBatch` … (الصندوق وقارئ
 * الـPDF من الجهاز: ضيفهم في `DeviceEnv`).
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] (`c.repos` · `c.env` · `c.shell`) بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class ImportsGraph(c: AreaContext) : ImportsDeps
