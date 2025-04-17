package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.simple.*;

import ca.ubc.ece.resess.taint.dynamic.vialin.MethodModel.MethodModelAssignment;


public class ConcreteCache extends TaintAnalysis {

    enum ClassRegion {
        HEADER, STATIC_FIELDS, INSTANCE_FIELDS, METHODS
    }

    protected static final List<String> twoLevelPkgs = new ArrayList<>();
    protected static final List<String> threeLevelPkgs = new ArrayList<>();

    private TaintTool tool;
    private List<String> smaliFiles;
    private final Map<String, JSONObject> classToMethodIndexMap;


    public ConcreteCache(TaintTool tool, List<String> smaliFiles, String frameworkAnalysisDir, boolean isFramework, String outDir) {
        this.tool = tool;
        this.smaliFiles = smaliFiles;
        this.classAnalysis = new ClassAnalysis(frameworkAnalysisDir, outDir);
        this.classToMethodIndexMap = new HashMap<>();
        this.statistics = new Statistics();
        this.isFramework = isFramework;
    }

    @Override
    public void addTaint() {
        for (String file : smaliFiles) {
            addTaintToClassFile(file);
        }
    }

    @SuppressWarnings("unchecked")
    private void addTaintToClassFile(String file) {

        List<String> classLines;
        try {
            classLines = Files.readAllLines(Paths.get(file));
        } catch (IOException e) {
            throw new Error("Cannot open class file: " + file);
        }

        String className = getLastToken(classLines.get(0));
        boolean classIsInterface = classLines.get(0).contains(" interface ");

        if (className.equals("Ljava/lang/PathTaint;") || className.equals("Ljava/lang/TaintDroid;")) {
            return;
        }

        if (forbiddenClasses.contains(className) || isIgnoredClass(className)) {
            // AnalysisLogger.log(true, "Ignoring class %s%n", className);
            return;
        }

        JSONObject classIndex = new JSONObject();
        classToMethodIndexMap.put(className, classIndex);

        InstrumentationContext context = new InstrumentationContext();
        context.classLines = classLines;

        boolean inAnnon = false;
        Deque<String> tryCatches = new ArrayDeque<>();
        String lastCalled = null;
        JSONObject methodIndex = null;

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

            AnalysisLogger.log(false, "At line #%s: %s%n", lineNum, line);

            if (!inAnnon) {

                if (line.isEmpty()) {
                    // pass
                } else if (line.startsWith(".field")) {
                    // pass
                } else if (context.currentMethod != null && line.startsWith("    invoke")) {
                    context.methodDelta++;
                    methodIndex.put(context.methodDelta, line);
                    linesToAdd.clear();
                    String oldLineForDebug = line;
                    try {
                        line = changeParamsToLocals(line, context);
                    } catch (InvalidInstructionError e) {
                        AnalysisLogger.log(true, "Bad paramter translation: at line %s, will not instrument class, exception: %s%n", line, e);
                        return;
                    }
                    if (line.contains("vnull")) {
                        AnalysisLogger.log(true, "Bad paramter translation: %s, newParams: %s%n", line, context.newParams);
                        AnalysisLogger.log(true, "Old line is: %s%n", oldLineForDebug);
                        AnalysisLogger.log(true, "Method is: %s%n", context.currentMethod.signature());
                        AnalysisLogger.log(true, "Code in class till this line%n", context.currentMethod.signature());
                        for (int i = 0; i <= lineNum; i++) {
                            AnalysisLogger.log(true, "    %s%n", classLines.get(i));
                        }
                        throw new RuntimeException("Bad paramter translation: " + line);
                    }

                    lastCalled = handleMethodCallOperation(classLines, className, context, lastCalled, linesToAddAtMethodEnd,
                            lineNum, line, linesToAdd, inTryBlock);
                } else if (context.currentMethod != null && line.startsWith("    .registers")) {
                    handleMethodStart(context, methodIndex, line, linesToAdd);
                } else if (line.startsWith(".method")) {
                    if (!line.contains(" native ")) {
                        context.currentMethod = getMethodInfo(className, line);
                        // AnalysisLogger.log(true, "    Method %s is in file %s%n", context.currentMethod.signature(), file);
                        context.maxRegs = 0;
                        boolean shouldTaintMethod = shouldTaint(context.currentMethod, lineNum, classLines);
                        if (!shouldTaintMethod) {
                            context.currentMethod = null;
                            statistics.addNotTainted();
                        } else {
                            statistics.addTainted();
                        }
                    }
                    context.fieldArraysInMethod = new HashMap<>();
                    context.regType = new HashMap<>();
                    context.methodDelta = 1;
                    methodIndex = new JSONObject();
                } else if (line.startsWith(".end method")) {
                    if (context.currentMethod != null) {
                        classIndex.put(context.currentMethod.getNameAndDesc(), methodIndex);
                        fixRegisterLine(context.taintedClassLines, context.maxRegs);
                    }
                    linesToAdd.clear();
                    linesToAdd.addAll(linesToAddAtMethodEnd);
                    linesToAdd.add(line);
                    linesToAddAtMethodEnd.clear();
                    context.currentMethod = null;
                    lastCalled = null;
                    debug = false;

                } else if (context.currentMethod != null) {
                    String instruction = getToken(line, 0);
                    if (instruction.startsWith(".")) {
                        // Ignored
                    } else if (instruction.startsWith(":")) {
                        context.erasedTaintRegs.clear();
                    } else if (instruction.startsWith("0x")) {
                        // Ignored
                    } else if (instruction.startsWith("-")) {
                        // Ignored
                    } else {

                        context.methodDelta++;
                        methodIndex.put(context.methodDelta, line);

                        linesToAdd.clear();

                        try {
                            line = changeParamsToLocals(line, context);
                        } catch (InvalidInstructionError e) {
                            AnalysisLogger.log(true, "Bad paramter translation: at line %s, will not instrument class, exception: %s%n", line, e);
                            return;
                        }

                        linesToAdd.add(line);

                        if (isNop(instruction)) {
                            // pass
                        } else if (isMove(instruction) || isMoveFrom16(instruction) || isMove16(instruction) ||
                            isMoveWide(instruction) || isMoveWideFrom16(instruction) || isMoveWide16(instruction) ||
                            isMoveObject(instruction) || isMoveObjectFrom16(instruction) || isMoveObject16(instruction)) {
                            // pass
                        } else if (isMoveResult(instruction) || isMoveResultWide(instruction) || isMoveResultObject(instruction)) {
                            lastCalled = handleMoveResultOperation(classLines, className, context, lastCalled,
                                    linesToAddAtMethodEnd, lineNum, line, linesToAdd, inTryBlock, instruction);
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
                            // handleInstanceArrayOperations(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isNewInstance(instruction) || isNewArray(instruction)) {
                            // pass
                        } else if (isFilledNewArray(instruction) || isFilledNewArrayRange(instruction) || isFillArrayData(instruction)) {
                            // pass
                        } else if (isThrow(instruction)) {
                            // pass
                        } else if (isGoto(instruction) || isGoto16(instruction) || isGoto32(instruction)) {
                            context.erasedTaintRegs.clear();
                        } else if (isPackedSwitch(instruction) || isSparseSwitch(instruction)) {
                            // pass
                        } else if (isCmpkind(instruction)) {
                            // pass
                        } else if (isIfTest(instruction) || isIfTestz(instruction)) {
                            // pass
                        } else if (isArrayOp(instruction)) {
                            // handleArrayOperation(classLines, context, linesToAddAtMethodEnd, lineNum, line, linesToAdd,
                            //         inTryBlock, instruction);
                        } else if (isIinstanceOp(instruction)) {
                            // handleInstanceFieldOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isSstaticOp(instruction)) {
                            // handleStaticFieldOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isInvokeKind(instruction) || isInvokeKindRange(instruction) ||
                            isInvokePolymorphic(instruction) || isInvokePolymorphicRange(instruction) ||
                            isInvokeCustom(instruction) || isInvokeCustomRange(instruction)) {
                            throw new Error("Invokes are handled in a separate branch");
                        } else if (isUnOp(instruction)) {
                            // handleUnaryOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isBinOp(instruction)) {
                            // handleBinaryOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock,
                            //         instruction);
                        } else if (isBinOp2addr(instruction)) {
                            // handleBinaryOperationTwoAddr(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock,
                            //         instruction);
                        } else if (isBinOpLit16(instruction) || isBinOpLit8(instruction)) {
                            // handleBinaryOperationLiteral(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock,
                            //         instruction);
                        } else if (isConstMethodHandle(instruction) || isConstMethodType(instruction)) {
                            // pass
                        } else {
                            throw new Error("Invalid instruction: " + line);
                        }
                    }
                }
            }
            context.taintedClassLines.addAll(linesToAdd);
        }

