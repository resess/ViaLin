import sys
import os
import subprocess
import math
import numpy as np
from scipy.stats import shapiro
from scipy.stats import lognorm
import xlsxwriter
import json
from multiprocessing import Pool
from timeit import default_timer as timer


def get_all_files(mypath):
    # list all files
    # https://stackoverflow.com/questions/3207219/how-do-i-list-all-files-of-a-directory
    files = [os.path.join(mypath, f) for f in os.listdir(mypath) if os.path.isfile(os.path.join(mypath, f))]
    return files


def run_proc_get_out(command):
    result = subprocess.run(command.split(" "), stdout=subprocess.PIPE)
    return result.stdout.decode('utf-8')

def write_to_file(string_to_write, file_name):
    with open(file_name, 'w') as f:
        f.write(string_to_write)

def read_file(file_name):
    data = list()
    with open(file_name, 'r') as f:
        for l in f:
            data.append(l.strip())
    return data

def p_value(data):
    return shapiro(data).pvalue


def write_to_excel(string_to_write, workbook, sheet_name):
    worksheet = workbook.add_worksheet(sheet_name)
    rows = string_to_write.split("\n")
    for row_id in range(0, len(rows)):
        row = rows[row_id]
        cols = row.split(",")
        for col_id in range(0, len(cols)):
            worksheet.write(row_id, col_id, cols[col_id])

def get_dumptaint_report_from_log(log):
    report = []
    parcels = []
    for line in log:
        if "DumpTaint for parcel: " in line:
            parcels.append(int(line.split("DumpTaint for parcel: ")[1]))
        if "DumpTaint for file: " in line:
            parcels.append(int(line.split("DumpTaint for file: ")[1]))
        if "DumpTaint-" in line:
            report.append(line.split("System.out: ")[1])
        if "SourceFound:" in line:
            report.append(line.split("System.out: ")[1])
        if "SinkFound:" in line:
            report.append(line.split("System.out: ")[1])
            # print(f"Valid sink: {line}")
    return report, parcels


def get_debug_paths(log):
    path = []
    for line in reversed(log):
        if "PathTaint:" in line:
            if "PathTaint: CreateTaint: " in line:
                path.append(line.split("PathTaint: CreateTaint: ")[1])
            if "PathTaint: Propagate: " in line:
                path.append(line.split("PathTaint: Propagate: ")[1].split(" <- ")[0])
    return {0: [path]}


def in_framework(stmt):
    stmt = stmt.split("(")[0]
    if stmt.startswith("Ljava") or stmt.startswith("Landroid") or stmt.startswith("Lcom/google") or stmt.startswith("Lcom/android") or stmt.startswith("Lkotlin"):
        return True
    return False


# def get_graphs_from_reports_threaded(report):
#     graphs_lines = dict()
#     sources = set()
#     real_source_map = dict()
#     insn_stmt_map = dict()
#     for line in report:
#         if "SourceFound: " in line:
#             if "injectTaintSeedIfReflectiveSource" in line:
#                 source_stmt = line.split(" ")[-1]
#                 class_name = line.split("->")[0].split(" ")[-1]
#                 real_source_map[class_name] = source_stmt
#         if "DumpTaint-" in line:
#             try:
#                 start, tail = line.split(": ->")
#                 num = int(start.split("-")[1])
#                 node, left = tail.split("->left->")
#                 if "->right->" in left:
#                     left, right = left.split("->right->")
#                 else:
#                     right = None
#                 if num not in graphs_lines:
#                     graphs_lines[num] = list()
#                 if [node, left, right] not in graphs_lines[num]:
#                     graphs_lines[num].append([node, left, right])
#             except:
#                 print(f"Ignored malformed line: {line}" )

#     src_insn_sink_ins_pairs = set()
#     src_sink_ins_pairs = set()
#     src_sink_pairs = set()

#     pair_cache = dict()
#     graphs = dict()

#     for n in graphs_lines:
#         sink_insn = None
#         source_insn = None
#         graph_rev = dict()
#         stmt1, insn1, stmt2, insn2 = [None, None, None, None]
#         # print(f"Graph lines of {n}:")
#         for l in graphs_lines[n]:
#             node, left, right = l
#             last_stmt1, last_insn1 = stmt1, insn1
#             last_stmt2, last_insn2 = stmt2, insn2
#             stmt1, insn1 = parse_node(node)
#             stmt2, insn2 = parse_node(left)
#             # print(f"    {l}")
#             if last_stmt2 and stmt1 and (last_stmt2 != stmt1) and in_framework(last_stmt2) and in_framework(stmt1):
#                 # print(f"Mismatch {last_stmt2} <--> {stmt1}")
#                 stmt1, insn1 = last_stmt2, last_insn2
#             if not sink_insn:
#                 sink_insn = insn1
#             if stmt2 == "STARTPATH" and not right:
#                 sources.add((insn2, insn_stmt_map[insn2]))
#                 source_insn = insn2
#                 src_insn_sink_ins_pairs.add((source_insn, sink_insn))
#                 src_sink_ins_pairs.add((stmt1, sink_insn))
#                 src_sink_pairs.add((stmt1, insn_stmt_map[sink_insn]))
#                 # print(f"Adding src: {(source_insn, sink_insn)}, {(stmt1, sink_insn)}, {(stmt1, insn_stmt_map[sink_insn])}")
#                 # print(f"Adding src: {stmt1}:{source_insn}")
#             elif stmt2 != "STARTPATH":
#                 graph_node = (insn2, stmt2)
#                 if graph_node in pair_cache:
#                     graph_node = pair_cache[graph_node]
#                 else:
#                     pair_cache[graph_node] = graph_node

