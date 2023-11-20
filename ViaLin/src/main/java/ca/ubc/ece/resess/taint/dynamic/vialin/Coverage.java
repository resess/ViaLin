package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;



public class Coverage extends TaintAnalysis {

    enum ClassRegion {
        Header, StaticFields, InstanceFields, Methods
    }

    private List<String> smaliFiles;
    private String coverageFile;
    private boolean bytecodeCov;


    public static Set<String> forbiddenClasses = new HashSet<>(Arrays.asList(
       "Ljava/lang/Object;", "Ljava/lang/DexCache;"
        , "Ljava/lang/String;", "Ldalvik/system/ClassExt;",
        "[Z", "[B", "[C", "[S", "[I", "[J", "[F", "[D", "Ljava/lang/Class;", "[Ljava/lang/Class;",
        "[Ljava/lang/Object;", "Ljava/lang/Cloneable;", "Ljava/io/Serializable;", "Ljava/lang/reflect/Proxy;",
        "Ljava/lang/reflect/Field;", "[Ljava/lang/reflect/Field;", "Ljava/lang/reflect/Constructor;",
        "[Ljava/lang/reflect/Constructor;", "Ljava/lang/reflect/Method;", "[Ljava/lang/reflect/Method;",
        "Ljava/lang/invoke/MethodType;", "Ljava/lang/invoke/MethodHandleImpl;", "Ljava/lang/invoke/MethodHandles$Lookup;",
        "Ljava/lang/invoke/CallSite;", "Ldalvik/system/EmulatedStackFrame;", "Ljava/lang/ref/Reference;",
        "Ljava/lang/ref/FinalizerReference;", "Ljava/lang/ref/PhantomReference;", "Ljava/lang/ref/SoftReference;",
        "Ljava/lang/ref/WeakReference;", "Ljava/lang/ClassLoader;", "Ljava/lang/Throwable;",
        "Ljava/lang/ClassNotFoundException;", "Ljava/lang/StackTraceElement;", "[Ljava/lang/StackTraceElement;"
    ));


    public static final String [] ignoreArray = ClassTaint.ignoreArray;


    public Coverage(List<String> smaliFiles, boolean bytecodeCov, String coverageFile) {
        this.smaliFiles = smaliFiles;
        this.coverageFile = coverageFile;
        this.bytecodeCov = bytecodeCov;
    }


    public void addTaint() {
        for (String file : smaliFiles) {
            // ClassInfo classInfo = ClassInfo.getClassInfo(file);
            addTaintToClassFile(file);
        }
    }

