#!/bin/bash
# frame stats (gfxinfo) for: tab switches + scrolling Operations + opening the + sheet (blur). App must be on Home.
. "$(dirname "$0")/lib.sh"
gfx() { adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Slow UI thread|Number Slow issue draw|Number Frame deadline missed" | head -10; }
adb shell dumpsys gfxinfo $PKG reset > /dev/null
tap 734 2208; sleep 2
for i in 1 2 3; do adb shell input swipe 540 1700 540 700 300; sleep 0.8; done
for i in 1 2 3; do adb shell input swipe 540 700 540 1700 300; sleep 0.8; done
echo "== operations: open + scroll 6x"; gfx
adb shell dumpsys gfxinfo $PKG reset > /dev/null
tap 930 2208; sleep 2
tap 540 2172; sleep 2; adb shell input keyevent 4; sleep 1.5
tap 660 2036; sleep 2; adb shell input keyevent 4; sleep 1.5
echo "== home + sheet (blur) + assistant"; gfx
