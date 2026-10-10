#!/bin/bash
# cold start: force-stop, clear log, start (with perf_uid), wait for home span; dump MPERF log to $1
. "$(dirname "$0")/lib.sh"
OUT="$1"
adb shell am force-stop $PKG
adb logcat -c
adb shell am start -W -n $PKG/app.masroufy.android.MainActivity --es perf_uid perf-user | grep -E "TotalTime"
wait_more "span screen:home" 0 180
sleep 4
adb logcat -d -v time -s MPERF:I > "$OUT"
grep -E "app create|session ready|span screen|synced group=transactions|sign-in" "$OUT"
echo "fs reads: $(grep -c 'src=fs' "$OUT")  mirror reads: $(grep -c 'src=mirror' "$OUT")"
