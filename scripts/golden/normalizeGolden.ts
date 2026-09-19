import { latinizeDigits, normalizeCompact, normalizedContains, normalizeText } from '../../src/domain/normalize'
import { record } from './goldenKit'

/** حالات `normalize.ts` — أسماء محلات وهمية بأشكال مختلفة. */
export function normalizeGolden() {
  const texts = [
    '', '   ', 'عبد الفتاح', 'عبدالفتاح', 'عبد الفتّاح', 'أحمد', 'إبراهيم', 'آمنة', 'ٱلبيت', 'مستشفى', 'مدرسة', 'مؤسسة', 'مسائل',
    'مـــحـــل', 'مَحَلٌّ', 'ﺔﻈﺣﻼﻣ', 'ﷲ', 'ﻻ', 'TEST MART', 'test  mart', 'Test-Mart #12', 'café', 'straße', 'İstanbul',
    'شراء ٢٥ ريال', '۱۲۳ متجر', 'A.B.C', '  leading and trailing  ', 'emoji 🙂 shop', 'x²', '½ price', '①②',
    'ALRAJHI‏BANK', 'mixed عربي and English', 'قهوة/كافيه', 'Ｆｕｌｌ ｗｉｄｔｈ', 'ـــ', '٫٬', 'ﻣﻄﻌﻢ ﺍﻟﺒﻴﺖ',
  ]
  const pairs: [string, string][] = [
    ['عبد الفتاح للتجارة', 'عبدالفتاح'], ['TEST MART 1265', 'test mart'], ['مطعم البيت', 'البيت'], ['مطعم البيت', ''],
    ['HUNGER STATION', 'hungerstation'], ['كافيه ريف', 'كافية'], ['ABC', 'abcd'], ['متجر ١٢', '12'], ['a-b-c', 'a b'],
  ]
  return {
    latinizeDigits: texts.map((t) => record(t, () => latinizeDigits(t))),
    normalizeText: texts.map((t) => record(t, () => normalizeText(t))),
    normalizeCompact: texts.map((t) => record(t, () => normalizeCompact(t))),
    normalizedContains: pairs.map(([h, n]) => record({ haystack: h, needle: n }, () => normalizedContains(h, n))),
  }
}
