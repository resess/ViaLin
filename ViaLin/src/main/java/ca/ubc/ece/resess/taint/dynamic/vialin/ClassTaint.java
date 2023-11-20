package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.File;
import java.io.FileWriter;
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
import java.util.Stack;
import org.json.simple.*;


public class ClassTaint extends TaintAnalysis {

    enum ClassRegion {
        HEADER, STATIC_FIELDS, INSTANCE_FIELDS, METHODS
    }

    protected static final List<String> twoLevelPkgs = new ArrayList<>();
    protected static final List<String> threeLevelPkgs = new ArrayList<>();

    private TaintTool tool;
    private String outDir;
    private List<String> smaliFiles;
    private final Map<String, JSONObject> classToMethodIndexMap;


    public ClassTaint(TaintTool tool, List<String> smaliFiles, String frameworkAnalysisDir, boolean isFramework, String outDir) {
        this.tool = tool;
        this.smaliFiles = smaliFiles;
        this.classAnalysis = new ClassAnalysis(frameworkAnalysisDir, outDir);
        this.classToMethodIndexMap = new HashMap<>();
        this.statistics = new Statistics();
        this.isFramework = isFramework;
        this.outDir = outDir;
    }

    @Override
    public void addTaint() {
        for (String file : smaliFiles) {
            addTaintToClassFile(file);
        }
        if (!smaliFiles.isEmpty()) {
            saveDexInfo();
        }
        statistics.print();
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
            return;
        }

        JSONObject classIndex = new JSONObject();
        classToMethodIndexMap.put(className, classIndex);

