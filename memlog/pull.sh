#!/bin/zsh
# 每小時由 launchd 執行：電視在線就抓記憶體快照、procstats（約保留 15 小時）、事件日誌（App 被砍、ANR、當掉）。
# 電視不在線（關機、Mac 不在家）就安靜跳過。資料：/Users/example-owner/TV/memlog/
T=192.0.2.69:5555; ADB=/opt/homebrew/bin/adb; D=${0:a:h}; ts=$(date +%Y%m%d-%H%M)
mkdir -p $D/raw
$ADB connect $T 2>&1 | grep -q '^\(connected\|already\)' || { echo "$ts offline" >> $D/pull.log; exit 0; }
a(){ $ADB -s $T shell "$@" 2>/dev/null | tr -d '\r'; }
[ "$(a getprop sys.boot_completed)" = 1 ] || { echo "$ts booting" >> $D/pull.log; exit 0; }
a logcat -b events -G 4M >/dev/null                      # 重開機會打回 256K，每次重設
up=$(a cut -d. -f1 /proc/uptime)
mem=$(a "grep -E '^(MemAvailable|SwapFree):' /proc/meminfo" | awk '{printf "%s,", $2}')
top=$(a "dumpsys activity activities | grep -m1 mResumedActivity" | grep -o 'u0 [^ /]*' | cut -c4-)
[ -f $D/mem.csv ] || echo "time,uptime_s,mem_available_kb,swap_free_kb,foreground" > $D/mem.csv
echo "$ts,$up,${mem}${top}" >> $D/mem.csv
a dumpsys procstats --hours 24 | gzip > $D/raw/$ts-procstats.txt.gz
a logcat -b events,crash -d -v year | gzip > $D/raw/$ts-events.txt.gz
echo "$ts ok" >> $D/pull.log
