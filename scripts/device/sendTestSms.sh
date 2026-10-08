#!/usr/bin/env bash
# رسايل بنك **وهمية** لمحاكي أندرويد (native-app/device) — زي ما الشبكة بتبعتها للموبايل.
# 1) قبل الاختبار: رسايل «القراية بالطلب» من 5550001.
# 2) لما `SmsOnDeviceTest` يكتب READY (تحت MasroufySms): رسايل «الصندوق» من 5550002.
# 3) لما `SmsAutoRecordOnDeviceTest` يكتب READY (تحت MasroufySmsAuto): من 5550003 (OVERRIDES §72) — شراء **بشكل معروف** (عنوان البنك
#    المركزي «PoS Purchase»، سطور بـ\n — المحاكي بيحوّل \n لسطر جديد) + شراء مفهوم **من كلمات عامة بس** (الجولة الرابعة: بيستنى
#    تأكيد المالك) + رسالة اتجاهها مش واضح. نفس النصوص متجربة على الكمبيوتر في `SmsDeviceScriptTest` (device/jvmTest).
# المراحل اللي هتتستنى: STAGES (الافتراضي "inbox auto") — لو شغّلت كلاس واحد بس، خلّيها مرحلته بس.
# التشغيل (Git Bash): ADB=<مسار adb> bash scripts/device/sendTestSms.sh   ثم في نفس الوقت: ./gradlew :device:connectedDebugAndroidTest
set -u
ADB="${ADB:-adb}"
STAGES="${STAGES:-inbox auto}"
"$ADB" emu sms send 5550001 "TEST-DEMAND purchase EGP 41.25 at SAMPLE STORE"
"$ADB" emu sms send 5550001 "OTP 123456 for purchase EGP 10.00"
"$ADB" logcat -c
pending="$STAGES"
for _ in $(seq 1 600); do
  if [[ " $pending " == *" inbox "* ]] && "$ADB" logcat -d -s MasroufySms | grep -q READY; then
    "$ADB" emu sms send 5550002 "TEST-INBOX transfer EGP 300.00 from account 9876543210"
    "$ADB" emu sms send 5550002 "OTP 654321 for transfer EGP 20.00"
    echo "sent inbox"
    pending="${pending/inbox/}"
  fi
  if [[ " $pending " == *" auto "* ]] && "$ADB" logcat -d -s MasroufySmsAuto | grep -q READY; then
    today="$(date +%Y-%m-%d)"
    "$ADB" emu sms send 5550003 "PoS Purchase\nAmount: SAR 25.00\nAt: TEST CAFE\nOn: $today"
    "$ADB" emu sms send 5550003 "TEST-FALLBACK Purchase SAR 12.00 at TEST SHOP on $today"
    "$ADB" emu sms send 5550003 "TEST-WAIT transfer SAR 10.00 $today"
    echo "sent auto"
    pending="${pending/auto/}"
  fi
  if [[ -z "${pending// /}" ]]; then exit 0; fi
  sleep 1
done
echo "READY ما ظهرش للمراحل: $pending" >&2
exit 1
