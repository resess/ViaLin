package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class TaintSource {
    private static List<String> sources = new ArrayList<>();
    private static List<String> sourceMethods = new ArrayList<>();

    public static void loadSources(String sourceFile) {
        List<String> sourceList;
        try {
            sourceList = Files.readAllLines(Paths.get(sourceFile));
        } catch (IOException e) {
            throw new Error("Cannot open file: " + sourceFile);
        }

        for (String src : sourceList) {
            if (src.startsWith("*->")) { // for sources that match by method, regardless of the class name
                sourceMethods.add(src.replace("*->", ""));
            } else { // for exact matching
                sources.add(src);
            }

        }
    }

    public static boolean isSource(String className, String signature) {
        if (sources.contains(signature)) {
            return true;
        } else if (sourceMethods.contains(signature.split("->")[1])) {
            return true;
        }
        return false;
    }

    public static List<String> getSources() {
        return sources;
    }

    public static int getTaintNum(String signature) {
        return sources.indexOf(signature);
    }
}


