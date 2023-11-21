import os

android_src_folder="/home/khaledea/data/android-aosp-pixel-2-xl-taintart/"

os.system(f"cp {android_src_folder}/libcore/ojluni/src/main/java/java/lang/PathTaint.java PathTaint.java")
os.system(f"cp {android_src_folder}/frameworks/base/core/java/android/os/Bundle.java Bundle.java")
os.system(f"cp {android_src_folder}/build/make/core/droiddoc.mk droiddoc.mk")
os.system(f"cp {android_src_folder}/libcore/ojluni/src/main/java/java/io/FileOutputStream.java FileOutputStream.java")
os.system(f"cp {android_src_folder}/libcore/ojluni/src/main/java/java/util/Formatter.java Formatter.java")
os.system(f"cp {android_src_folder}/external/okhttp/okhttp/src/main/java/com/squareup/okhttp/internal/http/HttpEngine.java HttpEngine.java")
os.system(f"cp {android_src_folder}/frameworks/base/core/java/android/content/Intent.java Intent.java")
os.system(f"cp {android_src_folder}/build/make/core/java.mk java.mk")
os.system(f"cp {android_src_folder}/libcore/ojluni/src/main/java/java/util/Map.java Map.java")
os.system(f"cp {android_src_folder}/libcore/ojluni/src/main/java/java/io/ObjectOutputStream.java ObjectOutputStream.java")
os.system(f"cp {android_src_folder}/libcore/openjdk_java_files.mk openjdk_java_files.mk")
os.system(f"cp {android_src_folder}/frameworks/base/core/java/android/app/SharedPreferencesImpl.java SharedPreferencesImpl.java")
os.system(f"cp {android_src_folder}/frameworks/base/core/java/android/content/SharedPreferences.java SharedPreferences.java")
os.system(f"cp {android_src_folder}/libcore/ojluni/src/main/java/java/net/Socket.java Socket.java")
os.system(f"cp {android_src_folder}/libcore/ojluni/src/main/java/java/lang/TaintDroid.java TaintDroid.java")
os.system(f"cp {android_src_folder}/libcore/ojluni/src/main/java/java/lang/Thread.java Thread.java")