#                 graph_neighbour = (insn1, stmt1)
#                 if graph_neighbour in pair_cache:
#                     graph_neighbour = pair_cache[graph_neighbour]
#                 else:
#                     pair_cache[graph_neighbour] = graph_neighbour

#                 add_to_graph(graph_rev, graph_node, graph_neighbour)
#                 insn_stmt_map[insn1] = stmt1
#                 insn_stmt_map[insn2] = stmt2

#             if right:
#                 # print(right)
#                 stmt3, insn3 = parse_node(right)

#                 graph_node = (insn3, stmt3)
#                 if graph_node in pair_cache:
#                     graph_node = pair_cache[graph_node]
#                 else:
#                     pair_cache[graph_node] = graph_node

#                 graph_neighbour = (insn1, stmt1)
#                 if graph_neighbour in pair_cache:
#                     graph_neighbour = pair_cache[graph_neighbour]
#                 else:
#                     pair_cache[graph_neighbour] = graph_neighbour


#                 add_to_graph(graph_rev, graph_node, graph_neighbour)
#                 insn_stmt_map[insn1] = stmt1
#                 insn_stmt_map[insn3] = stmt3

#         graphs[n] = split_graphs_by_source_statement_instance(graph_rev, sources)
#         del graphs_lines[n][:]
#         del graph_rev
#     del graphs_lines

#     # print(f"Graphs after splitting by source: ", flush=True)
#     return graphs, insn_stmt_map, src_insn_sink_ins_pairs, src_sink_ins_pairs, src_sink_pairs, real_source_map


def fix_graphs_from_cache(graphs_lines, graph_cache):
    # print(len(graphs_lines))
    line_cache = dict()
    new_graphs = dict()
    for num in graphs_lines:
        result = fix_graphs_from_cache_task((graphs_lines, graph_cache, num, line_cache))
        num, graph = result
        new_graphs[num] = graph

    # with Pool() as pool:
    #     for result in pool.map(fix_graphs_from_cache_task, [(graphs_lines, graph_cache, num) for num in graphs_lines]):
    #         num, graph = result
    #         new_graphs[num] = graph

    return new_graphs

def fix_graphs_from_cache_task(task_input):
    start = timer()
    graphs_lines, graph_cache, num, line_cache = task_input
    # print(f"Graph: {num} started with {len(graphs_lines[num])} lines", flush=True)
    new_graph = list()
    num_iters = 0
    visited_lines = set()
    processed = set()

    i = 0
    for l in graphs_lines[num]:
        i += 1
        if l in visited_lines:
            continue
        new_graph.append(l)
        visited_lines.add(l)
        to_process_stack = [l[1], l[2]]
        while len(to_process_stack):
            num_iters += 1
            to_process = to_process_stack.pop(0)
            if to_process in graph_cache and to_process not in processed:
                processed.add(to_process)
                cached_line = graph_cache[to_process]
                left_cached = cached_line["left"]
                right_cached = cached_line["right"]
                new_line = (to_process, left_cached, right_cached)

                # Avoid duplicate lines, to save memory
                if new_line in line_cache:
                    new_line = line_cache[new_line]
                else:
                    line_cache[new_line] = new_line

                if new_line not in visited_lines:
                    new_graph.append(new_line)
                    visited_lines.add(l)
                    if left_cached:
                        to_process_stack.append(left_cached)
                    if right_cached:
                        to_process_stack.append(right_cached)

    print(f"Graph: {num} had {len(graphs_lines[num])} lines and now has {len(new_graph)} lines, iterated {num_iters} times", flush=True)
    graphs_lines[num] = list() # erase old list
    return num, new_graph


