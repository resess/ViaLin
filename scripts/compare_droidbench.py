import pandas as pd
import re
from pathlib import Path
import os
import json
import sys


sctipt_path = os.path.dirname(__file__)

category_stats = dict()

def init_stats(category):
    if category not in category_stats:
        category_stats[category] = dict()
        category_stats[category]["vl"] = dict()
        category_stats[category]["Paths"] = dict()
        category_stats[category]["vl"]["found"] = 0
        category_stats[category]["vl"]["missed"] = 0
        category_stats[category]["Paths"]["Total"] = 0

def update_stats(category, tool, found):
    if category:
        category_stats[category][tool][found] += 1


def analysis(expected_csv, results_folder, ground_truth_folder):
    path_id = "path ID"
    vl_detected_col = "Expected?"
    source_col = "source"
    sink_col = "sink"
    category_col = "Category"
    appname_col = "AppName"

    na = "-"
    # assume each row is a path in an app

    df = pd.read_csv(expected_csv)

    processed_paths = set()
    all_paths = set()
    new_false_negative = list()
    new_false_positive = list()

    i = 0

    for row in df.iterrows():
        if  not df.at[i,path_id]:
            # then skip the row, we don't care
            pass
        elif not pd.isna(df.at[i,path_id]):
            source = df.at[i, source_col]
            sink = df.at[i, sink_col]

            category = str(df.at[i, category_col]).replace(" ", "")
            appname = str(df.at[i, appname_col]).replace(" ", "")
            # find corresponding folder
            print('====================')
            print ('category: ' + str(category))
            print('appname ' + str(appname))

            if category == "IccHandling" or category == "IccTargetFinding" or category == "Mixed" or category == "RpcHandling":
                category_vl = "ICCBench"
            else:
                category_vl = category
            vl_folder = Path(results_folder, category_vl + "_" + appname + ".big_list.paths")
            old_folder = Path(ground_truth_folder, category_vl + "_" + appname + ".big_list.paths")

            init_stats(category_vl)
            if not pd.isna(df.at[i,path_id]):
                print(f"Path ID {df.at[i,path_id]}")
                update_stats(category_vl, "Paths", "Total")

            # print('vialin folder: ' + str(vl_folder))

            # check if vialin found the path
            vl_path = found_target_path(source, sink, vl_folder, processed_paths, all_paths)

            if len(vl_path) > 0:
                print('VL Detected the path')
                update_stats(category_vl, "vl", "found")
                path_matches = True
                if df.loc[i, vl_detected_col] != "v":
                    path_matches = False

                old_path = found_target_path_by_len(source, sink, old_folder, len(vl_path))
                if len(vl_path) == len(old_path):
                    for idx, p in enumerate(vl_path):
                        # print(f"    Comparing {p} to {old_path[idx]}")
                        if p != old_path[idx]:
                            path_matches = False
                            print('Path is different from ground truth')
                            break

                if path_matches:
                    print('OK')
                else:
                    print('Was not expected')
                    new_false_positive.append(f"{str(category)}_{str(appname)} : path {df.at[i,path_id]}")
            else:
                print('VL Failed to detect the path')
                print(f'    source : {source}')
                print(f'    sink   : {sink}')
                update_stats(category_vl, "vl", "missed")
                if df.loc[i, vl_detected_col] == "x":
                    print('OK')
                else:
                    print('Was not expected')
                    new_false_negative.append(f"{str(category)}_{str(appname)} : path {df.at[i,path_id]}")


        i = i + 1
    print(json.dumps(category_stats, sort_keys=True, indent=4))

    for path in all_paths:
        if path not in processed_paths:
            new_false_positive.append(f"{path}")
    if new_false_positive:
        print("New false positives:")
        for fp in new_false_positive:
            print(f"   {fp}")
    else:
        print("No new false positives")
    if new_false_negative:
        print("New false negatives:")
        for fn in new_false_negative:
            print(f"   {fn}")
    else:
        print("No new false negatives")

