#!/bin/zsh
# 唯讀健檢快照：snap.sh <標籤>
D=/Users/example-owner/TV/research/healthcheck/$1; mkdir -p $D
A=(adb -s 192.0.2.69:5555 shell)
run(){ print "### $1" ; "${A[@]}" "$1" 2>&1 ; }
{
run 'date; uptime'
run 'getprop ro.build.fingerprint; getprop ro.build.version.release; getprop ro.product.model; getprop ro.boot.hardware; getprop ro.adb.secure'
run 'cat /proc/meminfo'
run 'df -h /data /system /cache 2>/dev/null; df -h'
run 'dumpsys cpuinfo | head -40'
run 'top -b -n 1 -m 25 2>/dev/null || top -n 1 | head -40'
run 'dumpsys meminfo | sed -n "1,80p"'
run 'dumpsys diskstats'
run 'cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN'
run 'cat /proc/pressure/memory 2>/dev/null; cat /proc/vmstat | grep -E "pgmajfault|allocstall|kswapd|lowmem"'
} > $D/report.txt
"${A[@]}" pm list packages -f > $D/packages_all.txt 2>&1
"${A[@]}" pm list packages -d > $D/packages_disabled.txt 2>&1
"${A[@]}" pm list packages -3 > $D/packages_thirdparty.txt 2>&1
wc -l $D/*.txt