def get_parcel_graph(num, graphs_lines, new_graphs):

    if num in new_graphs:
        return
    last_line = None
    new_graph = list()
    for l in graphs_lines[num]:
        stmt = l[0]
        if "(-2)" in stmt: # parcels
            parcel_nums = [int(x) for x in stmt.split("(")[0].split("-")]
            for parcel_num in parcel_nums:
                corrected_parcel_stmt = last_line[1]
                if "(-2)" in corrected_parcel_stmt or corrected_parcel_stmt.startswith("STARTPATH"):
                    corrected_parcel_stmt = last_line[2]
                if not corrected_parcel_stmt:
                    corrected_parcel_stmt = last_line[0]
                if parcel_num in graphs_lines:
                    # print(f"--------------")
                    # print(f"PPeParcelLine : {new_graph[-3]}")
                    # print(f"PreParcelLine : {new_graph[-2]}")
                    # print(f"OldParcelLine : {stmt}")
                    # print(f"NewParcelLine : {new_parcel_line}")
                    # print(f"PostParcelLine: {graphs_lines[parcel_num][0]}")
                    get_parcel_graph(parcel_num, graphs_lines, new_graphs)
                    new_parcel_line = (corrected_parcel_stmt, new_graphs[parcel_num][0][0], None)
                    new_graph.append(new_parcel_line)
                    for m in new_graphs[parcel_num]:
                        new_graph.append(m)
        else: # regular stmt
            new_graph.append(l)
        last_line = l

    graphs_lines[num] = list() # erase to save memory
    new_graphs[num] = new_graph
    return new_graph


def complete_graphs_from_parcels(graphs_lines):
    new_graphs = dict()
    for num in graphs_lines:
        # print(f"This graph {num} used to have first statement {graphs_lines[num][0]}")
        get_parcel_graph(num, graphs_lines, new_graphs)
        # print(f"This graph {num} now has first statement {new_graphs[num][0]}")
    return new_graphs

def get_graphs_from_reports_cached(report, parcels):
    sinks_found = dict()
    graphs_lines = dict()
    graph_cache = dict()
    graphs_rev = dict()
    sources = dict()
    sources_ids = dict()
    real_source_map = dict()
    insn_stmt_map = dict()
    sink_line = None
    source_stmt_map = dict()
    sink_stmt_map = dict()
    print("Extracting lines")
    for line in report:
        if "SinkFound:" in line:
            sink_line = line.split("SinkFound: ")[1].split(", ")[0]
            print(f"SinkFound:     {sink_line}")
            sink_stmt_map[sink_line] = line.split("SinkFound: ")[1].split(", ")[1]
        if "SourceFound: " in line:
            print(f"SourceFound line: {line}")
            if "injectTaintSeedIfReflectiveSource" in line:
                source_stmt = line.split(" ")[-1]
                class_name = line.split("->")[0].split(" ")[-1]
                real_source_map[class_name] = source_stmt
                s2 = line.split("SourceFound: ")[1].split(", ")[1]
            else:
                print(line)
                try:
                    sources_ids[int(line.split(")id(")[1].replace(")", ""))] = None
                    s1 = line.split("SourceFound: ")[1].split(", ")[0].split(")id(")[0] + ")"
                    source_stmt_map[s1] = s2
                except:
                    s2 = line.split("SourceFound: ")[1].split(", ")[1]
        if "DumpTaint-" in line:
            try:
                start, tail = line.split(": ->")
                num = int(start.split("-")[1])
                if sink_line and num not in sinks_found:
                    sinks_found[num] = sink_line
                node, left = tail.split("->left->")
                if "->right->" in left:
                    left, right = left.split("->right->")
                else:
                    right = None
                if num not in graphs_lines:
                    graphs_lines[num] = list()
                if (node, left, right) not in graphs_lines[num]:
                    graphs_lines[num].append((node, left, right))

                graph_cache[node] = {"left": left, "right" : right}
                # if num == 1042804163:
                #     print(graph_cache)
                #     input("Press enter to continue")
            except:
                print(f"Ignored malformed line: {line}" )

    del report

    print("Extending graphs from cache")
    graphs_lines = fix_graphs_from_cache(graphs_lines, graph_cache)

    graphs_lines = complete_graphs_from_parcels(graphs_lines)

    num_removed_parcels = 0
    for p in parcels:
        # print(p)
        if p in graphs_lines:
            num_removed_parcels += 1
            del graphs_lines[p]
    del parcels

    print(f"Deleted {num_removed_parcels} parcels")

    # print("Graphs after fixing parcels")

    # for n in graphs_lines:
    #     print(f"=====Graph {n}=====")
    #     for l in graphs_lines[n]:
    #         print(l)
    print(f"Sinks found: {sinks_found}")

    src_insn_sink_ins_pairs = dict()
    src_sink_ins_pairs = dict()
    src_sink_pairs = dict()
    graphs = dict()
    pair_cache = dict()

    print(f"Source IDs: {sources_ids}")
    for n in graphs_lines:
        graph_rev = dict()
        sink_insn = None
        source_insn = None
        stmt1, insn1, stmt2, insn2 = [None, None, None, None]
        print(f"Graph lines of {n}: num lines = {len(graphs_lines[n])}", flush = True)
        time_now = timer()
        for i, l in enumerate(graphs_lines[n]):
            node, left, right = l
            last_stmt1, last_insn1 = stmt1, insn1
            last_stmt2, last_insn2 = stmt2, insn2

            stmt1, insn1 = parse_node(node)
            stmt2, insn2 = parse_node(left)
            if right:
                stmt3, insn3 = parse_node(right)
            else:
                stmt3, insn3 = None, None
            # print(f"    {l}")
            if last_stmt2 and stmt1 and (last_stmt2 != stmt1) and in_framework(last_stmt2) and in_framework(stmt1):
                # print(f"Mismatch {last_stmt2} <--> {stmt1}")
                stmt1, insn1 = last_stmt2, last_insn2
            if not sink_insn:
                sink_insn = insn1
            if stmt2 != "STARTPATH":
                new_node = (insn2, stmt2)
                new_neighbor = (insn1, stmt1)

                if new_node in pair_cache:
                    new_node = pair_cache[new_node]
                else:
                    pair_cache[new_node] = new_node

                if new_neighbor in pair_cache:
                    new_neighbor = pair_cache[new_neighbor]
                else:
                    pair_cache[new_neighbor] = new_neighbor

                add_to_graph(graph_rev, new_node, new_neighbor)
                insn_stmt_map[insn1] = stmt1
                insn_stmt_map[insn2] = stmt2

            if insn1 in sources_ids:
                print(f"Adding to sources: {l}")
                sources[(insn1, insn_stmt_map[insn1])] = None
                source_insn = insn1
                src_insn_sink_ins_pairs[(source_insn, sink_insn)] = None
                src_sink_ins_pairs[(stmt1, sink_insn)] = None
                src_sink_pairs[(stmt1, insn_stmt_map[sink_insn])] = None
                # print(f"Adding src: {(source_insn, sink_insn)}, {(stmt1, sink_insn)}, {(stmt1, insn_stmt_map[sink_insn])}")
                # print(f"Adding src: {stmt1}:{source_insn}")
            if right:
                new_node = (insn3, stmt3)
                new_neighbor = (insn1, stmt1)

                if new_node in pair_cache:
                    new_node = pair_cache[new_node]
                else:
                    pair_cache[new_node] = new_node

                if new_neighbor in pair_cache:
                    new_neighbor = pair_cache[new_neighbor]
                else:
                    pair_cache[new_neighbor] = new_neighbor

                add_to_graph(graph_rev, new_node, new_neighbor)
                insn_stmt_map[insn1] = stmt1
                insn_stmt_map[insn3] = stmt3

        graphs[n] = split_graphs_by_source_statement_instance(graph_rev, sources)
        del graph_rev
        del graphs_lines[n][:]

    del graphs_lines


    # print(f"Graphs after splitting by source: ", flush=True)
    return graphs, insn_stmt_map, src_insn_sink_ins_pairs, src_sink_ins_pairs, src_sink_pairs, real_source_map, sources, sinks_found, source_stmt_map, sink_stmt_map


