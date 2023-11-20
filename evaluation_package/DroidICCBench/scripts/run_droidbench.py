import os
import sys
import time
import subprocess
import signal
import shutil
from pathlib import Path


print("Must run from main repo directory")

# Setup
mode = "vl"
out_dir = sys.argv[1]
device_id = sys.argv[2]
results_tag = "paths"
temp_dir = "temp_run_droidbench"
if len(sys.argv) > 3:
    temp_dir = sys.argv[3]

if len(sys.argv) > 4:
    rebuild = sys.argv[4]
else:
    rebuild = "rebuild"

run_single_app = input("Select app # to run, or 0 to run all or -n to run from n \n")

if not os.path.exists(out_dir):
    os.makedirs(out_dir)
debug_dir = f"{out_dir}/debug.log"
debug_dir = Path(debug_dir).absolute()
os.system(f"touch {debug_dir}")


# Paths
AAPT_PATH = "/home/khaledea/data/android-aosp-pixel-2-xl-taintart/out/host/linux-x86/bin/"
APKTOOL_PATH = "evaluation_package/apktool/"

# Constants
SHORT_SLEEP = 2
LONG_SLEEP = 5
IGNORE_ALL = f" >> {debug_dir} 2>&1"
IGNORE_OUT = f" >> {debug_dir}"
IGNORE_ERR = f" 2>> {debug_dir}"

def setup_app_files(app, out_dir, list_type, results_tag):
    app_name = "_".join(app.split("/")[1:3])
    apk_path = f"~/data/2022_composite_taints/code/{app}"
    apk_name = app.split("/")[-1]
    log_name = f"{out_dir}/{app_name}.{list_type}.log"
    results_log = f"{out_dir}/{app_name}.{list_type}.{results_tag}.log"
    replay_file = f"evaluation_package/DroidICCBench/config/{app_name}.replay.txt"
    if not os.path.isfile(replay_file):
        print("    Failed to find replay file, using default")
        replay_file = "evaluation_package/DroidICCBench/config/trigger_flow.replay.txt"

    return replay_file, apk_path, log_name, apk_name, app_name, results_log


def instrument_app(temp_dir, mode, source_list, sink_list, apk_path, log_name, apk_name, app_name, list_type):
    print(f"    Instrumenting {app_name}", flush=True)
    shutil.rmtree(f"{out_dir}/{app_name}.{list_type}.class_info", ignore_errors=True)
    if "DynamicLoading" in app_name:
        apktooldir = f"{temp_dir}_apktool"
        if apktooldir.endswith("/_apktool"):
            apktooldir = "_apktool".join(apktooldir.rsplit("/_apktool", 1))
        os.system(f"{APKTOOL_PATH}/apktool d {apk_path} -r -f -o {apktooldir} {IGNORE_OUT}")
        if os.path.exists(f"{apktooldir}/assets/"):
            for fname in os.listdir(f"{apktooldir}/assets/"):
                if fname.endswith(".apk"):
                    print(f"    Found an apk inside: {fname}")
                    instr_cmd = f"rm -r {temp_dir}; mkdir {temp_dir}; \
                        java -Xss16M -jar ViaLin/target/vialin-jar-with-dependencies.jar \
                        t false {mode} {temp_dir} framework_analysis_results \
                        evaluation_package/DroidICCBench/config/{source_list} evaluation_package/DroidICCBench/config/{sink_list} \
                        {apktooldir}/assets/{fname} > {log_name} 2>&1"
                    os.system(instr_cmd)
                    os.system(f"yes vialin {IGNORE_ERR} | apksigner sign --ks vialin.keystore {temp_dir}/new-jar/{fname} {IGNORE_ALL}")
                    os.system(f"cp -r {temp_dir}/class_info {out_dir}/{app_name}.{list_type}.class_info")
                    os.system(f"cp {temp_dir}/new-jar/{fname} {apktooldir}/assets/{fname}")
                    os.system(f"{APKTOOL_PATH}/apktool b {apktooldir} {IGNORE_OUT}")
                    apk_path = f"{apktooldir}/dist/{apk_name}"

        os.system(f"{APKTOOL_PATH}/apktool b {apktooldir} {IGNORE_OUT}")

    instr_cmd = f"rm -r {temp_dir}; mkdir {temp_dir}; \
        java -Xss16M -jar ViaLin/target/vialin-jar-with-dependencies.jar \
        t false {mode} {temp_dir} framework_analysis_results \
        evaluation_package/DroidICCBench/config/{source_list} evaluation_package/DroidICCBench/config/{sink_list} \
        {apk_path} > {log_name} 2>&1"
    os.system(instr_cmd)

    os.system(f"yes vialin {IGNORE_ERR} | apksigner sign --ks vialin.keystore {temp_dir}/new-jar/{apk_name} {IGNORE_ALL}")

    if os.path.exists(f"{out_dir}/{app_name}.{list_type}.class_info"):
        os.system(f"cp -r {temp_dir}/class_info/* {out_dir}/{app_name}.{list_type}.class_info/")
    else:
        os.system(f"cp -r {temp_dir}/class_info {out_dir}/{app_name}.{list_type}.class_info")

    proc = subprocess.Popen(f"{AAPT_PATH}/aapt dump badging {temp_dir}/new-jar/{apk_name}", stdout=subprocess.PIPE, shell=True)
    (out, err) = proc.communicate()
    package_name = out.decode("utf-8").split()[1][5:].replace("\'", "")
    return package_name


