#!/bin/bash

AAPT_PATH="/home/khaledea/data/android-aosp-pixel-2-xl-taintart/out/host/linux-x86/bin/"

apk_name=$1
device_id=$2
out_dir=$3
temp_dir=$4
src_list=$5
sink_list=$6
rebuild=$7

out_apk=$(basename $apk_name .apk)_Vialin.apk
app_name=$(basename $apk_name .apk)
echo $app_name

package_name=$(${AAPT_PATH}/aapt dump badging $apk_name | head -n 1 | cut -d ' ' -f2 | cut -d '=' -f2 | sed -e "s/'//g")
echo $package_name

if [ "$rebuild" == "rebuild" ]; then
    cd ViaLin/
    mvn package install
    cd -
fi

rm -r $temp_dir
mkdir $temp_dir
java -Xss16M -Xmx8G -jar ViaLin/target/vialin-jar-with-dependencies.jar t false vl $temp_dir framework_analysis_results ${src_list} ${sink_list} $apk_name


rm $out_dir/apps/$out_apk

cp $temp_dir/new-jar/*.apk $out_dir/apps/$out_apk

rm -r $out_dir/results_vialin/$app_name.class_info/
cp -r $temp_dir/class_info/ $out_dir/results_vialin/$app_name.class_info

yes vialin | apksigner sign --ks vialin.keystore $out_dir/apps/$out_apk


adb -s $device_id reboot

sleep 10

adb -s $device_id logcat -c

sleep 10

adb -s $device_id root

adb -s $device_id logcat -c

adb -s $device_id uninstall $package_name

adb -s $device_id install $out_dir/apps/$out_apk

adb -s $device_id shell monkey -p $package_name -c android.intent.category.LAUNCHER 1

sleep 1
