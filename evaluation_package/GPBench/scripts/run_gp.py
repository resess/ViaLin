import os
import sys
import time
import subprocess
import signal

AAPT_PATH = "/home/khaledea/data/android-aosp-pixel-2-xl-taintart/out/host/linux-x86/bin/"

experiment = sys.argv[1]

if experiment == "cov":
    mode = "cov"
    out_dir = "coverage"
    results_tag = "cov"
elif experiment == "vl":
    mode = "vl"
    out_dir = "results_vialin"
    results_tag = "paths"
elif experiment == "td":
    mode = "td"
    out_dir = "results_taintdroid"
    results_tag = "paths"
elif experiment == "orig":
    mode = "orig"
    out_dir = "results_orig"
    results_tag = "time?"
else:
    raise ValueError("Error, invalid experiment")

device_id = sys.argv[2]

if len(sys.argv) > 3:
    base_out_dir = sys.argv[3]
else:
    base_out_dir = "outdir_gp_apps"

if len(sys.argv) > 4:
    temp_dir = sys.argv[4]
else:
    temp_dir = "temp_gp_apps"

if len(sys.argv) > 5:
    rebuild = sys.argv[5]
else:
    rebuild = "rebuild"

app_names = {
    1: "1.com.echangecadeaux",
    5: "5.com.asiandate",
    6: "6.ca.passportparking.mobile.passportcanada",
    7: "7.com.aldiko.android",
    8: "8.com.passportparking.mobile.parkvictoria",
    9: "9.com.passportparking.mobile.toronto",
    10: "10.tc.tc.scsm.phonegap",
    11: "11.com.onetapsolutions.morneau.activity",
    12: "12.net.fieldwire.app",
    13: "13.com.ackroo.mrgas",
    14: "14.com.airbnb.android",
    15: "15.com.bose.gd.events",
    16: "16.com.phonehalo.itemtracker",
    17: "17.com.viagogo.consumer.viagogo.playstore",
    18: "18.com.yelp.android",
    19: "19.onxmaps.hunt"
}

print("Must run from repo directory")

app_num = input("Select app number to run \n\
    [1] 1.com.echangecadeaux \n\
    [5] 5.com.asiandate \n\
    [6] 6.ca.passportparking.mobile.passportcanada \n\
    [7] 7.com.aldiko.android \n\
    [8] 8.com.passportparking.mobile.parkvictoria \n\
    [9] 9.com.passportparking.mobile.toronto \n\
    [10] 10.tc.tc.scsm.phonegap \n\
    [11] 11.com.onetapsolutions.morneau.activity \n\
    [12] 12.net.fieldwire.app \n\
    [13] 13.com.ackroo.mrgas \n\
    [14] 14.com.airbnb.android \n\
    [15] 15.com.bose.gd.events \n\
    [16] 16.com.phonehalo.itemtracker \n\
    [17] 17.com.viagogo.consumer.viagogo.playstore \n\
    [18] 18.com.yelp.android \n\
    [19] 19.onxmaps.hunt \n\
")
app_num = int(app_num)

list_type = input("Select source/sink list to use \n\
    [1] App specific list \n\
    [2] TSE + FD list \n\
")
list_type = int(list_type)

app_name = app_names[app_num]
apk_name = app_name + ".apk"

if list_type == 2:
    source_list = "source_full_list.txt"
    sink_list = "tse_sinks.txt"
    list_type = "big_list"
elif list_type == 1:
    source_list = app_name + ".src.txt"
    sink_list = app_name + ".sink.txt"
    list_type = "single_list"
else:
    raise ValueError("Error, must select 1 or 2")

out_dir_path = f"{base_out_dir}/{out_dir}/"
if not os.path.exists(out_dir_path):
  os.makedirs(out_dir_path)

log_name = f"{base_out_dir}/{out_dir}/{app_name}.{list_type}.log"
results_log = f"{base_out_dir}/{out_dir}/{app_name}.{list_type}.{results_tag}.log"
replay_file = f"evaluation_package/GPBench/config/{app_name}.replay.txt"
replay_file1 = f"evaluation_package/GPBench/config/{app_name}.replay1.txt"
replay_file2 = f"evaluation_package/GPBench/config/{app_name}.replay2.txt"


