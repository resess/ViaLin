package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;


public class FindInstructionInSmali  extends TaintAnalysis {

    private String smaliFile;


    public FindInstructionInSmali(String smaliFiles) {
        this.smaliFile = smaliFiles;
    }

    public static void main(String[] args) {
        FindInstructionInSmali finder = new FindInstructionInSmali(args[0]);
        finder.transformFile(finder.smaliFile);
    }

    private void transformFile(String file) {
        List<String> classLines;
        try {
            classLines = Files.readAllLines(Paths.get(file));
        } catch (IOException e) {
            throw new Error("Cannot open class file: " + file);
        }

        String className = getLastToken(classLines.get(0));

        MethodInfo methodInfo = null;

        boolean instrument = true;
        boolean inAnnon = false;
        Stack<String> tryCatches = new Stack<>();
        int methodSize = 0;

        for (int lineNum = 0; lineNum < classLines.size(); lineNum++) {

            String line = classLines.get(lineNum);
            List<String> linesToAdd = new ArrayList<>();
            linesToAdd.add(line);


            if (line.trim().startsWith(".annotation")) {
                inAnnon = true;
            }
            if (line.trim().startsWith(".end annotation")) {
                inAnnon = false;
            }
            if (line.startsWith("    :try_start")) {
                tryCatches.push(line);
            }
            if (line.startsWith("    :try_end")) {
                tryCatches.pop();
            }


            if (instrument && !inAnnon) {

                if (line.isEmpty()) {
                    // pass
                } else if (line.startsWith(".field")) {
                    // pass
                } else if (line.startsWith("    invoke")) {
                    // pass
                } else if (line.startsWith("    .registers")) {
                    // pass


                } else if (line.startsWith(".method")) {
                    methodSize = 0;
                    if (!line.contains(" native ")) {
                        methodInfo = getMethodInfo(className, line);
                    }
                } else if (line.startsWith(".end method")) {
                    if (methodInfo != null) {
                        // System.out.println("Method: " + methodInfo.signature());
                        // System.out.println("MethodSize: " + methodSize);
                    }

                } else if (methodInfo != null) {
                    String instruction = getToken(line, 0);
                    if (instruction.startsWith(".line")) {
                    } else if (instruction.startsWith(".")) {
                    } else if (instruction.startsWith(":")) {
                    } else if (instruction.startsWith("0x")) {
                    } else if (instruction.startsWith("-")) {
                    } else if (instruction.startsWith("#")) {
                    } else {
                        methodSize += getSizeBit(instruction);
                    }
                }
            }

            AnalysisLogger.log(true, "[%s]: %s%n", Integer.toHexString(methodSize), line);
        }
    }

}