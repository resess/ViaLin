package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.ubc.ece.resess.taint.dynamic.vialin.MethodModel.MethodModelAssignment;


class TaintAnalysis {

    public static final int MAX_METHOD_DELTA = 512;
    public static boolean debug;
    protected ClassAnalysis classAnalysis;
    protected Statistics statistics;
    public boolean isFramework;

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


     public static String [] ignoreArray = {
        "Ljava/lang/Thread",
        "Ljava/lang/PathTaint",
        "[",
        "Ljava/",
        "Lsun/misc/",
        "Llibcore/",
        // "Landroid/app",
        "Lorg/apache/",
        "Ldalvik/",




        "Landroid/os",
        // Trying this:
        "Landroid/system/", // cuases bootloop
        "Landroid/system/OsConstants;", // This caused bootloop?
        "Landroid/system/Os;", // This caused bootloop?
        "Ljava/io/FileDescriptor;", // This caused bootloop?

        // One of those caused bootloop
        "Lsun/misc/Unsafe",
        "Lsun/",
        "Landroid/R",

        "Lcom/android/internal", // called inside instrumetnation logging
        "Landroid/util/Log",

        "Landroid/support/v7/app",
        "Lcom/android/messaging/ui",
        "Lcz/msebera/android/httpclient/message/AbstractHttpMessage",
        "Landroid/view/View$OnUnhandledKeyEventListener",

        // Sinks
        "Lorg/apache/http/client/",
        "Lcz/msebera/android/httpclient/client/",
        "Lretrofit2/",
        "Lokhttp3/",
        "Lcom/squareup/okhttp/",

        "Lokio/",
        "Lcom/android/okhttp/okio/",
        "Lcom/google/gson",
        "Lorg/json/", // TODO: remove


        // Compiles!
        "Landroid/Manifest",
        "Landroid/util",
        "Landroid/hidl/manager",
        "Lcom/android/providers/contacts",
        "Landroid/app/IActivityManager",
        "Landroid/os/BatteryStats",
        "Landroid/test/",
        "Lcom/android/server/power",
        "Lcom/android/internal/telephony",
        "Lcom/android/commands/",
        "Lcom/android/internal/",
        "Lcom/android/messaging/",
        "Lcom/android/providers/",
        "Lcom/android/phone/",
        "Lcom/android/server/",
        "Landroid/icu",
        "Landroid/provider",
        "Landroid/nfcs",
        "Landroid/printservice",
        "Landroid/Rsssssss",
        "Landroid/inputmethodservice",
        "Landroid/print",
        "Landroid/preference",
        "Landroid/graphics",
        "Landroid/database",
        "Landroid/gesture",
        "Landroid/service",
        "Landroid/telecom",
        "Landroid/net",
        "Landroid/sax",
        "Landroid/bluetooth",
        "Landroid/accounts",
        "Landroid/accessibilityservice",
        "Landroid/ddm",
        "Landroid/content",
        "Landroid/transition",
        "Landroid/mtp",
        "Landroid/companion",
        "Lcom/auth0/jwt",
        "Landroid/widget",
        "Landroid/support",
        "Landroid/view/View$OnUnhandledKeyEventListener",
        "Landroid/view/",
        "Landroid/text/",

        // android.app.ActivityThread should be enabled

        "Ljavax/" // To get around native methods inside it that require precise modeling of params

     };

     private static final Set<String> ignoreClass = new HashSet<>(Arrays.asList(ignoreArray));
     private static final Set<String> ignore = new HashSet<>(Arrays.asList(ignoreArray));

    public void analyze() {
        throw new UnsupportedOperationException("Should override this method");
    }

    public void addTaint() {
        throw new UnsupportedOperationException("Should override this method");
    }




