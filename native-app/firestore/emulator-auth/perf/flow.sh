#!/bin/bash
# full measured flow: cold start -> Home, Operations, detail, back+Home, People, + sheet, assistant. Log to $1
. "$(dirname "$0")/lib.sh"
OUT="$1"
adb shell am force-stop $PKG
adb logcat -c
adb shell log -p i -t MPERF "step cold begin"
adb shell am start -W -n $PKG/app.masroufy.android.MainActivity --es perf_uid perf-user | grep -E "TotalTime"
wait_more "span screen:home" 0 240
sleep 3
adb shell log -p i -t MPERF "step cold end"
step operations "span screen:operations" tap 734 2208
step detail "span screen:detail" tap 660 978
adb shell input keyevent 4
sleep 1.5
step home "span screen:home" tap 930 2208
step people "span screen:people" tap 344 2208
step home2 "span screen:home" tap 930 2208
step add "span sheet:add" tap 540 2172
adb shell input keyevent 4
sleep 1.5
step assistant "span screen:assistant" tap 660 2036
adb shell input keyevent 4
sleep 1
adb logcat -d -v time -s MPERF:I > "$OUT"
adb logcat -d -v time | grep -E "Choreographer.*Skipped|Davey" > "${OUT%.log}-jank.log"
node "$(dirname "$0")/summ.js" "$OUT"
