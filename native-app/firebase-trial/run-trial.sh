#!/usr/bin/env bash
# تشغيل تجربة مكتبة فايربيز على محاكي أندرويد (KOTLIN_PLAN §4-أ4) — HANDOVER فيه الخطوات كاملة.
# قبله: Firestore Emulator شغال من `seed/` وفيه البيانات الوهمية (`node seed/seed.mjs`)، ومحاكي أندرويد **مفتوح القفل**.
# بيشغّل النسختين بالتبادل [ROUNDS] مرة (كل مرة تشغيل جديد للتطبيق) وبيكتب سطور القياس في الملف [OUT].
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$HOME/Documents/Codex/android-build-tools/sdk/platform-tools/adb.exe}"
HERE="$(cd "$(dirname "$0")" && pwd)"
# adb على ويندوز محتاج مسار ويندوز (والتحويل التلقائي مقفول فوق عشان `pkg/Activity`)
command -v cygpath > /dev/null && HERE="$(cygpath -m "$HERE")"
OUT="${1:-trial-results.txt}"
ROUNDS="${ROUNDS:-3}"

for fl in direct gitlive; do
  "$ADB" install -r "$HERE/app/build/outputs/apk/$fl/release/app-$fl-release.apk" > /dev/null
done
: > "$OUT"
for run in $(seq 1 "$ROUNDS"); do
  for fl in direct gitlive; do
    pkg="app.masroufy.trial.$fl"
    "$ADB" shell am force-stop "$pkg"
    "$ADB" logcat -c
    "$ADB" shell am start -n "$pkg/app.masroufy.trial.TrialActivity" > /dev/null
    for _ in $(seq 1 120); do
      if "$ADB" logcat -d -s MASROUFY_TRIAL | grep -q 'TRIAL|done\|TRIAL|error'; then break; fi
      sleep 1
    done
    "$ADB" logcat -d -s MASROUFY_TRIAL | grep -o 'TRIAL|.*' | sed "s/^/run$run|/" >> "$OUT"
    "$ADB" shell am force-stop "$pkg"
  done
done
echo "lines: $(wc -l < "$OUT")"
