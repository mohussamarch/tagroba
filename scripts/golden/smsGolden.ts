import { parseBankSms, redactSms } from '../../src/infrastructure/import/bankSmsParser'
import { record, seeded } from './goldenKit'

/** محلل رسايل البنك — أشكال رسايل الراجحي الحقيقية **بأرقام وأسماء وهمية** + تركيبات عشوائية. */
export function smsGolden() {
  const rnd = seeded(1818)
  const RLM = String.fromCharCode(0x200f), LRM = String.fromCharCode(0x200e), NBSP = String.fromCharCode(0xa0), FSI = String.fromCharCode(0x2068), PDI = String.fromCharCode(0x2069)
  const crafted = [
    'شراء PoS\nعبر1111;مدى-سامسونج باي\nبـSR 24\nلـTEST STORE\n26/9/18 09:35',
    'شراء PoS\nعبر1111;مدى\nبـSR 12500\nلـTEST STORE\n26/9/18 09:35',
    'شراء انترنت بـSR 35.62\nعبر1111;مدى\nمن2222\nلـTEST INSURANCE\n18:57 16/9/26',
    'ننصح بعدم مشاركة الرمز لحمايتك من الاحتيال\nالرمز:111111\nبطاقة:*1111\nمبلغ:SAR 35.62\nلدى:TEST INSURANCE CO\nفي:18:56 26/09/16',
    'شراء انترنت\nبطاقة:1111;مدى\nمبلغ:USD 5.30\nلدى:TEST AI\nفي:26/9/18 10:15',
    'دفع\nعبر:1111;مدى\nمن2222\nبـSR 50\nلـTEST WALLET\n16:47 17/9/26',
    'حوالة داخلية صادرة\nمن:1111\nإلى:TEST PERSON\nبـSR 500\n26/9/18 09:35',
    'حوالة محلية واردة\nمن:TEST PERSON\nبـSR 1000\nإلى:1111\n26/9/18',
    'شراء عبر نقاط البيع\nبمبلغ: 29.99 SAR\nلدى: TEST FOOD\nفي: 2026-09-09\nبطاقة: 1234567812345678\nالرصيد: 1500 SAR',
    'حوالة واردة\nبمبلغ: ١٢٣٫٤٥ ريال\nفي: ٢٠٢٦/٠٩/٠٩',
    'حوالة واردة\nمن حساب SA0380000000608010167519\nبـSR 100\n26/9/18 09:35',
    `شراء\nبـ${RLM}SR 24.50${LRM}\nلـ${FSI}TEST SHOP${PDI}\n26/9/18`, `شراء بمبلغ${NBSP}25${NBSP}ر.س لدى TEST في 2026-09-10`,
    'Purchase of SAR 12.50 at TEST SHOP on 18/09/26', 'Purchase SAR 1,234.56 merchant: BIG STORE balance: 99 SAR 2026-09-10',
    'salary deposit 5000 SAR 2026-09-01', 'Refund SAR 20 from TEST 2026-09-02', 'outgoing transfer 300 SR to: 12345678 2026-09-03',
    'شراء 25 ر. س لدى متجر تجريبي في 18/09/2026', 'شراء 10 ريال و 20 ريال لدى X في 2026-09-10', 'شراء 10 ريال رسوم 2 ريال لدى X 2026-09-10',
    'شراء بمبلغ 25 لدى X في 2026-09-10', 'شراء لدى X في 2026-09-10', 'شراء 0 SAR لدى X 2026-09-10', 'شراء 25 SAR عند محل تجريبي بتاريخ 2026-09-10',
    'عرض خاص: شراء 25 SAR', 'سيتم خصم 25 SAR', 'شراء مرفوض 25 SAR 2026-09-10', 'عملية لم تتم 25 SAR', 'OTP 123456 purchase 25 SAR',
    'رمز التحقق 1234', 'كلمة المرور', 'تحويل 25 SAR 2026-09-10', 'شراء واسترداد 25 SAR', 'مبلغ 25 SAR', 'شراء 25 EGP 2026-09-10',
    'شراء 25 SAR في 2026-02-30', 'شراء 25 SAR في 31/02/2026', 'شراء 25 SAR 1/2/3', 'شراء 25 SAR 26-9-18', 'شراء 25 SAR 18-9-26 و 19-9-26',
    'شراء 25 SAR\nلـ 1234\nمن: TEST PAYER', 'شراء 25 SAR\nلـ:  ***1234  ', 'شراء 25SR لدى:TEST 2026-09-10', 'شراءSR25 2026-09-10', 'PURCHASE sar 5 2026-09-10',
    'شراء 25 SAR fees 2 SAR 2026-09-10', 'شراء 25 SAR fee 2 SAR 2026-09-10', 'خصم 25 SAR المتاح 1000 SAR 2026-09-10', 'شراء 25 SAR الحد 5000 SAR 2026-09-10',
    'شراء 1,000 SAR بطاقة 4000-1234-5678-9012 2026-09-10', 'شراء 1٬000٫50 ريال 2026-09-10', 'شراء 1.234 SAR 2026-09-10', 'Purchase at A-1 at B on 2026-09-10 25 SAR',
  ]
  const RECEIVED = ['2026-09-18T10:00:00Z', '2026-09-18T23:30:00.123Z', '2026-09-18T01:00:00+03:00', '2026-11-20T00:00:00Z', 'not a date', '2026-09-19']
  const HEAD = ['شراء', 'شراء PoS', 'شراء انترنت', 'دفع', 'سحب', 'حوالة داخلية صادرة', 'حوالة محلية واردة', 'إيداع', 'راتب', 'مدفوعات', 'Purchase', 'رسالة']
  const AMOUNT = ['بـSR 24', 'بـSR 1,250.75', 'مبلغ:SAR 35.62', 'بمبلغ 99 ريال', '12.5 ر.س', 'SR 7', 'بـ 40', 'مبلغ:USD 3', '15 SAR و 16 SAR']
  const MERCHANT = ['لـTEST STORE', 'لدى:TEST CAFE', 'عند محل تجريبي في', 'من:TEST PERSON', 'إلى: 9999', 'at TEST MART on', '']
  const DATE = ['26/9/18 09:35', '18:57 16/9/26', '17/9/26', '2026-09-17', '17/09/2026', 'في:26/09/16', '', '26/9/18 و 26/9/17']
  const EXTRA = ['عبر1111;مدى', 'الرصيد: 1500 SAR', 'رسوم: 2 SAR', 'بطاقة: 1234567812345678', 'SA0380000000608010167519', '']
  const random = Array.from({ length: 160 }, () =>
    [rnd.pick(HEAD), rnd.pick(EXTRA), rnd.pick(AMOUNT), rnd.pick(MERCHANT), rnd.pick(DATE), rnd.pick(EXTRA)].filter((l) => l !== '' || rnd.next() < 0.2).join(rnd.next() < 0.8 ? '\n' : ' '))
  const bodies = [...crafted, ...random]

  return {
    parseBankSms: bodies.flatMap((body, i) => [RECEIVED[0]!, rnd.pick(RECEIVED)].map((receivedAt) => {
      const message = { sender: rnd.pick(['AlRajhiBank', 'TESTBANK']), receivedAt, body }
      return record(message, () => parseBankSms(message, i + 1))
    })),
    redactSms: [...bodies.slice(0, 80), '1234567890', '١٢٣٤٥٦٧٨٩٠', 'حساب 12345 و 1234', 'IBAN sa03 8000 0000 6080 1016 7519', '4000 1234 5678 9012 3456', 'مبلغ 15000.50 SAR حساب 1234567890'].map((t) => record(t, () => redactSms(t))),
  }
}
