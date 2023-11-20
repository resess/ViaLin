package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


public class TransformConds extends TaintAnalysis {


    private List<String> smaliFiles;
    public static Set<String> forbiddenClasses = ClassTaint.forbiddenClasses;
    public static final String [] ignoreArray = ClassTaint.ignoreArray;


    public TransformConds(List<String> smaliFiles, String analysisDir, String coverageFile) {
        this.smaliFiles = smaliFiles;
    }


    public void transform() {
        for (String file : smaliFiles) {
            // ClassInfo classInfo = ClassInfo.getClassInfo(file);
            transformFile(file);
        }
    }

    private void transformFile(String file) {
        List<String> classLines;
        try {
            classLines = Files.readAllLines(Paths.get(file));
        } catch (IOException e) {
            throw new Error("Cannot open class file: " + file);
        }

        String className = getLastToken(classLines.get(0));



        if (forbiddenClasses.contains(className) || isIgnoredClass(className)) {
            // System.out.println("Ignored class: " + className);
            return;
        }


        MethodInfo methodInfo = null;
        List<String> taintedClassLines = new ArrayList<>();

        boolean instrument = true;
        boolean inAnnon = false;
        Stack<String> tryCatches = new Stack<>();
        int methodSize = 0;
        Map<String, Integer> labelDistanceMap = new HashMap<>();

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
                        labelDistanceMap = collectLabelDistanceMap(lineNum, classLines, methodInfo);
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
                        if (isNop(instruction)) {
                            // pass
                        } else if (isMove(instruction) || isMoveFrom16(instruction) || isMove16(instruction) ||
                            isMoveWide(instruction) || isMoveWideFrom16(instruction) || isMoveWide16(instruction) ||
                            isMoveObject(instruction) || isMoveObjectFrom16(instruction) || isMoveObject16(instruction)) {
                            // pass
                        } else if (isMoveResult(instruction) || isMoveResultWide(instruction) || isMoveResultObject(instruction)) {
                            // pass
                        } else if (isMoveException(instruction)) {
                            // pass
                        } else if (isReturnVoid(instruction)) {
                            // pass
                        } else if (isReturn(instruction) || isReturnWide(instruction) || isReturnObject(instruction)) {
                            // pass
                        } else if (isConst4(instruction) || isConst16(instruction) || isConst(instruction) || isConstHigh16(instruction) ||
                                isConstWide16(instruction) || isConstWide32(instruction) || isConstWide(instruction) || isConstWideHigh16(instruction) ||
                                isConstString(instruction) || isConstStringJumbo(instruction) || isConstClass(instruction)) {
                            // pass
                        } else if (isMonitorEnter(instruction) || isMonitorExit(instruction)) {
                            // pass
                        } else if (isCheckCast(instruction)) {
                            // pass
                        } else if (isInstanceOf(instruction) || isArrayLength(instruction)) {
                            // pass
                        } else if (isNewInstance(instruction) || isNewArray(instruction)) {
                            // pass
                        } else if (isFilledNewArray(instruction) || isFilledNewArrayRange(instruction) || isFillArrayData(instruction)) {
                            // pass
                        } else if (isThrow(instruction)) {
                            // pass
                        } else if (isGoto(instruction) || isGoto16(instruction) || isGoto32(instruction)) {
                            // pass
                        } else if (isPackedSwitch(instruction) || isSparseSwitch(instruction)) {
                            // pass
                        } else if (isCmpkind(instruction)) {
                            // pass
                        } else if (isIfTest(instruction) || isIfTestz(instruction)) {
                            // pass
                            String srcReg1 = getRegReference(line, 1);
                            String label = getRegReference(line, 2);
                            // System.out.println("At line: " + line);

                            String srcReg2 = "";
                            if (isIfTest(instruction)) {
                                srcReg2 = label;
                                label = getRegReference(line, 3);
                            }
                            // System.out.printf("Label %s, Distance %s%n", label, labelDistanceMap.get(label));
                            String oppositeConditional = getOppositeConditional(instruction);
                            int labelNum = lineNum;
                            String newLabel = ":TransformedLabel_" + labelNum;
                            String newInstruction = oppositeConditional + " " + srcReg1 + ", " + (srcReg2.equals("")? "" : srcReg2 + ", ") + newLabel;
                            String gotoInstruction = "goto/32 " + label;
                            // System.out.println("New Instruction: " + newInstruction);
                            int delta = labelDistanceMap.get(label) - methodSize;
                            // if (delta < -32768 || delta > 32767) {
                            if (delta < -4096 || delta > 4095) { // made more safe
                                // System.out.format("Bad delta To cond %s in method%s%n", delta, methodInfo.signature());
                                linesToAdd.clear();
                                linesToAdd.add("    " + newInstruction);
                                linesToAdd.add("    " + gotoInstruction);
                                linesToAdd.add("    " + newLabel);
                            }

                        } else if (isArrayOp(instruction)) {
                            // pass
                        } else if (isIinstanceOp(instruction)) {
                            // pass
                        } else if (isSstaticOp(instruction)) {
                            // pass
                        } else if (isInvokeKind(instruction) || isInvokeKindRange(instruction) ||
                            isInvokePolymorphic(instruction) || isInvokePolymorphicRange(instruction) ||
                            isInvokeCustom(instruction) || isInvokeCustomRange(instruction)) {
                            throw new Error("Invokes are handled in a separate branch");
                        } else if (isUnOp(instruction)) {
                            // pass
                        } else if (isBinOp(instruction)) {
                            // pass
                        } else if (isBinOp2addr(instruction)) {
                            // pass
                        } else if (isBinOpLit16(instruction) || isBinOpLit8(instruction)) {
                            // pass
                        } else if (isConstMethodHandle(instruction) || isConstMethodType(instruction)) {
                            // pass
                        } else {
                            throw new Error("Invalid instruction: " + line);
                        }
                    }
                }
            }
            taintedClassLines.addAll(linesToAdd);
        }


        try {
            Files.write(Paths.get(file), taintedClassLines);
        } catch (IOException e) {
            throw new Error("Cannot modify class file: " + file);
        }
    }




    private Map<String, Integer> collectLabelDistanceMap(int lineNumInit, List<String> classLines, MethodInfo methodInfo) {
        int methodSize = 0;
        boolean inAnnon = false;
        Map<String, Integer> labelDistanceMap = new HashMap<>();
        for (int lineNum = lineNumInit; lineNum < classLines.size(); lineNum++) {
            String line = classLines.get(lineNum);

            if (line.trim().startsWith(".annotation")) {
                inAnnon = true;
            }
            if (line.trim().startsWith(".end annotation")) {
                inAnnon = false;
            }
            if (!inAnnon) {
                if (line.isEmpty()) {
                    // pass
                } else if (line.startsWith(".end method")) {
                    return labelDistanceMap;

                } else if (methodInfo != null) {
                    String instruction = getToken(line, 0);
                    if (instruction.startsWith(".line")) {
                    } else if (instruction.startsWith(".")) {
                    } else if (instruction.startsWith(":")) {
                        labelDistanceMap.put(instruction, methodSize);
                    } else if (instruction.startsWith("0x")) {
                    } else if (instruction.startsWith("-")) {
                    } else if (instruction.startsWith("#")) {
                    } else {
                        methodSize += getSizeBit(instruction);
                    }
                }
            }
        }
        throw new Error("Didn't reach the end of method");
    }

}