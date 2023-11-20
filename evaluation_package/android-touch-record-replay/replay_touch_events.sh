#!/bin/bash
DEVICE=$1
EVENTS=$2
echo "Looking for touchscreen device..."
TOUCH_DEVICE=`./find_touchscreen_name_device.sh $DEVICE`

echo "$TOUCH_DEVICE"

MYSENDEVENT=`adb -s $DEVICE shell ls /data/local/tmp/mysendevent-x86 2>&1`
echo ---"$MYSENDEVENT"---
[[ "$MYSENDEVENT" == *"No such file or directory"* ]] && adb -s $DEVICE push mysendevent-x86 /data/local/tmp/

adb -s $DEVICE shell chmod 777 /data/local/tmp/mysendevent-x86

adb -s $DEVICE push $EVENTS /sdcard/recorded_touch_events.txt

echo /data/local/tmp/mysendevent-x86 "${TOUCH_DEVICE#*-> }" /sdcard/recorded_touch_events.txt

# Replay the recorded events
adb -s $DEVICE shell /data/local/tmp/mysendevent-x86 "${TOUCH_DEVICE#*-> }" /sdcard/recorded_touch_events.txt
