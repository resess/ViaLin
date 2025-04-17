# ViaLin

This repository hosts ViaLin, an accurate, low-overhead, path-aware, dynamic taint analyzer for Android.

This repository also hosts the ViaLin's evaluation package.


<b>If you use this tool, please cite:</b>

Khaled Ahmed, Yingying Wang, Mieszko Lis, and Julia Rubin. [ViaLin: Path-Aware Dynamic Taint Analysis for Android](https://people.ece.ubc.ca/mjulia/publications/ViaLin_FSE2023.pdf). The ACM Joint European Software Engineering Conference and Symposium on the Foundations of Software Engineering (FSE), 2023 (26% acceptance rate).

## Table of Contents
1. [Pre-requisites](#Pre-requisites)
2. [Building The Tool](#Building-The-Tool)
3. [Using The Tool](#Using-The-Tool)
4. [Evaluation package](#Evaluation-package)
---


## Pre-requisites

* Install the Android SDK and build tools: https://developer.android.com/studio/intro/update

* Install python3

    * Linux: https://docs.python-guide.org/starting/install3/linux/
    * Mac: https://docs.python-guide.org/starting/install3/osx/
    * Windows: https://docs.python.org/3/using/windows.html

* Install and build Android AOSP version 8.0.0 by following the instructions in the [Android manual](https://source.android.com/docs/setup/build/building) (in our evaluation, we targeted android-8.0.0_r21, lunch 31).

---
## Building The Tool

1. Change the directory to the AndroidSource: `cd AndroidSource`

2. In the `apply_code.py` script, change the path of `android_src_folder` to the Android AOSP.

3. Run `apply_code.py`: `python3 apply_code.py`

4. Change the directory to the ViaLin Java source code directory: `cd ViaLin/`, then run `mvn package install`. This produces a JAR file `ViaLin/target/vialin-jar-with-dependencies.jar`

5. In the `java.mk` file, find this line: `java -Xss16M -jar /home/khaledea/data/ViaLin/ViaLin/target/vialin-jar-with-dependencies.jar t true vl $(dir $@)/temp/ /home/khaledea/data/ViaLin/framework_analysis_results /home/khaledea/data/ViaLin/methodSummaries /home/khaledea/data/ViaLin/scripts/empty.txt /home/khaledea/data/ViaLin/scripts/empty.txt $(dir $<)/classes*.dex`

6. Replace the jar path (`/home/khaledea/data/ViaLin/ViaLin/target/vialin-jar-with-dependencies.jar`) in the line from step 5 with the full path of the jar file built from step 4.

7. Replace the `framework_analysis_results` path from

- Create a folder called framework_analysis_results, place its path in [framework_analysis_results] in the `java.mk`

- Create folder class_info/ inside framework_analysis_results

- Replace [path-to-sources] and [path-to-sink] in `java.mk` with path to the absolute path of GPBench/config/empty.txt from the evaluation package

- Change the directory to the downloaded AOSP, follow the "Setting up the environment", "Choosing a target", and "Building the code" section of the Building Android Manual

- Flash an Android device by following the instructions in the Android Manual Flashing Devices


---

## Using The Tool

An example on how to taint and install an app on the device is in the evaluation package GPBench/scripts/run_gp.py, run from the vialin directory python3 GPBench/scripts/run_gp.py, modify the paths in the script to point to the correct folder for the AOSP, framework_analysis_results, source/sink lists, the android-record-and-replay tool included in vialin, and the path to the apk.


---

## Evaluation package


This section describes both the [DroidICCBench](#droidiccbench) and the [GPBench](#GPBench) benchmarks along with the configuration used to run the benchmarks. The evaluation package scripts and configuration files are under `evaluation_package`.

This package is organized in the following structure:

    .
    ├── apktool/
    ├── android-record-replay/
    ├── DroidICCBench/
    │   ├── scripts/
    │   │   ├── run_droidbench.py
    │   │   └── translate_droidbench.py
    │   ├── config/
    │   │   ├── app1.src.log
    │   │   ├── app1.sink.log
    │   │   └── ...
    │   └── apps/
    │       ├── Category1/
    │       │   ├── app1
    │       │   ├── app2
    │       │   └── ...
    │       ├── Category2/
    │       └── ...
    └── GPBench/
        ├── scripts/
        │   ├── run_gp.py
        │   ├── run_overhead.py
        │   └── translate_gp.py
        ├── config/
        │   ├── app1.src.log
        │   ├── app1.sink.log
        │   └── ...
        └── apps/
            ├── app1
            ├── app2
            └── ...


The evaluation package contains the `android-touch-record-replay` tool which we used to record and replay the execution for each app.

Next, we describe the DroidICCBench and GPBench sections of the evaluation package.

* * *

DroidICCBench
-------------

### Apps

The benchmark consists of 217 apps from [DroidBench](https://github.com/secure-software-engineering/DroidBench/tree/develop) and [ICCbench](https://github.com/fgwei/ICC-Bench). We had to exclude eight apps for which we cannot reliably trigger the flow in an automated way, e.g., because it is triggered when the phone memory is low. The 8 eight excluded apps are:

1.  Callbacks.AnonymousClass1
2.  Callbacks.LocationLeak1
3.  Callbacks.LocationLeak2
4.  Callbacks.LocationLeak3
5.  Callbacks.RegisterGlobal1
6.  Callbacks.RegisterGlobal2
7.  GeneralJava.FactoryMethods1
8.  Lifecycle.ActivityLifecycle3

For the remaining 209 apps, as the benchmark apps were developed for an older Android API (level 19), where permissions to run any sensitive, we modified the apps to request permissions using the approach if the newer Android versions. At the end, we used 209 apps in our evaluation. The apps are grouped into categories under the `DroidICCBench/apps` folder.

### Configuration

The combined sources list is under `DroidICCBench/config/source_full_list.txt`, the combined sinks list is under `DroidICCBench/config/sinks_full_list.txt`. The replay script for each app is at `DroidICCBench/config/[app].replay.txt`, apps without an execution script use the default script `DroidICCBench/config/trigger_flow.replay.txt`.

### Replication

The script `DroidICCBench/scripts/run_droidbench.py` runs the specified benchmark app by selecting its number, the number of each benchmark is its line number in the `DroidICCBench/config/droidbench_apks.log`. The script `scripts/extract_path.py` extracts the paths from the Android logcat and translates it into a human readable format.

* * *

GPBench
-------

### Apps

We used the benchmark of Google Play applications from Zhang et al \[37\]. We excluded from our study three out of the 19 apps, as their backend servers were non-functional at the time of writing and we thus could not execute them dynamically. The remaining 16 apps are listed below:

![drawing](https://anonforreview.github.io//SupplementaryMaterials/img/subjects.png)

### Configuration

The sources short list for each app `GPBench/config/[app-name].src.log`, the sinks short list for each app is under `GPBench/config/[app-name].sink.log`. The long list of sources is at `GPBench/config/source_long_list.txt` and the long list of sinks is `GPBench/config/sinks_long_list.log`. The replay script for each app is at `GPBench/config//[app].replay.txt`.

### Replication

The script `GPBench/scripts/run_gp.py` runs the specified benchmark app. The script `GPBench/scripts/run_overhead.py` runs the overhead experiment. The script `GPBench/scripts/translate_gp.py` extracts the paths from the Android logcat and translates it into a human readable format.

* * *

Fake WhatsApp client
--------------------

### App

We cannot distribute the YoWhatsApp malicious apk online, instead, we provide its following indicators:

package name: com.gbbwhatsapp

sha1 hash: a8dbfd8d48e4a4952e1a822ce1323a37348f0c1c

sha256 hash: 89c23dc02f4f67972a5c4cd9ccc61f7c08c95173d07a980c7340101ba597939e

md5: 531d0a00d3b7221b4ac712fbfe846029

blog describing the malware: [link](https://securelist.com/malicious-whatsapp-mod-distributed-through-legitimate-apps/107690/)

### Sources and Sinks

*   Sources configuration [file](https://resess.github.io/artifacts/ViaLin/data/YoWhatsApp.src.txt)
*   Sink configuration [file](https://resess.github.io/artifacts/ViaLin/data/YoWhatsApp.sink.txt)

### ViaLin Path

The path provided to the analysts is available [here](https://resess.github.io/artifacts/ViaLin/data/YoWhatsAppPath.log)



---

# Publication

Khaled Ahmed, Yingying Wang, Mieszko Lis, and Julia Rubin. [ViaLin: Path-Aware Dynamic Taint Analysis for Android](https://people.ece.ubc.ca/mjulia/publications/ViaLin_FSE2023.pdf). The ACM Joint European Software Engineering Conference and Symposium on the Foundations of Software Engineering (FSE), 2023 (26% acceptance rate).

# Contact

If you experience any issues, please submit an issue or contact us at khaledea@ece.ubc.ca