def split_graphs_by_source_statement_instance(graph_rev, sources):
    graphs = list()

    for src in sources:
        print(f"Will split graph for source: {src}")
        # print(graph_rev)
        if src in graph_rev:
            print(f"{src} in graph")
            graphs.append(traverse_graph_from_stmt_inst_and_reverse(graph_rev, src))
    return graphs


def traverse_graph_from_stmt_inst_and_reverse(graph_rev, src):
    graph = dict()
    to_visit = list()
    to_visit.append(src)
    visited = set()
    # print(f"Src: {src}", flush=True)
    while len(to_visit):
        n = to_visit.pop()
        if n in visited:
            continue
        visited.add(n)
        # print(f"    visiting: {n}")
        if n in graph_rev:
            # print(f"    next nodes are: {graph_rev[n]}")
            for next_node in graph_rev[n]:
                if next_node not in graph:
                    graph[next_node] = set()
                graph[next_node].add(n)
                to_visit.append(next_node)
    # print(f"Graph: {graph}", flush=True)
    # print(f"--------------", flush=True)
    return graph


def parse_node(node):
    if "STARTPATH" in node:
        insn = node.split("STARTPATH(")[1]
        stmt = "STARTPATH"
    else:
        # print(node)
        try:
            stmt, insn = node.rsplit("id(", 1)
        except:
            print(f"node: {node}")
        stmt, insn = node.rsplit("id(", 1)
    insn = int(insn.split(")")[0])
    return stmt, insn


def add_to_graph(graph, node, neighbour):
    if node not in graph:
        graph[node] = dict()
    graph[node][neighbour] = None
    # print(f"   Adding to graph {node} ===> {neighbour}")


def get_graphs_from_reports_ordered(report):
    graphs = list()
    num = 0
    g = dict()
    for line in report:
        if "SinkFound" in line and len(g):
            graphs.append(g)
            num += 1
            g = dict()
        if "DumpTaint-" in line and "STARTPATH" not in line:
            start, tail = line.split(": ->")
            num = int(start.split("-")[1])
            # print(line)
            # print("--------")
            # print(tail)
            node, left = tail.split("->left->")
            if "->right->" in left:
                left, right = left.split("->right->")
            else:
                right = None

            stmt1, insn1 = parse_node(node)
            stmt2, insn2 = parse_node(left)
            add_to_graph(g, insn1, insn2)
            if right:
                stmt3, insn3 = parse_node(right)
                add_to_graph(g, insn1, insn3)


    if g:
        graphs.append(g)

    return graphs


