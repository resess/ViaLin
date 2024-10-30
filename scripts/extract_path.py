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

def default_class_filter(class_name):
    return True

def translate_file(f, modeled_methods_file, base_out_dir, framework_classes_dir, extra_folder=None, class_filter=default_class_filter):
    time_now = timer()

    app_dir_name = f.split("/")[-1].replace(".log", "")

    print(f"Translating file {f}", flush=True)
    log = utils.read_file(f)
    report, parcels, sinks = utils.get_dumptaint_report_from_log(log)

    del log

    print(f"Done processing file in {timer() - time_now}", flush=True)
    time_now = timer()

    graphs, insn_stmt_map, src_insn_sink_ins_pairs, src_sink_ins_pairs, src_sink_pairs, real_source_map, sources, sinks, source_stmt_map, sink_stmt_map = utils.get_graphs_from_reports_cached(report, parcels, sinks)
    del report

    # print(f"All initial sources: {sources}")
    # print(f"All initial sinks: {sinks}")

    print(f"Done extracting graphs in {timer() - time_now}", flush=True)
    time_now = timer()

    # print(f"Map: {insn_stmt_map}")
    # print("========")
    num_graphs = 0
    for n in graphs:
        # print(f"Graphs num: {n}")
        for g in graphs[n]:
            # for node in g:
            #     print(f"{node}, {g[node]}")
            num_graphs += 1
    print(f"Num_graphs: {num_graphs}")

    os.makedirs(base_out_dir+ "/" + app_dir_name +"/graphs/", exist_ok=True)

    for n in graphs:
        for i, g in enumerate(graphs[n]):
            with open(base_out_dir+ "/" + app_dir_name +"/graphs/graph_" + str(n) + "_" + str(i) + ".log", 'w') as g_file:
                g_file.write(str(g))


    classed_dir = f.replace(".log", ".class_info")

    print(f"Will load classes from {classed_dir}", flush=True)
    loaded_classes = dict()

    if extra_folder and ("InterAppCommunication" in classed_dir):
        # print(f"Will load first extra classes", flush=True)
        extra_loaded_classes = utils.load_all_classes(extra_folder + "/InterAppCommunication_Collector.big_list.class_info/")
        loaded_classes.update(extra_loaded_classes)
        # print(f"Will load second extra classes", flush=True)
        extra_loaded_classes = utils.load_all_classes(extra_folder + "/InterAppCommunication_Echoer.big_list.class_info/")
        loaded_classes.update(extra_loaded_classes)


    bytecode_graphs = utils.translate_graphs_to_bytecode(graphs, classed_dir, framework_classes_dir, loaded_classes)

    # Get file name from f (remove path and extension)


    print(f"App dir name: {app_dir_name}")
    print(f"Full out dir: {base_out_dir}/{app_dir_name}", flush=True)

    os.makedirs(base_out_dir+ "/" + app_dir_name + "/bytecode_graphs/", exist_ok=True)

    for n in bytecode_graphs:
        for i, g in enumerate(bytecode_graphs[n]):
            # print(f"Writing graph_" + str(n) + "_" + str(i) + ".log", flush=True)
            with open(base_out_dir+ "/" + app_dir_name + "/bytecode_graphs/graph_" + str(n) + "_" + str(i) + ".log", 'w') as g_file:
                for node in g:
                    g_file.write(str(node))
                    g_file.write("\n    ")
                    g_file.write(str(g[node]))
                    g_file.write("\n")


    print(f"Will translate graphs to paths", flush=True)
    # get_sink_func = utils.get_last_sink
    get_sink_func = utils.get_first_sink
    try:
        paths, paths_info, divergent, path_sources, path_sinks, graph_src_sink_pairs = utils.translate_graphs_to_paths(graphs, bytecode_graphs, insn_stmt_map, sources, sinks, get_sink_func)
        del graphs
    except Exception as e:
        out_dir = base_out_dir+ "/" + app_dir_name + "/paths/"
        shutil.rmtree(out_dir, ignore_errors=True)
        os.makedirs(out_dir)
        path_src_sink_ins_pairs = list()
        with open(out_dir + "/" + "sources_sinks.csv", 'w') as f:
            f.write("path,source,source_stmt,sink,sink_stmt,src_instance,sink_instance,length\n")
        raise Exception(f"Exception while translating graphs to paths: {e}") from e

    # print("========")
    # print(source_stmt_map)
    # print(path_sources)
    # print("========")
    # print(sink_stmt_map)
    # print(path_sinks)

    print(f"Paths:")
    print("========")
    for n in paths:
        print(f"Path num: {n}")
        for p in paths[n]:
            print(p)
            print("---------")
        print("========")

    print(f"Done translating graphs in {timer() - time_now}")
    time_now = timer()

    paths = utils.translate_paths_to_bytecode(paths, classed_dir, framework_classes_dir, loaded_classes)


    print(f"Done bytecode translation in {timer() - time_now}")
    time_now = timer()

    # print(f"Divergent: {divergent}", flush=True)
    num_divergent_unique = 0
    paths_unique = list()
    paths_info_unique = list()
    divergent_unique = list()
    path_sources_unique = list()
    path_sinks_unique = list()
    paths_numbers = list()

    seen_sources = set()
    for n in paths:
        for i, p in enumerate(paths[n]):
            if p[0] in seen_sources:
                # print(f"Path start already seen: {p[0]}")
                continue
            seen_sources.add(p[0])
            if p not in paths_unique:
                # insert in list but keep ordereing by size
                index = len(paths_unique)
                for committed_path in paths_unique:
                    if len(committed_path) > len(p):
                        index = paths_unique.index(committed_path)
                        break
                seen_sources.add(p[0])
                paths_unique.insert(index, p)
                paths_numbers.insert(index, n)
                paths_info_unique.insert(index, paths_info[n][i])
                divergent_unique.insert(index, divergent[n][i])
                path_sources_unique.insert(index, path_sources[n][i])
                path_sinks_unique.insert(index, path_sinks[n][i])
                if divergent[n][i] == True:
                    num_divergent_unique += 1

    paths_index_to_remove = list()
    for i, p in enumerate(paths_unique):
        path_src = path_sources_unique[i]
        path_sink = path_sinks_unique[i]
        for j in range(i+1, len(paths_unique)):
            other_path_src = path_sources_unique[j]
            other_path_sink = path_sinks_unique[j]
            if path_src == other_path_src and path_sink < other_path_sink:
                paths_index_to_remove.append(j)

    print(f"Will remove {len(paths_index_to_remove)} paths that go after their sink")
    paths_unique = [p for i, p in enumerate(paths_unique) if i not in paths_index_to_remove]
    paths_info_unique = [p for i, p in enumerate(paths_info_unique) if i not in paths_index_to_remove]
    path_sources_unique = [p for i, p in enumerate(path_sources_unique) if i not in paths_index_to_remove]
    path_sinks_unique = [p for i, p in enumerate(path_sinks_unique) if i not in paths_index_to_remove]
    paths_numbers = [p for i, p in enumerate(paths_numbers) if i not in paths_index_to_remove]
    divergent_unique = [p for i, p in enumerate(divergent_unique) if i not in paths_index_to_remove]

    # print the start of each path
    for i, p in enumerate(paths_unique):
        print(f"Path {i}: {p[0:10]}")

    print(f"Done removing duplicates in {timer() - time_now}")
    time_now = timer()

    # for i, path in enumerate(paths_unique):
    #     print(f"====Graph {paths_numbers[i]}====")
    #     for p in path:
    #         print(p)

    # print(f"Pair stats: {len(src_insn_sink_ins_pairs)}, {len(src_sink_ins_pairs)}, {len(src_sink_pairs)}")

    out_dir = base_out_dir+ "/" + app_dir_name + "/paths/"
    shutil.rmtree(out_dir, ignore_errors=True)
    os.makedirs(out_dir)

    out_dir_bytecode = base_out_dir+ "/" + app_dir_name + "/paths_bytecode/"
    shutil.rmtree(out_dir_bytecode, ignore_errors=True)
    os.makedirs(out_dir_bytecode)

    i = 0
    for path_bytecode in paths_unique:
        path_jimple = utils.translate_paths_to_jimple(path_bytecode, real_source_map, class_filter)
        # utils.print_path_consol(path_jimple)
        i += 1
        p_src = path_sources_unique[i-1]
        p_sink = path_sinks_unique[i-1]
        file_name = "path_" + str(i) + "_" + str(paths_numbers[i-1]) + "_" + str(p_src[0]) + "_" + str(p_sink[1][0]) + ".csv"
        utils.print_list(out_dir, file_name, path_jimple)
        utils.print_list(out_dir_bytecode, file_name, path_bytecode)
        # file_name = "path_bytecode_" + str(paths_numbers[i-1]) + "_" + str(i) + ".csv"
        # utils.print_list_reverse(out_dir, file_name, path_bytecode)
        # print(path_jimple)

    path_src_sink_ins_pairs = list()
    with open(out_dir + "/" + "sources_sinks.csv", 'w') as f:
        f.write("path,source,source_stmt,sink,sink_stmt,src_instance,sink_instance,length\n")
        i = 0
        for _ in paths_unique:
            # print(f"Will write path {i}")
            i += 1
            p_src = path_sources_unique[i-1]
            p_sink = path_sinks_unique[i-1]
            # print(f"{p_src} == {p_sink}")
            path_src_sink_ins_pairs.append((p_src[0], p_sink[1][0]))
            path_name = "path_" + str(i) + "_" + str(paths_numbers[i-1]) + "_" + str(p_src[0]) + "_" + str(p_sink[1][0])
            # try:
            f.write(path_name + "," + source_stmt_map[p_src[1]] + "," + p_src[1] + "," + sink_stmt_map[p_sink[0]] + "," + p_sink[0] + "," + str(p_src[0]) + "," + str(p_sink[1][0]) + "," + str(len(paths_unique[i-1])))
            f.write("\n")
            # except Exception as e:
            #     print(f"Exception while writing path: {e}")
            #     pass

    print(f"Done translating paths to jimple in {timer() - time_now}")
    time_now = timer()

    paths_num_methods = list()
    paths_num_classes = list()

    for path in paths_unique:
        num_methods, num_classes = method_class_stats(path)
        paths_num_methods.append(num_methods)
        paths_num_classes.append(num_classes)

    if len(paths_unique) == 0:
        print("No paths found")
        return

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

    class_info = classed_dir, framework_classes_dir, loaded_classes

    return bytecode_graphs, paths_info_unique, path_src_sink_ins_pairs, graph_src_sink_pairs, src_insn_sink_ins_pairs, sources, class_info


if __name__ == "__main__":

    f = sys.argv[1]
    modeled_methods_file = sys.argv[2]
    framework_classes_dir = sys.argv[3]
    base_out_dir = sys.argv[4]
    if len(sys.argv) > 5:
        extra_folder = sys.argv[5]
    else:
        extra_folder = None

    memory_limit()
    try:
        translate_file(f, modeled_methods_file, base_out_dir, framework_classes_dir, extra_folder)
    except MemoryError:
        sys.stderr.write('\n\nERROR: Memory Exception\n')
        sys.exit(1)