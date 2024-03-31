package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

public class TaintSink {
    private static Map<String, int[]> sinks = new HashMap<>();

    public static boolean isEmpty () {
        return sinks.isEmpty();
    }
    public static void loadSinks(String sinkFile) {
        List<String> sinkList;
        try {
            sinkList = Files.readAllLines(Paths.get(sinkFile));
        } catch (IOException e) {
            throw new Error("Cannot open file: " + sinkFile);
        }

        for (String sink : sinkList) {
            if (sink.startsWith("//")) {
                continue;
            }
            String [] splitArr = sink.split(",\\s+");
            String sinkStmt = splitArr[0];
            int [] params = new int[splitArr.length-1];

            if (splitArr[1].equals("*")) {
                params[0] = -1;
            } else {
                for (int i = 1; i < splitArr.length; i++) {
                    params[i-1] = Integer.parseInt(splitArr[i]);
                }
            }

            sinks.put(sinkStmt, params);
        }
    }


    public static int[] sinkParams(Set<String> classesOfMethod, String methodNameAndDesc) {
        for (String cls : classesOfMethod) {
            // System.out.format("Signature %s%n", cls + "->" + methodNameAndDesc);
            int [] params = sinks.get(cls + "->" + methodNameAndDesc);
            if (params != null) {
                return params;
            }
        }
        return new int[0];
    }

    public static int[] sinkParams(String signature) {
        return sinks.getOrDefault(signature, new int[0]);
    }

}