def is_path_file(file):
    if os.path.isfile(file) and (Path(file).suffix == '.log' or Path(file).suffix == '.csv') and 'sources_sinks.csv' not in file:
        return True
    return False

def found_target_path(tgtSource: str, tgtSink: str, folder: str, processed_paths: set, all_paths: set):
    print(f"Looking for target path in {folder}")
    if os.path.exists(folder) == False:
        print(f"Failed to find target path, folder doesn't exist")
        return []

    for filename in os.listdir(folder):
        file = os.path.join(folder, filename)
        if is_path_file(file):
            all_paths.add(file)

    for filename in os.listdir(folder):
        file = os.path.join(folder, filename)
        if file in processed_paths:
            continue
        if is_path_file(file):
            print('process path file ' + filename)
            # read path from the file
            path = read_path_from_file(file)

            if is_target_path(tgtSource, tgtSink, path):
                processed_paths.add(file)
                return path
    print(f"Failed to find target path, no path matches source/sink pairs")
    return []


def found_target_path_by_len(tgtSource: str, tgtSink: str, folder: str, target_len: int):
    print(f"Looking for old path in {folder}")
    if os.path.exists(folder) == False:
        print(f"Failed to find old path, folder doesn't exist")
        return []

    for filename in os.listdir(folder):
        file = os.path.join(folder, filename)
        if is_path_file(file):
            # read path from the file
            path = read_path_from_file(file)

            if is_target_path(tgtSource, tgtSink, path) and len(path) == target_len:
                print('old path file ' + filename)
                return path
    print(f"Failed to find old path, no path matches source/sink pairs")
    return []


def read_path_from_file(file):
    # vialin file
    # if Path(file).suffix == '.log':
    #     return open(file, 'r').readlines()

    # # flowdroid file
    # elif Path(file).suffix == '.csv':
    df = pd.read_csv(file)
    return df["stmt"].values.tolist()




def is_target_path(tgtSource, tgtSink, path):
    if len(path) == 0:
        return False; # no need to check

    # get source
    actualSource = get_processed_stmt(path[0])
    tgtSource = get_processed_stmt(tgtSource)

    # get sink
    actualSink = get_processed_stmt(path[-1])
    tgtSink = get_processed_stmt(tgtSink)

    is_target = (tgtSource in actualSource) and (tgtSink in actualSink)
    return is_target


def get_processed_stmt (string):
    # print ('stmt is: ' + str(string))
    str_out = str(string)
    splited = str(string).split(" = ")
    if len(splited) > 1:
        str_out = splited[1]

    # replace all stack variables with R
    str_out = re.sub("\$r\d+", "R", str_out, count=0, flags=0).replace('\t',"").replace('\n', "")
    str_out = re.sub("r\d+", "R", str_out, count=0, flags=0).replace('\t',"").replace('\n', "")
    str_out = re.sub("v\d+", "R", str_out, count=0, flags=0).replace('\t',"").replace('\n', "")

    # replace all pramater variables with R
    str_out = re.sub("p\d+", "R", str_out, count=0, flags=0).replace('\t',"").replace('\n', "")


    str_out = re.sub("r\d+\.", "R.", str_out, count=0, flags=0).replace('\t',"").replace('\n', "")

    # remove arrays
    str_out = re.sub("\[\d*\]", "", str_out, count=0, flags=0).replace('\t',"").replace('\n', "")
    str_out = re.sub("\[.+\]", "", str_out, count=0, flags=0).replace('\t',"").replace('\n', "")

    # remove actual params, in case vialin and flowdroid have different format
    str_out = re.sub("\)\>\(.+\)", ")>()", str_out, count=0, flags=0).replace('\t',"").replace('\n', "")
    return str_out



in_csv = sys.argv[1]
results_folder = sys.argv[2]
ground_truth_folder = sys.argv[2]
analysis(in_csv, results_folder, ground_truth_folder)