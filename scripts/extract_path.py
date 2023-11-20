import os
import sys
import utils
import shutil
import statistics
from timeit import default_timer as timer
import resource
import sys

def memory_limit():
    """Limit max memory usage to half."""
    soft, hard = resource.getrlimit(resource.RLIMIT_AS)
    # Convert KiB to bytes, and divide in two to half
    resource.setrlimit(resource.RLIMIT_AS, (get_memory() * int(1024 * 3 / 4), hard))

def get_memory():
    with open('/proc/meminfo', 'r') as mem:
        free_memory = 0
        for i in mem:
            sline = i.split()
            if str(sline[0]) in ('MemFree:', 'Buffers:', 'Cached:'):
                free_memory += int(sline[1])
    return free_memory  # KiB

def method_class_stats(path):
    methods = set()
    classes = set()
    for p in path:
        if "->" in p:
            class_name, method_name = p.split("->")
            method_name = method_name.rsplit("(", 1)[0]
            methods.add(f"{class_name}, {method_name}")
            classes.add(class_name)
    return len(methods), len(classes)

def translate_file(f):
    time_now = timer()
    print(f, flush=True)
    log = utils.read_file(f)
    report, parcels = utils.get_dumptaint_report_from_log(log)
    del log

    print(f"Done processing file in {timer() - time_now}")
    time_now = timer()

    graphs, insn_stmt_map, src_insn_sink_ins_pairs, src_sink_ins_pairs, src_sink_pairs, real_source_map, sources, sinks, source_stmt_map, sink_stmt_map = utils.get_graphs_from_reports_cached(report, parcels)
    del report

    print(f"Done extracting graphs in {timer() - time_now}")
    time_now = timer()

    # print(f"Map: {insn_stmt_map}")
    # print("========")
    num_graphs = 0
    for n in graphs:
        # print(f"Graphs num: {n}")
        for g in graphs[n]:
            # print(g)
            num_graphs += 1
    print(f"Num_graphs: {num_graphs}")

    paths, divergent, path_sources, path_sinks = utils.translate_graphs_to_paths(graphs, insn_stmt_map, sources, sinks)
    del graphs

    print("========")
    print(source_stmt_map)
    print(path_sources)
    print("========")
    print(sink_stmt_map)
    print(path_sinks)

    # print(f"Paths:")
    # print("========")
    # for n in paths:
    #     print(f"Path num: {n}")
    #     for p in paths[n]:
    #         print(p)
    #         print("---------")
    #     print("========")

    print(f"Done translating graphs in {timer() - time_now}")
    time_now = timer()

    classed_dir = f.replace(".log", ".class_info")

    print(f"Will load classes: {classed_dir}", flush=True)
    loaded_classes = dict()

    if extra_folder and ("InterAppCommunication" in classed_dir):
        # print(f"Will load first extra classes", flush=True)
        extra_loaded_classes = utils.load_all_classes(extra_folder + "/InterAppCommunication_Collector.big_list.class_info/")
        loaded_classes.update(extra_loaded_classes)
        # print(f"Will load second extra classes", flush=True)
        extra_loaded_classes = utils.load_all_classes(extra_folder + "/InterAppCommunication_Echoer.big_list.class_info/")
        loaded_classes.update(extra_loaded_classes)

    paths = utils.translate_paths_to_bytecode(paths, classed_dir, loaded_classes)


    print(f"Done bytecode translation in {timer() - time_now}")
    time_now = timer()

    # print(f"Divergent: {divergent}", flush=True)
    num_divergent_unique = 0
    paths_unique = list()
    divergent_unique = list()
    path_sources_unique = list()
    path_sinks_unique = list()
    paths_numbers = list()
    for n in paths:
        for i, p in enumerate(paths[n]):
            if p not in paths_unique:
                paths_unique.append(p)
                paths_numbers.append(n)
                divergent_unique.append(divergent[n][i])
                path_sources_unique.append(path_sources[n][i])
                path_sinks_unique.append(path_sinks[n][i])
                if divergent[n][i] == True:
                    num_divergent_unique += 1


    print(f"Done removing duplicates in {timer() - time_now}")
    time_now = timer()

    # for i, path in enumerate(paths_unique):
    #     print(f"====Graph {paths_numbers[i]}====")
    #     for p in path:
    #         print(p)

    # print(f"Pair stats: {len(src_insn_sink_ins_pairs)}, {len(src_sink_ins_pairs)}, {len(src_sink_pairs)}")

    out_dir = f.replace(".log", ".paths")
    shutil.rmtree(out_dir, ignore_errors=True)
    i = 0
    for path_bytecode in paths_unique:
        path_jimple = utils.translate_paths_to_jimple(path_bytecode, real_source_map)
        # utils.print_path_consol(path_jimple)
        i += 1
        file_name = "path_" + str(i) + "_" + str(paths_numbers[i-1]) + ".csv"
        utils.print_list_reverse(out_dir, file_name, path_jimple)
        # file_name = "path_bytecode_" + str(paths_numbers[i-1]) + "_" + str(i) + ".csv"
        # utils.print_list_reverse(out_dir, file_name, path_bytecode)
        # print(path_jimple)
    with open(out_dir + "/" + "sources_sinks.csv", 'w') as f:
        f.write("path,source,source_stmt,sink,sink_stmt\n")
        i = 0
        for _ in paths_unique:
            print(f"Will write path {i}")
            i += 1
            p_src = path_sources_unique[i-1]
            p_sink = path_sinks_unique[i-1]
            print(f"{p_src} == {p_sink}")
            path_name = "path_" + str(i) + "_" + str(paths_numbers[i-1])
            try:
                f.write(path_name + "," + source_stmt_map[p_src[1]] + "," + p_src[1] + "," + sink_stmt_map[p_sink] + "," + p_sink)
                f.write("\n")
            except Exception as e:
                print(f"Exception while writing path: {e}")
                pass

    print(f"Done translating paths to jimple in {timer() - time_now}")
    time_now = timer()

    paths_num_methods = list()
    paths_num_classes = list()

    for path in paths_unique:
        num_methods, num_classes = method_class_stats(path)
        paths_num_methods.append(num_methods)
        paths_num_classes.append(num_classes)

    min_length_s_paths_no_dup = min([len(p) for p in paths_unique])
    max_length_s_paths_no_dup = max([len(p) for p in paths_unique])
    avg_length_s_paths_no_dup = sum([len(p) for p in paths_unique])/len(paths_unique)
    median_length_s_paths_no_dup = statistics.median([len(p) for p in paths_unique])


    min_methods_s_paths_no_dup = min(paths_num_methods)
    max_methods_s_paths_no_dup = max(paths_num_methods)
    avg_methods_s_paths_no_dup = sum(paths_num_methods)/len(paths_unique)
    median_methods_s_paths_no_dup = statistics.median(paths_num_methods)

    min_classes_s_paths_no_dup = min(paths_num_classes)
    max_classes_s_paths_no_dup = max(paths_num_classes)
    avg_classes_s_paths_no_dup = sum(paths_num_classes)/len(paths_unique)
    median_classes_s_paths_no_dup = statistics.median(paths_num_classes)

    print(f"Num_graphs: {num_graphs}")
    print(f"# reports: source statement instance to sink statement instance: {len(src_insn_sink_ins_pairs)}")
    # print(f"# ViaLin paths that consist of multiple routes: source statement instance to sink statement instance: {num_graphs_non_linear_paths_si}")
    print(f"# reported source statmenet to sink statement flows (ViaLin): {len(src_sink_pairs)}")
    print(f"# reported source statement to sink statement paths (ViaLin): {len(paths_unique)}")
    print(f"# ViaLin paths that combine multiple routes: source statement to sink statement: {num_divergent_unique}")
    print(f"# reports: source statmenet to sink statement instance (TD+S reports): {len(src_sink_ins_pairs)}")
    # print(f"Number of routes within a graph between source instance to sink instance pair (raw, no dup): {num_si_paths}")

    print(f"======Paths at stmt level=======")
    print(f"min_length_s_paths_no_dup: {min_length_s_paths_no_dup}")
    print(f"max_length_s_paths_no_dup: {max_length_s_paths_no_dup}")
    print(f"avg_length_s_paths_no_dup: {avg_length_s_paths_no_dup}")
    print(f"median_length_s_paths_no_dup: {median_length_s_paths_no_dup}")

    print(f"======Methods on paths at stmt level=======")
    print(f"min_methods_s_paths_no_dup: {min_methods_s_paths_no_dup}")
    print(f"max_methods_s_paths_no_dup: {max_methods_s_paths_no_dup}")
    print(f"avg_methods_s_paths_no_dup: {avg_methods_s_paths_no_dup}")
    print(f"median_methods_s_paths_no_dup: {median_methods_s_paths_no_dup}")

    print(f"======Classes on paths at stmt level=======")
    print(f"min_classes_s_paths_no_dup: {min_classes_s_paths_no_dup}")
    print(f"max_classes_s_paths_no_dup: {max_classes_s_paths_no_dup}")
    print(f"avg_classes_s_paths_no_dup: {avg_classes_s_paths_no_dup}")
    print(f"median_classes_s_paths_no_dup: {median_classes_s_paths_no_dup}")

f = sys.argv[1]
if len(sys.argv) > 2:
    extra_folder = sys.argv[2]
else:
    extra_folder = None

memory_limit()
try:
    print(f"Translating file {f}")
    translate_file(f)
except MemoryError:
    sys.stderr.write('\n\nERROR: Memory Exception\n')
    sys.exit(1)