    public boolean isIgnored(Set<String> classNames) {
        for (String className : classNames) {
            if (className.startsWith("[")) {
                return true;
            }
            for (String c : ignore) {
                if (className.startsWith(c)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isIgnored(String className) {
        if (className.startsWith("[")) {
            return true;
        }
        for (String c : ignore) {
            if (className.startsWith(c)) {
                return true;
            }
        }
        return false;
    }

    public boolean isIgnoredClass(String className) {
        for (String c : ignoreClass) {
            if (className.startsWith(c)) {
                return true;
            }
        }
        return false;
    }

    public String getMoveByInstruction(String instruction) {
        String moveInstruction = "move";
        if (instruction.contains("-object") || instruction.contains("check-cast") || instruction.contains("throw")) {
            moveInstruction = "move-object";
        } else if (instruction.contains("-wide") || instruction.contains("-long")) {
            moveInstruction = "move-wide";
        }
        return moveInstruction;
    }

    public static String getMoveInstructionByType(String paramType) {
        String moveInstruction = "move-object/16";
        if (paramType.equals("Z") || paramType.equals("C") || paramType.equals("B") || paramType.equals("S") || paramType.equals("I") || paramType.equals("F")) {
            moveInstruction = "move/16";
        } else if (paramType.equals("J") || paramType.equals("D")) {
            moveInstruction = "move-wide/16";
        } else if (paramType.equals("*")) {
            moveInstruction = null;
        }
        return moveInstruction;
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

    public void addEraseTaint(List<String> linesToAdd, String tempReg, String taintReg, Set<String> erasedTaintRegs) {
        addConstTaint(linesToAdd, tempReg, taintReg, 0);
        erasedTaintRegs.add(taintReg);
    }

    public void addConstTaint(List<String> linesToAdd, String tempReg, String taintReg, int value) {
        if (getRegNumFromRef(taintReg) > 255) {
            linesToAdd.add("    const/16 " + tempReg + ", " + value);
            linesToAdd.add("    move/16 " + taintReg + ", " + tempReg);
        } else if (getRegNumFromRef(taintReg) > 15) {
            linesToAdd.add("    const/16 " + taintReg + ", " + value);
        } else if (value < 8) {
            linesToAdd.add("    const/4 " + taintReg + ", " + value);
        } else {
            linesToAdd.add("    const/16 " + taintReg + ", " + value);
        }
    }

    public void initTaintRegs(TaintTool tool, MethodInfo methodInfo, Integer taintTempReg, Map<String, String> taintRegMap,
            List<String> linesToAdd, int firstTaintReg, String regToUseForInit, String v0MoveInstruction,
            boolean alreadyMovedV0, Set<String> erasedTaintRegs) {
        int paramReg = methodInfo.getNumBaseLocalRegs() + methodInfo.getParams().size();

        String taintReg = taintRegMap.get("v"+paramReg);
        addEraseTaint(linesToAdd, regToUseForInit, taintReg, erasedTaintRegs);

        if (paramReg == getRegNumFromRef(regToUseForInit)) {
            v0MoveInstruction = tool.getMoveTaint() + "/16";
        }

        // Init taint registers, must be done after moving parameters
        if (v0MoveInstruction != null && !alreadyMovedV0 && (methodInfo.getBaseNumRegs() > 255)) {
            linesToAdd.add("    " + v0MoveInstruction + " v" + taintTempReg + ", "+regToUseForInit);
        }

        for (int i = 0; i < methodInfo.getNumBaseLocalRegs(); i++) {
            taintReg = "v"+String.valueOf(firstTaintReg + i);
            addEraseTaint(linesToAdd, regToUseForInit, taintReg, erasedTaintRegs);
        }

        if (v0MoveInstruction != null && (methodInfo.getBaseNumRegs() > 255)) {
            linesToAdd.add("    " + v0MoveInstruction + " "+regToUseForInit+", v" + taintTempReg);
        }
    }

    public void handleThreadingAtMethodStart(TaintTool tool, MethodInfo methodInfo, Map<String, String> taintRegMap, List<String> linesToAdd) {

        if (isFramework) {
            return;
        }

        if (methodInfo.getNameAndDesc().equals("doInBackground([Ljava/lang/Object;)Ljava/lang/Object;")) {
            String targetTaintReg = taintRegMap.get("p1");
            linesToAdd.add("    invoke-static {}, " + tool.getGetAsyncTaskParam());
            linesToAdd.add("    " + tool.getMoveResultTaint() + " " + targetTaintReg);
        } else if (methodInfo.getNameAndDesc().equals("invoke(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;")) {
            String targetTaintReg = taintRegMap.get("p3");
            if(methodInfo.isStatic()) {
                targetTaintReg = taintRegMap.get("p2");
            }
            linesToAdd.add("    invoke-static {}, " + tool.getGetAsyncTaskParam());
            linesToAdd.add("    " + tool.getMoveResultTaint() + " " + targetTaintReg);
        } else if (methodInfo.getNameAndDesc().equals("handleMessage(Landroid/os/Message;)V")) {
            String targetTaintReg = taintRegMap.get("p1");
            if (targetTaintReg != null) {
                linesToAdd.add("    invoke-static {}, " + tool.getGetAsyncTaskParam());
                linesToAdd.add("    " + tool.getMoveResultTaint() + " " + targetTaintReg);
            }
        }
    }

    public Integer handleReturnAtSink(TaintTool tool, List<String> linesToAdd, String instruction,
            String targReg, String sourceReg, String destTaintReg, String srcTaintReg, InstrumentationContext context, List<String> savedReg) {

        if (tool instanceof ViaLinTool) {
            context.maxRegs = handleReturnAtSink(context.taintTempReg, context.maxRegs, linesToAdd, instruction, targReg, sourceReg, destTaintReg, context.signatureRegister, context.deltaReg, srcTaintReg, context.methodDelta, context.regType, savedReg);
        }

        return context.maxRegs;
    }

    public Integer handleReturnAtSink(Integer taintTempReg, Integer maxRegs, List<String> linesToAdd, String instruction, String targReg, String sourceReg, String taintTargReg, String signatureRegister, String deltaReg, String leftTaint, int delta, Map<String, String> regType, List<String> savedReg) {

        String moveInstruction = getMoveByInstruction(instruction);

        String label = delta + "_" + linesToAdd.size();
        String smallReg = targReg;
        if (getRegNumFromRef(targReg) > 255) {
            smallReg = sourceReg;
            if (getRegNumFromRef(smallReg) > 255) {
                smallReg = "v0";
                String v0Type = regType.get("v0");
                if (v0Type == null) {
                    moveInstruction = null;
                } else {
                    moveInstruction = getMoveInstructionByType(v0Type);
                    moveInstruction = moveInstruction.replace("/16", "");
                }
            }
        }



        if (!savedReg.contains(smallReg)) {
            if (getRegNumFromRef(deltaReg) > 255 || getRegNumFromRef(taintTargReg) > 255) {
                if (!instruction.startsWith("invoke-with-return")) {
                    if (moveInstruction != null) {
                        linesToAdd.add("    " + moveInstruction + "/16 v" + taintTempReg + ", " + smallReg);
                    }
                }
            }
        }




        addConstTaint(linesToAdd, smallReg, deltaReg, delta);

        String newLine = "    invoke-static {" + leftTaint + ", " + signatureRegister + ", " + deltaReg + "}, Ljava/lang/PathTaint;->propagateSinkReturn(Ljava/lang/PathTaint;Ljava/lang/String;I)Ljava/lang/PathTaint;";
        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
        linesToAdd.addAll(rangedInvoke.first);
        int newMaxRegs = rangedInvoke.second;
        maxRegs = (maxRegs > newMaxRegs)? maxRegs : newMaxRegs;
        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    move-result-object "+ smallReg);
            linesToAdd.add("    move-object/16 " + taintTargReg + ", "  + smallReg);

        } else {
            linesToAdd.add("    move-result-object "+ taintTargReg);
        }

        // }


        if (!savedReg.contains(smallReg)) {
            if (getRegNumFromRef(deltaReg) > 255 || getRegNumFromRef(taintTargReg) > 255) {
                if (!instruction.startsWith("invoke-with-return")) {
                    if (moveInstruction != null) {
                        linesToAdd.add("    " + moveInstruction + "/16 " + smallReg + ", v" + taintTempReg);
                    }
                }
            }
        }
        return maxRegs;
    }

    public Integer handleOneSourceOneDest(TaintTool tool, List<String> linesToAdd, String instruction,
            String targReg, String sourceReg, String destTaintReg, String srcTaintReg, InstrumentationContext context, List<String> savedReg) {

        if (tool instanceof ViaLinTool) {
            context.maxRegs = addCreateTaintWithLeftViaLin(context.taintTempReg, context.maxRegs, linesToAdd, instruction, targReg, sourceReg, destTaintReg, context.signatureRegister, context.deltaReg, srcTaintReg, context.methodDelta, context.regType, savedReg);
        } else if (tool instanceof TaintDroidTool) {
            context.maxRegs = addCreateTaintWithLeftTaintDroid(context.maxRegs, linesToAdd, destTaintReg, srcTaintReg);
        }

        return context.maxRegs;
    }


    private Integer addCreateTaintWithLeftTaintDroid(Integer maxRegs, List<String> linesToAdd, String taintTargReg, String leftTaint) {
        addCopyTaint(new TaintDroidTool(), linesToAdd, taintTargReg, leftTaint);
        return maxRegs;
    }

    public Integer addCreateTaintWithLeftViaLin(Integer taintTempReg, Integer maxRegs, List<String> linesToAdd, String instruction, String targReg, String sourceReg, String taintTargReg, String signatureRegister, String deltaReg, String leftTaint, int delta, Map<String, String> regType, List<String> savedReg) {

        String moveInstruction = getMoveByInstruction(instruction);

        String label = delta + "_" + linesToAdd.size();
        String smallReg = targReg;
        if (getRegNumFromRef(targReg) > 255) {
            smallReg = sourceReg;
            if (getRegNumFromRef(smallReg) > 255) {
                smallReg = "v0";
                String v0Type = regType.get("v0");
                if (v0Type == null) {
                    moveInstruction = null;
                } else {
                    moveInstruction = getMoveInstructionByType(v0Type);
                    moveInstruction = moveInstruction.replace("/16", "");
                }
            }
        }



        if (!savedReg.contains(smallReg)) {
            if (getRegNumFromRef(leftTaint) > 255 || getRegNumFromRef(deltaReg) > 255 || getRegNumFromRef(taintTargReg) > 255) {
                if (!instruction.startsWith("invoke-with-return")) {
                    if (moveInstruction != null) {
                        linesToAdd.add("    " + moveInstruction + "/16 v" + taintTempReg + ", " + smallReg);
                    }
                }
            }
        }

        if (getRegNumFromRef(leftTaint) > 255) {
            linesToAdd.add("    move-object/16 " + smallReg + ", " + leftTaint);
            linesToAdd.add("    if-eqz "+ smallReg + ", :cond_taint_" + label);
        } else {
            linesToAdd.add("    if-eqz "+ leftTaint + ", :cond_taint_" + label);
        }



        addConstTaint(linesToAdd, smallReg, deltaReg, delta);

        String newLine = "    invoke-static {" + leftTaint + ", " + signatureRegister + ", " + deltaReg + "}, Ljava/lang/PathTaint;->propagateOneArg(Ljava/lang/PathTaint;Ljava/lang/String;I)Ljava/lang/PathTaint;";
        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
        linesToAdd.addAll(rangedInvoke.first);
        int newMaxRegs = rangedInvoke.second;
        maxRegs = (maxRegs > newMaxRegs)? maxRegs : newMaxRegs;
        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    move-result-object "+ smallReg);
            linesToAdd.add("    move-object/16 " + taintTargReg + ", "  + smallReg);

        } else {
            linesToAdd.add("    move-result-object "+ taintTargReg);
        }

        // }

        linesToAdd.add("    :cond_taint_" + label);
        if (!savedReg.contains(smallReg)) {
            if (getRegNumFromRef(leftTaint) > 255 || getRegNumFromRef(deltaReg) > 255 || getRegNumFromRef(taintTargReg) > 255) {
                if (!instruction.startsWith("invoke-with-return")) {
                    if (moveInstruction != null) {
                        linesToAdd.add("    " + moveInstruction + "/16 " + smallReg + ", v" + taintTempReg);
                    }
                }
            }
        }
        return maxRegs;
    }


    private int modelMethodCall(TaintTool tool, String line, List<String> linesToAdd, List<String> classLines, int lineNum, MethodInfo methodInfo, int taintTempReg, Map<String, String> taintRegMap, String signatureRegister, int methodDelta, List<String> taintedClassLines, String className, Map<String, String> regType, String threadReg, Set<String> transformations, InstrumentationContext context) {

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

        boolean nonDefaultModel = false;

        // AnalysisLogger.log(true, "Model model for %s:%n", calledMethodInfo.signature());
        Set<String> whereIsMethod = classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc());
        MethodModel combinedModel = new MethodModel("Combined");
        for (String foundClassName : whereIsMethod) {
            // AnalysisLogger.log(true, "    Found in Class: %s%n", foundClassName);
            MethodModel methodModel = classAnalysis.getMethodModel(foundClassName + "->" + calledMethodInfo.getNameAndDesc(), calledMethodInfo.isStatic());
            // AnalysisLogger.log(true, "    MethodModel: %s%n", methodModel);
            context.addModeledMethod(foundClassName + "->" + calledMethodInfo.getNameAndDesc(), methodModel.getModelType());
            if (methodModel.getModelType().equals("Manual") || methodModel.getModelType().equals("Kill") || methodModel.getModelType().equals("Exclude")) {
                nonDefaultModel = true;
                for (MethodModelAssignment assign : methodModel.getModel()) {
                    combinedModel.addAssign(assign);
                }
            }
        }

        if (nonDefaultModel) {
            int assignNum = 0;
            for (MethodModelAssignment assign : combinedModel.getModel()) {
                assignNum++;
                linesToAdd.add("    # Taint: ModelMethodCall (Non-default), assign: " + assign);
                // System.out.println("Model assignment: " + assign);
                if (assign.leftType.equals(MethodModelAssignment.VariableType.RETURN) && assign.rightType.equals(MethodModelAssignment.VariableType.INSTANCE)) {
                    if (methodReturnIsTainted) {
                        String reg = parsePassedRegs(passedRegs)[0];
                        String srcTaintReg = taintRegMap.get(reg);
                        if (assignNum == 1) {
                            context.maxRegs = handleOneSourceOneDest(tool,
                                linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(0)), parsePassedRegs(passedRegs)[0], parsePassedRegs(passedRegs)[0], returnTaintReg, srcTaintReg, context, savedReg);
                        } else {
                            context.maxRegs = handleTwoSourceOneDest(tool, taintTempReg,
                                linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(0)), parsePassedRegs(passedRegs)[0], parsePassedRegs(passedRegs)[0], returnTaintReg, returnTaintReg, srcTaintReg, context);
                        }
                    }
                } else if (assign.leftType.equals(MethodModelAssignment.VariableType.RETURN) && assign.rightType.equals(MethodModelAssignment.VariableType.PARAM)) {
                    if (methodReturnIsTainted) {
                        int paramNum = assign.rightParam;
                        if (!calledMethodInfo.isStatic() && paramNum < passedRegs.length - 1) {
                            paramNum++;
                        }
                        String reg = parsePassedRegs(passedRegs)[paramNum];
                        String srcTaintReg = taintRegMap.get(reg);
                        if (assignNum == 1) {
                            context.maxRegs = handleOneSourceOneDest(tool,
                                linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(0)), parsePassedRegs(passedRegs)[0], parsePassedRegs(passedRegs)[0], returnTaintReg, srcTaintReg, context, savedReg);
                        } else {
                            context.maxRegs = handleTwoSourceOneDest(tool, taintTempReg,
                                linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(0)), parsePassedRegs(passedRegs)[0], parsePassedRegs(passedRegs)[0], returnTaintReg, returnTaintReg, srcTaintReg, context);
                        }
                    }
                } else if (assign.leftType.equals(MethodModelAssignment.VariableType.INSTANCE)) {
                    int paramNum = 0;
                    if (assign.rightType.equals(MethodModelAssignment.VariableType.PARAM)) {
                        paramNum = assign.rightParam;
                        if (!calledMethodInfo.isStatic() && paramNum < passedRegs.length - 1) {
                            paramNum++;
                        }
                    }
                    String reg;
                    try {
                        reg = parsePassedRegs(passedRegs)[paramNum];
                    } catch (Exception e) {
                        System.out.println("Assign: " + assign);
                        System.out.println("Param num: " + paramNum);
                        System.out.println("Passed regs: " + Arrays.toString(passedRegs));
                        System.out.println("line: " + line);
                        throw e;
                    }
                    String srcTaintReg = taintRegMap.get(reg);


                    if (receiverRegTaint == null) {
                        System.out.println("Receiver reg taint is null: " + receiverReg);
                        System.out.println("Instruction is static: " + instruction.contains("static"));
                        System.out.println("Line: " + line);
                    }
                    if (srcTaintReg == null) {
                        System.out.println("Src taint reg is null: " + reg);
                    }

                    context.maxRegs = handleTwoSourceOneDest(tool, taintTempReg,
                        linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(0)), parsePassedRegs(passedRegs)[0], parsePassedRegs(passedRegs)[0], receiverRegTaint, receiverRegTaint, srcTaintReg, context);

                } else {
                    // linesToAdd.add("    # Taint: ModelMethodCall (Non-default), types are " + assign.leftType + " and " + assign.rightType);
                    // throw new Error("Non-default model assignment not implemented: " + assign);
                    // System.out.println("Non-default model assignment not implemented: " + assign);
                }
            }
        } else {
            String fristTargReg = null;

            if (receiverRegTaint != null) {
                fristTargReg = receiverRegTaint;
            } else if (methodReturnIsTainted) {
                fristTargReg = returnTaintReg;
            }


            if (fristTargReg != null) {
                for (int i = 0; i < parsePassedRegs(passedRegs).length; i++) {
                    if (i == 0 && calledMethodInfo.getMethodName().equals("<init>")) {
                        continue;
                    }
                    String reg = parsePassedRegs(passedRegs)[i];
                    String srcTaintReg = taintRegMap.get(reg);
                        if (srcTaintReg.equals(fristTargReg)) {
                            linesToAdd.add("    # Taint: ModelMethodCall, same source and target: " + srcTaintReg);
                            context.maxRegs = handleOneSourceOneDest(tool,
                                    linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(0)), parsePassedRegs(passedRegs)[0], parsePassedRegs(passedRegs)[0], fristTargReg, fristTargReg, context, savedReg);
                        } else {
                            linesToAdd.add("    # Taint: ModelMethodCall, from: " + srcTaintReg + " to: " + fristTargReg);
                            context.maxRegs = handleTwoSourceOneDest(tool, taintTempReg, linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(0)), parsePassedRegs(passedRegs)[0], parsePassedRegs(passedRegs)[0], fristTargReg, fristTargReg, srcTaintReg, context);
                        }
                }
            }

            if (methodReturnIsTainted) {
                String invokeReturnInstruction;
                if (passedRegs.length > 0) {
                    invokeReturnInstruction = (returnReg.equals(parsePassedRegs(passedRegs)[0]))? getMoveInstructionByType(calledMethodInfo.getParams().get(0)) : "invoke-with-return-" + getMoveInstructionByType(calledMethodInfo.getReturnType());
                } else {
                    invokeReturnInstruction = "invoke-with-return-" + getMoveInstructionByType(calledMethodInfo.getReturnType());
                }
                linesToAdd.add("    # Taint: ModelMethodCall, flow to return reg: " + returnReg + ", taint is in: " + returnTaintReg);
                context.maxRegs = handleOneSourceOneDest(tool,
                    linesToAdd, invokeReturnInstruction, returnReg, returnReg, returnTaintReg, fristTargReg, context, savedReg);
            }
        }

        // Handle specific method cases
        if (calledMethodInfo.signature().startsWith("Ljava/lang/StringBuilder;->append(")) {
            int prevInstructionIndex = previousInstructionIsExact(classLines, lineNum, "move-result-object " + receiverReg);
            if (prevInstructionIndex != -1) {
                String[] lastLineSplit = classLines.get(prevInstructionIndex - 2).split("}, ");
                if (lastLineSplit.length > 1) {
                    String lastCalledMethod = lastLineSplit[1];
                    if (lastCalledMethod.startsWith("Ljava/lang/StringBuilder;->append(")) {
                        String prevLine = classLines.get(prevInstructionIndex - 2);
                        String prevReceiver = getRegReference(prevLine, 1).replace("{", "").replace("}", "");
                        if (!prevReceiver.equals(receiverReg)) {
                            if (!registerIsAssingedAfterInstruction(prevReceiver, prevInstructionIndex, classLines, lineNum)) {
                                prevLine = changeParamsToLocals(prevLine, context);
                                prevReceiver = getRegReference(prevLine, 1).replace("{", "").replace("}", "");
                                context.maxRegs = handleTwoSourceOneDest(tool, taintTempReg, linesToAdd,
                                    "move-object/16", prevReceiver, prevReceiver, taintRegMap.get(prevReceiver), taintRegMap.get(prevReceiver), taintRegMap.get(receiverReg), context);
                            }
                        }
                    }
                }
            }
        }


        if (calledMethodInfo.signature().equals("Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V")) {
            context.maxRegs = handleOneSourceOneDest(tool,
                    linesToAdd, instruction, passedRegs[2], passedRegs[2], taintRegMap.get(passedRegs[2]), taintRegMap.get(passedRegs[4]), context, savedReg);
            context.maxRegs = handleTwoSourceOneDest(tool, taintTempReg, linesToAdd,
                "move/16", passedRegs[2], passedRegs[2], taintRegMap.get(passedRegs[2]), taintRegMap.get(passedRegs[2]), taintRegMap.get(passedRegs[3]), context);
            context.maxRegs = handleTwoSourceOneDest(tool, taintTempReg, linesToAdd,
                "move/16", passedRegs[2], passedRegs[2], taintRegMap.get(passedRegs[2]), taintRegMap.get(passedRegs[2]), taintRegMap.get(passedRegs[1]), context);
            context.maxRegs = handleTwoSourceOneDest(tool, taintTempReg, linesToAdd,
                "move/16", passedRegs[2], passedRegs[2], taintRegMap.get(passedRegs[2]), taintRegMap.get(passedRegs[2]), taintRegMap.get(passedRegs[0]), context);
            // context.maxRegs = handleOneSourceOneDest(tool,
            //         linesToAdd, instruction, passedRegs[2], passedRegs[2], taintRegMap.get(passedRegs[2]), taintRegMap.get(passedRegs[0]), context, savedReg);
            String prevLine = classLines.get(lineNum-2);
            String prevInstruction = getToken(prevLine, 0);
            if (prevInstruction.startsWith("aget")) {
                if (passedRegs[2].equals(getRegReference(prevLine, 1))) {
                    context.maxRegs = handleOneSourceOneDest(tool,
                    linesToAdd, instruction, passedRegs[2], passedRegs[2], taintRegMap.get(getRegReference(prevLine, 2)), taintRegMap.get(passedRegs[2]), context, savedReg);
                }
            }
        } else if (calledMethodInfo.signature().equals("Ljava/lang/String;->getChars(II[CI)V")) {
            context.maxRegs = handleOneSourceOneDest(tool,
                    linesToAdd, instruction, passedRegs[3], passedRegs[3], taintRegMap.get(passedRegs[3]), taintRegMap.get(passedRegs[0]), context, savedReg);
        }


        if (calledMethodInfo.getMethodName().equals("execute") && calledMethodInfo.getNumBaseParams() == 2 && calledMethodInfo.getReturnType().equals("Landroid/os/AsyncTask;")) {
            String reg = parsePassedRegs(passedRegs)[1];
            String srcTaintReg = taintRegMap.get(reg);
            if (getRegNumFromRef(srcTaintReg) < 16) {
                linesToAdd.add("    invoke-static {" + srcTaintReg + "}, " + tool.getSetAsyncTaskParam());
            } else {
                linesToAdd.add("    invoke-static/range {" + srcTaintReg + " .. " + srcTaintReg + "}, " + tool.getSetAsyncTaskParam());
            }

        }

        if ((calledMethodInfo.getMethodName().equals("send") || calledMethodInfo.getMethodName().equals("dispatchMessage")) && calledMethodInfo.getNumBaseParams() == 2 && calledMethodInfo.getParams().get(1).equals("Landroid/os/Message;")) {
            String reg = parsePassedRegs(passedRegs)[1];
            String srcTaintReg = taintRegMap.get(reg);
            if (getRegNumFromRef(srcTaintReg) < 16) {
                linesToAdd.add("    invoke-static {" + srcTaintReg + "}, " + tool.getSetAsyncTaskParam());
            } else {
                linesToAdd.add("    invoke-static/range {" + srcTaintReg + " .. " + srcTaintReg + "}, " + tool.getSetAsyncTaskParam());
            }
        }


        if (!isFramework) { // TODO: Debug, remove
            List<String> parcelLines = Parcels.addParcelTaint(tool, line, instruction, calledMethodInfo, passedRegs, taintRegMap, taintTempReg, returnTaintReg, classAnalysis, context);
            linesToAdd.addAll(parcelLines);

            Pair<List<String>, Integer> fileLines = FileTaint.addFileTaint(tool, line, instruction, calledMethodInfo, passedRegs, taintRegMap, taintTempReg, returnReg, returnTaintReg, signatureRegister, methodDelta, taintedClassLines, className, threadReg, transformations, context);
            linesToAdd.addAll(fileLines.first);
            context.maxRegs = (context.maxRegs > fileLines.second)? context.maxRegs : fileLines.second;
        }

        // if (isFramework) {
        //     return context.maxRegs;
        // }


        int prevInstructionIndex = previousInstructionIs(classLines, lineNum, "iget-object");
        if (prevInstructionIndex != -1 && receiverRegTaint != null && passedRegs.length > 1) {
            String prevLine = classLines.get(prevInstructionIndex);
            String prevInstruction = getToken(prevLine, 0);
            if (prevInstruction.startsWith("iget")) {
                String baseReg = getRegReference(prevLine, 2);
                if (!registerIsAssingedAfterInstruction(baseReg, prevInstructionIndex, classLines, lineNum)) {
                    prevLine = changeParamsToLocals(prevLine, context);
                    baseReg = getRegReference(prevLine, 2);
                    String igetTargetReg = getRegReference(prevLine, 1);
                    if (!igetTargetReg.equals(baseReg)) {
                        if (igetTargetReg.equals(receiverReg) && !baseReg.equals(igetTargetReg)) {
                            linesToAdd.add("    # Tainting base.instanceField " + baseReg + "." + igetTargetReg + " whose taint regsiter is " + taintRegMap.get(baseReg) + "." + taintRegMap.get(igetTargetReg));
                            linesToAdd.add("    # Found instruction is " + prevLine);
                            int newMaxRegs = addPutInstanceField(tool, taintTempReg, context.maxRegs, taintRegMap, prevLine, linesToAdd,
                                                prevInstruction, signatureRegister, regType, context);
                            context.maxRegs = (context.maxRegs > newMaxRegs)? context.maxRegs : newMaxRegs;
                        }
                    }
                }
            }
        }
        linesToAdd.add("    # Taint: ModelMethodCall, maxRegs = " + context.maxRegs);
        return context.maxRegs;
    }

    private Integer addPutInstanceField(TaintTool tool, Integer taintTempReg, Integer maxRegs, Map<String, String> taintRegMap,
            String line, List<String> linesToAdd, String instruction, String signatureRegister, Map<String, String> regType, InstrumentationContext context) {
        String fieldRef = getLastToken(line);
        String fieldType = fieldRef.substring(fieldRef.indexOf(":")+1);
        String fieldClass = getFieldClass(fieldRef);
        String fieldName = getFieldName(fieldRef);
        String targetReg = getRegReference(line, 1);
        String taintTargReg = taintRegMap.get(getRegReference(line, 1));

        String baseRegRef = getRegReference(line, 2);
        String taintBaseReg = taintRegMap.get(getRegReference(line, 2));


        String whereIsField = classAnalysis.getClassOfField(fieldClass, fieldName);
        if (whereIsField == null) {
            return maxRegs;
        }

        boolean movedReg = false;
        if (targetReg.equals(baseRegRef) || (instruction.equals("iget-wide") && getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(baseRegRef) )) {
            baseRegRef = "v" + String.valueOf(taintTempReg+1);
            if (taintTempReg+1> 15) {
                linesToAdd.add("    move-object/from16 v" + String.valueOf(taintTempReg+2) + ", " + targetReg);
                linesToAdd.add("    move-object/from16 " + targetReg + ", v" + String.valueOf(taintTempReg+1));
                baseRegRef = targetReg;
                movedReg = true;
            }
        }


        if (forbiddenClasses.contains(fieldClass) || isIgnored(whereIsField)) {
            if (isIgnored(whereIsField)) {
                maxRegs = handleTwoSourceOneDest(tool, taintTempReg,
                        linesToAdd, instruction, targetReg, baseRegRef, taintBaseReg, taintTargReg, taintBaseReg, context);
            }

        } else {
            String newLine = "    # TargTaint: " + taintTargReg + ", BaseTaint: " + taintBaseReg;
            linesToAdd.add(newLine);
            maxRegs = taintSetInstanceField(tool, taintTempReg, maxRegs, linesToAdd, instruction, signatureRegister, fieldName, fieldType, targetReg, taintTargReg,
                baseRegRef, whereIsField, regType, context);
        }


        if (movedReg) {
            linesToAdd.add("    move-object/16 v" + String.valueOf(taintTempReg+1) + ", " + targetReg);
            linesToAdd.add("    move-object/16 " + targetReg + ", v" + String.valueOf(taintTempReg+2));
        }
        return maxRegs;
    }


    public int addTaintToCallParams(TaintTool tool, String line, List<String> linesToAdd, List<String> classLines, int lineNum, int taintTempReg, String className, Set<String> transformations, InstrumentationContext context) {

        List<String> savedReg = new ArrayList<>();

        String delim = "L";
        String search = ", L";
        if (line.indexOf(search) == -1) {
            delim = "[";
            search = ", \\[";
        }
        String calledMethod = delim + line.split(search, 2)[1];
        String instruction = getToken(line, 0);
        if (classAnalysis.isNative(calledMethod)) {
            return modelMethodCall(tool, line, linesToAdd, classLines, lineNum, context.currentMethod, taintTempReg, context.taintRegMap, context.signatureRegister, context.methodDelta, context.taintedClassLines, className, context.regType, context.threadReg, transformations, context);
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

        if (passedRegs.length > 0) {
            if (passedRegs[0].contains("..")) {
                passedRegs[0] = passedRegs[0].replace("..", "Z");
                String firstReg = passedRegs[0].split("Z")[0];
                String lastReg = passedRegs[0].split("Z")[1];
                int firstRegNum = getRegNumFromRef(firstReg);
                int lastRegNum = getRegNumFromRef(lastReg);
                passedRegs = new String[lastRegNum-firstRegNum+1];
                for (int i = firstRegNum; i <= lastRegNum; i++) {
                    passedRegs[i-firstRegNum] = "v" + i;
                }
            }
        }

        // Set the param taints even for modelled methods, because an APK can have their own implementations of the modelled methods
        for (int i = 0; i < passedRegs.length; i++) {
            // if (i == 0 && calledMethodInfo.getMethodName().equals("<init>")) {
            //     continue;
            // }
            String taintReg = context.taintRegMap.get(passedRegs[i]);
            String moveInstruction = getMoveInstructionByType(calledMethodInfo.getParams().get(i));
            if (moveInstruction != null) {
                context.maxRegs = handleOneSourceOneDest(tool,
                        linesToAdd, moveInstruction, passedRegs[i], passedRegs[i], taintReg, taintReg, context, savedReg);
            }
            context.maxRegs = addSetParamTaintLine(tool, linesToAdd, i, taintReg, context.threadReg, taintTempReg, context.maxRegs);
        }

        if ((forbiddenClasses.contains(calledMethodInfo.getClassName())) || isIgnored(classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc())) ) {
            return modelMethodCall(tool, line, linesToAdd, classLines, lineNum, context.currentMethod, taintTempReg, context.taintRegMap, context.signatureRegister, context.methodDelta, context.taintedClassLines, className, context.regType, context.threadReg, transformations, context);
        }

        if (whereIsMethod == null || whereIsMethod.isEmpty() || isIgnored(whereIsMethod)) {
            return modelMethodCall(tool, line, linesToAdd, classLines, lineNum, context.currentMethod, taintTempReg, context.taintRegMap, context.signatureRegister, context.methodDelta, context.taintedClassLines, className, context.regType, context.threadReg, transformations, context);
        }


        if (calledMethodInfo.getNumBaseParams() == 2 && calledMethodInfo.getParams().get(1).equals("Lokhttp3/RequestBody;")) {
            String reg = parsePassedRegs(passedRegs)[1];
            String srcTaintReg = context.taintRegMap.get(reg);

            context.maxRegs = handleOneSourceOneDest(tool,
                    linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(1)), passedRegs[1], passedRegs[1], srcTaintReg, srcTaintReg, context, savedReg);
            if (getRegNumFromRef(srcTaintReg) < 16) {
                linesToAdd.add("    invoke-static {" + srcTaintReg + "}, " + tool.getSetAsyncTaskParam());
            } else {
                linesToAdd.add("    invoke-static/range {" + srcTaintReg + " .. " + srcTaintReg + "}, " + tool.getSetAsyncTaskParam());
            }
        }

        if (calledMethodInfo.getNameAndDesc().equals("invoke()Ljava/lang/Object;")) {
            String reg = parsePassedRegs(passedRegs)[0];
            String srcTaintReg = context.taintRegMap.get(reg);
            context.maxRegs = handleOneSourceOneDest(tool,
                    linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(0)), passedRegs[0], passedRegs[0], srcTaintReg, srcTaintReg, context, savedReg);
            if (getRegNumFromRef(srcTaintReg) < 16) {
                linesToAdd.add("    invoke-static {" + srcTaintReg + "}, " + tool.getSetAsyncTaskParam());
            } else {
                linesToAdd.add("    invoke-static/range {" + srcTaintReg + " .. " + srcTaintReg + "}, " + tool.getSetAsyncTaskParam());
            }
        }
        linesToAdd.add("    # Taint: AddTaintToCallParams");
        return context.maxRegs;
    }


    /**
     * This method is used to taint an instance field.
     * Its adds the necessary instructions to a list of lines to be added to a program.
     * It also updates the maximum number of registers used in the program.
     *
     * @param tool The TaintTool object that provides the taint instructions.
     * @param taintTempReg The temporary register used for taint tracking.
     * @param maxRegs The current maximum number of registers used in the program.
     * @param linesToAdd The list of lines to which the taint instructions will be added.
     * @param instruction The instruction to be executed.
     * @param signatureRegister The register that holds the signature of the method.
     * @param fieldName The name of the field to be tainted.
     * @param fieldType The type of the field to be tainted.
     * @param targetReg The target register for the taint.
     * @param taintTargReg The taint target register.
     * @param baseRegRef The base register reference.
     * @param whereIsField The location of the field to be tainted.
     * @param regType A map from register references to their types.
     * @param context The context of the instrumentation.
     * @return The updated maximum number of registers used in the program.
     */
    public Integer taintSetInstanceField(TaintTool tool, Integer taintTempReg, Integer maxRegs, List<String> linesToAdd, String instruction,
        String signatureRegister, String fieldName, String fieldType, String targetReg, String taintTargReg, String baseRegRef, String whereIsField, Map<String, String> regType, InstrumentationContext context) {

        List<String> savedReg = new ArrayList<>();

        maxRegs = handleOneSourceOneDest(tool, linesToAdd, instruction, targetReg, baseRegRef, taintTargReg, taintTargReg, context, savedReg);

        if (getRegNumFromRef(taintTargReg) < 16) {
            linesToAdd.add("    " + tool.putInstanceFieldInstr() + " " + taintTargReg + ", " + baseRegRef + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());

        } else {
            String moveInstruction = getMoveByInstruction(instruction);
            String smallReg = targetReg;
            if (targetReg.equals(baseRegRef)) {
                if (getRegNumFromRef(signatureRegister) < 16) {
                    smallReg = signatureRegister;
                    moveInstruction = "move-object";
                } else {
                    smallReg = "v0";
                    String v0Type = regType.get("v0");
                    if (v0Type == null) {
                        moveInstruction = null;
                    } else {
                        moveInstruction = getMoveInstructionByType(v0Type);
                        moveInstruction = moveInstruction.replace("/16", "");
                    }
                }
            }
            if (moveInstruction != null) {
                linesToAdd.add("    " + moveInstruction + "/16 v" + String.valueOf(taintTempReg+1) + ", " + smallReg);
            }
            linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + smallReg + ", " + taintTargReg);
            linesToAdd.add("    " + tool.putInstanceFieldInstr() + " " + smallReg + ", " + baseRegRef + ", " + whereIsField + "->zzz_" + fieldName + "_" + sanitizeFieldType(fieldType) + tool.fieldNameAndDesc());
            if (moveInstruction != null) {
                linesToAdd.add("    " + moveInstruction + "/16 " + smallReg + ", " + " v" + String.valueOf(taintTempReg+1));
            }
        }

        return maxRegs;
    }

    public boolean isSink(String line) {
        if (line == null) {
            return false;
        }

        String delim = "L";
        String search = ", L";
        if (line.indexOf(search) == -1) {
            delim = "[";
            search = ", \\[";
        }
        String calledMethod = delim + line.split(search, 2)[1];

        String instruction = getToken(line, 0);

        MethodInfo calledMethodInfo = new MethodInfo(calledMethod, instruction.contains("static"));

        Set<String> classesOfMethod = classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc());
        classesOfMethod.add(calledMethodInfo.getClassName());
        int[] sinkParams = TaintSink.sinkParams(classesOfMethod, calledMethodInfo.getNameAndDesc());

        if (sinkParams.length > 0) {
            return true;
        }
        return false;
    }


    /**
     * Injects taint sink handling.
     * Returns the maximum number of registers used in the process.
     *
     * @param tool the TaintTool to use for the injection
     * @param line the line to inject the taint sink into
     * @param linesToAdd the list of lines to add the injection to
     * @param taintTempReg the temporary register to use for the injection
     * @param context the InstrumentationContext to use for the injection
     * @return the maximum number of registers used in the process
     */
    public int injectTaintSink(TaintTool tool, String line, int lineNum, List<String> linesToAdd, int taintTempReg, InstrumentationContext context) {
        if (line == null) {
            return 0;
        }

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

        Set<String> classesOfMethod = classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc());
        classesOfMethod.add(calledMethodInfo.getClassName());
        int[] sinkParams = TaintSink.sinkParams(classesOfMethod, calledMethodInfo.getNameAndDesc());
        String[] passedRegs = null;
        if (sinkParams.length > 0) {
            passedRegs = parsePassedRegs(line);
        }

        if (sinkParams.length > 0) {
            addConstTaint(linesToAdd, "v"+taintTempReg, context.deltaReg, context.methodDelta);
            linesToAdd.add("    move-object/16 v" + taintTempReg + ", " + context.signatureRegister);
            linesToAdd.add("    const-string/jumbo " + context.signatureRegister + ", \"" + calledMethodInfo.signature() + "\"");
            String newLine = "    invoke-static {v" + taintTempReg + ", " + context.signatureRegister + ", " + context.deltaReg + "}, Ljava/lang/PathTaint;->printSinkFound(Ljava/lang/String;Ljava/lang/String;I)V";
            Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg + 1);
            linesToAdd.addAll(rangedInvoke.first);
            int newMaxRegs = rangedInvoke.second;
            context.maxRegs = (context.maxRegs > newMaxRegs)? context.maxRegs : newMaxRegs;

            linesToAdd.add("    move-object/16 " + context.signatureRegister + ", v" + taintTempReg);
            statistics.addSink();
        }

        try {
            Set<Integer> sinkParamsSet = new HashSet<Integer>();
            for (int sinkParam : sinkParams) {
                sinkParamsSet.add(sinkParam);
            }

            // AnalysisLogger.log(true, "Will inspect sink %s%d", line);

            int numPossibleRegs = 0;
            if (passedRegs != null) {
                numPossibleRegs = passedRegs.length;
            }
            // AnalysisLogger.log(true, "    numPossibleRegs %s%d", numPossibleRegs);
            for (int i = 0; i < numPossibleRegs; i++) {
                int sinkParam = i;
                if (sinkParams[0] != -1 && !sinkParamsSet.contains(i)) { // -1 for the case of all params (*)
                    continue;
                }

                String taintedVar = passedRegs[sinkParam];
                String taintTargReg = context.taintRegMap.get(taintedVar);

                context.maxRegs = handleOneSourceOneDest(tool, linesToAdd, instruction, taintedVar, taintedVar, taintTargReg, taintTargReg, context, savedReg);

                String newLine = "    invoke-static {" + taintTargReg + "}, Ljava/lang/PathTaint;->setSinkId(Ljava/lang/PathTaint;)V";
                Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                linesToAdd.addAll(rangedInvoke.first);
                int newMaxRegs = rangedInvoke.second;
                context.maxRegs = (context.maxRegs > newMaxRegs)? context.maxRegs : newMaxRegs;

                String type = calledMethodInfo.getParams().get(sinkParam);

                newLine = "    invoke-static {" + taintTargReg + "}, " + tool.getTaintDumpInstrOneArg();
                if (type.startsWith("L") || type.startsWith("[")) {
                    newLine = "    invoke-static {" + taintTargReg + ", " + taintedVar + "}, " + tool.getTaintDumpInstrTwoArg();
                }

                rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                linesToAdd.addAll(rangedInvoke.first);
                newMaxRegs = rangedInvoke.second;
                context.maxRegs = (context.maxRegs > newMaxRegs)? context.maxRegs : newMaxRegs;
            }

            if (tool instanceof ViaLinTool && sinkParams.length != 0) {
                linesToAdd.add("    invoke-static {}, Ljava/lang/PathTaint;->finishDumpTaint()V");
            }

            if (tool instanceof ViaLinTool && sinkParams.length != 0) {
                if (!calledMethodInfo.getReturnType().equals("V")) {
                    int nextMoveIndex = nextInstructionContains(context.classLines, lineNum, "move-result");
                    if (nextMoveIndex != -1) {
                        String moveInstruction = getToken(context.classLines.get(nextMoveIndex), 0);
                        String returnReg = getRegReference(context.classLines.get(nextMoveIndex), 1);
                        String returnTaintReg = context.taintRegMap.get(returnReg);
                        context.maxRegs = handleReturnAtSink(tool, linesToAdd, moveInstruction, returnReg, returnReg, returnTaintReg, returnTaintReg, context, savedReg);
                        String newLine = "    invoke-static {" + returnTaintReg + "}, Ljava/lang/PathTaint;->setSinkId(Ljava/lang/PathTaint;)V";
                        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                        linesToAdd.addAll(rangedInvoke.first);
                        int newMaxRegs = rangedInvoke.second;
                        context.maxRegs = (context.maxRegs > newMaxRegs)? context.maxRegs : newMaxRegs;
                    }
                }
            }

        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Failed to inject at sink: " + line);
            throw e;
        }
        return context.maxRegs;
    }

     public int injectViewCounting(TaintTool tool, String line, List<String> linesToAdd, int taintTempReg, InstrumentationContext context) {
        if (line == null) {
            return 0;
        }

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


        if (calledMethodInfo.signature().startsWith("Landroid/view/") || calledMethodInfo.signature().startsWith("Landroid/widget/")) {
            // do count
        } else {
            return context.maxRegs;
        }

        String[] passedRegs = parsePassedRegs(line);
        if (passedRegs == null) {
            return context.maxRegs;
        }

        try {

            for (int i = 0; i < passedRegs.length; i++) {

                String taintedVar = passedRegs[i];
                String taintTargReg = context.taintRegMap.get(taintedVar);

                String newLine = "    invoke-static {" + taintTargReg + "}, Ljava/lang/PathTaint;->logViewAPI(Ljava/lang/PathTaint;)V";

                Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                linesToAdd.addAll(rangedInvoke.first);
                int newMaxRegs = rangedInvoke.second;
                context.maxRegs = (context.maxRegs > newMaxRegs)? context.maxRegs : newMaxRegs;

            }

        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Failed to inject at ViewAPI: " + line);
            throw e;
        }
        return context.maxRegs;
    }



    /**
     * Creates a mapping between the original register names and the corresponding taint registers.
     * The mapping is stored in the provided taintRegMap.
     * @param methodInfo the method information object
     * @param taintRegMap the map to store the register mapping
     * @param firstTaintReg the number of the first taint register
     */
    public void createTaintTargRegMap(MethodInfo methodInfo, Map<String, String> taintRegMap, int firstTaintReg) {
        for (int i = 0; i < methodInfo.getBaseNumRegs() + 1; i++) {
            taintRegMap.put("v" + i, "v" + (firstTaintReg + i));
        }
    }

    /**
     * Moves parameters to registers.
     *
     * @param methodInfo the method information
     * @param newParams the new parameters
     * @param linesToAdd the lines to add
     * @param regToUseForInit the register to use for initialization
     * @return the v0 move instruction
     */
    public String moveParamsToRegs(MethodInfo methodInfo, Map<Integer, Integer> newParams, List<String> linesToAdd,
            String regToUseForInit) {
        String v0MoveInstruction = null;
        for (int i = 0; i < methodInfo.getParams().size(); i++ ) {

            String paramType = methodInfo.getParams().get(i);
            int paramReg = methodInfo.getNumBaseLocalRegs() + i;

            newParams.put(i, paramReg);

            String moveInstruction = getMoveInstructionByType(paramType);

            if (moveInstruction != null) {
                linesToAdd.add("    " + moveInstruction + " v" + paramReg + ", p" + i);

                if (paramReg == getRegNumFromRef(regToUseForInit)) {
                    v0MoveInstruction = moveInstruction;
                }
            }
        }
        return v0MoveInstruction;
    }

    /**
     * Moves taint parameters to taint registers.
     *
     * @param methodInfo the method information
     * @param taintTempReg the taint temporary register
     * @param linesToAdd the lines to add
     * @param regToUseForInit the register to use for initialization
     * @param v0MoveInstruction the v0 move instruction
     * @param alreadyMovedV0 whether v0 has already been moved
     * @return whether v0 has already been moved
     */
    public boolean moveParamsToRegs(MethodInfo methodInfo, Integer taintTempReg, List<String> linesToAdd,
        String regToUseForInit, String v0MoveInstruction, boolean alreadyMovedV0) {
        if ((v0MoveInstruction != null) && (methodInfo.getBaseNumRegs() > 255)) {
            linesToAdd.add("    " + v0MoveInstruction + " v" + taintTempReg + ", " + regToUseForInit);
            alreadyMovedV0 = true;
        }
        return alreadyMovedV0;
    }

    /**
     * Initializes the signature register for a given method with the provided signature register and lines to add.
     *
     * @param methodInfo the method information
     * @param signatureRegister the signature register
     * @param linesToAdd the lines to add
     * @param regToUseForInit the register to use for initialization
     */
    public void initSignatureReg(MethodInfo methodInfo, String signatureRegister, List<String> linesToAdd,
            String regToUseForInit) {
        if (getRegNumFromRef(signatureRegister) > 255) {
            linesToAdd.add("    const-string/jumbo "+regToUseForInit+", \"" + methodInfo.signature() + "\"");
            linesToAdd.add("    move-object/16 " + signatureRegister + ", "+regToUseForInit);
        } else {
            linesToAdd.add("    const-string/jumbo "+signatureRegister+", \"" + methodInfo.signature() + "\"");
        }
    }

    /**
     * Initializes a thread register with the current thread object or a specified register.
     * If the thread register index is greater than 255, the current thread object is stored in the specified register
     * and then moved to the thread register. Otherwise, the current thread object is directly stored in the thread register.
     * @param threadReg the thread register to be initialized
     * @param linesToAdd the list of lines to add the initialization code to
     * @param threadRegInt the index of the thread register
     * @param regToUseForInit the register to use for initialization if the thread register index is greater than 255
     */
    public void initThreadReg(String threadReg, List<String> linesToAdd, int threadRegInt, String regToUseForInit) {
        if (threadRegInt > 255) {
            linesToAdd.add("    invoke-static {}, Ljava/lang/Thread;->currentThread()Ljava/lang/Thread;");
            linesToAdd.add("    move-result-object " + regToUseForInit);
            linesToAdd.add("    move-object/16 " + threadReg + ", " + regToUseForInit);
        } else {
            linesToAdd.add("    invoke-static {}, Ljava/lang/Thread;->currentThread()Ljava/lang/Thread;");
            linesToAdd.add("    move-result-object " + threadReg);
        }
    }

    /**
     * Initializes the parameter array register.
     * If the parameter array register integer is greater than 15, the register to use for initialization is specified.
     * Otherwise, the parameter array register is initialized directly.
     *
     * @param tool the TaintTool object to initialize the parameter array register for
     * @param paramArrayReg the name of the parameter array register
     * @param linesToAdd the list of lines to add the initialization code to
     * @param paramArrayRegInt the integer value of the parameter array register
     * @param regToUseForInit the register to use for initialization if the parameter array register integer is greater than 15
     */
    public void initParamArrayReg(TaintTool tool, String paramArrayReg, List<String> linesToAdd, int paramArrayRegInt, String regToUseForInit) {
        if (paramArrayRegInt > 15) {
            linesToAdd.add("    invoke-static {}, " + tool.getParamArray());
            linesToAdd.add("    move-result-object " + regToUseForInit);
            linesToAdd.add("    move-object/16 " + paramArrayReg + ", " + regToUseForInit);
        } else {
            linesToAdd.add("    invoke-static {}, " + tool.getParamArray());
            linesToAdd.add("    move-result-object " + paramArrayReg);
        }
    }

    /**
     * For each parameter of the method, adds a line that sets the taint of the parameter.
     * Updates the taintRegMap with the taint information of the parameters of the method at its start.
     *
     * @param tool the TaintTool instance to use
     * @param methodInfo the MethodInfo instance representing the method
     * @param threadReg the register holding the thread object
     * @param taintRegMap the map of register names to their taint information, example: p0: v3 --> means that register v3 holds the taint for p0
     * @param linesToAdd the list of lines to add to the method body
     * @param regToUseForInit the register to use for initialization
     */
    public void getParamTaintsAtMethodStart(TaintTool tool, MethodInfo methodInfo, String threadReg, Map<String, String> taintRegMap,
            List<String> linesToAdd, String regToUseForInit) {

        for (int i = 0; i < methodInfo.getParams().size(); i++) {

            int paramReg = methodInfo.getNumBaseLocalRegs() + i;
            String taintReg = taintRegMap.get("v"+paramReg);
            if(taintReg == null){
                continue;
            }

            if (getRegNumFromRef(taintReg) > 255) {
                linesToAdd.add("    invoke-static/range {" + threadReg + " .. " + threadReg + "}, " + tool.getGetParamTaintInstr(i));
                linesToAdd.add("    " + tool.getMoveResultTaint() + " " + regToUseForInit);
                linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + taintReg + ", " + regToUseForInit);
            } else {
                if (getRegNumFromRef(threadReg) > 15) {
                    linesToAdd.add("    invoke-static/range {" + threadReg + " .. " + threadReg + "}, " + tool.getGetParamTaintInstr(i));
                } else {
                    linesToAdd.add("    invoke-static {" + threadReg + "}, " + tool.getGetParamTaintInstr(i));
                }
                linesToAdd.add("     " + tool.getMoveResultTaint() + " " + taintReg);
            }
            taintRegMap.put("p" + i, taintReg);
        }
    }

    /**
     * Injects taint seed by reflection.
     *
     * @param tool TaintTool instance.
     * @param line The line to inject taint seed into.
     * @param linesToAdd List of lines to add.
     * @param taintTempReg Taint temporary register.
     * @param className The name of the class.
     * @param extraTaintMethods List of extra taint methods.
     * @param context InstrumentationContext instance.
     * @return The maximum number of registers.
     */
    public int injectTaintSeedByReflection(TaintTool tool, String line, List<String> linesToAdd, int taintTempReg, String className, List<String> extraTaintMethods, InstrumentationContext context) {
        if (line == null) {
            return 0;
        }

        List<String> savedReg = new ArrayList<>();

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


        if (calledMethodInfo.signature().equals("Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) {
            String[] passedRegs = parsePassedRegs(line);
            String methodRegister = passedRegs[0];
            String taintReg = context.taintRegMap.get(methodRegister);

            for (int i = 0; i < passedRegs.length; i++) {
                maxRegs = handleOneSourceOneDest(tool, linesToAdd, getMoveInstructionByType(calledMethodInfo.getParams().get(i)), passedRegs[i], passedRegs[i], taintReg, taintReg, context, savedReg);
                maxRegs = addSetParamTaintLine(tool, linesToAdd, i, taintReg, context.threadReg, taintTempReg, maxRegs);
            }

            String taintTargReg = context.taintRegMap.get(passedRegs[0]);
            String firstParamReg = passedRegs[1];
            String firstParamTaintReg = context.taintRegMap.get(passedRegs[1]);
            maxRegs = Reflection.injectTaintSeedIfReflectiveSource(tool, methodRegister, taintTargReg, firstParamReg, context.signatureRegister, context.methodDelta, linesToAdd, extraTaintMethods, taintTempReg, className, maxRegs);
            maxRegs = handleTwoSourceOneDest(tool, taintTempReg, linesToAdd, instruction, firstParamReg, firstParamReg, firstParamTaintReg, firstParamTaintReg, taintTargReg, context);

        }
        return maxRegs;
    }

    /**
     * Handles the instrumentation of an instruction with two source registers and one destination register.
     *
     * This method delegates the instrumentation to the appropriate tool-specific method based on the type of
     * taint analysis tool provided. It returns the updated maximum register count.
     *
     * @param tool               The TaintTool instance representing the taint analysis tool being used.
     * @param taintTempReg       The temporary register used for taint operations.
     * @param linesToAdd         The list of lines to which the taint-related instructions will be added.
     * @param instruction        The original instruction related to the tainting operation.
     * @param targReg            The destination register for the tainting operation.
     * @param sourceReg          The first source register for the tainting operation.
     * @param destTaintReg       The register containing the taint information for the destination register.
     * @param firstSrcTaintReg   The register containing the taint information for the first source register.
     * @param secondSrcTaintReg  The register containing the taint information for the second source register.
     * @param context            The InstrumentationContext object containing contextual information.
     * @return The updated maximum register count.
     */
    public Integer handleTwoSourceOneDest(TaintTool tool, Integer taintTempReg,
            List<String> linesToAdd, String instruction,
            String targReg, String sourceReg, String destTaintReg, String firstSrcTaintReg, String secondSrcTaintReg, InstrumentationContext context) {
        if (tool instanceof ViaLinTool) {
            return addCreateTaintWithLeftRightViaLin(taintTempReg, context.maxRegs, linesToAdd, instruction, targReg, sourceReg, destTaintReg, context.signatureRegister, context.deltaReg, firstSrcTaintReg, secondSrcTaintReg, context.methodDelta, context.regType);
        } else if (tool instanceof TaintDroidTool) {
            return addCreateTaintWithLeftRightTaintDroid(taintTempReg, context.maxRegs, linesToAdd, instruction, targReg, destTaintReg, firstSrcTaintReg, secondSrcTaintReg, context.regType);
        }
        return context.maxRegs;
    }

    /**
     * Adds a line of code to create a taint value from two source taints for TaintDroid.
     *
     * @param taintTempReg the temporary register to use for taint operations
     * @param maxRegs the maximum number of registers used so far
     * @param linesToAdd the list of lines to add the new line to
     * @param instruction the instruction to use for the new line
     * @param targReg the register to store the new taint value in
     * @param taintTargReg the register containing the taint value for the target
     * @param leftTaint the register containing the taint value for the left source
     * @param rightTaint the register containing the taint value for the right source
     * @param regType the map of register types
     * @return the new maximum number of registers used after adding the new line
     */
    private Integer addCreateTaintWithLeftRightTaintDroid(Integer taintTempReg, Integer maxRegs, List<String> linesToAdd, String instruction, String targReg, String taintTargReg, String leftTaint, String rightTaint, Map<String, String> regType) {
        int firstSrcIndex = getRegNumFromRef(leftTaint);
        int secondSrcIndex = getRegNumFromRef(rightTaint);
        int dstIndex = getRegNumFromRef(taintTargReg);
        if (firstSrcIndex == dstIndex && secondSrcIndex == dstIndex) {
            return maxRegs;
        }
        if (dstIndex < 256 && firstSrcIndex < 256 && secondSrcIndex < 256) {
            linesToAdd.add("    or-int " + taintTargReg + ", " + leftTaint + ", " + rightTaint);
        } else {
            String moveInstruction = getMoveByInstruction(instruction);
            linesToAdd.add("    " + moveInstruction + "/16 v" + taintTempReg + ", " + targReg);

            String v0MoveInstruction;
            String v0Type = regType.get("v0");
            if (v0Type == null) {
                v0MoveInstruction = null;
            } else {
                v0MoveInstruction = getMoveInstructionByType(v0Type);
                v0MoveInstruction = v0MoveInstruction.replace("/16", "");
            }

            if (v0MoveInstruction != null) {
                linesToAdd.add("    " + v0MoveInstruction + "/16 v" + String.valueOf(taintTempReg+2) + ", v0");
            }

            linesToAdd.add("    move/16 " + targReg + ", " + leftTaint);
            linesToAdd.add("    move/16 v0, " + rightTaint);
            linesToAdd.add("    or-int v0, v0, " + targReg);
            linesToAdd.add("    move/16 " + taintTargReg + ", v0");

            if (v0MoveInstruction != null) {
                linesToAdd.add("    " + v0MoveInstruction + "/16 v0, v" + String.valueOf(taintTempReg+2));
            }

            linesToAdd.add("    " + moveInstruction + "/16 " + targReg + ", v" + taintTempReg);
        }
        return maxRegs + 2;
    }

    /**
     * Adds a line of code to create a taint value from two source taints for ViaLin.
     *
     * @param taintTempReg the temporary register to use for taint operations
     * @param maxRegs the maximum number of registers used so far
     * @param linesToAdd the list of lines to add the new line to
     * @param instruction the instruction to use for the new line
     * @param targReg the register to store the new taint value in
     * @param sourceReg the register containing the source value
     * @param taintTargReg the register containing the taint value for the target
     * @param signatureRegister the register containing the signature of the method
     * @param deltaReg the register containing the delta value for the method
     * @param leftTaint the register containing the taint value for the left source
     * @param rightTaint the register containing the taint value for the right source
     * @param delta the delta value for the method
     * @param regType the map of register types
     * @return the new maximum number of registers used after adding the new line
     */
    private Integer addCreateTaintWithLeftRightViaLin(Integer taintTempReg, Integer maxRegs, List<String> linesToAdd, String instruction, String targReg, String sourceReg, String taintTargReg, String signatureRegister, String deltaReg, String leftTaint, String rightTaint, int delta, Map<String, String> regType) {

        String moveInstruction = getMoveByInstruction(instruction);

        String label = delta + "_" + linesToAdd.size();

        String smallReg = targReg;
        if (getRegNumFromRef(targReg) > 255) {
            smallReg = sourceReg;
            if (getRegNumFromRef(smallReg) > 255) {
                smallReg = "v0";
                String v0Type = regType.get("v0");
                if (v0Type == null) {
                    moveInstruction = null;
                } else {
                    moveInstruction = getMoveInstructionByType(v0Type);
                    moveInstruction = moveInstruction.replace("/16", "");
                }

            }
        }

        if (getRegNumFromRef(leftTaint) > 255 || getRegNumFromRef(rightTaint) > 255 || getRegNumFromRef(deltaReg) > 255 || getRegNumFromRef(taintTargReg) > 255) {
            if (moveInstruction != null) {
                linesToAdd.add("    " + moveInstruction + "/16 v" + taintTempReg + ", " + smallReg);
            }
            linesToAdd.add("    move-object/16 " + smallReg + ", " + rightTaint);
        }

        if (getRegNumFromRef(leftTaint) > 255) {
            linesToAdd.add("    if-eqz "+ smallReg + ", :cond_taint_1_" + label);
        } else {
            linesToAdd.add("    if-eqz "+ leftTaint + ", :cond_taint_1_" + label);
        }

        linesToAdd.add("    goto :cond_taint_2_" + label);
        linesToAdd.add("    :cond_taint_1_" + label);
        if (getRegNumFromRef(rightTaint) > 255) {
            linesToAdd.add("    if-eqz "+ smallReg + ", :cond_taint_" + label);
        } else {
            linesToAdd.add("    if-eqz "+ rightTaint + ", :cond_taint_" + label);
        }
        linesToAdd.add("    :cond_taint_2_" + label);

        addConstTaint(linesToAdd, smallReg, deltaReg, delta);

        String newLine = "    invoke-static {" + leftTaint + ", " + rightTaint + ", " + signatureRegister + ", " + deltaReg + "}, Ljava/lang/PathTaint;->propagateTwoArgs(Ljava/lang/PathTaint;Ljava/lang/PathTaint;Ljava/lang/String;I)Ljava/lang/PathTaint;";
        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg+2);
        linesToAdd.addAll(rangedInvoke.first);
        int newMaxRegs = rangedInvoke.second;
        maxRegs = (maxRegs > newMaxRegs)? maxRegs : newMaxRegs;
        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    move-result-object "+ smallReg);
            linesToAdd.add("    move-object/16 " + taintTargReg + ", "  + smallReg);
        } else {
            linesToAdd.add("    move-result-object "+ taintTargReg);
        }


        linesToAdd.add("    :cond_taint_" + label);

        if (getRegNumFromRef(leftTaint) > 255 || getRegNumFromRef(rightTaint) > 255 || getRegNumFromRef(deltaReg) > 255 || getRegNumFromRef(taintTargReg) > 255) {
            if (moveInstruction != null) {
                linesToAdd.add("    " + moveInstruction + "/16 " + smallReg + ", v" + taintTempReg);
            }
        }

        return maxRegs;
    }

    /**
     * Adds a line of code to set parameter taint.
     *
     * @param tool the TaintTool object to use for setting parameter taint
     * @param linesToAdd the list of lines to add the new line to
     * @param i the index of the parameter to set taint for
     * @param taintReg the register containing the taint value
     * @param threadReg the register containing the thread ID
     * @param taintTempReg the temporary register to use for taint operations
     * @param maxRegs the maximum number of registers used so far
     * @return the new maximum number of registers used after adding the new line
     */
    public int addSetParamTaintLine(TaintTool tool, List<String> linesToAdd, int i, String taintReg, String threadReg, int taintTempReg, int maxRegs) {
        String newLine = "    invoke-static {" + threadReg + ", " + taintReg + "}, " + tool.getSetParamTaintInstr(i);
        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
        linesToAdd.addAll(rangedInvoke.first);
        int newMaxRegs = rangedInvoke.second;
        maxRegs = (maxRegs > newMaxRegs)? maxRegs : newMaxRegs;

        return maxRegs;
    }

    /**
     * Adds taint to the exception thrown by a method call.
     * @param tool The TaintTool instance.
     * @param linesToAdd The list of lines to add the taint instructions to.
     * @param taintTargReg The register containing the taint to be added.
     * @param targetReg The register containing the target object.
     * @param instruction The instruction that caused the exception.
     * @param context The instrumentation context.
     */
    public void addGetExceptionTaint(TaintTool tool, List<String> linesToAdd, String taintTargReg, String targetReg, String instruction, InstrumentationContext context) {
        String moveInstruction = getMoveByInstruction(instruction);
        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    " + moveInstruction + "/16 v" + context.taintTempReg + ", " + targetReg);
        }
        linesToAdd.add("    invoke-static {}, " + tool.getGetThrowTaintInstr());
        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    " + tool.getMoveResultTaint() + " " + targetReg);
            linesToAdd.add("    " + moveInstruction + "/16 " + targetReg + ", v" + context.taintTempReg);
        } else {
            linesToAdd.add("    " + tool.getMoveResultTaint() + " " + taintTargReg);
        }
    }

    /**
     * Handles the taint propagation for parmaters are return instructions.
     *
     * @param tool The TaintTool object that provides the set param taint instruction.
     * @param line The line of code containing the return instruction.
     * @param linesToAdd The list of lines to which the taint instruction will be added.
     * @param instruction The instruction to be handled.
     * @param context The InstrumentationContext instance to use.
     * @return The updated maximum number of registers used in the program.
     */
    public int addSetParamsAtReturn(TaintTool tool, List<String> linesToAdd, InstrumentationContext context) {
        for (int i = 0; i < context.currentMethod.getParams().size(); i++) {
            int paramReg = context.currentMethod.getNumBaseLocalRegs() + i;
            String taintReg = context.taintRegMap.get("v" + paramReg);
            String originalType = context.currentMethod.getParams().get(i);
            String currentType = context.regType.get("v" + context.newParams.get(i));
            if (originalType.equals(currentType) || currentType == null) {
                context.maxRegs = addSetParamTaintLine(tool, linesToAdd, i, taintReg, context.threadReg, context.taintTempReg, context.maxRegs);
            }
        }
        return context.maxRegs;
    }

    /**
     * Handles the taint propagation for return instructions.
     *
     * @param tool The TaintTool object that provides the set return taint instruction.
     * @param line The line of code containing the return instruction.
     * @param linesToAdd The list of lines to which the return taint instruction will be added.
     * @param instruction The instruction to be handled.
     * @param context The InstrumentationContext instance to use.
     * @return The updated maximum number of registers used in the program.
     */
    public Integer handleReturn(TaintTool tool, String line, List<String> linesToAdd, String instruction, InstrumentationContext context) {
        List<String> savedReg = new ArrayList<>();
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);
        context.maxRegs = handleOneSourceOneDest(tool, linesToAdd, instruction, targetReg, targetReg, taintTargReg, taintTargReg, context, savedReg);
        context.maxRegs = addSetReturnTaint(tool, context.taintTempReg, context.maxRegs, linesToAdd, taintTargReg);
        context.maxRegs = addSetParamsAtReturn(tool, linesToAdd, context);
        return context.maxRegs;
    }

    /**
     * This method is used to add taint propagation for return taint instructions.
     *
     * @param tool The TaintTool object that provides the set return taint instruction.
     * @param taintTempReg The temporary register used for taint tracking.
     * @param maxRegs The current maximum number of registers used in the program.
     * @param linesToAdd The list of lines to which the return taint instruction will be added.
     * @param taintTargReg The target register for the taint.
     * @return The updated maximum number of registers used in the program.
     */
    private Integer addSetReturnTaint(TaintTool tool, Integer taintTempReg, Integer maxRegs, List<String> linesToAdd, String taintTargReg) {
        String newLine;
        newLine = "    invoke-static {" + taintTargReg + "}, " + tool.getSetReturnTaintInstr();
        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
        linesToAdd.addAll(rangedInvoke.first);
        int newMaxRegs = rangedInvoke.second;
        maxRegs = (maxRegs > newMaxRegs)? maxRegs : newMaxRegs;
        return maxRegs;
    }

    /**
     * Handles the taint propagation for throw instructions.
     *
     * @param tool The TaintTool object that provides the set throw taint instruction.
     * @param line The line of code containing the throw instruction.
     * @param linesToAdd The list of lines to which the throw taint instruction will be added.
     * @param instruction The instruction to be handled.
     * @param context The InstrumentationContext instance to use.
     * @return The updated maximum number of registers used in the program.
     */
    public Integer handleThrow(TaintTool tool, String line, List<String> linesToAdd, String instruction, InstrumentationContext context) {
        List<String> savedReg = new ArrayList<>();
        String targetReg = getRegReference(line, 1);
        String taintTargReg = context.taintRegMap.get(targetReg);
        context.maxRegs = handleOneSourceOneDest(tool, linesToAdd, instruction, targetReg, targetReg, taintTargReg, taintTargReg, context, savedReg);
        context.maxRegs = addSetThrowTaint(tool, context.taintTempReg, context.maxRegs, linesToAdd, taintTargReg);
        context.maxRegs = addSetParamsAtReturn(tool, linesToAdd, context);
        return context.maxRegs;
    }

    /**
     * This method is used to add taint propagation for throw taint instructions.
     *
     * @param tool The TaintTool object that provides the set throw taint instruction.
     * @param taintTempReg The temporary register used for taint tracking.
     * @param maxRegs The current maximum number of registers used in the program.
     * @param linesToAdd The list of lines to which the throw taint instruction will be added.
     * @param taintTargReg The target register for the taint.
     * @return The updated maximum number of registers used in the program.
     */
    private Integer addSetThrowTaint(TaintTool tool, Integer taintTempReg, Integer maxRegs, List<String> linesToAdd, String taintTargReg) {
        String newLine;
        newLine = "    invoke-static {" + taintTargReg + "}, " + tool.getSetThrowTaintInstr();
        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
        linesToAdd.addAll(rangedInvoke.first);
        int newMaxRegs = rangedInvoke.second;
        maxRegs = (maxRegs > newMaxRegs)? maxRegs : newMaxRegs;
        return maxRegs;
    }

    /**
     * Adds a copy taint instruction to the list of lines to add.
     *
     * @param tool the taint tool to use
     * @param linesToAdd the list of lines to add the instruction to
     * @param taintTargReg the target register to add taint to
     * @param taintSrcReg the source register to copy taint from
     */
    public void addCopyTaint(TaintTool tool, List<String> linesToAdd, String taintTargReg, String taintSrcReg) {
        int srcIndex = getRegNumFromRef(taintSrcReg);
        int dstIndex = getRegNumFromRef(taintTargReg);
        if (srcIndex == dstIndex) {
            return;
        }
        if (dstIndex < 16 && srcIndex < 16) {
            linesToAdd.add("    " + tool.getMoveTaint() + " " + taintTargReg + ", " + taintSrcReg);
        } else if (dstIndex < 256) {
            linesToAdd.add("    " + tool.getMoveTaint() + "/from16 " + taintTargReg + ", " + taintSrcReg);
        } else {
            linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + taintTargReg + ", " + taintSrcReg);
        }
    }


    /**
     * Adds taint to the parameters passed to a method after the method call.
     *
     * @param tool the TaintTool instance to use
     * @param line the line of code containing the method call
     * @param linesToAdd the list of lines to add the taint code to
     * @param classLines the list of lines in the class containing the method call
     * @param lineNum the line number of the method call in the class
     * @param taintTempReg the register to use for temporary taint storage
     * @param context the InstrumentationContext instance to use
     * @return the maximum number of registers used
     */
    public int addGetParamTaintAfterCall(TaintTool tool, String line, List<String> linesToAdd, List<String> classLines, int lineNum, int taintTempReg, InstrumentationContext context) {
        int maxRegs = 0;
        String delim = "L";
        String search = ", L";
        if (line == null) {
            return maxRegs;
        }
        if (line.indexOf(search) == -1) {
            delim = "[";
            search = ", \\[";
        }
        String calledMethod = delim + line.split(search, 2)[1];

        String instruction = getToken(line, 0);

        if (classAnalysis.isNative(calledMethod)) {
            return maxRegs;
        }

        MethodInfo calledMethodInfo = new MethodInfo(calledMethod, instruction.contains("static"));

        Set<String> whereIsMethod = classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethod.split("->")[1]);

        if ((forbiddenClasses.contains(calledMethodInfo.getClassName())) || isIgnored(classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc())) ) {
            return maxRegs;
        }


        if (whereIsMethod == null || whereIsMethod.isEmpty() || isIgnored(whereIsMethod)) {
            return maxRegs;
        }

        String [] lineSplit = line.split("\\}");
        String [] regSplit = lineSplit[0].split("\\{");
        String [] passedRegs = new String[0];
        if (regSplit.length > 1) {
            passedRegs = regSplit[1].replace(" ", "").split("\\,");
        }

        passedRegs = parsePassedRegs(passedRegs);

        String currentLine = classLines.get(lineNum);
        String currentInstruction = getToken(currentLine, 0);
        String moveResultReg = "";
        if (isMoveResult(currentInstruction) || isMoveResultWide(currentInstruction) || isMoveResultObject(currentInstruction)) {
            moveResultReg = getRegReference(currentLine, 1);
        }

        for (int i = 0; i < passedRegs.length; i++) {
            String taintReg = context.taintRegMap.get(passedRegs[i]);

            if (i > 0 && getMoveInstructionByType(calledMethodInfo.getParams().get(i-1)) != null
                      && getMoveInstructionByType(calledMethodInfo.getParams().get(i-1)).equals("move-wide/16")
                      && calledMethodInfo.getParams().get(i).equals("*")){
                // when we are moving the taint from upper half of a long variable, which is not needed
                continue;
            }

            // For the case where the paramter register is overwritten by the return of the method
            // Should not propagate taint to overwritten register
            if (!moveResultReg.isEmpty() && passedRegs[i].equals(moveResultReg)) {
                // moveInstruction = getMoveByInstruction(currentInstruction) + "/16";
                continue;
            }

            linesToAdd.add("    # addGetParamTaintAfterCall -> reg: " + passedRegs[i] + ", taint: " + taintReg + ", returnReg: " + moveResultReg);

            if (getRegNumFromRef(taintReg) > 255 ) {
                String smallReg = passedRegs[i];

                String moveInstruction = getMoveInstructionByType(calledMethodInfo.getParams().get(i));

                if (getRegNumFromRef(passedRegs[i]) > 255 ) {
                    if (!moveResultReg.isEmpty()) {
                        smallReg = moveResultReg;
                    } else {
                        smallReg = "v0";
                    }
                    String smallRegType = calledMethodInfo.getReturnType();
                    if (smallRegType == null) {
                        moveInstruction = null;
                    } else {
                        moveInstruction = getMoveInstructionByType(smallRegType);
                    }
                }
                if (moveInstruction != null) {
                    linesToAdd.add("    " + moveInstruction + " v" + taintTempReg + ", " + smallReg);
                }
                linesToAdd.add("    invoke-static/range {" + context.threadReg + " .. " + context.threadReg + "}, " + tool.getGetParamTaintInstr(i));
                linesToAdd.add("    " + tool.getMoveResultTaint() + " " + smallReg);
                linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + taintReg + ", " + smallReg);
                if (moveInstruction != null) {
                    linesToAdd.add("    " + moveInstruction + " " + smallReg + ", v" + taintTempReg);
                }
            } else {
                if (getRegNumFromRef(context.threadReg) > 15) {
                    linesToAdd.add("    invoke-static/range {" + context.threadReg + " .. " + context.threadReg + "}, " + tool.getGetParamTaintInstr(i));
                } else {
                    linesToAdd.add("    invoke-static {" + context.threadReg + "}, " + tool.getGetParamTaintInstr(i));
                }
                linesToAdd.add("    " + tool.getMoveResultTaint() + " " + taintReg);
            }
        }

        return maxRegs;
    }

    /**
     * Determines the type of the register after a two-operand arithmetic operation and updates the regType map accordingly.
     * @param regType a map of register names to their types
     * @param instruction the instruction string
     * @param targetReg the target register name
     */
    public void getRegTypeForArithmaticsTwoOps(Map<String, String> regType, String instruction, String targetReg) {
        if (instruction.endsWith("-int/2addr")) {
            regType.put(targetReg, "I");
        } else if (instruction.endsWith("-long/2addr")) {
            regType.put(targetReg, "J");
        } else if (instruction.endsWith("-float/2addr")) {
            regType.put(targetReg, "F");
        } else if (instruction.endsWith("-double/2addr")) {
            regType.put(targetReg, "D");
        } else if (instruction.endsWith("-byte/2addr")) {
            regType.put(targetReg, "B");
        } else if (instruction.endsWith("-char/2addr")) {
            regType.put(targetReg, "C");
        } else if (instruction.endsWith("-short/2addr")) {
            regType.put(targetReg, "S");
        }
    }

    /**
     * This method maps the register to its type according to the arithmetic instruction.
     * @param regType a map of register types
     * @param instruction the arithmetic instruction
     * @param targetReg the target register
     */
    public void getRegTypeForArithmatics(Map<String, String> regType, String instruction, String targetReg) {
        if (instruction.endsWith("-int")) {
            regType.put(targetReg, "I");
        } else if (instruction.endsWith("-long")) {
            regType.put(targetReg, "J");
        } else if (instruction.endsWith("-float")) {
            regType.put(targetReg, "F");
        } else if (instruction.endsWith("-double")) {
            regType.put(targetReg, "D");
        } else if (instruction.endsWith("-byte")) {
            regType.put(targetReg, "B");
        } else if (instruction.endsWith("-char")) {
            regType.put(targetReg, "C");
        } else if (instruction.endsWith("-short")) {
            regType.put(targetReg, "S");
        }
    }

    /**
     * Determines the register type for structs based on the instruction and target register.
     * If the instruction contains "object", it calls getRegTypeForObject.
     * If the instruction contains "wide", it sets the register type to long "J".
     * Otherwise, it sets the register type to integer "I".
     *
     * @param regType a map of register types
     * @param instruction the instruction being analyzed
     * @param targetReg the target register being analyzed
     */
    public void getRegTypeForStructs(Map<String, String> regType, String instruction, String targetReg) {
        if (instruction.contains("object")) {
            getRegTypeForObject(regType, targetReg);
        } else if (instruction.contains("wide")) {
            regType.put(targetReg, "J");
        } else {
            regType.put(targetReg, "I");
        }
    }

    /**
     * Adds a mapping of the given target register to the boolean type "Z" in the provided register type map.
     * @param regType the register type map to add the mapping to
     * @param targetReg the target register to add the mapping for
     */
    public void getRegTypeForCompares(Map<String, String> regType, String targetReg) {
        regType.put(targetReg, "Z");
    }

    /**
     * Determines the register type for constants and updates the regType map accordingly.
     * If the instruction is a constant of type 4, 16, or high16, the target register is assigned an integer "I" type.
     * If the instruction is a constant of type wide16, wide32, wide, or wideHigh16, the target register is assigned a long "J" type.
     * Otherwise, the getRegTypeForObject method is called to determine the register type.
     *
     * @param regType a map of register names to their types
     * @param instruction the instruction being analyzed
     * @param targetReg the target register being assigned a type
     */
    public void getRegTypeForConstants(Map<String, String> regType, String instruction, String targetReg) {
        if (isConst4(instruction) || isConst16(instruction) || isConst(instruction) || isConstHigh16(instruction)) {
            regType.put(targetReg, "I");
        } else if (isConstWide16(instruction) || isConstWide32(instruction) || isConstWide(instruction) || isConstWideHigh16(instruction)) {
            regType.put(targetReg, "J");
        } else {
            getRegTypeForObject(regType, targetReg);
        }
    }

    /**
     * Adds the type of a target register to a map of register types.
     * @param regType a map of register types
     * @param targetReg the target register
     */
    public void getRegTypeForObject(Map<String, String> regType, String targetReg) {
        regType.put(targetReg, "Ljava/lang/Object;");
    }

    /**
     * Determines the register type for move results and updates the regType map accordingly.
     * If the instruction is a move result, the target register is assigned an integer type "I".
     * If the instruction is a move result wide, the target register is assigned a long type "J".
     * Otherwise, the getRegTypeForObject method is called to determine the register type.
     *
     * @param regType a map of register names to their types
     * @param instruction the instruction being analyzed
     * @param targetReg the target register of the instruction
     */
    public void getRegTypeForMoveResults(Map<String, String> regType, String instruction, String targetReg) {
        if (isMoveResult(instruction)) {
            regType.put(targetReg, "I");
        } else if (isMoveResultWide(instruction)) {
            regType.put(targetReg, "J");
        } else {
            getRegTypeForObject(regType, targetReg);
        }
    }

    /**
     * Determines the register type for move instructions and updates the regType map accordingly.
     * If the instruction is a move instruction, the target register is assigned an integer type.
     * If the instruction is a move wide instruction, the target register is assigned a long type.
     * Otherwise, the register type is determined by calling getRegTypeForObject.
     *
     * @param regType a map of register names to their corresponding types
     * @param instruction the instruction being analyzed
     * @param targetReg the target register of the instruction
     */
    public void getRegTypeForMoves(Map<String, String> regType, String instruction, String targetReg) {
        if (isMove(instruction) || isMoveFrom16(instruction) || isMove16(instruction)) {
            regType.put(targetReg, "I");
        } else if (isMoveWide(instruction) || isMoveWideFrom16(instruction) || isMoveWide16(instruction)) {
            regType.put(targetReg, "J");
        } else {
            getRegTypeForObject(regType, targetReg);
        }
    }

    /**
     * Get taint from parcel at the start of the method.
     * It adds lines of code to the given list of linesToAdd to get the return taint instruction and move the result taint to the specified register.
     *
     * @param tool TaintTool object used for taint analysis.
     * @param methodInfo MethodInfo object representing the method being analyzed.
     * @param taintRegMap Map of register numbers to their corresponding taint values.
     * @param linesToAdd List of lines of code to add the taint analysis instructions.
     * @param threadReg String representing the thread register.
     */
    public void createFromParcelAtMethodStart(TaintTool tool, MethodInfo methodInfo, Map<String, String> taintRegMap, List<String> linesToAdd, String threadReg) {
        if (methodInfo.getNameAndDesc().startsWith("createFromParcel(Landroid/os/Parcel;)")) {
            int firstParamReg = methodInfo.getNumBaseLocalRegs() + 1;
            if (getRegNumFromRef(threadReg) < 16) {
                linesToAdd.add("    invoke-static { " + threadReg + " }, " + tool.getGetReturnTaintInstr());
            } else {
                linesToAdd.add("    invoke-static/range { " + threadReg + " .. " + threadReg + " }, " + tool.getGetReturnTaintInstr());
            }

            linesToAdd.add("    " + tool.getMoveResultTaint() + " " + taintRegMap.get("v"+firstParamReg));
        }
    }

    /**
     * Adds instructions to get the return taint of a method call and store it in a specified register.
     *
     * @param tool the TaintTool instance to use for getting the return taint
     * @param linesToAdd the list of lines to add the instructions to
     * @param taintTargReg the register to store the taint in
     * @param targetReg the register containing the return value of the method call
     * @param instruction the instruction of the method call
     * @param context the InstrumentationContext instance containing context information
     */
    public void addGetReturnTaint(TaintTool tool, List<String> linesToAdd, String taintTargReg, String targetReg, String instruction, InstrumentationContext context) {
        String moveInstruction = getMoveByInstruction(instruction);
        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    " + moveInstruction + "/16 v" + context.taintTempReg + ", " + targetReg);
        }

        if (getRegNumFromRef(context.threadReg) < 16) {
            linesToAdd.add("    invoke-static { " + context.threadReg + " }, " + tool.getGetReturnTaintInstr());
        } else {
            linesToAdd.add("    invoke-static/range { " + context.threadReg + " .. " + context.threadReg + " }, " + tool.getGetReturnTaintInstr());
        }

        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    " + tool.getMoveResultTaint() + " " + targetReg);
            linesToAdd.add("    move-object/16 " + taintTargReg + ", " + targetReg);
            linesToAdd.add("    " + moveInstruction + "/16 " + targetReg + ", v" + context.taintTempReg);
        } else {
            linesToAdd.add("    " + tool.getMoveResultTaint() + " " + taintTargReg);
        }
    }

    /**
     * Adds taint to the return value of a method and propagates it to the target register.
     *
     * @param tool TaintTool instance.
     * @param linesToAdd List of lines to add the taint to.
     * @param taintTargReg The register containing the taint.
     * @param targetReg The target register to propagate the taint to.
     * @param instruction The instruction to add the taint to.
     * @param context InstrumentationContext instance.
     * @return The maximum number of registers used.
     */
    public int addPropagateReturnTaint(TaintTool tool, List<String> linesToAdd, String taintTargReg, String targetReg, String instruction, InstrumentationContext context) {
        String moveInstruction = getMoveByInstruction(instruction);

        // Need to save the taintTargReg, then get the taint using return, then propagate from the saved
        linesToAdd.add("    " + tool.getMoveTaint() + "/16 v" + context.taintTempReg + ", " + taintTargReg);
        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    " + moveInstruction + "/16 v" + (context.taintTempReg + 1) + ", " + targetReg);
        }

        if (getRegNumFromRef(context.threadReg) < 16) {
            linesToAdd.add("    invoke-static { " + context.threadReg + " }, " + tool.getGetReturnTaintInstr());
        } else {
            linesToAdd.add("    invoke-static/range { " + context.threadReg + " .. " + context.threadReg + " }, " + tool.getGetReturnTaintInstr());
        }

        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    " + tool.getMoveResultTaint() + " " + targetReg);
            linesToAdd.add("    move-object/16 " + taintTargReg + ", " + targetReg);
        } else {
            linesToAdd.add("    " + tool.getMoveResultTaint() + " " + taintTargReg);
        }

        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    " + moveInstruction + "/16 " + targetReg + ", v" + (context.taintTempReg + 1));
        }

        context.maxRegs = handleTwoSourceOneDest(tool, context.taintTempReg + 3, linesToAdd, instruction, targetReg, targetReg, taintTargReg, taintTargReg, "v" + context.taintTempReg, context);

        return context.maxRegs;
    }

    /**
     * Injects a taint seed into the given line of code, if it contains a source method call.
     * If a taint seed is injected, the corresponding statistics are updated.
     *
     * @param tool either ViaLin or TaintDroid
     * @param line the line of code to analyze for source method calls
     * @param linesToAdd a list of lines to add to the code, if a taint seed is injected
     * @param taintTempReg the register to use for temporary taint values
     * @param taintTargReg the register to use for the taint target
     * @param className the name of the class containing the analyzed code
     * @param context the InstrumentationContext for the analyzed code
     * @return the maximum number of registers used in the added lines of code
     */
    public int injectTaintSeed(TaintTool tool, String line, List<String> linesToAdd, int taintTempReg, String taintTargReg, String className, InstrumentationContext context) {
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

        if (!calledMethodInfo.getReturnType().equals("V") && TaintSource.isSource(className, calledMethodInfo.signature())) {
            AnalysisLogger.log(true, "Added source: %s%n", calledMethodInfo.signature());
            statistics.addSource();

            linesToAdd.add("    move-object/16 v" + taintTempReg + ", " + context.signatureRegister);
            linesToAdd.add("    const-string/jumbo " + context.signatureRegister + ", \"" + calledMethodInfo.signature() + "\"");
            if (getRegNumFromRef(context.signatureRegister) < 16) {
                linesToAdd.add("    invoke-static {" + context.signatureRegister + "}, Ljava/lang/PathTaint;->printSourceFound(Ljava/lang/String;)V");
            } else {
                linesToAdd.add("    invoke-static/range {" + context.signatureRegister + " .. " + context.signatureRegister + "}, Ljava/lang/PathTaint;->printSourceFound(Ljava/lang/String;)V");
            }
            linesToAdd.add("    move-object/16 " + context.signatureRegister + ", v" + taintTempReg);

            int taintNum = TaintSource.getTaintNum(calledMethodInfo.signature());
            maxRegs = addCreateTaint(tool, taintTempReg, maxRegs, linesToAdd, taintTargReg, context.signatureRegister, context.deltaReg, context.methodDelta, taintNum);
        }

        return maxRegs;
    }

    /**
     * Injects taint seed by reflection after move result instruction.
     *
     * @param tool TaintTool object
     * @param line String representing the line
     * @param lastCalled String representing the last called method
     * @param linesToAdd List of Strings representing the lines to add
     * @param context InstrumentationContext object
     * @return int representing the maximum number of registers
     */
    public int injectTaintSeedByReflectionAtMoveResult(TaintTool tool, String line, String lastCalled, List<String> linesToAdd, InstrumentationContext context) {
        if (lastCalled == null) {
            return 0;
        }
        int maxRegs = 0;
        String delim = "L";
        String search = ", L";
        if (lastCalled.indexOf(search) == -1) {
            delim = "[";
            search = ", \\[";
        }
        String calledMethod = delim + lastCalled.split(search, 2)[1];

        String instruction = getToken(lastCalled, 0);

        MethodInfo calledMethodInfo = new MethodInfo(calledMethod, instruction.contains("static"));

        if (calledMethodInfo.signature().equals("Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) {
            String[] passedRegs = parsePassedRegs(lastCalled);
            String methodRegister = passedRegs[0];
            String methodRegisterTaint = context.taintRegMap.get(methodRegister);
            String targetReg = getRegReference(line, 1);
            String taintTargReg = context.taintRegMap.get(targetReg);
            addCopyTaint(tool, linesToAdd, taintTargReg, methodRegisterTaint);
        }
        return maxRegs;
    }

    /**
     * Injects a taint seed into a method based on the method signature.
     *
     * @param tool the taint tool to use
     * @param line the line of code to analyze
     * @param linesToAdd the list of lines to add the taint seed to
     * @param taintTempReg the temporary register to use for taint operations
     * @param taintTargReg the register to taint
     * @param className the name of the class containing the method being analyzed
     * @param context the instrumentation context
     * @return the maximum number of registers used in the method after the taint seed is injected
     */
    private Integer addCreateTaint(TaintTool tool, Integer taintTempReg, Integer maxRegs, List<String> linesToAdd, String taintTargReg, String signatureRegister, String deltaReg, int delta, int taintNum) {

        if (tool instanceof ViaLinTool) {
            addConstTaint(linesToAdd, "v"+taintTempReg, deltaReg, delta);
            String newLine = "    invoke-static {" + taintTargReg + ", " + signatureRegister + ", " + deltaReg + "}, Ljava/lang/PathTaint;->addTaintSource(Ljava/lang/PathTaint;Ljava/lang/String;I)Ljava/lang/PathTaint;";
            Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
            linesToAdd.addAll(rangedInvoke.first);
            int newMaxRegs = rangedInvoke.second;
            maxRegs = (maxRegs > newMaxRegs)? maxRegs : newMaxRegs;
            linesToAdd.add("    move-result-object "+ taintTargReg);
        } else if (tool instanceof TaintDroidTool) {
            linesToAdd.add("    or-int/lit8 " + taintTargReg + ", " + taintTargReg + ", " + (1 << taintNum));
        }
        return maxRegs;
    }

    /**
     * Determines whether a method should be modeled or not based on the given line of code.
     *
     * @param line the line of code to analyze
     * @return true if the method should be modeled, false otherwise
     */
    public boolean shouldModelMethod(String line) {
        String delim = "L";
        String search = ", L";

        if (line == null) {
            return false;
        }

        if (line.indexOf(search) == -1) {
            delim = "[";
            search = ", \\[";
        }

        String calledMethod = delim + line.split(search, 2)[1];

        String instruction = getToken(line, 0);

        if (classAnalysis.isNative(calledMethod)) {
            return true;
        }

        MethodInfo calledMethodInfo = new MethodInfo(calledMethod, instruction.contains("static"));

        Set<String> whereIsMethod = classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethod.split("->")[1]);

        if ((forbiddenClasses.contains(calledMethodInfo.getClassName())) || isIgnored(classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc())) ) {
            return true;
        }

        if (whereIsMethod == null || whereIsMethod.isEmpty() || isIgnored(whereIsMethod)) {
            return true;
        }

        return false;
    }

    /**
     * Determines whether a method should be tainted or not based on its input and output.
     * @param methodInfo information about the method being analyzed
     * @param lineNum the line number of the method being analyzed
     * @param classLines the list of lines in the class containing the method being analyzed
     * @return true if the method should be tainted, false otherwise
     */
    public boolean shouldTaint(MethodInfo methodInfo, int lineNum, List<String> classLines) {
        boolean hasInput = false;
        boolean hasOutput = false;
        if (!methodInfo.getParams().isEmpty()) {
            hasInput = true;
        }
        for (int i = lineNum; i < classLines.size(); i++) {
            String line = classLines.get(i);
            String instruction = getToken(line, 0);
            if (instruction.startsWith(".")) {
            } else if (instruction.startsWith(":")) {
            } else if (instruction.startsWith("0x")) {
            } else if (instruction.startsWith("-")) {
            } else if (instruction.startsWith("iget") || instruction.startsWith("sget") || instruction.startsWith("aget") || instruction.startsWith("move-result") || instruction.startsWith("move-exception")) {
                hasInput = true;
            } else if (instruction.startsWith("iput") || instruction.startsWith("sput") || instruction.startsWith("aput") || (instruction.startsWith("return") && !instruction.equals("return-void")) || instruction.startsWith("throw") || instruction.startsWith("invoke")) {
                hasOutput = true;
            }
            if (line.startsWith(".end method")) {
                return hasInput && hasOutput;
            }
        }
        return hasInput && hasOutput;
    }

    /**
     * Finds the next instruction that contains a given string in a list of class lines starting from a given line number.
     *
     * @param classLines the list of class lines to search through
     * @param lineNum the starting line number for the search
     * @param string the string to search for in the instructions
     * @return the line number of the next instruction that contains the given string, or -1 if not found
     */
    public int nextInstructionContains(List<String> classLines, int lineNum, String string) {
        for (int i = lineNum+1; i < classLines.size(); i++) {
            String line = classLines.get(i);
            if (line.startsWith(".")) {
                break;
            }
            if (line.startsWith("    :") || line.startsWith("    .")) {
                continue;
            }
            if (line.isEmpty()) {
                continue;
            }
            String instruction = getToken(line, 0);
            if (instruction.contains(string)) {
                return i;
            } else {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Returns the next instruction in the method given a list of class lines and the current line number.
     *
     * @param classLines the list of class lines
     * @param lineNum the current line number
     * @return the next instruction in the method
     */
    public String getNextInstructionInMethod(List<String> classLines, int lineNum) {
        for (int i = lineNum + 1; i < classLines.size(); i++) {
            String line = classLines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(".") || trimmed.startsWith(":")) {
                continue;
            } else {
                return line;
            }
        }
        return classLines.get(lineNum+2);
    }

    /**
     * Searches for the previous instruction in a list of class lines that starts with a given string.
     *
     * @param classLines the list of class lines to search in
     * @param lineNum the current line number to start searching from
     * @param string the string to search for at the beginning of the instruction
     * @return the line number of the previous instruction that starts with the given string, or -1 if not found
     */
    public int previousInstructionIs(List<String> classLines, int lineNum, String string) {
        for (int i = lineNum-1; i >= 0; i--) {
            String line = classLines.get(i);
            if (line.startsWith(".")) {
                break;
            }
            if (line.startsWith("    :") || line.startsWith("    .")) {
                break;
            }
            if (line.isEmpty()) {
                continue;
            }
            String instruction = getToken(line, 0);
            if (instruction.startsWith(string)) {
                return i;
            } else {
                continue;
            }
        }
        return -1;
    }

    public int previousInstructionIsExact(List<String> classLines, int lineNum, String string) {
        for (int i = lineNum-1; i >= 0; i--) {
            String line = classLines.get(i).trim();
            if (line.startsWith(":") || line.startsWith(".")) {
                break;
            }
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(string)) {
                return i;
            } else {
                continue;
            }
        }
        return -1;
    }

    public int indexOfMatchingGetField(List<String> classLines, int lineNum, String string, String reg) {
        for (int i = lineNum-1; i >= 0; i--) {
            String line = classLines.get(i);
            if (line.startsWith(".")) {
                break;
            }
            if (line.isEmpty()) {
                continue;
            }
            String instruction = getToken(line, 0);
            if (instruction.startsWith(string)) {
                String igetTargetReg = getRegReference(line, 1);
                if (reg.equals(igetTargetReg)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Checks if a register is assigned after a given instruction index in a list of class lines.
     *
     * @param register the register to check
     * @param prevInstructionIndex the index of the previous instruction
     * @param classLines the list of class lines
     * @param lineNum the line number to start checking from
     * @return true if the register is assigned after the given instruction index, false otherwise
     */
    public boolean registerIsAssingedAfterInstruction(String register, int prevInstructionIndex, List<String> classLines, int lineNum) {
        for (int i = lineNum-2; i > prevInstructionIndex; i--) {
            String line = classLines.get(i);
            String instruction = getToken(line, 0);
            if (instruction.startsWith(".") || instruction.startsWith(":")) {
                continue;
            } else if (isConst(instruction)) {
                String targetReg = getRegReference(line, 1);
                if (targetReg.equals(register)) {
                    return true;
                }
            } else if (isSstaticOp(instruction)) {
                if (instruction.startsWith("sget")) {
                    String targetReg = getRegReference(line, 1);
                    if (targetReg.equals(register)) {
                        return true;
                    }
                }
            } else {
                // This should cover all cases, including above ones and some false positives (like iput), should optimize
                try {
                    String targetReg = getRegReference(line, 1);
                    if (targetReg.equals(register)) {
                        return true;
                    } else if (instruction.contains("wide") && (getRegNumFromRef(targetReg) + 1 == getRegNumFromRef(register))) {
                        return true;
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    // pass
                }

            }
        }
        return false;
    }

    /**
     * Parses the passed registers from a given line of code.
     *
     * @param line the line of code to parse
     * @return an array of passed registers
     */
    public static String[] parsePassedRegs(String line) {
        String [] lineSplit = line.split("\\}");
        String [] regSplit = lineSplit[0].split("\\{");
        String [] passedRegs = new String[0];
        if (regSplit.length > 1) {
            passedRegs = regSplit[1].replace(" ", "").split("\\,");
        }

        passedRegs = parsePassedRegs(passedRegs);
        return passedRegs;
    }

    /**
     * Parses an array of passed registers and returns an array of individual registers.
     * If the first element of the passedRegs array contains "..", it is treated as a range of registers.
     * The range is replaced with individual register names and returned as an array.
     *
     * @param passedRegs the array of passed registers to be parsed
     * @return an array of individual registers
     */
    public static String[] parsePassedRegs(String[] passedRegs) {
        if (passedRegs.length > 0 && passedRegs[0].contains("..")) {
            passedRegs[0] = passedRegs[0].replace("..", "Z");
            String firstReg = passedRegs[0].split("Z")[0];
            String lastReg = passedRegs[0].split("Z")[1];
            int firstRegNum = getRegNumFromRef(firstReg);
            int lastRegNum = getRegNumFromRef(lastReg);
            passedRegs = new String[lastRegNum-firstRegNum+1];
            for (int i = firstRegNum; i <= lastRegNum; i++) {
                passedRegs[i-firstRegNum] = "v" + i;
            }
        }
        return passedRegs;
    }


    /**
     * Transforms the invoke into invoke-range so that the instruction is valid (only v0-v15 are allowed in invoke-static).
     *
     * @param line the line to transform
     * @param taintTempReg the highest previously-used register (a temporary register for instrumentation usage)
     * @return a pair containing a list of instructions including the invoke-range, and an integer representing the new highest-use register
     */
    protected static Pair<List<String>, Integer> makeInvokeToRange(String line, int taintTempReg) {

        // Find passed registers
        taintTempReg = taintTempReg +1;
        List<String> lines = new ArrayList<>();
        int maxRegs = 0;
        String calledMethod = "L"+line.split(", L", 2)[1];
        String instruction = getToken(line, 0);

        MethodInfo calledMethodInfo = new MethodInfo(calledMethod, instruction.contains("static"));
        String [] lineSplit = line.split("\\}");

        String [] regSplit = lineSplit[0].split("\\{");

        if (regSplit.length < 2) {
            lines.add(line);
            return new Pair<>(lines, 0);
        }

        String [] passedRegs = regSplit[1].replace(" ", "").split("\\,");
        if (passedRegs[0].contains("..")) {
            lines.add(line);
            return new Pair<>(lines, 0);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    ");

        // If the passed registers are more than 4, we need to use invoke-range
        if (passedRegs.length == 0){
            sb.append(instruction);
            sb.append(" {");
        } else if(passedRegs.length == 1 && getRegNumFromRef(passedRegs[0]) < 16) {
            sb.append(instruction);
            sb.append(" {");
            sb.append(passedRegs[0]);
        } else if(passedRegs.length == 2 && getRegNumFromRef(passedRegs[0]) < 16 && getRegNumFromRef(passedRegs[1]) < 16) {
            sb.append(instruction);
            sb.append(" {");
            sb.append(passedRegs[0]);
            sb.append(", ");
            sb.append(passedRegs[1]);
        } else if(passedRegs.length == 3 && getRegNumFromRef(passedRegs[0]) < 16 && getRegNumFromRef(passedRegs[1]) < 16 && getRegNumFromRef(passedRegs[2]) < 16) {
            sb.append(instruction);
            sb.append(" {");
            sb.append(passedRegs[0]);
            sb.append(", ");
            sb.append(passedRegs[1]);
            sb.append(", ");
            sb.append(passedRegs[2]);
        } else if(passedRegs.length == 4 && getRegNumFromRef(passedRegs[0]) < 16 && getRegNumFromRef(passedRegs[1]) < 16 && getRegNumFromRef(passedRegs[2]) < 16 && getRegNumFromRef(passedRegs[3]) < 16) {
            sb.append(instruction);
            sb.append(" {");
            sb.append(passedRegs[0]);
            sb.append(", ");
            sb.append(passedRegs[1]);
            sb.append(", ");
            sb.append(passedRegs[2]);
            sb.append(", ");
            sb.append(passedRegs[3]);
        } else { // passedRegs.length > 4
            if (!instruction.contains("range")) {
                instruction = instruction + "/range";
            }

            sb.append(instruction);
            sb.append(" {");

            // Add the move instructions to move the registers to the temporary registers
            for (int i = 0; i < passedRegs.length; i++) {
                String mod = "";
                if (taintTempReg+i+2 > 15) {
                    mod = "/16";
                }

                String param = calledMethodInfo.getParams().get(i);
                String moveInstruction = "move-object";
                if (param.equals("Z") || param.equals("C") || param.equals("B") || param.equals("S") || param.equals("I") || param.equals("F")) {
                    moveInstruction = "move";
                } else if (param.equals("J") || param.equals("D")) {
                    moveInstruction = "move-wide";
                } else if (param.equals("*")) {
                    moveInstruction = null;
                }

                if (moveInstruction != null && passedRegs.length > 1) {
                    String move = "    " + moveInstruction + mod + " v" + (taintTempReg+i+2) + ", " + passedRegs[i];
                    lines.add(move);
                }
            }

            // Add the range of registers to the invoke instruction
            if (passedRegs.length == 1) {
                sb.append(passedRegs[0]);
                sb.append(" .. ");
                sb.append(passedRegs[0]);
            } else {
                maxRegs = taintTempReg + 2 + passedRegs.length;
                sb.append("v").append(taintTempReg+2);
                sb.append(" .. ");
                sb.append("v").append(taintTempReg + 1 + passedRegs.length);
            }

        }

        sb.append("}");
        sb.append(lineSplit[1]);
        lines.add(sb.toString());

        return new Pair<>(lines, maxRegs);
    }

    /**
     * Returns a string with the parameters replaced by local variables.
     *
     * @param line the original string
     * @param context the instrumentation context
     * @return the modified string
     */
    protected String changeParamsToLocals(String line, InstrumentationContext context) throws InvalidInstructionError {
        line = changeParamsToLocals(context.newParams, line, " p");
        line = changeParamsToLocals(context.newParams, line, "{p");
        return line;
    }

    /**
     * Replaces the parameters in a given line of code with local variables.
     *
     * @param newParams the map of new parameters
     * @param line the line of code to modify
     * @param token the token to replace
     * @return the modified line with the parameters replaced
     */
    private String changeParamsToLocals(Map<Integer, Integer> newParams, String line, String token) throws InvalidInstructionError {
        int indexOfParam = 0;

        // For packed-switch and sparse-switch, the index of the parameter is 1 less
        if (line.startsWith("    packed-switch")) {
            indexOfParam = "    packed-switch".length()-1;
        }

        // Iterate through the line and replace the parameters with local variables
        while (line.indexOf(token, indexOfParam) > indexOfParam) {
            // Find the index of the parameter
            indexOfParam = line.indexOf(token, indexOfParam) + 2;
            int lastIndex = line.indexOf("\"");
            if (lastIndex == -1) {
                lastIndex = line.length();
            }
            if (indexOfParam > lastIndex) {
                break;
            }

            // Replace the parameter with the local variable
            Matcher matcher = Pattern.compile("\\d+").matcher(line.subSequence(indexOfParam, line.length()));
            matcher.find();
            int i = Integer.parseInt(matcher.group());

            if (!newParams.containsKey(i)) {
                throw new InvalidInstructionError("Parameter " + i + " not found in params map");
            }
            line = line.substring(0, indexOfParam-1) + "v" + matcher.replaceFirst(String.valueOf(newParams.get(i)));
        }
        return line;
    }

    /**
     * Returns the type of the field from the given field reference.
     *
     * @param fieldRef the field reference
     * @return the type of the field
     */
    protected String getFieldClass(String fieldRef) {
        return fieldRef.substring(0, fieldRef.indexOf("->"));
    }

    /**
     * Returns the name of the taint field from the given field reference.
     *
     * @param fieldRef the field reference
     * @return the name of the taint field
     */
    protected String createTaintField(TaintTool tool, String fieldClass, String fieldName) {
        return fieldClass + "->" + "zzz_" + fieldName + tool.fieldNameAndDesc();
    }

    /**
     * Returns the name of the field from the given field reference.
     *
     * @param fieldRef the field reference
     * @return the name of the field
     */
    protected String getFieldName(String fieldRef) {
        return fieldRef.substring(fieldRef.indexOf("->") + 2, fieldRef.indexOf(":"));
    }

    /**
     * Returns the last token in a given line of code.
     *
     * @param line the line of code
     * @return the last token in the line
     */
    protected static String getLastToken(String line) {
        String[] split = line.split("\\s+");
        return split[split.length-1];
    }

    /**
     * Returns the nth token in a given line of code.
     *
     * @param line the line of code
     * @param n the index of the token in the line
     * @return the nth token in the line
     */
    protected static String getToken(String line, int n) {
        String[] split = line.trim().split("\\s+");
        return split[n];
    }

    /**
     * Replaces the nth token in a given line with a specified token.
     *
     * @param line the line to modify
     * @param token the token to replace the nth token with
     * @param n the index of the token to replace (0-indexed, negative values count from the end of the line)
     * @return the modified line with the nth token replaced
     */
    protected static String replaceToken(String line, String token, int n) {
        String[] split = line.trim().split("\\s+");
        if (n < 0) {
            n = n + split.length;
        }
        split[n] = token;
        StringBuilder newLine = new StringBuilder(split[0]);
        for (int i = 1; i < split.length; i++) {
            newLine.append(' ');
            newLine.append(split[i]);
        }
        return newLine.toString();
    }

    /**
     * Returns the method information object from the given line of code.
     *
     * @param className the name of the class containing the method
     * @param line the line of code containing the method
     * @return the method information object
     */
    protected static MethodInfo getMethodInfo(String className, String line) {
        String methodName = className + "->" + getLastToken(line);
        boolean isStatic = line.contains(" static ");
        return new MethodInfo(methodName, isStatic);
    }

    /**
     * Returns a string that represents the reference of a register.
     *
     * @param line the line containing the register reference
     * @param n the index of the register reference in the line
     * @return a string that represents the reference of a register
     */
    protected static String getRegReference(String line, int n) {
        return getToken(line, n).replace(",", "");
    }

    /**
     * Returns the type of register from the given register reference.
     *
     * @param regRef the register reference
     * @return the type of register
     */
    protected static String getRegTypeFromRef(String regRef) {
        return regRef.substring(0, 1);
    }

    /**
     * Returns the register number from the given register reference string.
     *
     * @param regRef the register reference string
     * @return the register number
     */
    protected static int getRegNumFromRef(String regRef) {
        return Integer.parseInt(regRef.substring(1));
    }

    /**
     * Returns the register number of a given register type in a method.
     * @param methodInfo the method information object
     * @param line the line of code containing the register reference
     * @param n the index of the register reference in the line
     * @param regType the type of register ("v" for variable, "p" for parameter)
     * @return the register number
     */
    protected static int getRegNum(MethodInfo methodInfo, String line, int n, String regType) {
        int regNum = Integer.parseInt(getRegReference(line, n).substring(1));
        if (regType.equals("p")) {
            regNum += methodInfo.getNumBaseParams();
        }
        return regNum;
    }

    /**
     * Returns the type of register as a String.
     *
     * @param line the line containing the register reference
     * @param n the index of the register reference in the line
     * @return the type of register as a String
     */
    protected static String getRegType(String line, int n) {
        return getRegReference(line, n).substring(0, 1);
    }

    /**
     * Checks if the given instruction is a "nop" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "nop" instruction, false otherwise
     */
    protected boolean isNop(String instruction) {
        return instruction.equals("nop");
    }

    /**
     * Checks if the given instruction is a move instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a move instruction, false otherwise
     */
    protected boolean isMove(String instruction) {
        return instruction.equals("move");
    }

    /**
     * Checks if the given instruction is "move/from16".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "move/from16", false otherwise
     */
    protected boolean isMoveFrom16(String instruction) {
        return instruction.equals("move/from16");
    }

    /**
     * Checks if the given instruction is a move/16 instruction.
     * @param instruction the instruction to check
     * @return true if the instruction is a move/16 instruction, false otherwise
     */
    protected boolean isMove16(String instruction) {
        return instruction.equals("move/16");
    }

    /**
     * Checks if the given instruction is a move-wide instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a move-wide instruction, false otherwise
     */
    protected boolean isMoveWide(String instruction) {
        return instruction.equals("move-wide");
    }

    /**
     * Checks if the given instruction is a move-wide/from16 instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a move-wide/from16 instruction, false otherwise
     */
    protected boolean isMoveWideFrom16(String instruction) {
        return instruction.equals("move-wide/from16");
    }

    /**
     * Checks if the given instruction is a move-wide/16 instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a move-wide/16 instruction, false otherwise
     */
    protected boolean isMoveWide16(String instruction) {
        return instruction.equals("move-wide/16");
    }

    /**
     * Checks if the given instruction is a move-object instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a move-object instruction, false otherwise
     */
    protected boolean isMoveObject(String instruction) {
        return instruction.equals("move-object");
    }

    /**
     * Checks if the given instruction is "move-object/from16".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "move-object/from16", false otherwise
     */
    protected boolean isMoveObjectFrom16(String instruction) {
        return instruction.equals("move-object/from16");
    }

    /**
     * Checks if the given instruction is "move-object/16".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "move-object/16", false otherwise
     */
    protected boolean isMoveObject16(String instruction) {
        return instruction.equals("move-object/16");
    }

    /**
     * Checks if the given instruction is a move-result instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a move-result instruction, false otherwise
     */
    protected boolean isMoveResult(String instruction) {
        return instruction.equals("move-result");
    }

    /**
     * Checks if the given instruction is a move-result-wide instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a move-result-wide instruction, false otherwise
     */
    protected boolean isMoveResultWide(String instruction) {
        return instruction.equals("move-result-wide");
    }

    /**
     * Determines if the given instruction is a move-result-object instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a move-result-object instruction, false otherwise
     */
    protected boolean isMoveResultObject(String instruction) {
        return instruction.equals("move-result-object");
    }

    /**
     * Checks if the given instruction is a move-exception instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a move-exception instruction, false otherwise
     */
    protected boolean isMoveException(String instruction) {
        return instruction.equals("move-exception");
    }

    /**
     * Checks if the given instruction is a return-void instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a return-void instruction, false otherwise
     */
    protected boolean isReturnVoid(String instruction) {
        return instruction.equals("return-void");
    }

    /**
     * Checks if the given instruction is a return statement.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a return statement, false otherwise
     */
    protected boolean isReturn(String instruction) {
        return instruction.equals("return");
    }

    /**
     * Checks if the given instruction is a "return-wide" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "return-wide" instruction, false otherwise
     */
    protected boolean isReturnWide(String instruction) {
        return instruction.equals("return-wide");
    }

    /**
     * Checks if the given instruction is a "return-object" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "return-object" instruction, false otherwise
     */
    protected boolean isReturnObject(String instruction) {
        return instruction.equals("return-object");
    }

    /**
     * Checks if the given instruction is "const/4".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "const/4", false otherwise
     */
    protected boolean isConst4(String instruction) {
        return instruction.equals("const/4");
    }

    /**
     * Checks if the given instruction is "const/16".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "const/16", false otherwise
     */
    protected boolean isConst16(String instruction) {
        return instruction.equals("const/16");
    }

    /**
     * Determines if the given instruction is a constant.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a constant, false otherwise
     */
    protected boolean isConst(String instruction) {
        return instruction.equals("const");
    }

    /**
     * Checks if the given instruction is "const/high16".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "const/high16", false otherwise
     */
    protected boolean isConstHigh16(String instruction) {
        return instruction.equals("const/high16");
    }

    /**
     * Checks if the given instruction is a const-wide/16 instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a const-wide/16 instruction, false otherwise
     */
    protected boolean isConstWide16(String instruction) {
        return instruction.equals("const-wide/16");
    }

    /**
     * Checks if the given instruction is "const-wide/32".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "const-wide/32", false otherwise
     */
    protected boolean isConstWide32(String instruction) {
        return instruction.equals("const-wide/32");
    }

    /**
     * Checks if the given instruction is a "const-wide" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "const-wide" instruction, false otherwise
     */
    protected boolean isConstWide(String instruction) {
        return instruction.equals("const-wide");
    }

    /**
     * Checks if the given instruction is "const-wide/high16".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "const-wide/high16", false otherwise
     */
    protected boolean isConstWideHigh16(String instruction) {
        return instruction.equals("const-wide/high16");
    }

    /**
     * Checks if the given instruction is a constant string.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a constant string, false otherwise
     */
    protected boolean isConstString(String instruction) {
        return instruction.equals("const-string");
    }

    /**
     * Checks if the given instruction is "const-string/jumbo".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "const-string/jumbo", false otherwise
     */
    protected boolean isConstStringJumbo(String instruction) {
        return instruction.equals("const-string/jumbo");
    }

    /**
     * Checks if the given instruction is a "const-class" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "const-class" instruction, false otherwise
     */
    protected boolean isConstClass(String instruction) {
        return instruction.equals("const-class");
    }

    /**
     * Checks if the given instruction is a monitor-enter instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a monitor-enter instruction, false otherwise
     */
    protected boolean isMonitorEnter(String instruction) {
        return instruction.equals("monitor-enter");
    }

    /**
     * Checks if the given instruction is a monitor-exit instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a monitor-exit instruction, false otherwise
     */
    protected boolean isMonitorExit(String instruction) {
        return instruction.equals("monitor-exit");
    }

    /**
     * Checks if the given instruction is a check-cast instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a check-cast instruction, false otherwise
     */
    protected boolean isCheckCast(String instruction) {
        return instruction.equals("check-cast");
    }

    /**
     * Checks if the given instruction is an instance-of instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is an instance-of instruction, false otherwise
     */
    protected boolean isInstanceOf(String instruction) {
        return instruction.equals("instance-of");
    }

    /**
     * Checks if the given instruction is "array-length".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "array-length", false otherwise
     */
    protected boolean isArrayLength(String instruction) {
        return instruction.equals("array-length");
    }

    /**
     * Checks if the given instruction is a "new-instance" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "new-instance" instruction, false otherwise
     */
    protected boolean isNewInstance(String instruction) {
        return instruction.equals("new-instance");
    }

    /**
     * Checks if the given instruction is a new-array instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a new-array instruction, false otherwise
     */
    protected boolean isNewArray(String instruction) {
        return instruction.equals("new-array");
    }

    /**
     * Checks if the given instruction is a "filled-new-array" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "filled-new-array" instruction, false otherwise
     */
    protected boolean isFilledNewArray(String instruction) {
        return instruction.equals("filled-new-array");
    }

    /**
     * Checks if the given instruction is "filled-new-array/range".
     *
     * @param instruction the instruction to check
     * @return true if the instruction is "filled-new-array/range", false otherwise
     */
    protected boolean isFilledNewArrayRange(String instruction) {
        return instruction.equals("filled-new-array/range");
    }

    /**
     * Checks if the given instruction is a "fill-array-data" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "fill-array-data" instruction, false otherwise
     */
    protected boolean isFillArrayData(String instruction) {
        return instruction.equals("fill-array-data");
    }

    /**
     * Checks if the given instruction is a throw statement.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a throw statement, false otherwise
     */
    protected boolean isThrow(String instruction) {
        return instruction.equals("throw");
    }

    /**
     * Checks if the given instruction is a "goto" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "goto" instruction, false otherwise
     */
    protected boolean isGoto(String instruction) {
        return instruction.equals("goto");
    }

    /**
     * Checks if the given instruction is a "goto/16" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "goto/16" instruction, false otherwise
     */
    protected boolean isGoto16(String instruction) {
        return instruction.equals("goto/16");
    }

    /**
     * Checks if the given instruction is a "goto/32" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "goto/32" instruction, false otherwise
     */
    protected boolean isGoto32(String instruction) {
        return instruction.equals("goto/32");
    }

    /**
     * Checks if the given instruction is a packed-switch instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a packed-switch instruction, false otherwise
     */
    protected boolean isPackedSwitch(String instruction) {
        return instruction.equals("packed-switch");
    }

    /**
     * Checks if the given instruction is a "sparse-switch" instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is a "sparse-switch" instruction, false otherwise
     */
    protected boolean isSparseSwitch(String instruction) {
        return instruction.equals("sparse-switch");
    }

    /**
     * Determines if the given instruction is a comparison instruction.
     * @param instruction the instruction to check
     * @return true if the instruction is a comparison instruction, false otherwise
     */
    protected boolean isCmpkind(String instruction) {
        if(instruction.equals("cmpl-float")) {
            return true;
        }
        if(instruction.equals("cmpg-float")) {
            return true;
        }
        if(instruction.equals("cmpl-double")) {
            return true;
        }
        if(instruction.equals("cmpg-double")) {
            return true;
        }
        return instruction.equals("cmp-long");
    }

    /**
     * Determines if the given instruction is an if-test.
     * @param instruction the instruction to check
     * @return true if the instruction is an if-test, false otherwise
     */
    protected boolean isIfTest(String instruction) {
        if (instruction.equals("if-eq")) {
            return true;
        }
        if (instruction.equals("if-ne")) {
            return true;
        }
        if (instruction.equals("if-lt")) {
            return true;
        }
        if (instruction.equals("if-ge")) {
            return true;
        }
        if (instruction.equals("if-gt")) {
            return true;
        }
        return instruction.equals("if-le");
    }

    /**
     * Determines if the given instruction is an if-testz instruction.
     *
     * @param instruction the instruction to check
     * @return true if the instruction is an if-testz instruction, false otherwise
     */
    protected boolean isIfTestz(String instruction) {
        if (instruction.equals("if-eqz")) {
            return true;
        }
        if (instruction.equals("if-nez")) {
            return true;
        }
        if (instruction.equals("if-ltz")) {
            return true;
        }
        if (instruction.equals("if-gez")) {
            return true;
        }
        if (instruction.equals("if-gtz")) {
            return true;
        }
        return instruction.equals("if-lez");
    }

    /**
     * Determines if the given instruction is an array operation.
     * @param instruction the instruction to check
     * @return true if the instruction is an array operation, false otherwise
     */
    protected boolean isArrayOp(String instruction) {
        if(instruction.equals("aget")) {
            return true;
        }
        if(instruction.equals("aget-wide")) {
            return true;
        }
        if(instruction.equals("aget-object")) {
            return true;
        }
        if(instruction.equals("aget-boolean")) {
            return true;
        }
        if(instruction.equals("aget-byte")) {
            return true;
        }
        if(instruction.equals("aget-char")) {
            return true;
        }
        if(instruction.equals("aget-short")) {
            return true;
        }
        if(instruction.equals("aput")) {
            return true;
        }
        if(instruction.equals("aput-wide")) {
            return true;
        }
        if(instruction.equals("aput-object")) {
            return true;
        }
        if(instruction.equals("aput-boolean")) {
            return true;
        }
        if(instruction.equals("aput-byte")) {
            return true;
        }
        if(instruction.equals("aput-char")) {
            return true;
        }
        return instruction.equals("aput-short");
    }

    /**
     * Determines if the given instruction is an instance field operation.
     * @param instruction the instruction to check
     * @return true if the instruction is an instance field operation, false otherwise
     */
    protected boolean isIinstanceOp(String instruction) {
        if(instruction.equals("iget")) {
            return true;
        }
        if(instruction.equals("iget-wide")) {
            return true;
        }
        if(instruction.equals("iget-object")) {
            return true;
        }
        if(instruction.equals("iget-boolean")) {
            return true;
        }
        if(instruction.equals("iget-byte")) {
            return true;
        }
        if(instruction.equals("iget-char")) {
            return true;
        }
        if(instruction.equals("iget-short")) {
            return true;
        }
        if(instruction.equals("iput")) {
            return true;
        }
        if(instruction.equals("iput-wide")) {
            return true;
        }
        if(instruction.equals("iput-object")) {
            return true;
        }
        if(instruction.equals("iput-boolean")) {
            return true;
        }
        if(instruction.equals("iput-byte")) {
            return true;
        }
        if(instruction.equals("iput-char")) {
            return true;
        }
        return instruction.equals("iput-short");
    }

    /**
     * Determines if the given instruction is a static field operation.
     * @param instruction the instruction to check
     * @return true if the instruction is a static field operation, false otherwise
     */
    protected boolean isSstaticOp(String instruction) {
        if(instruction.equals("sget")) {
            return true;
        }
        if(instruction.equals("sget-wide")) {
            return true;
        }
        if(instruction.equals("sget-object")) {
            return true;
        }
        if(instruction.equals("sget-boolean")) {
            return true;
        }
        if(instruction.equals("sget-byte")) {
            return true;
        }
        if(instruction.equals("sget-char")) {
            return true;
        }
        if(instruction.equals("sget-short")) {
            return true;
        }
        if(instruction.equals("sput")) {
            return true;
        }
        if(instruction.equals("sput-wide")) {
            return true;
        }
        if(instruction.equals("sput-object")) {
            return true;
        }
        if(instruction.equals("sput-boolean")) {
            return true;
        }
        if(instruction.equals("sput-byte")) {
            return true;
        }
        if(instruction.equals("sput-char")) {
            return true;
        }
        return instruction.equals("sput-short");
    }

    /**
     * Determines if the given instruction is an invoke kind.
     * @param instruction the instruction to check
     * @return true if the instruction is an invoke kind, false otherwise
     */
    protected boolean isInvokeKind(String instruction) {
        if(instruction.equals("invoke-virtual")) {
            return true;
        }
        if(instruction.equals("invoke-super")) {
            return true;
        }
        if(instruction.equals("invoke-direct")) {
            return true;
        }
        if(instruction.equals("invoke-static")) {
            return true;
        }
        return instruction.equals("invoke-interface");
    }

    /**
     * Determines if the given instruction is an invoke kind range instruction.
     * @param instruction the instruction to check
     * @return true if the instruction is an invoke kind range instruction, false otherwise
     */
    protected boolean isInvokeKindRange(String instruction) {
        if(instruction.equals("invoke-virtual/range")) {
            return true;
        }
        if(instruction.equals("invoke-super/range")) {
            return true;
        }
        if(instruction.equals("invoke-direct/range")) {
            return true;
        }
        if(instruction.equals("invoke-static/range")) {
            return true;
        }
        return instruction.equals("invoke-interface/range");
    }


    /**
     * Determines if the given instruction is an unary operation.
     * @param instruction the instruction to check
     * @return true if the instruction is an unary operation, false otherwise
     */
    protected boolean isUnOp(String instruction) {
        if (instruction.equals("neg-int")) {
            return true;
        }
        if (instruction.equals("not-int")) {
            return true;
        }
        if (instruction.equals("neg-long")) {
            return true;
        }
        if (instruction.equals("not-long")) {
            return true;
        }
        if (instruction.equals("neg-float")) {
            return true;
        }
        if (instruction.equals("neg-double")) {
            return true;
        }
        if (instruction.equals("int-to-long")) {
            return true;
        }
        if (instruction.equals("int-to-float")) {
            return true;
        }
        if (instruction.equals("int-to-double")) {
            return true;
        }
        if (instruction.equals("long-to-int")) {
            return true;
        }
        if (instruction.equals("long-to-float")) {
            return true;
        }
        if (instruction.equals("long-to-double")) {
            return true;
        }
        if (instruction.equals("float-to-int")) {
            return true;
        }
        if (instruction.equals("float-to-long")) {
            return true;
        }
        if (instruction.equals("float-to-double")) {
            return true;
        }
        if (instruction.equals("double-to-int")) {
            return true;
        }
        if (instruction.equals("double-to-long")) {
            return true;
        }
        if (instruction.equals("double-to-float")) {
            return true;
        }
        if (instruction.equals("int-to-byte")) {
            return true;
        }
        if (instruction.equals("int-to-char")) {
            return true;
        }
        return instruction.equals("int-to-short");
    }

    /**
     * Determines if the given instruction is a binary operation.
     * @param instruction the instruction to check
     * @return true if the instruction is a binary operation, false otherwise
     */
    protected boolean isBinOp(String instruction) {
        if(instruction.equals("add-int")) {
            return true;
        }
        if(instruction.equals("sub-int")) {
            return true;
        }
        if(instruction.equals("mul-int")) {
            return true;
        }
        if(instruction.equals("div-int")) {
            return true;
        }
        if(instruction.equals("rem-int")) {
            return true;
        }
        if(instruction.equals("and-int")) {
            return true;
        }
        if(instruction.equals("or-int")) {
            return true;
        }
        if(instruction.equals("xor-int")) {
            return true;
        }
        if(instruction.equals("shl-int")) {
            return true;
        }
        if(instruction.equals("shr-int")) {
            return true;
        }
        if(instruction.equals("ushr-int")) {
            return true;
        }
        if(instruction.equals("add-long")) {
            return true;
        }
        if(instruction.equals("sub-long")) {
            return true;
        }
        if(instruction.equals("mul-long")) {
            return true;
        }
        if(instruction.equals("div-long")) {
            return true;
        }
        if(instruction.equals("rem-long")) {
            return true;
        }
        if(instruction.equals("and-long")) {
            return true;
        }
        if(instruction.equals("or-long")) {
            return true;
        }
        if(instruction.equals("xor-long")) {
            return true;
        }
        if(instruction.equals("shl-long")) {
            return true;
        }
        if(instruction.equals("shr-long")) {
            return true;
        }
        if(instruction.equals("ushr-long")) {
            return true;
        }
        if(instruction.equals("add-float")) {
            return true;
        }
        if(instruction.equals("sub-float")) {
            return true;
        }
        if(instruction.equals("mul-float")) {
            return true;
        }
        if(instruction.equals("div-float")) {
            return true;
        }
        if(instruction.equals("rem-float")) {
            return true;
        }
        if(instruction.equals("add-double")) {
            return true;
        }
        if(instruction.equals("sub-double")) {
            return true;
        }
        if(instruction.equals("mul-double")) {
            return true;
        }
        if(instruction.equals("div-double")) {
            return true;
        }
        return instruction.equals("rem-double");
    }

    /**
     * Determines if the given instruction is a binary operation with 2 addresses.
     * @param instruction the instruction to check
     * @return true if the instruction is a binary operation with 2 addresses, false otherwise
     */
    protected boolean isBinOp2addr(String instruction) {
        if(instruction.equals("add-int/2addr")) {
            return true;
        }
        if(instruction.equals("sub-int/2addr")) {
            return true;
        }
        if(instruction.equals("mul-int/2addr")) {
            return true;
        }
        if(instruction.equals("div-int/2addr")) {
            return true;
        }
        if(instruction.equals("rem-int/2addr")) {
            return true;
        }
        if(instruction.equals("and-int/2addr")) {
            return true;
        }
        if(instruction.equals("or-int/2addr")) {
            return true;
        }
        if(instruction.equals("xor-int/2addr")) {
            return true;
        }
        if(instruction.equals("shl-int/2addr")) {
            return true;
        }
        if(instruction.equals("shr-int/2addr")) {
            return true;
        }
        if(instruction.equals("ushr-int/2addr")) {
            return true;
        }
        if(instruction.equals("add-long/2addr")) {
            return true;
        }
        if(instruction.equals("sub-long/2addr")) {
            return true;
        }
        if(instruction.equals("mul-long/2addr")) {
            return true;
        }
        if(instruction.equals("div-long/2addr")) {
            return true;
        }
        if(instruction.equals("rem-long/2addr")) {
            return true;
        }
        if(instruction.equals("and-long/2addr")) {
            return true;
        }
        if(instruction.equals("or-long/2addr")) {
            return true;
        }
        if(instruction.equals("xor-long/2addr")) {
            return true;
        }
        if(instruction.equals("shl-long/2addr")) {
            return true;
        }
        if(instruction.equals("shr-long/2addr")) {
            return true;
        }
        if(instruction.equals("ushr-long/2addr")) {
            return true;
        }
        if(instruction.equals("add-float/2addr")) {
            return true;
        }
        if(instruction.equals("sub-float/2addr")) {
            return true;
        }
        if(instruction.equals("mul-float/2addr")) {
            return true;
        }
        if(instruction.equals("div-float/2addr")) {
            return true;
        }
        if(instruction.equals("rem-float/2addr")) {
            return true;
        }
        if(instruction.equals("add-double/2addr")) {
            return true;
        }
        if(instruction.equals("sub-double/2addr")) {
            return true;
        }
        if(instruction.equals("mul-double/2addr")) {
            return true;
        }
        if(instruction.equals("div-double/2addr")) {
            return true;
        }
        return instruction.equals("rem-double/2addr");
    }

    /**
     * Determines if the given instruction is a binary operation with a 16-bit literal.
     * @param instruction the instruction to check
     * @return true if the instruction is a binary operation with a 16-bit literal, false otherwise
     */
    protected boolean isBinOpLit16(String instruction) {
        if(instruction.equals("add-int/lit16")) {
            return true;
        }
        if(instruction.equals("rsub-int")) {
            return true;
        }
        if(instruction.equals("mul-int/lit16")) {
            return true;
        }
        if(instruction.equals("div-int/lit16")) {
            return true;
        }
        if(instruction.equals("rem-int/lit16")) {
            return true;
        }
        if(instruction.equals("and-int/lit16")) {
            return true;
        }
        if(instruction.equals("or-int/lit16")) {
            return true;
        }
        return instruction.equals("xor-int/lit16");
    }


    /**
     * Determines if the given instruction is a binary operation with a literal value of 8 bits.
     * @param instruction the instruction to check
     * @return true if the instruction is a binary operation with a literal value of 8 bits, false otherwise
     */
    protected boolean isBinOpLit8(String instruction) {
        if(instruction.equals("add-int/lit8")) {
            return true;
        }
        if(instruction.equals("rsub-int/lit8")) {
            return true;
        }
        if(instruction.equals("mul-int/lit8")) {
            return true;
        }
        if(instruction.equals("div-int/lit8")) {
            return true;
        }
        if(instruction.equals("rem-int/lit8")) {
            return true;
        }
        if(instruction.equals("and-int/lit8")) {
            return true;
        }
        if(instruction.equals("or-int/lit8")) {
            return true;
        }
        if(instruction.equals("xor-int/lit8")) {
            return true;
        }
        if(instruction.equals("shl-int/lit8")) {
            return true;
        }
        if(instruction.equals("shr-int/lit8")) {
            return true;
        }
        return instruction.equals("ushr-int/lit8");
    }

    /**
     * Checks if the given instruction is an invoke-direct instruction.
     * @param instruction the instruction to check
     * @return true if the instruction is an invoke-direct instruction, false otherwise
     */
    protected boolean isInvokePolymorphic(String instruction) {
        return instruction.equals("invoke-polymorphic");
    }

    /**
     * Checks if the given instruction is an invoke-polymorphic-range instruction.
     * @param instruction the instruction to check
     * @return true if the instruction is an invoke-polymorphic-range instruction, false otherwise
     */
    protected boolean isInvokePolymorphicRange(String instruction) {
        return instruction.equals("invoke-polymorphic/range");
    }

    /**
     * Checks if the given instruction is an invoke-custom instruction.
     * @param instruction the instruction to check
     * @return true if the instruction is an invoke-custom instruction, false otherwise
     */
    protected boolean isInvokeCustom(String instruction) {
        return instruction.equals("invoke-custom");
    }

    /**
     * Checks if the given instruction is an invoke-custom-range instruction.
     * @param instruction the instruction to check
     * @return true if the instruction is an invoke-custom-range instruction, false otherwise
     */
    protected boolean isInvokeCustomRange(String instruction) {
        return instruction.equals("invoke-custom/range");
    }

    /**
     * Checks if the given instruction is a const-method-handle instruction.
     * @param instruction the instruction to check
     * @return true if the instruction is a const-method-handle instruction, false otherwise
     */
    protected boolean isConstMethodHandle(String instruction) {
        return instruction.equals("const-method-handle");
    }

    /**
     * Checks if the given instruction is a const-method-type instruction.
     * @param instruction the instruction to check
     * @return true if the instruction is a const-method-type instruction, false otherwise
     */
    protected boolean isConstMethodType(String instruction) {
        return instruction.equals("const-method-type");
    }


    /**
     * Returns a list of register types for the given unary operation instruction.
     * @param instruction the unary operation instruction
     * @return a list of register types for the given unary operation instruction
     * @throws InvalidInstructionError if the instruction is not a valid unary operation
     */
    protected List<String> unOpRegTypes(String instruction) {
        List<String> types = new ArrayList<>();
        if (instruction.equals("neg-int") || instruction.equals("not-int")) {
            types.add("I");
            types.add("I");
            return types;
        }
        if (instruction.equals("neg-long") || instruction.equals("not-long")) {
            types.add("J");
            types.add("J");
            return types;
        }
        if (instruction.equals("neg-float")) {
            types.add("F");
            types.add("F");
            return types;
        }
        if (instruction.equals("neg-double")) {
            types.add("D");
            types.add("D");
            return types;
        }
        if (instruction.equals("int-to-long")) {
            types.add("I");
            types.add("J");
            return types;
        }
        if (instruction.equals("int-to-float")) {
            types.add("I");
            types.add("F");
            return types;
        }
        if (instruction.equals("int-to-double")) {
            types.add("I");
            types.add("D");
            return types;
        }
        if (instruction.equals("long-to-int")) {
            types.add("J");
            types.add("I");
            return types;
        }
        if (instruction.equals("long-to-float")) {
            types.add("J");
            types.add("F");
            return types;
        }
        if (instruction.equals("long-to-double")) {
            types.add("J");
            types.add("D");
            return types;
        }
        if (instruction.equals("float-to-int")) {
            types.add("F");
            types.add("I");
            return types;
        }
        if (instruction.equals("float-to-long")) {
            types.add("F");
            types.add("J");
            return types;
        }
        if (instruction.equals("float-to-double")) {
            types.add("F");
            types.add("D");
            return types;
        }
        if (instruction.equals("double-to-int")) {
            types.add("D");
            types.add("I");
            return types;
        }
        if (instruction.equals("double-to-long")) {
            types.add("D");
            types.add("J");
            return types;
        }
        if (instruction.equals("double-to-float")) {
            types.add("D");
            types.add("F");
            return types;
        }
        if (instruction.equals("int-to-byte")) {
            types.add("I");
            types.add("B");
            return types;
        }
        if (instruction.equals("int-to-char")) {
            types.add("I");
            types.add("C");
            return types;
        }
        if (instruction.equals("int-to-short")) {
            types.add("I");
            types.add("S");
            return types;
        }
        throw new InvalidInstructionError("Not an unop");
    }


    /**
     * Checks if the given instruction is wide, meaning it operates on 64-bit values.
     * @param instruction the instruction to check
     * @return true if the instruction is wide, false otherwise
     */
    public boolean targetIsWide(String instruction) {
        return instruction.contains("-wide") || instruction.contains("-long") || instruction.contains("-double");
    }

    /**
     * Returns the opposite conditional of the given conditional.
     *
     * @param instruction the conditional to get the opposite of
     * @return the opposite conditional
     * @throws InvalidInstructionError if the instruction is not a conditional
     */
    public String getOppositeConditional(String instruction) {
        if (instruction.equals("if-eq")) {
            return "if-ne";
        }
        if (instruction.equals("if-ne")) {
            return "if-eq";
        }
        if (instruction.equals("if-lt")) {
            return "if-ge";
        }
        if (instruction.equals("if-ge")) {
            return "if-lt";
        }
        if (instruction.equals("if-gt")) {
            return "if-le";
        }
        if (instruction.equals("if-le")) {
            return "if-gt";
        }

        if (instruction.equals("if-eqz")) {
            return "if-nez";
        }
        if (instruction.equals("if-nez")) {
            return "if-eqz";
        }
        if (instruction.equals("if-ltz")) {
            return "if-gez";
        }
        if (instruction.equals("if-gez")) {
            return "if-ltz";
        }
        if (instruction.equals("if-gtz")) {
            return "if-lez";
        }
        if (instruction.equals("if-lez")) {
            return "if-gtz";
        }
        throw new InvalidInstructionError("Not a conditional");
    }

    /**
     * Returns the size of the instruction in bits.
     *
     * @param instruction the instruction to get the size of
     * @return the size of the instruction in bits
     * @throws InvalidInstructionError if the instruction is invalid
     */
    public int getSizeBit(String instruction) {
        int num16Bits = 0;
        num16Bits = getSizeIfNop(instruction, num16Bits);
        num16Bits = getSizeIfMove(instruction, num16Bits);
        num16Bits = getSizeIfMoveResult(instruction, num16Bits);
        num16Bits = getSizeIfReturn(instruction, num16Bits);
        num16Bits = getSizeIfConst(instruction, num16Bits);
        num16Bits = getSizeIfMonitor(instruction, num16Bits);
        num16Bits = getSizeIfSpecial(instruction, num16Bits);
        num16Bits = getSizeIfGoto(instruction, num16Bits);
        num16Bits = getSizeIfTest(instruction, num16Bits);
        num16Bits = getSizeIfStruct(instruction, num16Bits);
        num16Bits = getSizeIfInvoke(instruction, num16Bits);
        num16Bits = getSizeIfOperator(instruction, num16Bits);
        num16Bits = getSizeIfConstMethod(instruction, num16Bits);
        if (num16Bits == 0) {
            throw new InvalidInstructionError("Invalid instruction: " + instruction);
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a constant method handle or a constant method type.
     * If the instruction is a constant method handle or a constant method type, the size is 2.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfConstMethod(String instruction, int num16Bits) {
        if (isConstMethodHandle(instruction) || isConstMethodType(instruction)) {
            num16Bits = 2;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a monitor enter or a monitor exit.
     * If the instruction is a monitor enter or a monitor exit, the size is 1.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfMonitor(String instruction, int num16Bits) {
        if (isMonitorEnter(instruction) || isMonitorExit(instruction)) {
            num16Bits = 1;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a nop.
     * If the instruction is a nop, the size is 1.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfNop(String instruction, int num16Bits) {
        if (isNop(instruction)) {
            num16Bits = 1;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a special instruction.
     * If the instruction is a special instruction, the size is 1, 2, or 3.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfSpecial(String instruction, int num16Bits) {
        if (isCheckCast(instruction)) {
            num16Bits = 2;
        } else if (isInstanceOf(instruction)) {
            num16Bits = 2;
        } else if (isArrayLength(instruction)) {
            num16Bits = 1;
        } else if (isNewInstance(instruction) || isNewArray(instruction)) {
            num16Bits = 2;
        } else if (isFilledNewArray(instruction) || isFilledNewArrayRange(instruction) || isFillArrayData(instruction)) {
            num16Bits = 3;
        }
        if (isThrow(instruction)) {
            num16Bits = 1;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is an invoke instruction.
     * If the instruction is an invoke instruction, the size is 3 or 4.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfInvoke(String instruction, int num16Bits) {
        if (isInvokeKind(instruction) || isInvokeKindRange(instruction) ||
            isInvokeCustom(instruction) || isInvokeCustomRange(instruction)) {
            num16Bits = 3;
        } else if (isInvokePolymorphic(instruction) || isInvokePolymorphicRange(instruction)) {
            num16Bits = 4;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a struct instruction.
     * If the instruction is a struct instruction, the size is 2.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfStruct(String instruction, int num16Bits) {
        if (isArrayOp(instruction) || isIinstanceOp(instruction) || isSstaticOp(instruction)) {
            num16Bits = 2;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a test instruction.
     * If the instruction is a test instruction, the size is 2 or 3.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfTest(String instruction, int num16Bits) {
        if (isPackedSwitch(instruction) || isSparseSwitch(instruction)) {
            num16Bits = 3;
        } else if (isCmpkind(instruction)) {
            num16Bits = 2;
        } else if (isIfTest(instruction) || isIfTestz(instruction)) {
            num16Bits = 2;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is an operator instruction.
     * If the instruction is an operator instruction, the size is 1, 2, or 5.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfOperator(String instruction, int num16Bits) {
        if (isUnOp(instruction)) {
            num16Bits = 1;
        } else if (isBinOp(instruction)) {
            num16Bits = 2;
        } else if (isBinOp2addr(instruction)) {
            num16Bits = 1;
        } else if (isBinOpLit16(instruction) || isBinOpLit8(instruction)) {
            num16Bits = 2;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a goto instruction.
     * If the instruction is a goto instruction, the size is 1, 2, or 3.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfGoto(String instruction, int num16Bits) {
        if (isGoto(instruction)) {
            num16Bits = 1;
        } else if (isGoto16(instruction)) {
            num16Bits = 2;
        } else if (isGoto32(instruction)) {
            num16Bits = 3;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a const instruction.
     * If the instruction is a const instruction, the size is 1, 2, 3, or 5.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfConst(String instruction, int num16Bits) {
        if (isConst4(instruction)) {
            num16Bits = 1;
        } else if (isConst16(instruction) || isConstHigh16(instruction) || isConstWide16(instruction) || isConstWideHigh16(instruction)
                || isConstString(instruction) || isConstClass(instruction)) {
            num16Bits = 2;
        } else if (isConst(instruction) || isConstWide32(instruction) || isConstStringJumbo(instruction)) {
            num16Bits = 3;
        } else if (isConstWide(instruction)) {
            num16Bits = 5;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a return instruction.
     * If the instruction is a return instruction, the size is 1 or 2.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfReturn(String instruction, int num16Bits) {
        if (isReturnVoid(instruction) || isReturn(instruction) || isReturnWide(instruction) || isReturnObject(instruction)) {
            num16Bits = 1;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a move result instruction.
     * If the instruction is a move result instruction, the size is 1 or 2.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     */
    private int getSizeIfMoveResult(String instruction, int num16Bits) {
        if (isMoveResult(instruction) || isMoveResultWide(instruction) || isMoveResultObject(instruction) || isMoveException(instruction)) {
            return 1;
        }
        return num16Bits;
    }

    /**
     * Returns the size of the instruction if it is a move instruction.
     * If the instruction is a move instruction, the size is 1, 2, or 3.
     * @param instruction the instruction to check
     * @param num16Bits the current size of the instruction
     * @return the size of the instruction
     * @throws InvalidInstructionError if the instruction is invalid
     */
    private int getSizeIfMove(String instruction, int num16Bits) {
        if (isMove(instruction) || isMoveWide(instruction) || isMoveObject(instruction) ) {
            num16Bits = 1;
        } else if (isMoveFrom16(instruction) || isMoveWideFrom16(instruction) || isMoveObjectFrom16(instruction)) {
            num16Bits = 2;
        } else if (isMove16(instruction) || isMoveWide16(instruction) || isMoveObject16(instruction)) {
            num16Bits = 3;
        }
        return num16Bits;
    }

    /**
     * Sanitizes the given filed type string by replacing '/', '$', '[', and ';' characters with '_' or 'array_'.
     * Example 1: Landroid/net/Uri; --> Landroid_net_Uri
     * Example 2: [B --> array_B
     * @param fieldType the filed type string to be sanitized
     * @return the sanitized filed type string
     */
    public String sanitizeFieldType(String fieldType) {
        fieldType = fieldType.replace('/', '_');
        fieldType = fieldType.replace("$", "_");
        fieldType = fieldType.replace("[", "array_");
        fieldType = fieldType.replace(";", "");
        return fieldType;
    }

}