def translate_graphs_to_paths(graphs, insn_stmt_map, sources, sinks):
    paths = dict()
    divergent = dict()
    path_sources = dict()
    path_sinks = dict()
    for n in graphs:
        translate_to_path_task(graphs, insn_stmt_map, sources, sinks, paths, divergent, path_sources, path_sinks, n)
    # with Pool() as pool:
    #     pool.map(translate_to_path_task_expand, [(graphs, insn_stmt_map, sources, sinks, paths, divergent, path_sources, path_sinks, n) for n in graphs])

    # print(f"Translated paths: {paths}", flush=True)
    # print(f"Divergent: {divergent}", flush=True)
    return paths, divergent, path_sources, path_sinks

# def translate_to_path_task_expand(task_input):
#     translate_to_path_task(task_input[0], task_input[1], task_input[2], task_input[3],
#                            task_input[4], task_input[5], task_input[6], task_input[7], task_input[8])

def translate_to_path_task(graphs, insn_stmt_map, sources, sinks, paths, divergent, path_sources, path_sinks, n):
    paths[n] = []
    divergent[n] = []
    path_sources[n] = []
    path_sinks[n] = []
    print(f"Will translate graphs for: {n}", flush=True)
    for g in graphs[n]:
            # p, div = translate_single_graphs_to_paths_from_sink(n, g, insn_stmt_map, sinks[n])
        sink = get_sink(g, sinks[n])
        for source in sources:
                # print(f"Source: {source} -- Sink: {sink}")
            p, div = translate_single_graphs_to_paths_from_src(n, g, insn_stmt_map, source, sink)
                # p = combine_src_sink_path(p_src, p_sink)
            if p:
                    # print("    Found a graph")
                paths[n].append(p)
                path_sources[n].append(source)
                path_sinks[n].append(sinks[n])
                divergent[n].append(div)
            else:
                    # print("    Didn't find a graph")
                pass

# def translate_single_graphs_to_paths_from_sink(num, graph, insn_stmt_map, sinks_found):
#     sink = get_sink(graph, sinks_found)
#     to_visit = list()
#     to_visit.append(sink)
#     path = list()
#     visited_si = set()
#     path_set = set(path)
#     graph_set = set(graph)
#     path_is_divergant = False
#     while len(to_visit):
#         n = to_visit.pop(0)
#         visited_si.add(n)
#         if n[1] not in path_set:
#             path.append(n[1])
#             path_set.add(n[1])
#         if n in graph_set:
#             if len(graph[n]) > 1:
#                 path_is_divergant = True
#             for next_node in graph[n]:
#                 if next_node not in visited_si and next_node not in to_visit:
#                     to_visit.append(next_node)
#         else:
#             path.remove(n[1])
#             path.append(n[1])

#     return path, path_is_divergant


def translate_single_graphs_to_paths_from_src(num, graph, insn_stmt_map, source, sink):
    graph_rev = reverse_graph(graph)

    to_visit = list()
    visited_si = set()

    if source in graph_rev:
        to_visit.append(source)
        visited_si.add(source[0])

    time_dict = dict()
    path_is_divergant = False
    while len(to_visit):
        n = to_visit.pop(0)
        if n[1] not in time_dict:
            time_dict[n[1]] = n[0] # Map statement to first time it is executed
        if n == sink:
            continue
        if n in graph_rev:
            if len(graph_rev[n]) > 1:
                path_is_divergant = True
            for next_node in graph_rev[n]:
                if next_node[0] not in visited_si:
                    to_visit.append(next_node)
                    visited_si.add(next_node[0])

    # print(f"======Path {num}:======")
    # print(f"  Source: {source}")
    # print(f"  Source: {sink}")
    timed_path = sorted(time_dict.items(), key=lambda x: x[1])
    path = list()
    for p in timed_path:
        path.append(p[0])
        # print(f"    {p[1]} -- {p[0]}")
        if p[0] == sink[1]:
            break

    path.reverse()
    return path, path_is_divergant


def combine_src_sink_path(p_src, p_sink):
    path_from_src = list()
    path_from_sink = list()
    for i in range(max(len(p_src), len(p_sink))):
        p1 = p_src[i] if i < len(p_src) else None
        p2 = p_sink[i] if i < len(p_sink) else None
        if p1 and p1 not in path_from_src and p1 not in path_from_sink:
            path_from_src.append(p1)
        if p2 and p2 not in path_from_src and p2 not in path_from_sink:
            path_from_sink.append(p2)
    path = path_from_sink + [p for p in reversed(path_from_src)]
    return path

def reverse_graph(graph):
    graph_rev = dict()
    for node in graph:
        for neighbour in graph[node]:
            if neighbour not in graph_rev:
                graph_rev[neighbour] = list()
            graph_rev[neighbour].append(node)
    return graph_rev