def uninstall_app(device_id, package_name):
    print(f"    Uninstalling {package_name}", flush=True)
    os.system(f"adb -s {device_id} uninstall {package_name} {IGNORE_ALL}")


def reboot_phone(device_id):
    print("    Rebooting", flush=True)
    os.system(f"adb -s {device_id} reboot")
    time.sleep(SHORT_SLEEP)
    os.system(f"adb -s {device_id} logcat -c {IGNORE_ALL}")
    time.sleep(SHORT_SLEEP)
    print("    Phone rebooted", flush=True)
    time.sleep(SHORT_SLEEP)
    os.chdir("evaluation_package/android-touch-record-replay/")
    os.system(f"./replay_touch_events_device.sh {device_id} ../../evaluation_package/DroidICCBench/config/clear_bkg_tasks.txt {IGNORE_ALL}")
    os.chdir("../../")
    time.sleep(SHORT_SLEEP)



def install_app(device_id, temp_dir, apk_name, log_name):
    print(f"    Installing {apk_name}", flush=True)
    os.system(f"adb -s {device_id} logcat -c")
    os.system(f"adb -s {device_id} install {temp_dir}/new-jar/{apk_name} {IGNORE_OUT}")
    os.system(f"adb -s {device_id} logcat -G 100M")
    proc = subprocess.Popen(f"adb -s {device_id} logcat >> {log_name}", stdout=subprocess.PIPE, shell=True, preexec_fn=os.setsid)
    time.sleep(SHORT_SLEEP)
    proc.kill()
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)



def launch_app(device_id, log_name, package_name):
    os.system(f"adb -s {device_id} shell pm grant {package_name} android.permission.READ_CONTACTS {IGNORE_ALL}")
    proc = subprocess.Popen(f"adb -s {device_id} logcat >> {log_name}", stdout=subprocess.PIPE, shell=True, preexec_fn=os.setsid)
    os.system(f"adb -s {device_id} shell monkey -p {package_name} -c android.intent.category.LAUNCHER 1 {IGNORE_ALL}")
    time.sleep(SHORT_SLEEP)
    proc.kill()
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
    time.sleep(SHORT_SLEEP)


