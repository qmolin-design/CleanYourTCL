#!/bin/zsh
# apply.sh <批名> <套件...>：只停用目前為啟用的套件，記錄實際改變的清單供還原
# 還原：cat <批名>/changed.txt | while read p; do adb -s $T shell pm enable $p; done
T=192.0.2.69:5555; B=/Users/example-owner/TV/research/batches/$1; shift; mkdir -p $B
adb -s $T shell pm list packages -d | tr -d '\r' > $B/disabled_before.txt
: > $B/changed.txt
for p in "$@"; do
  if grep -qx "package:$p" $B/disabled_before.txt; then echo "SKIP(已停用) $p"; continue; fi
  r=$(adb -s $T shell pm disable-user --user 0 $p 2>&1 | tr -d '\r'); echo "$p -> $r"
  [[ $r == *"disabled-user"* ]] && echo $p >> $B/changed.txt
done
echo "changed: $(wc -l < $B/changed.txt)"