def get_sink(graph, sink_found):
    method_name = sink_found.split("->")[1].split("(")[0]
    class_name = "L" + sink_found.split("->")[0].replace(".", "/") + ";"
    sinks = list()
    vals = set()
    for k in graph:
        # k_method_name = k[1].split("->")[1].split("(")[0]
        # k_class_name = k[1].split("->")[0]
        # if method_name == k_method_name and class_name == k_class_name:
        #     print(f"SinkFoundInGraph: {k}")
        for n in graph[k]:
            vals.add(n)
    for k in graph:
        if k not in vals:
            k_method_name = k[1].split("->")[1].split("(")[0]
            k_class_name = k[1].split("->")[0]
            if method_name == k_method_name and class_name == k_class_name:
                return k
            sinks.append(k)
    # print(f"Sink list: {sinks}")
    # print(f"Sinks found: {sink_found}")
    return sinks[0]
    raise Exception("Can't find a sink!")


def translate_paths_to_bytecode(paths, classed_dir, loaded_classes):
    # print(f"loaded_classes: ")
    # print(loaded_classes)
    jimple_paths = dict()
    # print("Paths:")
    # print(f"There are {len(paths)} paths", flush=True)
    for n in paths:
        print(f"Translating paths for {n}", flush=True)
        # print(f"Processing path #{n}, there are {len(paths[n])} sub-paths", flush=True)
        jimple_paths[n] = list()
        for idx, p in enumerate(paths[n]):
            # print(f"There are {len(p)} statements in this sub-path", flush=True)
            convereted_path = list()
            for stmt in p:
                # print(stmt)
                if ";->" in stmt:
                    # print(f"Appending regular statement", flush=True)
                    bytecode_stmt = stmt_to_bytecode(stmt, classed_dir, loaded_classes)
                    if bytecode_stmt:
                        convereted_path.append(bytecode_stmt)

            jimple_paths[n].append(convereted_path)

        del paths[n][:]
    return jimple_paths


def stmt_to_bytecode(stmt, classed_dir, loaded_classes):
    line_num = -1
    class_name, rest = stmt.split(";->")
    class_name = class_name.replace("_", "/")
    method_name, rest = rest.rsplit("(", 1)
    bytecode_line_num = rest.split(")")[0]
    found = load_class_lazy(class_name, classed_dir, loaded_classes)
    if not found:
        return None
    method_code = loaded_classes[class_name][method_name]
    if bytecode_line_num in method_code:
        bytecode = method_code[bytecode_line_num]
        if "move-result" in bytecode:
            bytecode = bytecode + "=" + method_code[str(int(bytecode_line_num)-1)]
        elif (str(int(bytecode_line_num)+1) in method_code) and ("move-result" in method_code[str(int(bytecode_line_num)+1)]):
            bytecode = method_code[str(int(bytecode_line_num)+1)] + "=" + bytecode
    else:
        # TODO: fix this
        # raise Exception("Cannot find the bytecode for line: "+ bytecode_line_num)
        for b in method_code:
            bytecode = method_code[b]
    return (class_name+";", method_name, bytecode_line_num, line_num, bytecode)

def load_all_classes(folder):
    loaded_classes = dict()
    files = get_all_files(folder)
    for path in files:
        class_name = path.split("/")[-1].replace(".json", "").replace("_", "/")
        # print(f"loaded class {class_name}")
        with open(path, 'r') as f:
            loaded_classes[class_name] = json.load(f)
        # print(f"loaded content: {loaded_classes[class_name]}")
    return loaded_classes

def load_class_lazy(class_name, folder, loaded_classes):
    try:
        if class_name not in loaded_classes:
            path = folder + "/" + class_name.replace("/", "_") + ".json"
            # print(f"loaded class {class_name}")
            with open(path, 'r') as f:
                loaded_classes[class_name] = json.load(f)
            # print(f"loaded content: {loaded_classes[class_name]}")
        return True
    except:
        return False

def convert_type_from_bytecode_to_jimple(old_type):

    num_arr = 0
    while old_type.startswith("["):
        old_type = old_type[1:]
        num_arr += 1

    if old_type == "V":
        new_type = "void"
    elif old_type == "B":
        new_type = "byte"
    elif old_type == "S":
        new_type = "short"
    elif old_type == "I":
        new_type = "int"
    elif old_type == "J":
        new_type = "long"
    elif old_type == "D":
        new_type = "double"
    elif old_type == "F":
        new_type = "float"
    elif old_type == "Z":
        new_type = "boolean"
    elif old_type == "C":
        new_type = "char"
    elif old_type.startswith("L"):
        new_type = old_type[1:-1].replace("/", ".")
    else:
        raise Exception("Un-supported type: "+ old_type)
    while num_arr > 0:
        new_type = new_type + "[]"
        num_arr -= 1

    return new_type


def convert_param_list_from_bytecode_to_jimple(params):
    params_list = []
    new_param = ""
    is_object = False
    for c in params:
        if c == "[":
            new_param += c
            new_param = ""
        elif (not is_object) and c == "L":
            new_param = "L"
            is_object = True
        elif is_object:
            new_param += c
        elif not is_object:
            new_param += c
            params_list.append(new_param)
            new_param = ""
        if c == ";":
            params_list.append(new_param)
            new_param = ""
            is_object = False
    # print(f"paramlist: {params_list}")
    return params_list


