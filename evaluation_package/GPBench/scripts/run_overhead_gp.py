import os
import sys
import time
import subprocess
import signal
import threading

mode = sys.argv[1]
device_id = sys.argv[2]

if len(sys.argv) > 3:
    base_out_dir = sys.argv[3]
else:
    base_out_dir = "outdir_gp_apps_overhead"

if not os.path.exists(base_out_dir):
  os.makedirs(base_out_dir)


if len(sys.argv) > 4:
    temp_dir = sys.argv[4]
else:
    temp_dir = "temp_gp_apps_overhead"


if "_" in mode:
    mode = mode.split("_")
else:
    mode = [mode]

AAPT_PATH = "/home/khaledea/data/android-aosp-pixel-2-xl-taintart/out/host/linux-x86/bin/"

def get_paths(type, list_type, base_out_dir):
    out_dir_path = f"{base_out_dir}/results_mem_overhead/{type}_{list_type}/"
    if not os.path.exists(out_dir_path):
        os.makedirs(out_dir_path)
    log_name = f"{out_dir_path}/{app_name}.{list_type}.log"
    paths_log = f"{out_dir_path}/{app_name}.{list_type}.paths.log"
    return log_name, paths_log


def instrument(apk_name, log_name, source_list, sink_list, type, temp_dir, base_out_dir):

    if type == "vl":
        result_path = f"{base_out_dir}/tool_gp_apps"
    elif type == "td":
        result_path = f"{base_out_dir}/taintdroid_gp_apps"
    elif type == "orig":
        result_path = f"{base_out_dir}/orig_timing_gp_apps"
    else:
        raise ValueError("Error, invalid tool")

    if not os.path.exists(result_path):
        os.makedirs(result_path)

    new_apk_name = apk_name.replace(".apk", "_" + type + "_" + list_type + ".apk")

    # Comment out to avoid re-instrumentation
    os.system(f"rm -r {temp_dir}; mkdir {temp_dir}; \
        java -Xmx4g -jar ViaLin/target/vialin-jar-with-dependencies.jar \
        t false {type} {temp_dir} framework_analysis_results \
        evaluation_package/GPBench/config/{source_list} evaluation_package/GPBench/config/{sink_list} \
        evaluation_package/GPBench/apps/{apk_name} > {log_name} 2>&1")
    os.system(f"yes vialin | apksigner sign --ks vialin.keystore {temp_dir}/new-jar/{apk_name}")
    os.system(f"cp {temp_dir}/new-jar/{apk_name} {result_path}/{new_apk_name}")
    print(f"Copying : cp -r {temp_dir}/class_info/ {result_path}/{apk_name}.{type}.{list_type}.class_info")
    os.system(f"cp -r {temp_dir}/class_info/ {result_path}/{apk_name}.{type}.{list_type}.class_info")


    proc = subprocess.Popen(f"{AAPT_PATH}/aapt dump badging {result_path}/{new_apk_name}", stdout=subprocess.PIPE, shell=True)
    (out, err) = proc.communicate()
    package_name = out.decode("utf-8").split()[1][5:].replace("\'", "")


    return package_name, new_apk_name


def get_mem_file(device_id, log_name, i, pid, stop):
    file_name = os.path.abspath(f"{log_name}.mem.{i}.log")
    with open(file_name, 'w') as f:
        f.write("")

    t = 0
    while True:
        time.sleep(1)
        t = t + 1
        proc = subprocess.Popen(f"adb -s {device_id} shell cat /proc/{pid}/status", stdout=subprocess.PIPE, shell=True)
        (out, err) = proc.communicate()
        status = out.decode("utf-8").strip()
        with open(file_name, 'a') as f:
            f.write(f"TimeStamp: {t}\n")
            f.write(f"{status}\n")
            f.write("----------------------------\n")
        if stop():
            break



