import os

# Change to the path of the AOSP directory
aosp_path = "/home/khaledea/data/saved_out"
print(f"Using AOSP path: {aosp_path}")


def instr_file_by_file():
    """
    Analyzes each file specified in 'dex_files.log' by instrumenting it using ViaLin.

    This function reads the file paths from 'dex_files.log', then performs the following steps for each file:
    1. Appends the AOSP path to the file path.
    2. Checks if the file exists. If not, prints 'not a file' and returns.
    3. Executes the ViaLin command to instrument the file, redirecting the output to 'debug.log'.
    """
    paths = list()
    with open("scripts/dex_files.log", 'r') as f:
        for line in f:
            if line.strip():
                paths.append(line.strip())

    os.system("cd ViaLin/; mvn package install > ../debug.log 2>&1; cd -")

    for p in paths:
        p = aosp_path + p
        print(f"{p}")
        with open("debug.log", 'a') as debug_file:
            debug_file.write(p + "\n")
        if (not os.path.isfile(p)):
            print("not a file")
            return
        os.system("rm -r temp")
        os.system("mkdir temp")
        os.system(f"java -Xss16M -jar ViaLin/target/vialin-jar-with-dependencies.jar a true vl temp framework_analysis_results scripts/empty.txt scripts/empty.txt {p}>> debug.log 2>&1")


instr_file_by_file()