if rebuild == "rebuild":
    os.system("cd ViaLin/; mvn package install; cd -;")

print("Tainting", flush=True)

os.system(f"rm -r {temp_dir}; mkdir {temp_dir}; \
    java -Xss16M -jar ViaLin/target/vialin-jar-with-dependencies.jar \
    t false {mode} {temp_dir} framework_analysis_results methodSummaries \
    evaluation_package/GPBench/config/{source_list} evaluation_package/GPBench/config/{sink_list} \
    evaluation_package/GPBench/apps/{apk_name} > {log_name} 2>&1")

print("Done tainting", flush=True)

os.system(f"cp -r {temp_dir}/class_info {base_out_dir}/{out_dir}/{app_name}.{list_type}.class_info")


proc = subprocess.Popen(f"{AAPT_PATH}/aapt dump badging {temp_dir}/new-jar/{apk_name}", stdout=subprocess.PIPE, shell=True)
(out, err) = proc.communicate()
package_name = out.decode("utf-8").split()[1][5:].replace("\'", "")

if experiment == "cov":
    os.system(f"cp {temp_dir}/full_coverage.log {base_out_dir}/{out_dir}/{app_name}.{list_type}.all_lines.log")


print("Will reboot phone", flush=True)

os.system(f"adb -s {device_id} reboot")
time.sleep(10)
os.system(f"adb -s {device_id} logcat -c")
time.sleep(10)
print("Phone rebooted, waiting 20s ..", flush=True)
# print("Stuck here 12")
time.sleep(20)
os.system(f"adb -s {device_id} root")
time.sleep(2)
os.system(f"adb -s {device_id} logcat -c")

print("Will install app", flush=True)
os.system(f"adb -s {device_id} uninstall {package_name} 2>/dev/null")
os.system(f"yes vialin | apksigner sign --ks vialin.keystore {temp_dir}/new-jar/{apk_name}")
os.system(f"adb -s {device_id} logcat -c")
os.system(f"adb -s {device_id} install {temp_dir}/new-jar/{apk_name}")
os.system(f"adb -s {device_id} logcat -G 100M")
log_proc = subprocess.Popen(f"adb -s {device_id} logcat >> {log_name}", stdout=subprocess.PIPE, shell=True, preexec_fn=os.setsid)

time.sleep(10)
os.system(f"adb -s {device_id} shell monkey -p {package_name} -c android.intent.category.LAUNCHER 1")


print("Waiting for startup", flush=True)
time.sleep(30)

os.chdir("evaluation_package/android-touch-record-replay/")


if app_num == 6 or app_num == 8 or app_num == 9 or app_num == 10:
    os.system(f"./replay_touch_events_device.sh {device_id} ../../{replay_file1}")
    input("enter code then presse enter")
    os.system(f"./replay_touch_events_device.sh {device_id} ../../{replay_file2}")
else:
    os.system(f"./replay_touch_events_device.sh {device_id} ../../{replay_file}")

os.chdir("../../")

time.sleep(120)

proc = subprocess.Popen(f"adb -s {device_id} shell pidof {package_name}", stdout=subprocess.PIPE, shell=True)
(out, err) = proc.communicate()
pid = out.decode("utf-8").strip()
# Compute up time
try:
    proc = subprocess.Popen(f"adb -s {device_id} shell cat /proc/{pid}/stat", stdout=subprocess.PIPE, shell=True)
    (out, err) = proc.communicate()
    proc_time = out.decode("utf-8").strip()
    utime = proc_time.split(" ")[13]
    stime = proc_time.split(" ")[14]
    starttime = proc_time.split(" ")[21]
    print(f"utime: {utime}, stime:{stime}, start:{starttime}")

    total_time = (int(utime) + int(stime))/100
    print(f"total time: {total_time}")
except:
    total_time = -1

os.system(f"echo \"Total time (utime+stime): {total_time} s\" >> {log_name}.log")
os.system(f"echo \"Total time (utime+stime): {total_time} s\" >> {log_name}")

os.system(f"adb -s {device_id} uninstall {package_name}")

log_proc.kill()
os.killpg(os.getpgid(log_proc.pid), signal.SIGTERM)


print(f"Finished running {app_num}:{app_name} with list {list_type} and mode {mode}")