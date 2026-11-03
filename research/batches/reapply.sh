#!/bin/zsh
# 重套停用清單（冷開機被還原時用）：先停更新程式打斷還原，再停清單，最後重開驗證
T=192.0.2.69:5555; a(){ adb -s $T shell "$@"; }
D=${0:a:h}; adb connect $T >/dev/null
miss=(); for p in $(cat $D/keep_disabled.txt); do a pm list packages -d | tr -d '\r' | grep -qx "package:$p" || miss+=$p; done
[[ ${#miss} -eq 0 ]] && { echo "全部 ${$(wc -l < $D/keep_disabled.txt)// /} 個仍停用，不需重套"; exit 0; }
echo "被還原：$miss"; a pm disable-user --user 0 com.tcl.versionUpdateApp
for p in $miss; do a pm disable-user --user 0 $p; done
echo "已重套 ${#miss} 個；建議再重開一次確認"
