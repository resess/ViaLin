import os

paths = list()
with open("dex_files_out.log", 'r') as f:
    for line in f:
        if line.strip():
            paths.append(line.strip())

for p in paths:
    print(f"Deleting {p}")
    os.system(f"rm {p}")