def run_replay_script(device_id, log_name, replay_file, category_name, app_name):
    print("    Running execution script", flush=True)
    os.system(f"adb -s {device_id} logcat -c")
    time.sleep(SHORT_SLEEP)
    proc = subprocess.Popen(f"adb -s {device_id} logcat >> {log_name}", stdout=subprocess.PIPE, shell=True, preexec_fn=os.setsid)
    os.chdir("evaluation_package/android-touch-record-replay/")
    os.system(f"./replay_touch_events_device.sh {device_id} ../../{replay_file} {IGNORE_ALL}")
    if category_name == "AndroidSpecific" and "PrivateDataLeak3" in app_name:
        os.system(f"adb -s {device_id} shell input keyevent KEYCODE_HOME {IGNORE_ALL}")
        time.sleep(SHORT_SLEEP)
        os.system(f"adb -s {device_id} shell monkey -p {package_name} -c android.intent.category.LAUNCHER 1 {IGNORE_ALL}")
    if category_name == "Lifecycle" and ("AsynchronousEventOrdering1" in app_name or "ActivityLifecycle4" in app_name):
        os.system(f"adb -s {device_id} shell input keyevent KEYCODE_HOME {IGNORE_ALL}")
        time.sleep(SHORT_SLEEP)

    os.chdir("../../")
    time.sleep(LONG_SLEEP)
    proc.kill()
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)



# Main logic
start_from = False
if run_single_app.startswith('-'):
    start_from = True
    run_single_app = run_single_app[1:]
run_single_app = int(run_single_app)

apps = []
with open ("evaluation_package/DroidICCBench/config/droidbench_apks.log", 'r') as f:
    for line in f:
        apps.append(line.strip())


source_list = "source_full_list.txt"
sink_list = "sinks_full_list.txt"
list_type = "big_list"

if rebuild == "rebuild":
    build_cmd = "cd ViaLin/; mvn package install; cd -"
    print(build_cmd)
    os.system(build_cmd)

for app_index in range(0, len(apps)):

    if run_single_app > 0 and not start_from:
        if (run_single_app - 1) != app_index:
            continue

    if (run_single_app > 0) and (start_from) and ((run_single_app - 1) >= app_index):
        continue

    app = apps[app_index]
    if app.startswith("-"):
        continue
    category_name, bench_name = app.split("/")[1:3]

    print(f"Will run {app}", flush=True)

    if category_name == "InterAppCommunication":

        collector_app = "DroidBenchDyanmicAnalysis/InterAppCommunication/Collector/Collector/release/Collector-release.apk"
        aux_replay_file, aux_apk_path, aux_log_name, aux_apk_name, aux_app_name, aux_results_log = setup_app_files(collector_app, out_dir, list_type, results_tag)
        aux_package_name_1 = instrument_app(temp_dir, mode, source_list, sink_list, aux_apk_path, aux_log_name, aux_apk_name, aux_app_name, list_type)
        uninstall_app(device_id, aux_package_name_1)
        install_app(device_id, temp_dir, aux_apk_name, aux_log_name)

        echoer_app = "DroidBenchDyanmicAnalysis/InterAppCommunication/Echoer/Echoer/release/Echoer-release.apk"
        aux_replay_file, aux_apk_path, aux_log_name, aux_apk_name, aux_app_name, aux_results_log = setup_app_files(echoer_app, out_dir, list_type, results_tag)
        aux_package_name_2 = instrument_app(temp_dir, mode, source_list, sink_list, aux_apk_path, aux_log_name, aux_apk_name, aux_app_name, list_type)
        uninstall_app(device_id, aux_package_name_2)
        install_app(device_id, temp_dir, aux_apk_name, aux_log_name)

    replay_file, apk_path, log_name, apk_name, app_name, results_log = setup_app_files(app, out_dir, list_type, results_tag)

    package_name = instrument_app(temp_dir, mode, source_list, sink_list, apk_path, log_name, apk_name, app_name, list_type)

    uninstall_app(device_id, package_name)

    reboot_phone(device_id)

    install_app(device_id, temp_dir, apk_name, log_name)

    launch_app(device_id, log_name, package_name)

    run_replay_script(device_id, log_name, replay_file, category_name, app_name)

    os.system(f"adb -s {device_id} uninstall {package_name} {IGNORE_ALL}")
    if category_name == "InterAppCommunication":
        uninstall_app(device_id, aux_package_name_1)
        uninstall_app(device_id, aux_package_name_2)


    print(f"    Finished running {app_name} with list {list_type} and mode {mode}", flush=True)
    time.sleep(SHORT_SLEEP)