def run_experiment(device_id, apk_to_install, package_name, log_name, replay_file, paths_log, i):
    os.system(f"adb -s {device_id} uninstall {package_name} 2>/dev/null")
    os.system(f"adb -s {device_id} reboot")
    time.sleep(10)
    os.system(f"adb -s {device_id} logcat -c")
    time.sleep(10)
    print("Phone rebooted, waiting 20s ..")
    time.sleep(20)
    os.system(f"adb -s {device_id} root")
    time.sleep(2)
    os.system(f"adb -s {device_id} logcat -c")
    os.system(f"adb -s {device_id} install {apk_to_install}")
    os.system(f"adb -s {device_id} shell cmd package compile -m speed -f {package_name}")

    os.system(f"adb -s {device_id} logcat -G 100M")

    procLog = subprocess.Popen(f"exec adb -s {device_id} logcat > {log_name}.{i}.log", stdout=subprocess.PIPE, shell=True)

    print("**********************************************************************")
    print(f"creating {log_name}.{i}.log")
    print("**********************************************************************")
    time.sleep(10)
    os.system(f"adb -s {device_id} shell monkey -p {package_name} -c android.intent.category.LAUNCHER 1")
    proc = subprocess.Popen(f"adb -s {device_id} shell pidof {package_name}", stdout=subprocess.PIPE, shell=True)
    (out, err) = proc.communicate()
    pid = out.decode("utf-8").strip()
    print(f"pid is {pid}")

    stop_threads = False
    th = threading.Thread(target=get_mem_file, args=(device_id, log_name, i, pid, lambda : stop_threads))
    th.start()

    print("Waiting for startup")
    time.sleep(120)

    print(f"At {os.getcwd()}")

    os.chdir("evaluation_package/android-touch-record-replay/")

    print(f"Switching {os.getcwd()}")

    print(f"./replay_touch_events_device.sh {device_id} ../../{replay_file}")
    os.system(f"./replay_touch_events_device.sh {device_id} ../../{replay_file}")

    os.chdir("../../")

    stop_threads = True
    th.join()

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

    os.system(f"adb -s {device_id} uninstall {package_name}")
    os.system(f"adb -s {device_id} shell input keyevent 26")
    os.system(f"python3 translate_path.py {log_name}.{i}.log > {paths_log}.{i}.log 2>&1")
    proc.kill()
    procLog.kill()
    time.sleep(10)

    os.system(f"echo \"Total time (utime+stime): {total_time} s\" >> {log_name}.{i}.log")


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

temp_dir = "temp_gp_overhead"
app_num_list = [1, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19]


list_type_select = input("Select source/sink list to use \n\
    [1] App specific list \n\
    [2] TSE + FD list \n\
    [3] empty list \n\
    [4] TSE sources, empty sink \n\
")
list_type_select = int(list_type_select)

os.system("cd ViaLin/; mvn package install; cd -;")

num_runs = 5

for app_num in app_num_list:

    app_name = app_names[app_num]
    apk_name = app_name + ".apk"

    if list_type_select == 4:
        source_list = "source_full_list.txt"
        sink_list = "empty.txt"
        list_type = "big_source_only"
    elif list_type_select == 3:
        source_list = "empty.txt"
        sink_list = "empty.txt"
        list_type = "empty"
    elif list_type_select == 2:
        source_list = "source_full_list.txt"
        sink_list = "tse_sinks.txt"
        list_type = "big_list"
    elif list_type_select == 1:
        source_list = app_name + ".src.txt"
        sink_list = app_name + ".sink.txt"
        list_type = "single_list"
    else:
        raise ValueError("Error, must select 1-4")

    replay_file = f"evaluation_package/GPBench/config/{app_name}.replay.txt"
    replay_file1 = f"evaluation_package/GPBench/config/{app_name}.replay1.txt"
    replay_file2 = f"evaluation_package/GPBench/config/{app_name}.replay2.txt"

    log_name_orig, paths_log_orig = get_paths("orig", list_type, base_out_dir)
    log_name_td, paths_log_td = get_paths("td", list_type, base_out_dir)
    log_name_vl, paths_log_vl = get_paths("vl", list_type, base_out_dir)

    print(f"Source list: {source_list}, sink list: {sink_list}")
    print(mode)
    if "orig" in mode:
        print(f"Instrumenting orig app {app_name}")
        package_name, new_apk_name = instrument(apk_name, log_name_orig, source_list, sink_list, "orig", temp_dir, base_out_dir)
    if "td" in mode:
        print(f"Instrumenting td app {app_name}")
        package_name, new_apk_name = instrument(apk_name, log_name_td, source_list, sink_list, "td", temp_dir, base_out_dir)
    if "vl" in mode:
        print(f"Instrumenting pt app {app_name}")
        package_name, new_apk_name = instrument(apk_name, log_name_vl, source_list, sink_list, "vl", temp_dir, base_out_dir)

    for i in range(0, num_runs):
        if "orig" in mode:
            run_experiment(device_id, f"{base_out_dir}/orig_timing_gp_apps/{new_apk_name}", package_name, log_name_orig, replay_file, paths_log_orig, i)
        if "vl" in mode:
            run_experiment(device_id, f"{base_out_dir}/tool_gp_apps/{new_apk_name}", package_name, log_name_vl, replay_file, paths_log_vl, i)
        if "td" in mode:
            run_experiment(device_id, f"{base_out_dir}/taintdroid_gp_apps/{new_apk_name}", package_name, log_name_td, replay_file, paths_log_td, i)
