package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.ui.screens.imports.ImportsDeps

/**
 * «الاستيراد» — `ReviewSmsInbox` · `ImportStatement` · `ReadPdfStatement` · `RevertImportBatch` … (الصندوق وقارئ الـPDF من الجهاز: ضيفهم في `DeviceEnv`).
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [SpaceRepositories] + [DeviceEnv] بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class ImportsGraph(space: Space, r: SpaceRepositories, env: DeviceEnv) : ImportsDeps
