import os
import sys
import utils
import time
import subprocess
import signal


def main(device_id):

    os.system("rm -r test/output/")
    os.system("mkdir test/output/")

    os.system("cd ViaLin/; mvn package install; cd -;")

    # WhatsApp Mini Test
    os.system("mkdir test/output/WhatsApp/")
    os.system("mkdir test/output/WhatsApp/apps")
    os.system("mkdir test/output/WhatsApp/results_vialin")

    os.system(f"./scripts/run_app.sh test/apps/ViaLinMiniTester/app/release/app-release.apk {device_id} \
              test/output/WhatsApp/ test/temp test/config/ViaLinMiniTester_src.txt test/config/ViaLinMiniTester_sink.txt norebuild")

    proc = subprocess.Popen(f"adb -s {device_id} logcat > test/output/WhatsApp/results_vialin/app-release.log", stdout=subprocess.PIPE, shell=True, preexec_fn=os.setsid)

    os.system(f"cd evaluation_package/android-touch-record-replay/; ./replay_touch_events_device.sh {device_id} ../../test/config/ViaLinMiniTester_replay.txt; cd -")

    time.sleep(2)
    proc.kill()
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)

    os.system(f"adb -s {device_id} uninstall ca.ubc.resess.vialinminitester > /dev/null 2>&1")

    os.system("python3 scripts/extract_path.py test/output/WhatsApp/results_vialin/app-release.log")

    # Check 3 flows are detected
    num_flows = 0
    with open("test/output/WhatsApp/results_vialin/app-release.paths/sources_sinks.csv", "r") as f:
        for l in f:
            if l.startswith("path_"):
                num_flows += 1
    if (num_flows == 3):
        print("Test Result: WhatsApp Mini Test is OK", flush=True)
    else:
        print("Test Result: WhatsApp Mini Test Failed", flush=True)

    time.sleep(10)

    # GPBench: Overhead
    os.system(f"(echo 17; echo 2) | python3 evaluation_package/GPBench/scripts/run_gp.py vl {device_id} test/output/ test/temp/ norebuild")
    with open("test/output/results_vialin/17.com.viagogo.consumer.viagogo.playstore.big_list.log.log", "r") as f:
        for l in f:
            if l.startswith("Total time (utime+stime): "):
                total_time = float(l.split(" ")[-2])
    if total_time < 70: # it should be around 60
        print("Test Result: GP Overhead Test is OK", flush=True)
    else:
        print("Test Result: GP Overhead Test Failed", flush=True)

    os.system(f"(echo 17; echo 1) | python3 evaluation_package/GPBench/scripts/run_gp.py vl {device_id} test/output/ test/temp/ norebuild")
    os.system("python3 scripts/extract_path.py test/output/results_vialin/17.com.viagogo.consumer.viagogo.playstore.single_list.log")

    num_flows = 0
    with open("test/output/results_vialin/17.com.viagogo.consumer.viagogo.playstore.single_list.paths/sources_sinks.csv", "r") as f:
        for l in f:
            if l.startswith("path_"):
                num_flows += 1
    if (num_flows == 2): # Check 2 flows are detected
        print("Test Result: GP Accuracy Test 1 is OK", flush=True)
    else:
        print("Test Result: GP Accuracy Test 1 Failed", flush=True)

    # GPBench: Accuracy
    os.system(f"(echo 13; echo 1) | python3 evaluation_package/GPBench/scripts/run_gp.py vl {device_id} test/output/ test/temp/ norebuild")
    os.system("python3 scripts/extract_path.py test/output/results_vialin/13.com.ackroo.mrgas.single_list.log")
    num_flows = 0
    with open("test/output/results_vialin/13.com.ackroo.mrgas.single_list.paths/sources_sinks.csv", "r") as f:
        for l in f:
            if l.startswith("path_"):
                num_flows += 1
    if (num_flows >= 15): # Check at least 15 flows are detected
        print("Test Result: GP Accuracy Test 2 is OK", flush=True)
    else:
        print("Test Result: GP Accuracy Test 2 Failed", flush=True)

    time.sleep(60)

    # Droidbench
    os.system("mkdir test/output/droidbench/")
    os.system(f"(echo 0) | python3 evaluation_package/DroidICCBench/scripts/run_droidbench.py test/output/droidbench/ {device_id} test/temp/ norebuild")

    files = utils.get_all_files("test/output/droidbench/")
    for f in files:
        if f.endswith(".log"):
            os.system(f"python3 scripts/extract_path.py {f} test/output/droidbench/")

    os.system("python3 scripts/compare_droidbench.py test/ground_truth/droidbench.csv test/output/droidbench/ test/ground_truth/droidbench/")


if __name__ == '__main__':
    try:
        device_id = sys.argv[1]
        main(device_id)
    except KeyboardInterrupt:
        print('Interrupted', flush=True)
        try:
            sys.exit(130)
        except SystemExit:
            os._exit(130)