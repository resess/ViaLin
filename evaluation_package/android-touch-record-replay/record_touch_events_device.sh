#!/bin/bash
DEVICE=$1
echo "Looking for touchscreen device..."
TOUCH_DEVICE=`./find_touchscreen_name_device.sh $DEVICE`
ANDROID_VERSION_STR=`adb -s $DEVICE shell getprop ro.build.version.sdk`
ANDROID_VERSION=$(echo "$ANDROID_VERSION_STR"| tr -d $'\r' | bc) # convert string to int
MIN_VERSION=23

echo "$TOUCH_DEVICE"

# Check if input device exists
if [[ "$TOUCH_DEVICE" = *"Touchscreen device found!"* ]]
then
    echo -e "SDK version: $ANDROID_VERSION\n"
    # Device found! Start recording
    echo "Recording will START as soon as you put your finger in the touchscreen."
    echo "Press ctrl+c to STOP recording..."

    if (( ANDROID_VERSION > MIN_VERSION )); then
        #exec-out is shell without buffering, fixing missing last touch data event
        adb -s $DEVICE exec-out getevent -t "${TOUCH_DEVICE#*-> }" > recorded_touch_events.txt
    else
        # if Android version <= 6.0
        adb -s $DEVICE shell getevent -t "${TOUCH_DEVICE#*-> }" > recorded_touch_events.txt
    fi
fi