        InstrumentationContext context = new InstrumentationContext();

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
                    if (!forbiddenClasses.contains(className) && ! isIgnored(className)) {
                        addTaintField(linesToAdd, line, classIsInterface);
                    }
                } else if (context.currentMethod != null && line.startsWith("    invoke")) {
                    context.methodDelta++;
                    methodIndex.put(context.methodDelta, line);
                    linesToAdd.clear();
                    line = changeParamsToLocals(line, context);
                    lastCalled = handleMethodCallOperation(classLines, className, context, lastCalled, linesToAddAtMethodEnd,
                            lineNum, line, linesToAdd, inTryBlock);
                } else if (context.currentMethod != null && line.startsWith("    .registers")) {
                    handleMethodStart(context, methodIndex, line, linesToAdd);
                } else if (line.startsWith(".method")) {
                    if (!line.contains(" native ")) {
                        context.currentMethod = getMethodInfo(className, line);
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

                        line = changeParamsToLocals(line, context);

                        linesToAdd.add(line);

                        if (isNop(instruction)) {
                            // pass
                        } else if (isMove(instruction) || isMoveFrom16(instruction) || isMove16(instruction) ||
                            isMoveWide(instruction) || isMoveWideFrom16(instruction) || isMoveWide16(instruction) ||
                            isMoveObject(instruction) || isMoveObjectFrom16(instruction) || isMoveObject16(instruction)) {
                            handleMoveOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isMoveResult(instruction) || isMoveResultWide(instruction) || isMoveResultObject(instruction)) {
                            lastCalled = handleMoveResultOperation(classLines, className, context, lastCalled,
                                    linesToAddAtMethodEnd, lineNum, line, linesToAdd, inTryBlock, instruction);
                        } else if (isMoveException(instruction)) {
                            handleMoveExceptionOperation(classLines, context, linesToAddAtMethodEnd, lineNum, line, linesToAdd,
                                    inTryBlock, instruction);
                        } else if (isReturnVoid(instruction)) {
                            handleReturnVoidOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock);
                        } else if (isReturn(instruction) || isReturnWide(instruction) || isReturnObject(instruction)) {
                            handleReturnOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isConst4(instruction) || isConst16(instruction) || isConst(instruction) || isConstHigh16(instruction) ||
                                isConstWide16(instruction) || isConstWide32(instruction) || isConstWide(instruction) || isConstWideHigh16(instruction) ||
                                isConstString(instruction) || isConstStringJumbo(instruction) || isConstClass(instruction)) {
                                    handleConstAssignOperation(context, line, linesToAdd, instruction);
                        } else if (isMonitorEnter(instruction) || isMonitorExit(instruction)) {
                            // pass
                        } else if (isCheckCast(instruction)) {
                            handleCheckCastOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isInstanceOf(instruction) || isArrayLength(instruction)) {
                            handleInstanceArrayOperations(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isNewInstance(instruction) || isNewArray(instruction)) {
                            handleConstOperation(context, line, linesToAdd);
                        } else if (isFilledNewArray(instruction) || isFilledNewArrayRange(instruction) || isFillArrayData(instruction)) {
                            // pass
                        } else if (isThrow(instruction)) {
                            handleThrowOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isGoto(instruction) || isGoto16(instruction) || isGoto32(instruction)) {
                            context.erasedTaintRegs.clear();
                        } else if (isPackedSwitch(instruction) || isSparseSwitch(instruction)) {
                            // pass
                        } else if (isCmpkind(instruction)) {
                            handleConstOperation(context, line, linesToAdd);
                        } else if (isIfTest(instruction) || isIfTestz(instruction)) {
                            // pass
                        } else if (isArrayOp(instruction)) {
                            handleArrayOperation(classLines, context, linesToAddAtMethodEnd, lineNum, line, linesToAdd,
                                    inTryBlock, instruction);
                        } else if (isIinstanceOp(instruction)) {
                            handleInstanceFieldOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isSstaticOp(instruction)) {
                            handleStaticFieldOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isInvokeKind(instruction) || isInvokeKindRange(instruction) ||
                            isInvokePolymorphic(instruction) || isInvokePolymorphicRange(instruction) ||
                            isInvokeCustom(instruction) || isInvokeCustomRange(instruction)) {
                            throw new Error("Invokes are handled in a separate branch");
                        } else if (isUnOp(instruction)) {
                            handleUnaryOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock, instruction);
                        } else if (isBinOp(instruction)) {
                            handleBinaryOperation(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock,
                                    instruction);
                        } else if (isBinOp2addr(instruction)) {
                            handleBinaryOperationTwoAddr(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock,
                                    instruction);
                        } else if (isBinOpLit16(instruction) || isBinOpLit8(instruction)) {
                            handleBinaryOperationLiteral(context, linesToAddAtMethodEnd, line, linesToAdd, inTryBlock,
                                    instruction);
                        } else if (isConstMethodHandle(instruction) || isConstMethodType(instruction)) {
                            handleConstOperation(context, line, linesToAdd);
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
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            jumpTarget = addTaintCodeJump(context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else {
        }

        List<String> taintAdditionSite;
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }


        int newMaxRegs = injectTaintSink(tool, line, taintAdditionSite, context.taintTempReg+1, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
        Set<String> transformations = new HashSet<>();
        if (!line.startsWith("    invoke-polymorphic")) {
            newMaxRegs = addTaintToCallParams(tool, line, taintAdditionSite, classLines, lineNum, context.taintTempReg+1, className, transformations, context);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
            lastCalled = line;
        }


        newMaxRegs = injectTaintSeedByReflection(tool, line, taintAdditionSite, context.taintTempReg+1, className, context.extraTaintMethods, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
        } else {
        }

        if (!transformations.contains("Added line")) {
            linesToAdd.add(line);
        }

        if (!line.startsWith("    invoke-polymorphic")) {
            if (line.endsWith(")V")) { // for the no return case
                newMaxRegs = addGetParamTaintAfterCall(tool, line, taintAdditionSite, classLines, lineNum, context.taintTempReg+1, context);
                context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
            }
        }


        // to make sure no optimizations after method call, in case the method propagates taint to its parameters.
        context.erasedTaintRegs.clear();
        return lastCalled;
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

        if (tool instanceof ViaLinTool) {
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

        if (tool instanceof ViaLinTool) {
            initSignatureReg(context.currentMethod, context.signatureRegister, linesToAdd, regToUseForInit);
        }

        getParamTaintsAtMethodStart(tool, context.currentMethod, context.threadReg, context.taintRegMap, linesToAdd, regToUseForInit);

        createFromParcelAtMethodStart(tool, context.currentMethod, context.taintRegMap, linesToAdd, context.threadReg);

        initTaintRegs(tool, context.currentMethod, context.taintTempReg, context.taintRegMap, linesToAdd, firstTaintReg, regToUseForInit,
                v0MoveInstruction, alreadyMovedV0, context.erasedTaintRegs);

        handleThreadingAtMethodStart(tool, context.currentMethod, context.taintRegMap, linesToAdd);
    }

    private void handleMoveOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);
        String srcReg = getRegReference(line, 2);
        String taintSrcReg = context.taintRegMap.get(srcReg);

        if (context.erasedTaintRegs.contains(taintSrcReg) && context.taintTempReg < 255) {
            context.erasedTaintRegs.add(taintTargReg);
            linesToAdd.remove(linesToAdd.size()-1);
            linesToAdd.add("    # Removed taint proapgation from " + taintSrcReg + " to " + taintTargReg);
            addEraseTaint(linesToAdd, "v" + context.taintTempReg, taintTargReg, context.erasedTaintRegs);
            linesToAdd.add(line);
        } else {
            context.erasedTaintRegs.remove(taintTargReg);
            String jumpTarget = null;
            if (inTryBlock && tool instanceof ViaLinTool) {
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && tool instanceof ViaLinTool) {
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            context.maxRegs = handleOneSourceOneDest(tool,
                taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, context);

            if (targetIsWide(instruction)) {
                addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
            }

            if (inTryBlock && tool instanceof ViaLinTool) {
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
        }

        context.fieldArraysInMethod.remove(targetReg);
        getRegTypeForMoves(context.regType, instruction, targetReg);
    }

    private String handleMoveResultOperation(List<String> classLines, String className, InstrumentationContext context,
            String lastCalled, List<String> linesToAddAtMethodEnd, int lineNum, String line, List<String> linesToAdd,
            boolean inTryBlock, String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        context.erasedTaintRegs.remove(taintTargReg);

        String jumpTarget = null;
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else {
        }

        List<String> taintAdditionSite;
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }

        int newMaxRegs = addGetParamTaintAfterCall(tool, lastCalled, taintAdditionSite, classLines, lineNum, context.taintTempReg+1, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

        if (shouldModelMethod(lastCalled)) {
            context.maxRegs = addPropagateReturnTaint(tool, taintAdditionSite, taintTargReg, targetReg, instruction, context);
        } else {
            addGetReturnTaint(tool, taintAdditionSite, taintTargReg, targetReg, instruction, context);
        }


        newMaxRegs = injectTaintSeed(tool, lastCalled, taintAdditionSite, context.taintTempReg+1, taintTargReg, className, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

        newMaxRegs = injectTaintSeedByReflectionAtMoveResult(tool, line, lastCalled, taintAdditionSite, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

        if (targetIsWide(instruction)) {
            addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
        }

        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            addTaintCodeReturn(linesToAdd, taintAdditionSite, jumpTarget);
        } else {
        }

        lastCalled = null;
        context.fieldArraysInMethod.remove(targetReg);

        getRegTypeForMoveResults(context.regType, instruction, targetReg);
        return lastCalled;
    }

    private void handleMoveExceptionOperation(List<String> classLines, InstrumentationContext context,
            List<String> linesToAddAtMethodEnd, int lineNum, String line, List<String> linesToAdd, boolean inTryBlock,
            String instruction) {
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        context.erasedTaintRegs.remove(taintTargReg);

        if (isMonitorExit(getToken(getNextInstructionInMethod(classLines, lineNum), 0))) {
            linesToAdd.add("    # Monitor exit after exeception");
        } else {

            String jumpTarget = null;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else {
            }

            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            addGetExceptionTaint(tool, taintAdditionSite, taintTargReg, targetReg, instruction, context);
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
            }

            context.fieldArraysInMethod.remove(targetReg);
            getRegTypeForObject(context.regType, targetReg);
        }
    }

    private void handleReturnVoidOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock) {
        linesToAdd.clear();
        String jumpTarget = null;
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            jumpTarget = addTaintCodeJump(context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else { // TaintDroid
        }

        List<String> taintAdditionSite;
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }

        context.maxRegs = addSetParamsAtReturn(tool, taintAdditionSite, context);
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
        } else {
            // TaintDroid
        }
        linesToAdd.add(line);
    }

    private void handleReturnOperation(InstrumentationContext context, List<String> linesToAddAtMethodEnd, String line,
            List<String> linesToAdd, boolean inTryBlock, String instruction) {
        linesToAdd.clear();
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);

        context.erasedTaintRegs.remove(taintTargReg);

        String jumpTarget = null;
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else {
        }

        List<String> taintAdditionSite;
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }
        int newMaxRegs = handleReturn(tool, line, taintAdditionSite, instruction, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);


        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
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
            if (inTryBlock && tool instanceof ViaLinTool) {
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && tool instanceof ViaLinTool) {
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            context.maxRegs = handleOneSourceOneDest(tool,
                taintAdditionSite, instruction, targetReg, targetReg, taintTargReg, taintTargReg, context);
            if (inTryBlock && tool instanceof ViaLinTool) {
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
            if (inTryBlock && tool instanceof ViaLinTool) {
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && tool instanceof ViaLinTool) {
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            context.maxRegs = handleOneSourceOneDest(tool,
                taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, context);

            if (inTryBlock && tool instanceof ViaLinTool) {
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
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else { // TaintDroid
        }

        List<String> taintAdditionSite;
        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }

        int newMaxRegs = handleThrow(tool, line, taintAdditionSite, instruction, context);
        context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

        if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
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

        if (instruction.startsWith("aget") && context.erasedTaintRegs.contains(taintSrcReg)) {
            context.erasedTaintRegs.add(taintTargReg);
            linesToAdd.add("    # Removed taint proapgation from " + taintSrcReg + " to " + taintTargReg);
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
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
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


                // Multidimensioanl arrays
                String prevLine = classLines.get(lineNum-2);
                String prevInstruction = getToken(prevLine, 0);
                if (prevInstruction.startsWith("aget")) {
                    if (getRegReference(line, 2).equals(getRegReference(prevLine, 1))) {

                        context.maxRegs = handleOneSourceOneDest(tool,
                            taintAdditionSite, instruction, getRegReference(prevLine, 2), getRegReference(prevLine, 1), context.taintRegMap.get(getRegReference(prevLine, 2)), taintTargReg, context);
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

                context.maxRegs = handleOneSourceOneDest(tool,
                    taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, context);
                getRegTypeForStructs(context.regType, instruction, targetReg);
            }

            if (targetIsWide(instruction)) {
                addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
            }

            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
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

            if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
                linesToAdd.clear();
                linesToAdd.add("    move-object/16 v" + String.valueOf(context.taintTempReg+2) + ", " + baseRegRef);
                linesToAdd.add(line);
                linesToAdd.add("    " + moveInstruction + "/16 v" + String.valueOf(context.taintTempReg) + ", " + targetReg);
                linesToAdd.add("    move-object/16 " + baseRegRef + ", v" + String.valueOf(context.taintTempReg+2));
            }

            String jumpTarget = null;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else {
            }

            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            int newMaxRegs = handleIinstanceOpGet(context.taintTempReg+2, context.maxRegs, context.taintRegMap, context.fieldArraysInMethod, line, taintAdditionSite,
            instruction, context);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);

            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
            }
            if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
                linesToAdd.add("    " + moveInstruction + "/16 " + targetReg + ", v" + String.valueOf(context.taintTempReg));
            }
            getRegTypeForStructs(context.regType, instruction, targetReg);
        } else { // iput
            String jumpTarget = null;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else {
            }
            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }
            int newMaxRegs = handleIinstanceOpPut(context.taintTempReg, context.maxRegs, context.taintRegMap, context.fieldArraysInMethod, line, taintAdditionSite,
            instruction, context.signatureRegister, context.regType, context);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
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
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            int newMaxRegs = handleSstaticOpGet(line, taintAdditionSite, instruction, context);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
            } else {
                // TaintDroid
            }
            linesToAdd.add(line);
            getRegTypeForStructs(context.regType, instruction, targetReg);
        } else { // sput
            String jumpTarget = null;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }

            List<String> taintAdditionSite;
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            int newMaxRegs = handleSstaticOpPut(line, taintAdditionSite, instruction, context);
            context.maxRegs = context.maxOfCurrentMaxRegsAndNewMaxRegs(newMaxRegs);
            if (inTryBlock && (tool instanceof ViaLinTool || tool instanceof TaintDroidTool)) { // special case for exception
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
            if (inTryBlock && tool instanceof ViaLinTool) {
                jumpTarget = addTaintCodeJump(context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }


            List<String> taintAdditionSite;
            if (inTryBlock && tool instanceof ViaLinTool) {
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            context.maxRegs = handleOneSourceOneDest(tool,
                taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, context);
            if (targetIsWide(instruction)) {
                addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
            }
            if (inTryBlock && tool instanceof ViaLinTool) {
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
            if (inTryBlock && tool instanceof ViaLinTool) {
                jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
            } else { // TaintDroid
            }


            List<String> taintAdditionSite;
            if (inTryBlock && tool instanceof ViaLinTool) {
                taintAdditionSite = linesToAddAtMethodEnd;
            } else {
                taintAdditionSite = linesToAdd;
            }

            context.maxRegs = handleTwoSourceOneDest(tool, context.taintTempReg,
                taintAdditionSite, instruction, targetReg, getRegReference(line, 2), taintTargReg, firstTaintSrcReg, secondTaintSrcReg, context);

            if (targetIsWide(instruction)) {
                addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
            }

            if (inTryBlock && tool instanceof ViaLinTool) {
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
        if (inTryBlock && tool instanceof ViaLinTool) {
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else { // TaintDroid
        }


        context.erasedTaintRegs.remove(taintTargReg);

        List<String> taintAdditionSite;
        if (inTryBlock && tool instanceof ViaLinTool) {
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }

        context.maxRegs = handleTwoSourceOneDest(tool, context.taintTempReg,
            taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintTargReg, taintSrcReg, context);

        if (targetIsWide(instruction)) {
            addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
        }
        if (inTryBlock && tool instanceof ViaLinTool) {
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
        if (inTryBlock && tool instanceof ViaLinTool) {
            jumpTarget = addTaintCodeJump( context.methodDelta, linesToAdd, linesToAddAtMethodEnd);
        } else { // TaintDroid
        }


        context.erasedTaintRegs.remove(taintTargReg);

        List<String> taintAdditionSite;
        if (inTryBlock && tool instanceof ViaLinTool) {
            taintAdditionSite = linesToAddAtMethodEnd;
        } else {
            taintAdditionSite = linesToAdd;
        }

        context.maxRegs = handleOneSourceOneDest(tool,
            taintAdditionSite, instruction, targetReg, srcReg, taintTargReg, taintSrcReg, context);

        if (targetIsWide(instruction)) {
            addCopyTaint(tool, taintAdditionSite, "v" + String.valueOf(getRegNumFromRef(taintTargReg) + 1), taintTargReg);
        }
        if (inTryBlock && tool instanceof ViaLinTool) {
            addTaintCodeReturn(linesToAdd, linesToAddAtMethodEnd, jumpTarget);
        } else {
            // TaintDroid
        }
        context.fieldArraysInMethod.remove(targetReg);
        context.regType.put(targetReg, "I");
    }


    public static String addTaintCodeJump(Integer methodDelta, List<String> linesToAdd, List<String> linesToAddAtMethodEnd) {
        String jumpTarget = methodDelta + "_" + linesToAdd.size();
        // System.out.println("    Jump target:" + jumpTarget);
        linesToAdd.add("    goto :taint_code_" + jumpTarget);
        linesToAddAtMethodEnd.add("    :taint_code_" + jumpTarget);
        return jumpTarget;
    }

    public static void addTaintCodeReturn(List<String> linesToAdd, List<String> linesToAddAtMethodEnd, String jumpTarget) {
        linesToAdd.add("    :taint_return_" + jumpTarget);
        linesToAddAtMethodEnd.add("    goto :taint_return_" + jumpTarget);
    }

    private void saveDexInfo() {
        saveDexInfoToDir(outDir);
    }

    private void saveDexInfoToDir(String dir) throws Error {
        File infoDir = new File(dir, "class_info");
        if (!infoDir.isDirectory()) {
            infoDir.mkdirs();
        }
        for (Map.Entry<String, JSONObject> entry: classToMethodIndexMap.entrySet()) {
            String className = entry.getKey();
            JSONObject classInfo = entry.getValue();
            String unixClassName = className.replace("/", "_").replace(";", "") + ".json";
            File classFile = new File(infoDir, unixClassName);
            try {
                FileWriter fileWriter = new FileWriter(classFile);
                fileWriter.write(classInfo.toJSONString());
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                throw new Error("Cannot save class info: " + classFile);
            }
        }
    }







    private Integer handleIinstanceOpGet(Integer taintTempReg, Integer maxRegs, Map<String, String> taintRegMap,
            Map<String, FieldAccessInfo> fieldArraysInMethod, String line, List<String> linesToAdd, String instruction, InstrumentationContext context) {
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
                            linesToAdd, instruction, targetReg, baseRegRef, taintTargReg, taintBaseReg, context);
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
                } else {
                    linesToAdd.add("    " + moveInstruction + "/16 v" + String.valueOf(taintTempReg) + ", " + targetReg);
                }

                linesToAdd.add("    " + tool.getInstanceFieldInstr() + " " + targetReg + ", " + baseRegRef + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());
                linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + taintTargReg + ", " + targetReg);
                if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
                } else {
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


            context.maxRegs = handleOneSourceOneDest(tool,
                            linesToAdd, instruction, targetReg, targetReg, taintTargReg, taintTargReg, context);
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


        context.maxRegs = handleOneSourceOneDest(tool,
                linesToAdd, instruction, targetReg, targetReg, taintTargReg, taintTargReg, context);

        if (getRegNumFromRef(taintTargReg) < 256) {
            linesToAdd.add("    " + tool.putStaticFieldInstr() + " " + taintTargReg + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());
        } else {
            String moveInstruction = getMoveByInstruction(instruction);
            linesToAdd.add("    " + moveInstruction + "/16 v" + String.valueOf(context.taintTempReg+1) + ", " + targetReg);
            linesToAdd.add("    " + moveInstruction + "/16 " + targetReg + ", " + taintTargReg);
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
     * Adds a taint field to the list of lines to be added during bytecode instrumentation for taint analysis.
     *
     * This method takes the original line containing field information and generates access modifiers
     * based on certain conditions, such as changing "private" to "public" and removing "final" for
     * non-interface classes. It also appends a unique taint-related identifier to the field name.
     * The resulting taint field line is then added to the beginning of the lines to be injected into
     * the bytecode.
     *
     * @param linesToAdd         The list of lines to which the taint field will be added.
     * @param line               The original line containing field information.
     * @param classIsInterface   A flag indicating whether the class is an interface.
     */
    private void addTaintField(List<String> linesToAdd, String line, boolean classIsInterface) {
        // Split the original line to extract field type and modifiers
        String[] parse = line.split(":");
        String left = parse[0];
        String fieldType = parse[1].split("\\s+")[0];
        parse = left.split("\\s+");
        String [] accessModifierArray = Arrays.copyOfRange(parse, 0, parse.length-1);

        boolean isTransiet = false;

        // Change access modifiers to public
        for (int i = 0; i < accessModifierArray.length; i++) {
            String token = accessModifierArray[i];
            if (token.equals("private")) {
                accessModifierArray[i] = "public";
            } else if (!classIsInterface && token.equals("final")) {
                accessModifierArray[i] = "";
            } else if (token.equals("transient")) {
                isTransiet = true;
            }
        }

        // Generate a new field name
        String fieldName = "zzz_" + parse[parse.length-1] + "_" + sanitizeFieldType(fieldType)  + tool.fieldNameAndDesc();
        String accessModifier = String.join(" ", accessModifierArray);

        // Modify access modifier for transient fields
        if (accessModifier.contains(" static ")) {
        } else {
            if (!isTransiet) {
                accessModifier += " transient";
            }
        }

        // Construct the taint field line and add it to the beginning of the lines to be injected into the bytecode
        String taintField = accessModifier + " " + fieldName;
        linesToAdd.add(0, taintField);
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