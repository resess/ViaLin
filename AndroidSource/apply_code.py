import os

android_src_folder="/home/khaledea/data/android-aosp-pixel-2-xl-taintart/"

os.system(f"cp PathTaint.java {android_src_folder}/libcore/ojluni/src/main/java/java/lang/PathTaint.java")
os.system(f"cp Bundle.java {android_src_folder}/frameworks/base/core/java/android/os/Bundle.java")
os.system(f"cp droiddoc.mk {android_src_folder}/build/make/core/droiddoc.mk")
os.system(f"cp FileOutputStream.java {android_src_folder}/libcore/ojluni/src/main/java/java/io/FileOutputStream.java")
os.system(f"cp Formatter.java {android_src_folder}/libcore/ojluni/src/main/java/java/util/Formatter.java")
os.system(f"cp HttpEngine.java {android_src_folder}/external/okhttp/okhttp/src/main/java/com/squareup/okhttp/internal/http/HttpEngine.java")
os.system(f"cp Intent.java {android_src_folder}/frameworks/base/core/java/android/content/Intent.java")
os.system(f"cp java.mk {android_src_folder}/build/make/core/java.mk")
os.system(f"cp Map.java {android_src_folder}/libcore/ojluni/src/main/java/java/util/Map.java")
os.system(f"cp ObjectOutputStream.java {android_src_folder}/libcore/ojluni/src/main/java/java/io/ObjectOutputStream.java")
os.system(f"cp openjdk_java_files.mk {android_src_folder}/libcore/openjdk_java_files.mk")
os.system(f"cp SharedPreferencesImpl.java {android_src_folder}/frameworks/base/core/java/android/app/SharedPreferencesImpl.java")
os.system(f"cp SharedPreferences.java {android_src_folder}/frameworks/base/core/java/android/content/SharedPreferences.java")
os.system(f"cp Socket.java {android_src_folder}/libcore/ojluni/src/main/java/java/net/Socket.java")
os.system(f"cp TaintDroid.java {android_src_folder}/libcore/ojluni/src/main/java/java/lang/TaintDroid.java")
os.system(f"cp Thread.java {android_src_folder}/libcore/ojluni/src/main/java/java/lang/Thread.java")