        context.taintedClassLines.addAll(context.extraTaintMethods);

        try {
            Files.write(Paths.get(file), context.taintedClassLines);
        } catch (IOException e) {
            throw new Error("Cannot modify class file: " + file);
        }
    }

    private String handleMethodCallOperation(List<String> classLines, String className, InstrumentationContext context,
            String lastCalled, List<String> linesToAddAtMethodEnd, int lineNum, String line, List<String> linesToAdd,
            boolean inTryBlock) {
        String jumpTarget = null;
        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            jumpTarget = addTaintCodeJump(context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else {
        }

        List<String> taintAdditionSite;
        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }


        int newMaxRegs;

        Set<String> transformations = new HashSet<>();
        if (!line.startsWith("    invoke-polymorphic")) {
            newMaxRegs = saveMethodCallVariables(tool, line, linesToAdd, classLines, lineNum, context.currentMethod, context.taintTempReg+1, context.taintRegMap, context.signatureRegister, context.methodDelta, context.taintedClassLines, className, context.regType, context.threadReg, transformations, context);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
            lastCalled = line;
        }

        // newMaxRegs = injectTaintSink(tool, line, lineNum, taintAdditionSite, context.taintTempReg+1, context);
        // context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
        

        // newMaxRegs = injectTaintSeedByReflection(tool, line, taintAdditionSite, context.taintTempReg+1, className, context.extraTaintMethods, context);
        // context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
        } else {
        }

        if (!transformations.contains("Added line")) {
            linesToAdd.add(line);
        }

        // to make sure no optimizations after method call, in case the method propagates taint to its parameters.
        context.erasedTaintRegs.clear();
        return lastCalled;
    }

    public int handleMethodCallStatement(TaintTool tool, String line, List<String> linesToAdd, List<String> classLines, int lineNum, int taintTempReg, String className, Set<String> transformations, InstrumentationContext context) {

        String delim = "L";
        String search = ", L";
        if (line.indexOf(search) == -1) {
            delim = "[";
            search = ", \\[";
        }
        String calledMethod = delim + line.split(search, 2)[1];
        String instruction = getToken(line, 0);
        if (classAnalysis.isNative(calledMethod)) {
            return saveMethodCallVariables(tool, line, linesToAdd, classLines, lineNum, context.currentMethod, taintTempReg, context.taintRegMap, context.signatureRegister, context.methodDelta, context.taintedClassLines, className, context.regType, context.threadReg, transformations, context);
        }
        MethodInfo calledMethodInfo = new MethodInfo(calledMethod, instruction.contains("static"));
        Set<String> whereIsMethod = classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethod.split("->")[1]);


        String [] lineSplit = line.split("\\}");
        String [] regSplit = lineSplit[0].split("\\{");
        String [] passedRegs = new String[0];
        if (regSplit.length > 1) {
            passedRegs = regSplit[1].replace(" ", "").split("\\,");
        }

        if (passedRegs.length == 0) {
            return context.maxRegs;
        }

        if ((forbiddenClasses.contains(calledMethodInfo.getClassName())) || isIgnored(classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc())) ) {
            return saveMethodCallVariables(tool, line, linesToAdd, classLines, lineNum, context.currentMethod, taintTempReg, context.taintRegMap, context.signatureRegister, context.methodDelta, context.taintedClassLines, className, context.regType, context.threadReg, transformations, context);
        }

        if (whereIsMethod == null || whereIsMethod.isEmpty() || isIgnored(whereIsMethod)) {
            return saveMethodCallVariables(tool, line, linesToAdd, classLines, lineNum, context.currentMethod, taintTempReg, context.taintRegMap, context.signatureRegister, context.methodDelta, context.taintedClassLines, className, context.regType, context.threadReg, transformations, context);
        }


        return context.maxRegs;
    }

    private int saveMethodCallVariables(TaintTool tool, String line, List<String> linesToAdd, List<String> classLines, int lineNum, MethodInfo methodInfo, int taintTempReg, Map<String, String> taintRegMap, String signatureRegister, int methodDelta, List<String> taintedClassLines, String className, Map<String, String> regType, String threadReg, Set<String> transformations, InstrumentationContext context) {

        List<String> savedReg = new ArrayList<>();
        String delim = "L";
        String search = ", L";
        if (line.indexOf(search) == -1) {
            delim = "[";
            search = ", \\[";
        }
        String calledMethod = delim + line.split(search, 2)[1];
        String instruction = getToken(line, 0);
        MethodInfo calledMethodInfo = new MethodInfo(calledMethod, instruction.contains("static"));

        // AnalysisLogger.log(true, "Model model for %s:%n", calledMethodInfo.signature());
        Set<String> whereIsMethod = classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc());
        for (String foundClassName : whereIsMethod) {
            // AnalysisLogger.log(true, "    Found in Class: %s%n", foundClassName);
            MethodModel methodModel = classAnalysis.getMethodModel(foundClassName + "->" + calledMethodInfo.getNameAndDesc(), calledMethodInfo.isStatic());
            // AnalysisLogger.log(true, "    MethodModel: %s%n", methodModel);
            context.addModeledMethod(foundClassName + "->" + calledMethodInfo.getNameAndDesc(), methodModel.getModelType());
            context.addModeledMethod(calledMethodInfo.getClassName() + "->" + calledMethodInfo.getNameAndDesc(), foundClassName + "->" + calledMethodInfo.getNameAndDesc());
        }

        if (!isSink(line) /* && !TaintSource.isSource(className, calledMethodInfo.signature()) */) {
            return context.maxRegs;
        }

        System.out.println("SavingSink: " + calledMethodInfo.signature());

        // A list of the parameters passed between {... ...}. Includes the receiver register (if exists)
        String[] passedRegs = parsePassedRegs(line);

        if (Arrays.toString(passedRegs).contains("null")) {
            AnalysisLogger.log(true, "In method: " + methodInfo.signature() + ", tainting method: " + calledMethodInfo.signature() +  " reg: " + passedRegs[0] + " taint: " + taintRegMap.get(passedRegs[0]) + "%n");
            AnalysisLogger.log(true, "Passed regs: " + Arrays.toString(passedRegs) + "%n");
            AnalysisLogger.log(true, "Line: " + line + "%n");
            throw new RuntimeException("passedRegsContainsNull");
        }


        String receiverReg = null;
        String receiverRegTaint = null;
        if (!instruction.contains("static") && !line.contains("{}")) { // has receiver register
            receiverReg = passedRegs[0];
            receiverRegTaint = taintRegMap.get(receiverReg);
        }

        String returnReg = null;
        String returnTaintReg = null;

        boolean methodReturnIsTainted = false;
        // Get the return register and its taint register if we use the return value of the modeled method
        if (!calledMethodInfo.getReturnType().equals("V")) {
            int nextMoveIndex = nextInstructionContains(classLines, lineNum, "move-result");
            if (nextMoveIndex != -1) {
                methodReturnIsTainted = true;
                returnReg = getRegReference(classLines.get(nextMoveIndex), 1);
                returnTaintReg = taintRegMap.get(returnReg);

            }
        }
        
        
        String fristTargReg = null;

        if (receiverRegTaint != null) {
            fristTargReg = receiverRegTaint;
        } else if (methodReturnIsTainted) {
            fristTargReg = returnTaintReg;
        }
        
        linesToAdd.add("    # Taint: ConcreteCache, all param types: " + calledMethodInfo.getParams());
        if (fristTargReg != null) {
            for (int i = 0; i < parsePassedRegs(passedRegs).length; i++) {
                if (i == 0 && calledMethodInfo.getMethodName().equals("<init>")) {
                    continue;
                }
                String paramType = calledMethodInfo.getParams().get(i);
                if (paramType.equals("*")) {
                    continue;
                }
                String reg = parsePassedRegs(passedRegs)[i];
                linesToAdd.add("    # Taint: ConcreteCache, reg: " + reg + ", type: " + paramType);
                context.maxRegs = saveRegisterValue(linesToAdd, taintTempReg, reg, i, paramType, calledMethodInfo.signature());
            }
        }

        if (methodReturnIsTainted) {
            String invokeReturnInstruction;
            if (passedRegs.length > 0) {
                invokeReturnInstruction = (returnReg.equals(parsePassedRegs(passedRegs)[0]))? getMoveInstructionByType(calledMethodInfo.getParams().get(0)) : "invoke-with-return-" + getMoveInstructionByType(calledMethodInfo.getReturnType());
            } else {
                invokeReturnInstruction = "invoke-with-return-" + getMoveInstructionByType(calledMethodInfo.getReturnType());
            }
            linesToAdd.add("    # Taint: ConcreteCache, flow to return reg: " + returnReg + ", taint is in: " + returnTaintReg);
            context.maxRegs = handleOneSourceOneDest(tool,
                linesToAdd, invokeReturnInstruction, returnReg, returnReg, returnTaintReg, fristTargReg, context, savedReg);
        }

        linesToAdd.add("    # ConcreteCache: SaveMethodCallVariables");
        return context.maxRegs;
    }

    private int saveRegisterValue(List<String> linesToAdd, int taintTempReg, String regToSave, int regNum, String paramType, String methodSignature) {
        String moveTypeForRegToSave;
        if (paramType.equals("I") || paramType.equals("Z") || paramType.equals("B") || paramType.equals("S") || paramType.equals("C") || paramType.equals("F")) {
            moveTypeForRegToSave = "move/16";
        } else if (paramType.equals("J") || paramType.equals("D")) {
            moveTypeForRegToSave = "move-wide/16";
        } else {
            moveTypeForRegToSave = "move-object/16";
        }

        // Fuzzing:
        if (regNum == -1 && methodSignature.equals("Ljava/util/Locale;->getCountry()Ljava/lang/String;")) {
            String fuzzLine = "    invoke-static {}, " +
                "Lcom/thoughtworks/xstream/FuzzUtils;->longString()Ljava/lang/String;";
            linesToAdd.add(fuzzLine);
            linesToAdd.add("    move-result-object " + regToSave);
        }

        linesToAdd.add("    " + moveTypeForRegToSave + " v" + taintTempReg + ", " + regToSave);
        linesToAdd.add("    const-string/jumbo " + regToSave + ", \"" + methodSignature + "\"");
        linesToAdd.add("    move-object/16 v" + (taintTempReg + 2) + ", " + regToSave);
        linesToAdd.add("    const/16 " + regToSave + ", " + regNum);
        linesToAdd.add("    move/16 v" + (taintTempReg + 3) + ", " + regToSave);
        linesToAdd.add("    " + moveTypeForRegToSave + " v" + (taintTempReg + 4) + ", v" + taintTempReg);

        String callParamType;
        int lastReg = taintTempReg + 4;
        if (paramType.equals("I") || paramType.equals("Z") || paramType.equals("B") || paramType.equals("S") || paramType.equals("C") || paramType.equals("F")) {
            callParamType = paramType;
        } else if (paramType.equals("J") || paramType.equals("D")) {
            callParamType = paramType;
            lastReg = taintTempReg + 5;
        } else {
            callParamType = "Ljava/lang/Object;";
        }
        String newLine;
        if (regNum == -1 && methodSignature.contains("getText()Landroid/text/Editable;")) {
            newLine = "    invoke-static/range {v" + (taintTempReg + 2) + " .. v" + (lastReg) + "}, " +
                "Lcom/thoughtworks/xstream/XstreamUtils;->saveSourceOfType(Ljava/lang/String;ILandroid/text/Editable;)V";
        } else if (regNum == -1 && methodSignature.contains("Lorg/apache/http/HttpResponse;->getEntity()Lorg/apache/http/HttpEntity;")) {
            newLine = "    invoke-static/range {v" + (taintTempReg + 2) + " .. v" + (lastReg) + "}, " +
                "Lcom/thoughtworks/xstream/XstreamUtils;->saveSourceOfType(Ljava/lang/String;ILorg/apache/http/HttpEntity;)V";
        } else {
            newLine = "    invoke-static/range {v" + (taintTempReg + 2) + " .. v" + (lastReg) + "}, " +
                "Lcom/thoughtworks/xstream/XstreamUtils;->saveObject(Ljava/lang/String;I" + callParamType + ")V";
        }
        linesToAdd.add(newLine);

        linesToAdd.add("    " + moveTypeForRegToSave + " " + regToSave + ", v" + taintTempReg);

        return lastReg;
    }

    @SuppressWarnings("unchecked")
    private void handleMethodStart(InstrumentationContext context, JSONObject methodIndex, String line,
            List<String> linesToAdd) {
        methodIndex.put(context.methodDelta, context.currentMethod.signature());
        addTaintRegisters(line, linesToAdd, context.currentMethod);
        int threadRegInt = context.currentMethod.getBaseNumRegs();
        context.threadReg = "v" + threadRegInt;
        int signatureRegisterInt;
        int deltaRegInt;

        if (tool instanceof ConcTool) {
            signatureRegisterInt = threadRegInt + 1;
            deltaRegInt = signatureRegisterInt + 1;
        } else {
            signatureRegisterInt = threadRegInt;
            deltaRegInt = signatureRegisterInt;
        }
        context.signatureRegister = "v" + signatureRegisterInt;
        context.deltaReg = "v" + deltaRegInt;
        int firstTaintReg = deltaRegInt + 1;
        context.timerRegister = firstTaintReg + context.currentMethod.getNumBaseLocalRegs() + context.currentMethod.getNumBaseParams() + 2;
        context.taintTempReg = context.timerRegister + 2;
        context.maxRegs = context.taintTempReg + 2;

        createTaintTargRegMap(context.currentMethod, context.taintRegMap, firstTaintReg);

        String regToUseForInit = "v0";

        String v0MoveInstruction = moveParamsToRegs(context.currentMethod, context.newParams, linesToAdd, regToUseForInit);

        boolean alreadyMovedV0 = false;

        alreadyMovedV0 = moveParamsToRegs(context.currentMethod, context.taintTempReg, linesToAdd, regToUseForInit, v0MoveInstruction,
                alreadyMovedV0);

        initThreadReg(context.threadReg, linesToAdd, threadRegInt, regToUseForInit);

        if (tool instanceof ConcTool) {
            initSignatureReg(context.currentMethod, context.signatureRegister, linesToAdd, regToUseForInit);
        }

        initTaintRegs(tool, context.currentMethod, context.taintTempReg, context.taintRegMap, linesToAdd, firstTaintReg, regToUseForInit,
                v0MoveInstruction, alreadyMovedV0, context.erasedTaintRegs);

    }

    private String handleMoveResultOperation(List<String> classLines, String className, InstrumentationContext context,
            String lastCalled, List<String> linesToAddAtMethodEnd, int lineNum, String line, List<String> linesToAdd,
            boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        context.erasedTaintRegs.remove(taintTargReg);

        String jumpTarget = null;
        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else {
        }

        List<String> taintAdditionSite;
        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }

        
        int newMaxRegs = injectTaintSeed(tool, lastCalled, taintAdditionSite, 
            classLines, lineNum,
            context.taintTempReg+1, targetReg, className, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

        // TODO: Add
        // newMaxRegs = injectTaintSeedByReflectionAtMoveResult(tool, line, lastCalled, taintAdditionSite, context);
        // context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);


        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            addTaintCodeReturn(linesToAdd, taintAdditionSite, jumpTarget);
        } else {
        }

        lastCalled = null;
        context.fieldArraysInMethod.remove(targetReg);

        getRegTypeForMoveResults(context.regType, instruction, targetReg);
        return lastCalled;
    }

    public int injectTaintSeed(TaintTool tool, String line, List<String> linesToAdd, 
        List<String> classLines, int lineNum,
        int taintTempReg, String targReg, String className, InstrumentationContext context) {
        if (line == null) {
            return 0;
        }
        int maxRegs = 0;
        String delim = "L";
        String search = ", L";
        if (line.indexOf(search) == -1) {
            delim = "[";
            search = ", \\[";
        }
        String calledMethod = delim + line.split(search, 2)[1];

        String instruction = getToken(line, 0);

        MethodInfo calledMethodInfo = new MethodInfo(calledMethod, instruction.contains("static"));

        // if (!calledMethodInfo.getReturnType().equals("V") && TaintSource.isSource(className, calledMethodInfo.signature())) {
        //     System.out.println("SavingSource: " + calledMethodInfo.signature());
        //     linesToAdd.add("    # Taint: ConcreteCache, reg: " + targReg + ", type: " + calledMethodInfo.getReturnType());
        //     maxRegs = saveRegisterValue(linesToAdd, taintTempReg, targReg, -1, calledMethodInfo.getReturnType(), calledMethodInfo.signature());
        // }

        return maxRegs;
    }


    private void handleReturnOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        linesToAdd.clear();
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        context.erasedTaintRegs.remove(taintTargReg);

        String jumpTarget = null;
        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else {
        }

        List<String> taintAdditionSite;
        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }
        int newMaxRegs = handleReturn(tool, line, taintAdditionSite, instruction, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);


        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
        } else {
        }
        linesToAdd.add(line);
    }

    private void handleConstAssignOperation(InstrumentationContext context, String line, List<String> linesToAdd,
            String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        linesToAdd.remove(linesToAdd.size()-1);
        addEraseTaint(linesToAdd, targetReg, taintTargReg, context.erasedTaintRegs);
        linesToAdd.add(line);
        // taintTempReg = maxRegs;

        context.fieldArraysInMethod.remove(targetReg);

        getRegTypeForConstants(context.regType, instruction, targetReg);
    }

    private void handleCheckCastOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        if (context.erasedTaintRegs.contains(taintTargReg)) {
            context.erasedTaintRegs.add(taintTargReg);
            linesToAdd.remove(linesToAdd.size()-1);
            linesToAdd.add("    # Removed taint proapgation from " + taintTargReg + " to " + taintTargReg);
            addEraseTaint(linesToAdd, targetReg, taintTargReg, context.erasedTaintRegs);
            linesToAdd.add(line);
        } else {
            context.erasedTaintRegs.remove(taintTargReg);
            String jumpTarget = null;
            if (inTryBlock && tool instanceof ConcTool) {
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && tool instanceof ConcTool) {
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }
            List<String> savedReg = new ArrayList<>();
            context.maxRegs = handleOneSourceOneDest(tool,
                taintAdditionSite, instruction, targetReg, targetReg, taintTargReg, taintTargReg, context, savedReg);
            if (inTryBlock && tool instanceof ConcTool) {
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
        }

        context.fieldArraysInMethod.remove(targetReg);
        getRegTypeForObject(context.regType, targetReg);
    }

    private void handleInstanceArrayOperations(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        String srcReg = getRegReference(line, 2);
        String taintSrcReg = context.taintRegMap.get(srcReg);

        if (context.erasedTaintRegs.contains(taintSrcReg)) {
            context.erasedTaintRegs.add(taintTargReg);
            linesToAdd.remove(linesToAdd.size()-1);
            linesToAdd.add("    # Removed taint proapgation from " + taintSrcReg + " to " + taintTargReg);
            String eraseTempReg = getRegNumFromRef(targetReg) < getRegNumFromRef(srcReg)? targetReg : srcReg;
            addEraseTaint(linesToAdd, eraseTempReg, taintTargReg, context.erasedTaintRegs);
            linesToAdd.add(line);
        } else {
            context.erasedTaintRegs.remove(taintTargReg);
            String jumpTarget = null;
            if (inTryBlock && tool instanceof ConcTool) {
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && tool instanceof ConcTool) {
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }
            List<String> savedReg = new ArrayList<>();
            context.maxRegs = handleOneSourceOneDest(tool,
                taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, context, savedReg);

            if (inTryBlock && tool instanceof ConcTool) {
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
        }


        context.fieldArraysInMethod.remove(targetReg);
        if (isArrayLength(instruction)) {
            context.regType.put(targetReg, "I");
        } else {
            getRegTypeForObject(context.regType, targetReg);
        }
    }

    private void handleThrowOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        linesToAdd.clear();

        context.erasedTaintRegs.remove(taintTargReg);

        String jumpTarget = null;
        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else { // TaintDroid
        }

        List<String> taintAdditionSite;
        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }

        int newMaxRegs = handleThrow(tool, line, taintAdditionSite, instruction, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

        if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
            addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
        } else {
            // TaintDroid
        }
        linesToAdd.add(line);
    }

    private void handleArrayOperation(List<String> classLines, InstrumentationContext context,
            List<String> linesToAddAtMethodEnd, int lineNum, String line, List<String> linesToAdd, boolean inTryBlock,
            String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        String srcReg = getRegReference(line, 2);
        String taintSrcReg = context.taintRegMap.get(srcReg);

        String indexReg = getRegReference(line, 3);
        String taintIndexReg = context.taintRegMap.get(indexReg);

        if (instruction.startsWith("aget") && context.erasedTaintRegs.contains(taintSrcReg) && context.erasedTaintRegs.contains(taintIndexReg)) {
            context.erasedTaintRegs.add(taintTargReg);
            linesToAdd.add("    # Removed taint proapgation from " + taintSrcReg + " and " + taintIndexReg + " to " + taintTargReg);
            String eraseTempReg = getRegNumFromRef(targetReg) < getRegNumFromRef(srcReg)? targetReg : srcReg;

            if (getRegNumFromRef(taintTargReg) > 255) {
                String moveInstruction = getMoveByInstruction(instruction);
                linesToAdd.add("    " + moveInstruction + " v" + context.taintTempReg + ", " + eraseTempReg);
            }

            addEraseTaint(linesToAdd, eraseTempReg, taintTargReg, context.erasedTaintRegs);

            if (getRegNumFromRef(taintTargReg) > 255) {
                String moveInstruction = getMoveByInstruction(instruction);
                linesToAdd.add("    " + moveInstruction + " " + eraseTempReg + ", v" + context.taintTempReg);
            }

        } else {
            context.erasedTaintRegs.remove(taintTargReg);
            String jumpTarget = null;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            if (instruction.startsWith("aput")) {
                String temp = taintTargReg;
                taintTargReg = taintSrcReg;
                taintSrcReg = temp;
                context.maxRegs = handleTwoSourceOneDest(tool, context.taintTempReg,
                    taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintTargReg, taintSrcReg, context);

                // For the case of x = obj.field; x[index] = tainted, need to taint obj.field
                int prevInstructionIndex = indexOfMatchingGetField(classLines, lineNum, "iget-object", srcReg);
                if (prevInstructionIndex != -1) {
                    String prevLine = classLines.get(prevInstructionIndex);
                    String prevInstruction = getToken(prevLine, 0);
                    if (prevInstruction.startsWith("iget")) {
                        String baseReg = getRegReference(prevLine, 2);
                        if (!registerIsAssingedAfterInstruction(baseReg, prevInstructionIndex, classLines, lineNum) && 
                            !registerIsAssingedAfterInstruction(srcReg, prevInstructionIndex, classLines, lineNum)) {
                            prevLine = changeParamsToLocals(prevLine, context);
                            baseReg = getRegReference(prevLine, 2);
                            String igetTargetReg = getRegReference(prevLine, 1);
                            if (!baseReg.equals(igetTargetReg)) {
                                taintAdditionSite.add("    # Taint: edit of field array, taint the field");
                                int newMaxRegs = handleIinstanceOpPut(context.taintTempReg, context.maxRegs, context.taintRegMap, context.fieldArraysInMethod, prevLine, taintAdditionSite,
                                        prevInstruction, context.signatureRegister, context.regType, context);
                            }
                        }
                    }
                }


                // Multidimensioanl arrays
                String prevLine = classLines.get(lineNum-2);
                String prevInstruction = getToken(prevLine, 0);
                if (prevInstruction.startsWith("aget")) {
                    if (getRegReference(line, 2).equals(getRegReference(prevLine, 1))) {
                        List<String> savedReg = new ArrayList<>();
                        context.maxRegs = handleOneSourceOneDest(tool,
                            taintAdditionSite, instruction, getRegReference(prevLine, 2), getRegReference(prevLine, 1), context.taintRegMap.get(getRegReference(prevLine, 2)), taintTargReg, context, savedReg);
                    }
                }

                // Static arrays
                if (context.fieldArraysInMethod.containsKey(srcReg)) { // srcReg is not the source, but it's the destination array object
                    FieldAccessInfo fieldAccessInfo = context.fieldArraysInMethod.get(srcReg);
                    if (fieldAccessInfo.refType.equals("Static") && fieldAccessInfo.whereIsField != null) {
                        context.maxRegs = taintSetStaticField(taintAdditionSite, instruction, fieldAccessInfo.fieldName, fieldAccessInfo.fieldType,
                            fieldAccessInfo.targetReg, fieldAccessInfo.taintTargReg, fieldAccessInfo.whereIsField, context);
                    }
                }
            } else {
                List<String> savedReg = new ArrayList<>();
                // context.maxRegs = handleOneSourceOneDest(tool,
                //     taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, context, savedReg);
                context.maxRegs = handleTwoSourceOneDest(tool, context.taintTempReg,
                    taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, taintIndexReg, context);
                getRegTypeForStructs(context.regType, instruction, targetReg);
            }

            if (targetIsWide(instruction)) {
                addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
            }

            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
        }

        context.fieldArraysInMethod.remove(targetReg);
    }

    private void handleInstanceFieldOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        context.erasedTaintRegs.remove(taintTargReg);

        if (instruction.startsWith("iget") ) {
            // linesToAdd.clear();
            String baseRegRef = getRegReference(line, 2);

            String moveInstruction = getMoveByInstruction(instruction);

            List<String> savedReg = new ArrayList<>();
            
            if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
                linesToAdd.clear();
                linesToAdd.add("    move-object/16 v" + String.valueOf(context.taintTempReg+2) + ", " + baseRegRef);
                linesToAdd.add(line);
                linesToAdd.add("    " + moveInstruction + "/16 v" + String.valueOf(context.taintTempReg) + ", " + targetReg);
                linesToAdd.add("    move-object/16 " + baseRegRef + ", v" + String.valueOf(context.taintTempReg+2));
                savedReg.add(targetReg);
            }

            String jumpTarget = null;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else {
            }

            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            int newMaxRegs = handleIinstanceOpGet(context.taintTempReg+2, context.maxRegs, context.taintRegMap, context.fieldArraysInMethod, line, taintAdditionSite,
            instruction, context, savedReg);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
            }
            if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
                linesToAdd.add("    " + moveInstruction + "/16 " + targetReg + ", v" + String.valueOf(context.taintTempReg));
                savedReg.remove(savedReg.size()-1);
            }
            getRegTypeForStructs(context.regType, instruction, targetReg);
        } else { // iput
            String jumpTarget = null;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else {
            }
            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }
            int newMaxRegs = handleIinstanceOpPut(context.taintTempReg, context.maxRegs, context.taintRegMap, context.fieldArraysInMethod, line, taintAdditionSite,
            instruction, context.signatureRegister, context.regType, context);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
        }
    }

    private void handleStaticFieldOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        context.erasedTaintRegs.remove(taintTargReg);

        if (instruction.startsWith("sget") ) {
            linesToAdd.clear();
            String jumpTarget = null;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            int newMaxRegs = handleSstaticOpGet(line, taintAdditionSite, instruction, context);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
            linesToAdd.add(line);
            getRegTypeForStructs(context.regType, instruction, targetReg);
        } else { // sput
            String jumpTarget = null;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            int newMaxRegs = handleSstaticOpPut(line, taintAdditionSite, instruction, context);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
            if (inTryBlock && (tool instanceof ConcTool)) { // special case for exception
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
        }
    }

    private void handleUnaryOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);
        String srcReg = getRegReference(line, 2);
        String taintSrcReg = context.taintRegMap.get(srcReg);

        if (context.erasedTaintRegs.contains(taintSrcReg)) {
            context.erasedTaintRegs.add(taintTargReg);
            linesToAdd.remove(linesToAdd.size()-1);
            linesToAdd.add("    # Removed taint proapgation from " + taintSrcReg + " to " + taintTargReg);
            String eraseTempReg = getRegNumFromRef(targetReg) < getRegNumFromRef(srcReg)? targetReg : srcReg;
            addEraseTaint(linesToAdd, eraseTempReg, taintTargReg, context.erasedTaintRegs);
            linesToAdd.add(line);
        } else {
            context.erasedTaintRegs.remove(taintTargReg);

            String jumpTarget = null;
            if (inTryBlock && tool instanceof ConcTool) {
                jumpTarget = addTaintCodeJump(context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }


            List<String> taintAdditionSite;
            if (inTryBlock && tool instanceof ConcTool) {
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }
            List<String> savedReg = new ArrayList<>();
            context.maxRegs = handleOneSourceOneDest(tool,
                taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, context, savedReg);
            if (targetIsWide(instruction)) {
                addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
            }
            if (inTryBlock && tool instanceof ConcTool) {
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
        }

        context.fieldArraysInMethod.remove(targetReg);

        getRegTypeForArithmatics(context.regType, instruction, targetReg);
    }

    private void handleBinaryOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String firstSrcReg = getRegReference(line, 2);
        String secondSrcReg = getRegReference(line, 3);
        String taintTargReg = context.taintRegMap.get(targetReg);
        String firstTaintSrcReg = context.taintRegMap.get(firstSrcReg);
        String secondTaintSrcReg = context.taintRegMap.get(secondSrcReg);

        if (context.erasedTaintRegs.contains(firstTaintSrcReg) && context.erasedTaintRegs.contains(secondTaintSrcReg)) {
            context.erasedTaintRegs.add(taintTargReg);
            linesToAdd.remove(linesToAdd.size()-1);
            linesToAdd.add("    # Removed taint proapgation from " + firstTaintSrcReg + " and " + secondTaintSrcReg + " to " + taintTargReg);
            String eraseTempReg = getRegNumFromRef(targetReg) < getRegNumFromRef(firstSrcReg)? targetReg : firstSrcReg;
            addEraseTaint(linesToAdd, eraseTempReg, taintTargReg, context.erasedTaintRegs);
            linesToAdd.add(line);
        } else {
            context.erasedTaintRegs.remove(taintTargReg);

            String jumpTarget = null;
            if (inTryBlock && tool instanceof ConcTool) {
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }


            List<String> taintAdditionSite;
            if (inTryBlock && tool instanceof ConcTool) {
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            context.maxRegs = handleTwoSourceOneDest(tool, context.taintTempReg,
                taintAdditionSite, instruction, targetReg, getRegReference(line, 2), taintTargReg, firstTaintSrcReg, secondTaintSrcReg, context);

            if (targetIsWide(instruction)) {
                addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
            }

            if (inTryBlock && tool instanceof ConcTool) {
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
        }

        context.fieldArraysInMethod.remove(getRegReference(line, 1));

        getRegTypeForArithmatics(context.regType, instruction, targetReg);
    }

    private void handleBinaryOperationTwoAddr(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);
        String srcReg = getRegReference(line, 2);
        String taintSrcReg = context.taintRegMap.get(srcReg);

        String jumpTarget = null;
        if (inTryBlock && tool instanceof ConcTool) {
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else { // TaintDroid
        }


        context.erasedTaintRegs.remove(taintTargReg);

        List<String> taintAdditionSite;
        if (inTryBlock && tool instanceof ConcTool) {
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }

        context.maxRegs = handleTwoSourceOneDest(tool, context.taintTempReg,
            taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintTargReg, taintSrcReg, context);

        if (targetIsWide(instruction)) {
            addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
        }
        if (inTryBlock && tool instanceof ConcTool) {
            addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
        } else {
            // TaintDroid
        }
        context.fieldArraysInMethod.remove(targetReg);

        getRegTypeForArithmaticsTwoOps(context.regType, instruction, targetReg);
    }

    private void handleConstOperation(InstrumentationContext context, String line, List<String> linesToAdd) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);
        linesToAdd.remove(linesToAdd.size()-1);
        addEraseTaint(linesToAdd, targetReg, taintTargReg, context.erasedTaintRegs);
        linesToAdd.add(line);
        context.fieldArraysInMethod.remove(targetReg);
        getRegTypeForObject(context.regType, targetReg);
    }

    private void handleBinaryOperationLiteral(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        String srcReg = getRegReference(line, 2);
        String taintSrcReg = context.taintRegMap.get(srcReg);

        String jumpTarget = null;
        if (inTryBlock && tool instanceof ConcTool) {
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else { // TaintDroid
        }


        context.erasedTaintRegs.remove(taintTargReg);

        List<String> taintAdditionSite;
        if (inTryBlock && tool instanceof ConcTool) {
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }
        List<String> savedReg = new ArrayList<>();
        context.maxRegs = handleOneSourceOneDest(tool,
            taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, context, savedReg);

        if (targetIsWide(instruction)) {
            addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
        }
        if (inTryBlock && tool instanceof ConcTool) {
            addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
        } else {
            // TaintDroid
        }
        context.fieldArraysInMethod.remove(targetReg);
        context.regType.put(targetReg, "I");
    }


    private Integer handleIinstanceOpGet(Integer taintTempReg, Integer maxRegs, Map<String, String> taintRegMap,
            Map<String, FieldAccessInfo> fieldArraysInMethod, String line, List<String> linesToAdd, String instruction, InstrumentationContext context, List<String> savedReg) {
        String fieldRef = getLastToken(line);
        String fieldType = fieldRef.substring(fieldRef.indexOf(":")+1);
        String fieldClass = getFieldClass(fieldRef);
        String fieldName = getFieldName(fieldRef);
        String taintField = createTaintField(fieldClass, fieldName, fieldType);
        String targetReg = getRegReference(line, 1);
        String taintTargReg = taintRegMap.get(getRegReference(line, 1));

        String baseRegRef = getRegReference(line, 2);
        String taintBaseReg = taintRegMap.get(getRegReference(line, 2));


        String whereIsField = classAnalysis.getClassOfField(fieldClass, fieldName);

        if (fieldType.startsWith("[")) {
            fieldArraysInMethod.put(targetReg, new FieldAccessInfo(fieldRef, fieldType, fieldClass, fieldName, taintField, targetReg, taintTargReg, "Instance", whereIsField, baseRegRef));
        } else {
            fieldArraysInMethod.remove(targetReg);
        }

        if (forbiddenClasses.contains(fieldClass) || whereIsField == null || isIgnored(whereIsField)) {

            if (whereIsField == null || isIgnored(whereIsField)) {
                if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
                    instruction = "move-object";
                }
                maxRegs = handleOneSourceOneDest(tool,
                            linesToAdd, instruction, targetReg, baseRegRef, taintTargReg, taintBaseReg, context, savedReg);
            }


        } else {


            String moveInstruction = getMoveByInstruction(instruction);
            if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
                moveInstruction = "move-object";
            }

            String newLine = "    # TargTaint: " + taintTargReg + ", BaseTaint: " + taintBaseReg;
            linesToAdd.add(newLine);

            if (getRegNumFromRef(taintTargReg) < 16) {
                linesToAdd.add("    " + tool.getInstanceFieldInstr() + " " + taintTargReg + ", " + baseRegRef + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());
            } else {
                if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
                } else if (!savedReg.contains(targetReg)) {
                    linesToAdd.add("    " + moveInstruction + "/16 v" + String.valueOf(taintTempReg) + ", " + targetReg);
                }

                linesToAdd.add("    " + tool.getInstanceFieldInstr() + " " + targetReg + ", " + baseRegRef + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());
                linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + taintTargReg + ", " + targetReg);
                if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
                } else if (!savedReg.contains(targetReg)) {
                    linesToAdd.add("    " + moveInstruction + "/16 " + targetReg + ", v" + String.valueOf(taintTempReg));
                }

            }

            maxRegs = handleTwoSourceOneDest(tool, taintTempReg,
                    linesToAdd, instruction, targetReg, baseRegRef, taintTargReg, taintTargReg, taintBaseReg, context);

            if (targetIsWide(instruction)) {
                linesToAdd.add("    " + tool.getMoveTaint() + "/16 v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1) + ", " + taintTargReg);
            }


        }
        return maxRegs;
    }


    private Integer handleIinstanceOpPut(Integer taintTempReg, Integer maxRegs, Map<String, String> taintRegMap,
            Map<String, FieldAccessInfo> fieldArraysInMethod, String line, List<String> linesToAdd, String instruction, String signatureRegister, Map<String, String> regType, InstrumentationContext context) {
        String fieldRef = getLastToken(line);
        String fieldType = fieldRef.substring(fieldRef.indexOf(":")+1);
        String fieldClass = getFieldClass(fieldRef);
        String fieldName = getFieldName(fieldRef);
        String taintField = createTaintField(fieldClass, fieldName, fieldType);
        String targetReg = getRegReference(line, 1);
        String taintTargReg = taintRegMap.get(getRegReference(line, 1));

        String baseRegRef = getRegReference(line, 2);
        String taintBaseReg = taintRegMap.get(getRegReference(line, 2));


        String whereIsField = classAnalysis.getClassOfField(fieldClass, fieldName);

        if (fieldType.startsWith("[")) {
            fieldArraysInMethod.put(targetReg, new FieldAccessInfo(fieldRef, fieldType, fieldClass, fieldName, taintField, targetReg, taintTargReg, "Instance", whereIsField, baseRegRef));
        } else {
            fieldArraysInMethod.remove(targetReg);
        }

        // AnalysisLogger.log(methodInfo.getNameAndDesc().startsWith("generateDefaultLayoutParams()Landroidx/appcompat/widget/ActionMenuView$LayoutParams;"),
        //      "For field %s%n    Where:%s%n", fieldRef, whereIsField);

        if (forbiddenClasses.contains(fieldClass) || whereIsField == null || isIgnored(whereIsField)) {
            // String moveInstruction = getMoveInstructionByType(fieldType);
            // eraseTaint(taintTempReg, linesToAdd, targetReg, taintTargReg, moveInstruction);
            // if (targetIsWide(instruction)) {
            //     eraseTaint(taintTempReg, linesToAdd, targetReg, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), moveInstruction);
            // }
            if (whereIsField == null || isIgnored(whereIsField)) {

                maxRegs = handleTwoSourceOneDest(tool, taintTempReg,
                        linesToAdd, instruction, targetReg, baseRegRef, taintBaseReg, taintTargReg, taintBaseReg, context);
            }


        } else {
            String newLine = "    # TargTaint: " + taintTargReg + ", BaseTaint: " + taintBaseReg;
            linesToAdd.add(newLine);
            maxRegs = taintSetInstanceField(tool, taintTempReg, maxRegs, linesToAdd, instruction, signatureRegister, fieldName, fieldType, targetReg,
                taintTargReg, baseRegRef, whereIsField, regType, context);
        }
        return maxRegs;
    }

    // private Integer handleIinstanceOpPutArray(String targetReg, Integer taintTempReg, Integer maxRegs, Map<String, String> taintRegMap,
    //         Map<String, FieldAccessInfo> fieldArraysInMethod, String line, List<String> linesToAdd, String instruction, String signatureRegister, Map<String, String> regType, InstrumentationContext context) {
    //     String fieldRef = getLastToken(line);
    //     String fieldType = fieldRef.substring(fieldRef.indexOf(":")+1);
    //     String fieldClass = getFieldClass(fieldRef);
    //     String fieldName = getFieldName(fieldRef);
    //     String taintField = createTaintField(fieldClass, fieldName, fieldType);
    //     String taintTargReg = taintRegMap.get(targetReg);

    //     String baseRegRef = getRegReference(line, 2);
    //     String taintBaseReg = taintRegMap.get(getRegReference(line, 2));


    //     String whereIsField = classAnalysis.getClassOfField(fieldClass, fieldName);

    //     if (fieldType.startsWith("[")) {
    //         fieldArraysInMethod.put(targetReg, new FieldAccessInfo(fieldRef, fieldType, fieldClass, fieldName, taintField, targetReg, taintTargReg, "Instance", whereIsField, baseRegRef));
    //     } else {
    //         fieldArraysInMethod.remove(targetReg);
    //     }

    //     // AnalysisLogger.log(methodInfo.getNameAndDesc().startsWith("generateDefaultLayoutParams()Landroidx/appcompat/widget/ActionMenuView$LayoutParams;"),
    //     //      "For field %s%n    Where:%s%n", fieldRef, whereIsField);

    //     if (forbiddenClasses.contains(fieldClass) || whereIsField == null || isIgnored(whereIsField)) {
    //         // String moveInstruction = getMoveInstructionByType(fieldType);
    //         // eraseTaint(taintTempReg, linesToAdd, targetReg, taintTargReg, moveInstruction);
    //         // if (targetIsWide(instruction)) {
    //         //     eraseTaint(taintTempReg, linesToAdd, targetReg, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), moveInstruction);
    //         // }
    //         if (whereIsField == null || isIgnored(whereIsField)) {

    //             maxRegs = handleTwoSourceOneDest(tool, taintTempReg,
    //                     linesToAdd, instruction, targetReg, baseRegRef, taintBaseReg, taintTargReg, taintBaseReg, context);
    //         }


    //     } else {
    //         String newLine = "    # TargTaint: " + taintTargReg + ", BaseTaint: " + taintBaseReg;
    //         linesToAdd.add(newLine);
    //         maxRegs = taintSetInstanceField(tool, taintTempReg, maxRegs, linesToAdd, instruction, signatureRegister, fieldName, fieldType, targetReg,
    //             taintTargReg, baseRegRef, whereIsField, regType, context);
    //     }
    //     return maxRegs;
    // }

    private Integer handleSstaticOpGet(String line, List<String> linesToAdd, String instruction, InstrumentationContext context) {
        String fieldRef = getLastToken(line);
        String fieldType = fieldRef.substring(fieldRef.indexOf(":")+1);
        String fieldClass = getFieldClass(fieldRef);
        String fieldName = getFieldName(fieldRef);
        String taintField = createTaintField(fieldClass, fieldName, fieldType);
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(getRegReference(line, 1));

        String whereIsField = classAnalysis.getClassOfField(fieldClass, fieldName);

        if (fieldType.startsWith("[")) {
            context.fieldArraysInMethod.put(targetReg, new FieldAccessInfo(fieldRef, fieldType, fieldClass, fieldName, taintField, targetReg, taintTargReg, "Static", whereIsField, null));
        } else {
            context.fieldArraysInMethod.remove(targetReg);
        }

        if (forbiddenClasses.contains(fieldClass) || whereIsField == null || isIgnored(whereIsField)) {
            // Ignored
        } else {

            String newLine = "    # TargTaint: " + taintTargReg;
            linesToAdd.add(newLine);

            if (getRegNumFromRef(taintTargReg) < 256) {
                linesToAdd.add("    " + tool.getStaticFieldInstr() + " " + taintTargReg + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());
            } else {
                linesToAdd.add("    " + tool.getStaticFieldInstr() + " " + targetReg + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());
                linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + taintTargReg + ", " + targetReg);
            }

            List<String> savedReg = new ArrayList<>();
            savedReg.add(targetReg);

            context.maxRegs = handleOneSourceOneDest(tool,
                            linesToAdd, instruction, targetReg, targetReg, taintTargReg, taintTargReg, context, savedReg);
            
            savedReg.remove(savedReg.size()-1);

            if (targetIsWide(instruction)) {
                linesToAdd.add("    " + tool.getMoveTaint() + "/16 v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1) + ", " + taintTargReg);
            }

        }
        return context.maxRegs;
    }


    private Integer handleSstaticOpPut(String line, List<String> linesToAdd, String instruction, InstrumentationContext context) {
        String fieldRef = getLastToken(line);
        String fieldType = fieldRef.substring(fieldRef.indexOf(":")+1);
        String fieldClass = getFieldClass(fieldRef);
        String fieldName = getFieldName(fieldRef);
        String taintField = createTaintField(fieldClass, fieldName, fieldType);
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(getRegReference(line, 1));

        String whereIsField = classAnalysis.getClassOfField(fieldClass, fieldName);

        if (fieldType.startsWith("[")) {
            context.fieldArraysInMethod.put(targetReg, new FieldAccessInfo(fieldRef, fieldType, fieldClass, fieldName, taintField, targetReg, taintTargReg, "Static", whereIsField, null));
        } else {
            context.fieldArraysInMethod.remove(targetReg);
        }

        if (forbiddenClasses.contains(fieldClass) || whereIsField == null || isIgnored(whereIsField)) {
            // String moveInstruction = getMoveInstructionByType(fieldType);
            // eraseTaint(taintTempReg, linesToAdd, targetReg, taintTargReg, moveInstruction);
            // if (targetIsWide(instruction)) {
            //     eraseTaint(taintTempReg, linesToAdd, targetReg, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), moveInstruction);
            // }
        } else {
            String newLine = "    # TargTaint: " + taintTargReg;
            linesToAdd.add(newLine);

            context.maxRegs = taintSetStaticField(linesToAdd, instruction, fieldName, fieldType,
                targetReg, taintTargReg, whereIsField, context);

        }
        return context.maxRegs;
    }


    private Integer taintSetStaticField(List<String> linesToAdd, String instruction,
            String fieldName, String fieldType, String targetReg, String taintTargReg, String whereIsField, InstrumentationContext context) {

        List<String> savedReg = new ArrayList<>();
        context.maxRegs = handleOneSourceOneDest(tool,
                linesToAdd, instruction, targetReg, targetReg, taintTargReg, taintTargReg, context, savedReg);

        if (getRegNumFromRef(taintTargReg) < 256) {
            linesToAdd.add("    " + tool.putStaticFieldInstr() + " " + taintTargReg + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());
        } else {
            String moveInstruction = getMoveByInstruction(instruction);
            linesToAdd.add("    # Taint: taintSetStaticField, targetReg: " + targetReg + ", taintTargReg: " + taintTargReg + ", moveInstruction: " + moveInstruction);
            linesToAdd.add("    " + moveInstruction + "/16 v" + String.valueOf(context.taintTempReg+1) + ", " + targetReg);
            linesToAdd.add("    move-object/16 " + targetReg + ", " + taintTargReg);
            linesToAdd.add("    " + tool.putStaticFieldInstr() + " " + targetReg + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());
            linesToAdd.add("    " + moveInstruction + "/16 " + targetReg + ", " + " v" + String.valueOf(context.taintTempReg+1));
        }

        return context.maxRegs;
    }

    /**
     * Adjusts the register line during bytecode instrumentation.
     *
     * This method iterates through the tainted class lines in reverse order, looking for the first
     * occurrence of a line starting with ".registers". If found, it updates the number of registers based
     * on the provided maximum register count, adds a fixed offset, and replaces the original line with the
     * new line reflecting the adjusted register count. If no ".registers" line is found or if the maximum
     * register count is zero, no changes are made.
     *
     * @param taintedClassLines The list of tainted class lines to be adjusted.
     * @param maxRegs           The maximum register count to consider during adjustment.
     */
    private void fixRegisterLine(List<String> taintedClassLines, int maxRegs) {
        // If the maximum register count is zero, no adjustment is needed
        if (maxRegs == 0) {
            return;
        }

        // Iterate through tainted class lines in reverse order
        for (int i = taintedClassLines.size() - 1; i >= 0; i--) {
            String line = taintedClassLines.get(i);

            // Check if the line starts with ".registers"
            if (line.startsWith("    .registers")) {
                // Extract the current number of registers from the line
                int numRegs = Integer.parseInt(getLastToken(line));

                // Update the number of registers with the maximum register count and a fixed offset
                maxRegs = (maxRegs > numRegs) ? maxRegs : numRegs;
                maxRegs = maxRegs + 4;

                // Create a new line reflecting the adjusted register count
                String newRegsLine = "    .registers " + maxRegs;

                // Replace the original line with the new line
                taintedClassLines.set(i, newRegsLine);

                // Exit the method after the adjustment
                return;
            }

            // If a ".method" line is encountered, exit the method without making any changes
            if (line.startsWith(".method")) {
                return;
            }
        }
    }

    /**
     * Creates a unique taint field identifier based on the provided field class, field name,
     * and field type.
     *
     * This method constructs a string that represents a taint field identifier by combining the
     * field class, a prefixed identifier, the field name, sanitized field type, and additional information.
     * The resulting string is intended to uniquely identify a taint field.
     *
     * @param fieldClass The class to which the field belongs.
     * @param fieldName  The name of the field.
     * @param fieldType  The type of the field.
     * @return A string representing the taint field identifier.
     */
    private String createTaintField(String fieldClass, String fieldName, String fieldType) {
        return fieldClass + "->" + "zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc();
    }


    /**
     * Adds taint register information to the list of lines to be added during bytecode instrumentation
     * for taint analysis in a method.
     *
     * This method extracts the base number of registers from the provided line, updates the MethodInfo object
     * with this information, calculates the new total number of registers (considering additional taint
     * registers), and then creates a new line reflecting the updated register count. The resulting line is added
     * to the list of lines to be injected into the bytecode.
     *
     * @param line        The original line containing register information.
     * @param linesToAdd  The list of lines to which the taint register information will be added.
     * @param methodInfo  The MethodInfo object representing information about the current method.
     */
    private void addTaintRegisters(String line, List<String> linesToAdd, MethodInfo methodInfo) {
        // Extract the base number of registers from the provided line
        Integer baseNumRegs = Integer.parseInt(getLastToken(line));

        // Update the MethodInfo object with the base number of registers
        methodInfo.setBaseNumRegs(baseNumRegs);

        // Calculate the new total number of registers (considering additional taint registers)
        String newRegsLine = "    .registers " + ((methodInfo.getBaseNumRegs()*2) + 3); // 1 site reg, 2 temp regs

        // Clear the existing lines to be added and add the new line reflecting the updated register count
        linesToAdd.clear();
        linesToAdd.add(newRegsLine);
    }

    /**
     * Initiates the analysis process on the provided Smali files using the associated ClassAnalysis.
     */
    @Override
    public void analyze() {
        // Delegate the analysis to the associated ClassAnalysis
        classAnalysis.analyze(smaliFiles);
    }
}