#!/bin/bash
# helpers for the perf flow on the Android emulator (logcat tag MPERF)
export PATH="/c/Users/atgs0/Documents/Codex/android-build-tools/sdk/platform-tools:$PATH"
PKG=app.masroufy.mobile

count() { adb logcat -d -s MPERF:I 2>/dev/null | grep -c -- "$1"; }

# wait until pattern count exceeds $2 (previous count); timeout $3 seconds
wait_more() {
  local pat="$1" before="$2" end=$((SECONDS + ${3:-90}))
  while [ $SECONDS -lt $end ]; do
    [ "$(count "$pat")" -gt "$before" ] && return 0
    sleep 0.3
  done
  echo "TIMEOUT: $pat"
  return 1
}

# step <name> <pattern-to-wait> <command...>
step() {
  local name="$1" pat="$2"; shift 2
  local before; before=$(count "$pat")
  adb shell log -p i -t MPERF "step $name begin"
  "$@"
  wait_more "$pat" "$before" 120
  sleep 1.5
  adb shell log -p i -t MPERF "step $name end"
}

tap() { adb shell input tap "$1" "$2"; }
