#!/usr/bin/env bash
# رسايل بنك **وهمية** لمحاكي أندرويد عشان `SmsOnDeviceTest` (native-app/device) — زي ما الشبكة بتبعتها للموبايل.
# 1) قبل الاختبار: رسايل «القراية بالطلب» من 5550001.  2) لما الاختبار يكتب READY: رسايل «الصندوق» من 5550002.
# التشغيل (Git Bash): ADB=<مسار adb> bash scripts/device/sendTestSms.sh   ثم في نفس الوقت: ./gradlew :device:connectedDebugAndroidTest
set -u
ADB="${ADB:-adb}"
"$ADB" emu sms send 5550001 "TEST-DEMAND purchase EGP 41.25 at SAMPLE STORE"
"$ADB" emu sms send 5550001 "OTP 123456 for purchase EGP 10.00"
"$ADB" logcat -c
for _ in $(seq 1 300); do
  if "$ADB" logcat -d -s MasroufySms | grep -q READY; then
    "$ADB" emu sms send 5550002 "TEST-INBOX transfer EGP 300.00 from account 9876543210"
    "$ADB" emu sms send 5550002 "OTP 654321 for transfer EGP 20.00"
    echo "sent"
    exit 0
  fi
  sleep 1
done
echo "READY ما ظهرش" >&2
exit 1