    private void addTaintToClassFile(String file) {
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

        InstrumentationContext context = new InstrumentationContext();
        Integer signatureRegister = null;
        // Map<Integer, Integer> newParams = new HashMap<>();

        List<String> taintedClassLines = new ArrayList<>();
        List<String> lineNumberMethods = new ArrayList<>();
        Set<Integer> linesToPrint = new HashSet<>();
        Set<Integer> byteCodeLinesToPrint = new HashSet<>();


        boolean instrument = true;
        boolean inAnnon = false;
        Stack<String> tryCatches = new Stack<>();
        Integer methodDelta = 1;
        List<String> linesToAddAtMethodEnd = new ArrayList<>();

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

            boolean inTryBlock = !tryCatches.isEmpty();

            if (instrument && !inAnnon) {

                if (line.isEmpty()) {
                    // pass
                } else if (line.startsWith(".field")) {
                    // pass
                } else if (line.startsWith("    invoke")) {
                    linesToAdd.clear();

                    line = changeParamsToLocals(line, context);
                    linesToAdd.add(line);
                } else if (line.startsWith("    .registers")) {
                    addTaintRegisters(line, linesToAdd, context.currentMethod);

                    int firstTaintReg = context.currentMethod.getBaseNumRegs();
                    AnalysisLogger.log(className.contains("ViewPumpLayoutInflater"), "Method: %s%n", context.currentMethod);
                    AnalysisLogger.log(className.contains("ViewPumpLayoutInflater"), "    base regs: %s%n", context.currentMethod.getBaseNumRegs());
                    AnalysisLogger.log(className.contains("ViewPumpLayoutInflater"), "    base local regs: %s%n", context.currentMethod.getNumBaseLocalRegs());

                    signatureRegister = firstTaintReg + context.currentMethod.getNumBaseLocalRegs() + context.currentMethod.getNumBaseParams() + 1;
                    context.timerRegister = signatureRegister + 1;
                    context.taintTempReg = context.timerRegister + 2;
                    context.maxRegs = context.taintTempReg + 2;

                    for (int i = 0; i < context.currentMethod.getParams().size(); i++ ) {

                        String paramType = context.currentMethod.getParams().get(i);
                        int paramReg = context.currentMethod.getNumBaseLocalRegs() + i;

                        context.newParams.put(i, paramReg);

                        String moveInstruction = getMoveInstructionByType(paramType);

                        if (moveInstruction != null) {
                            linesToAdd.add("    " + moveInstruction + " v" + paramReg + ", p" + i);
                        }
                    }

                    addSignatureRegister(linesToAdd, context.timerRegister, context.taintTempReg, context.currentMethod, signatureRegister, context.newParams);

                    byteCodeLinesToPrint.add(methodDelta);
                    context.maxRegs = addPrintLineNum(linesToAdd, methodDelta, signatureRegister, context.currentMethod, context.taintTempReg, context.maxRegs, className);
                    context.taintTempReg = context.maxRegs;


                } else if (line.startsWith(".method")) {
                    if (!line.contains(" native ")) {
                        context.currentMethod = getMethodInfo(className, line);
                    }
                    methodDelta = 1;
                } else if (line.startsWith(".end method")) {
                    linesToAdd.clear();
                    linesToAdd.addAll(linesToAddAtMethodEnd);
                    linesToAdd.add(line);
                    linesToAddAtMethodEnd.clear();
                    context.currentMethod = null;
                    fixRegisterLine(taintedClassLines, context.maxRegs);
                } else if (context.currentMethod != null) {
                    String instruction = getToken(line, 0);

                    if (instruction.startsWith(".label") || instruction.startsWith("goto") || instruction.startsWith("if") || instruction.startsWith(":cond") ) {


                        linesToAdd.clear();
                        line = changeParamsToLocals(line, context);
                        linesToAdd.add(line);

                        methodDelta++;

                        if (bytecodeCov) {
                            String jumpTarget = null;
                            if (inTryBlock) {
                                jumpTarget = ClassTaint.addTaintCodeJump(methodDelta, linesToAdd, linesToAddAtMethodEnd);
                            }

                            List<String> taintAdditionSite;
                            if (inTryBlock) {
                                taintAdditionSite = linesToAddAtMethodEnd;
                            } else {
                                taintAdditionSite = linesToAdd;
                            }
                            byteCodeLinesToPrint.add(methodDelta);
                            context.maxRegs = addPrintLineNum(taintAdditionSite, methodDelta, signatureRegister, context.currentMethod, context.taintTempReg, context.maxRegs, className);
                            context.taintTempReg = context.maxRegs;
                            if (inTryBlock) {
                                ClassTaint.addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
                            }
                        }

                    } else if (instruction.startsWith(".")) {
                    } else if (instruction.startsWith(":")) {
                    } else if (instruction.startsWith("0x")) {
                    } else if (instruction.startsWith("-")) {
                    } else {
                        linesToAdd.clear();
                        line = changeParamsToLocals(line, context);
                        linesToAdd.add(line);
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

        addLineNumberMethods(className, lineNumberMethods, linesToPrint);
        addByteCodeLineNumberMethods(className, lineNumberMethods, byteCodeLinesToPrint);

        taintedClassLines.addAll(lineNumberMethods);

        try {
            Files.write(Paths.get(file), taintedClassLines);
        } catch (IOException e) {
            throw new Error("Cannot modify class file: " + file);
        }
    }


    private int addPrintLineNum(List<String> linesToAdd, Integer sourceLineNum, Integer signatureRegister, MethodInfo methodInfo, Integer taintTempReg, Integer maxRegs, String className) {
        try {
            Files.write(Paths.get(coverageFile), (methodInfo.signature() + ": " + sourceLineNum + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new Error("Cannot modify coverage file: " + coverageFile);
        }
        String newLine = "    invoke-static {v" + signatureRegister + "}, " + className + "->printNum" + sourceLineNum + "(Ljava/lang/String;)V";
        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
        linesToAdd.addAll(rangedInvoke.first);
        int newMaxRegs = rangedInvoke.second;
        return (maxRegs > newMaxRegs)? maxRegs : newMaxRegs;
    }

    private void addSignatureRegister(List<String> linesToAdd, Integer timerRegister, Integer taintTempReg, MethodInfo methodInfo, Integer signatureRegister, Map<Integer, Integer> newParams) {
        String p0Move = null;
        String p1Move = null;
        if (methodInfo.getNumBaseLocalRegs() < 2) {
            if (methodInfo.getNumBaseParams() > 0) {
                p0Move = getMoveInstructionByType(methodInfo.getParams().get(0));
            }
            if (methodInfo.getNumBaseParams() > 1) {
                p1Move = getMoveInstructionByType(methodInfo.getParams().get(1));
            }
        }
        if (p0Move != null) {
            linesToAdd.add("    " + p0Move + " v" + taintTempReg + ", v" + newParams.get(0));
            if (p0Move != null && methodInfo.getNumBaseParams() > 1 && !p0Move.contains("wide") && p1Move != null) {
                linesToAdd.add("    " + p1Move + " v" + String.valueOf(taintTempReg+1) + ", v" + newParams.get(1));
            }
        }

        linesToAdd.add("    const-string/jumbo v0, \"" + methodInfo.signature() + "\"");
        linesToAdd.add("    move-object/16 v" + String.valueOf(signatureRegister) + ", v0");

        if (p0Move != null) {
            linesToAdd.add("    " + p0Move + " v" + newParams.get(0) + ", v" + taintTempReg);
            if (p0Move != null && methodInfo.getNumBaseParams() > 1 && !p0Move.contains("wide") && p1Move != null) {
                linesToAdd.add("    " + p1Move + " v" + newParams.get(1) + ", v" + String.valueOf(taintTempReg+1));
            }
        }
    }



    private void fixRegisterLine(List<String> taintedClassLines, int maxRegs) {
        if (maxRegs == 0) {
            return;
        }
        for (int i = taintedClassLines.size()-1; i >=0; i--) {
            String line = taintedClassLines.get(i);
            if (line.startsWith("    .registers")) {
                int numRegs = Integer.parseInt(getLastToken(line));
                maxRegs = (maxRegs > numRegs)? maxRegs : numRegs;
                maxRegs = maxRegs + 2;
                String newRegsLine = "    .registers " + maxRegs;
                // System.out.println("        Fixed class line from: " + line);
                // System.out.println("                           to: " + newRegsLine);
                taintedClassLines.set(i, newRegsLine);
                return;
            }
            if (line.startsWith(".method")) {
                return;
            }
        }
    }


    private void addTaintRegisters(String line, List<String> linesToAdd, MethodInfo methodInfo) {
        Integer baseNumRegs;
        baseNumRegs = Integer.parseInt(getLastToken(line));
        methodInfo.setBaseNumRegs(baseNumRegs);
        String newRegsLine = "    .registers " + ((methodInfo.getBaseNumRegs()*2) + 3); // 1 site reg, 2 temp regs
        linesToAdd.clear();
        linesToAdd.add(newRegsLine);
    }

    private void addLineNumberMethods(String className, List<String> lineNumberMethods, Set<Integer> linesToPrint) {

        for (Integer sourceLineNum : linesToPrint) {
            lineNumberMethods.add(".method public static printNum" + sourceLineNum + "(Ljava/lang/String;)V");
            lineNumberMethods.add(".registers 4");
            lineNumberMethods.add("    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;");
            lineNumberMethods.add("    new-instance v1, Ljava/lang/StringBuilder;");
            lineNumberMethods.add("    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V");
            lineNumberMethods.add("    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            lineNumberMethods.add("    const-string/jumbo p0, \": " + sourceLineNum + "\"");
            lineNumberMethods.add("    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            lineNumberMethods.add("    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;");
            lineNumberMethods.add("    move-result-object v1");
            lineNumberMethods.add("    invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V");
            lineNumberMethods.add("    return-void");
            lineNumberMethods.add(".end method");
        }
    }

    private void addByteCodeLineNumberMethods(String className, List<String> lineNumberMethods, Set<Integer> linesToPrint) {

        for (Integer sourceLineNum : linesToPrint) {
            lineNumberMethods.add(".method public static printNum" + sourceLineNum + "(Ljava/lang/String;)V");
            lineNumberMethods.add(".registers 5");
            lineNumberMethods.add("    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;");
            lineNumberMethods.add("    new-instance v1, Ljava/lang/StringBuilder;");
            lineNumberMethods.add("    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V");
            lineNumberMethods.add("    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            lineNumberMethods.add("    const-string/jumbo v2, \":ByteCode\"");
            lineNumberMethods.add("    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            lineNumberMethods.add("    const-string/jumbo p0, \": " + sourceLineNum + "\"");
            lineNumberMethods.add("    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            lineNumberMethods.add("    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;");
            lineNumberMethods.add("    move-result-object v1");
            lineNumberMethods.add("    invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V");
            lineNumberMethods.add("    return-void");
            lineNumberMethods.add(".end method");
        }
    }

    public void analyze() {
        throw new Error("Shouldn't analyze before coverage instrumentation");
    }

}