def convert_bytecode_stmt_to_jimple(line):
    if "=" in line:
        assigned_var, line = line.split("=")
        assigned_var = assigned_var.strip().split(" ")[1] + " = "
    else:
        assigned_var = ""
    # print(line)
    line = line.strip().split(" ")
    instruction = line[0]
    jimple_line = assigned_var
    if instruction.startswith("invoke"):
        kind = instruction.split("-")[1]
        kind = kind.replace("/range", "")
        if kind == "direct":
            kind = "special"
        jimple_instr =  kind+"invoke"
        jimple_line += jimple_instr + " "
        if kind != "static":
            receiver_reg = line[1].replace("{", "").replace("}", "").replace(",", "")
            jimple_line += receiver_reg + "."
        paramsRegs = " ".join(line[1:-1]).replace("},", "").replace("{", "")
        if ".." in paramsRegs:
            # print(line)
            first_reg = int(line[1][2:])
            last_reg = int(line[3][1:].replace("},", ""))
            range_params = list()
            for i in range(first_reg, last_reg+1):
                range_params.append("v" + str(i))

            if kind == "static": # remove first regsiter if it is the receiver
                range_params.pop(0)

            paramsRegs = ", ".join(range_params)
        desc = line[-1]
        class_name, method_name = desc.split("->")
        method_name, params = method_name.split("(")
        params, ret = params.split(")")
        jimple_class = convert_type_from_bytecode_to_jimple(class_name)
        jimple_params = ",".join([convert_type_from_bytecode_to_jimple(x) for x in convert_param_list_from_bytecode_to_jimple(params)])
        jimple_ret = convert_type_from_bytecode_to_jimple(ret)
        jimple_line += "<" + jimple_class + ": " + jimple_ret + " " + method_name + "(" + jimple_params + ")>(" + paramsRegs + ")"
        return jimple_line
    if instruction.startswith("iget") or instruction.startswith("sget"):
        jimple_line += line[1].replace(",", "") + " = "
        if instruction.startswith("iget"):
            jimple_line += line[2].replace(",", "") + "."
        desc = line[-1]
        class_name, field_name = desc.split("->")
        field_name, field_type = field_name.split(":")
        jimple_class = convert_type_from_bytecode_to_jimple(class_name)
        jimple_type = convert_type_from_bytecode_to_jimple(field_type)
        jimple_line += "<" + jimple_class + ": " + jimple_type + " " + field_name + ">"
        return jimple_line
    if instruction.startswith("iput") or instruction.startswith("sput"):
        if instruction.startswith("iput"):
            jimple_line += line[2].replace(",", "") + "."
        desc = line[-1]
        class_name, field_name = desc.split("->")
        field_name, field_type = field_name.split(":")
        jimple_class = convert_type_from_bytecode_to_jimple(class_name)
        jimple_type = convert_type_from_bytecode_to_jimple(field_type)
        jimple_line += "<" + jimple_class + ": " + jimple_type + " " + field_name + "> = " + line[1].replace(",", "")
        return jimple_line
    if instruction.startswith("aget"):
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2}[{var3}]"
    if instruction.startswith("aput"):
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var2}[{var3}] = {var1}"
    if instruction.startswith("move"):
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} = {var2}"
    if instruction.startswith("int-to-") or instruction.startswith("long-to-") or instruction.startswith("float-to-") or instruction.startswith("double-to-"):
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        dest_type = instruction.split("-")[2]
        return f"{var1} = ({dest_type}) {var2}"
    if instruction.startswith("const"):
        var1 = line[1].replace(",", "")
        return f"{var1} = " + line[-1]
    if instruction.startswith("return"):
        if instruction == "return-void" or instruction == "return":
            return "return"
        else:
            var1 = line[1].replace(",", "")
            return f"return {var1}"
    if instruction.startswith("throw"):
        var1 = line[1].replace(",", "")
        return f"throw {var1}"
    if instruction.startswith("if-"):
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        if_type = instruction.split("-")[1]
        return f"if {var1} {if_type} {var2}"
    if instruction.startswith("goto"):
        return f"goto"
    if instruction.startswith("check-cast"):
        var1 = line[1].replace(",", "")
        cast_type = convert_type_from_bytecode_to_jimple(line[2])
        return f"{var1} = ({cast_type}) {var1}"
    if instruction.startswith("array-length"):
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} = lengthof {var2}"
    if instruction == "add-int" or instruction == "add-int/lit8" \
        or instruction == "and-int/lit16":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} + {var3}"
    if instruction == "and-int" or instruction == "and-int/lit8" \
        or instruction == "and-int/lit16" \
        or instruction == "and-long" :
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} & {var3}"
    if instruction == "sub-int" or instruction == "sub-int/lit8" \
        or instruction == "sub-float" or instruction == "sub-float/lit8" \
        or instruction == "rsub-int" or instruction == "rsub-int/lit8" \
        or instruction == "sub-long":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} - {var3}"
    if instruction == "or-int" or instruction == "or-int/lit8" \
        or instruction == "or-int/lit16":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} | {var3}"
    if instruction == "xor-int" or instruction == "xor-int/lit8":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} ^ {var3}"
    if instruction == "rem-int" or instruction == "rem-int/lit8":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} % {var3}"
    if instruction == "div-float" or instruction == "div-float/lit8" or instruction == "div-int" or instruction == "div-int/lit8":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} / {var3}"
    if instruction == "mul-float" or instruction == "mul-float/lit8" \
        or instruction == "mul-double" or instruction == "mul-double/lit8" \
        or instruction == "mul-int/lit8" or instruction == "mul-int/lit16":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} * {var3}"
    if instruction == "ushr-int/lit8":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} > {var3}"
    if instruction == "shl-int/lit8":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        var3 = line[3].replace(",", "")
        return f"{var1} = {var2} < {var3}"

    if instruction == "add-int/2addr" or instruction == "add-float/2addr" \
        or instruction == "add-long/2addr":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} += {var2}"
    if instruction == "mul-float/2addr":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} *= {var2}"
    if instruction == "sub-int/2addr" or instruction == "sub-float/2addr" \
        or instruction == "sub-long/2addr":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} -= {var2}"
    if instruction == "or-int/2addr":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} |= {var2}"
    if instruction == "xor-int/2addr":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} ^= {var2}"
    if instruction == "rem-int/2addr" or instruction == "rem-long/2addr":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} %= {var2}"
    if instruction == "div-float/2addr":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} %= {var2}"
    if instruction == "mul-double/2addr":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} *= {var2}"
    if instruction == "shl-int/2addr" or instruction == "shl-long/2addr":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} = {var1} << {var2}"
    if instruction == "neg-int":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        return f"{var1} = ~ {var2}"
    if instruction == "new-array":
        var1 = line[1].replace(",", "")
        var2 = line[2].replace(",", "")
        arr_type = convert_type_from_bytecode_to_jimple(line[3])
        return f"{var1} = new {arr_type}({var2})"
    # if instruction.contains("->"):
    #     class_name, method_name = desc.split("->")
    #     method_name, params = method_name.split("(")
    #     params, ret = params.split(")")
    #     jimple_class = convert_type_from_bytecode_to_jimple(class_name)
    #     jimple_params = ",".join([convert_type_from_bytecode_to_jimple(x) for x in convert_param_list_from_bytecode_to_jimple(params)])
    #     jimple_ret = convert_type_from_bytecode_to_jimple(ret)
    #     jimple_line += "<" + jimple_class + ": " + jimple_ret + " " + method_name + "(" + jimple_params + ")>(" + ",".join(["R" for x in convert_param_list_from_bytecode_to_jimple(params)]) +  ")"
    #     return jimple_line


    # raise Exception(f"Un-supported instruction: {line}")
    print(f"Warining, skipped instruction {line}")
    return "SKIP"



