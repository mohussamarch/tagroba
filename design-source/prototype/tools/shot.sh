#!/usr/bin/env bash
# لقطة شاشة لشاشة من النموذج بمتصفح مخفي (محتاج سيرفر المعاينة شغال: launch config prototype-preview، المنفذ 5179).
# الاستعمال: shot.sh <out.png> "<query>"   مثال: shot.sh /tmp/p.png "a=People.dc.html&scroll=210"
OUT="$1"; QUERY="$2"; BUDGET="${3:-6000}"
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
[ -x "$CHROME" ] || CHROME="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=390,844 \
  --virtual-time-budget="$BUDGET" --screenshot="$(cygpath -w "$OUT")" "http://localhost:5179/?$QUERY" >/dev/null 2>&1
[ -s "$OUT" ] && echo "ok $OUT" || echo "failed"
