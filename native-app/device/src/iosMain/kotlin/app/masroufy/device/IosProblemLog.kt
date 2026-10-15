package app.masroufy.device

import app.masroufy.memory.MemoryProblemLog
import app.masroufy.port.ProblemLogPort

/**
 * «سجل المشاكل» على الآيفون — **مؤقت في الذاكرة** (بيتمسح مع قفل البرنامج): الملف على الآيفون لسه ما اتعملش، وتقارير الأعطال
 * (Crashlytics) لأندرويد بس دلوقتي (OVERRIDES §90). ⚠️ اتبنى بس — ما اتجربش (محتاج ماك).
 */
class IosProblemLog : ProblemLogPort by MemoryProblemLog()