def translate_paths_to_jimple(path_bytecode, real_source_map):
    old_jimple_stmt = ""
    jimple_path = list()
    for i, line in enumerate(reversed(path_bytecode)):
        class_name, method_name, bytecode_line_num, line_num, bytecode = line
        jimple_class = convert_type_from_bytecode_to_jimple(class_name)
        method_name, params = method_name.split("(")
        params, ret = params.split(")")
        jimple_params = ",".join([convert_type_from_bytecode_to_jimple(x) for x in convert_param_list_from_bytecode_to_jimple(params)])
        jimple_ret = convert_type_from_bytecode_to_jimple(ret)
        jimple_method = "<" + jimple_class + ": " + jimple_ret + " " + method_name + "(" + jimple_params + ")>"
        if "," in jimple_method:
            jimple_method = '\"' + jimple_method + '\"'

        jimple_stmt = convert_bytecode_stmt_to_jimple(bytecode)

        if jimple_stmt == "return":
            continue

        if i == 0 and "<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object)>" in jimple_stmt:
            jimple_stmt = convert_bytecode_stmt_to_jimple("invoke-static {v0}, " + real_source_map[jimple_class])
        if "," in jimple_stmt:
            jimple_stmt = '\"' + jimple_stmt + '\"'

        if old_jimple_stmt != jimple_method + jimple_stmt:
            jimple_path.append((str(i), jimple_method, str(bytecode_line_num), jimple_stmt))

        old_jimple_stmt = jimple_method + jimple_stmt
    return jimple_path


def print_list_reverse(out_dir, file_name, path):

    if not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    with open(out_dir + "/" + file_name, 'w') as f:
        f.write(",method,stmtLineNumber,stmt\n")
        for p in path:
            f.write(",".join([str(x) for x in p]))
            f.write("\n")

def print_path_consol(path):
    print("---------")
    for l in reversed(path):
        print(l)
    print("---------")