package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Parcels extends TaintAnalysis {
    public static List<String> addParcelTaint(TaintTool tool, String line, String instruction, MethodInfo calledMethodInfo, String[] passedRegs, Map<String, String> taintRegMap, int taintTempReg, String returnTaintReg, ClassAnalysis classAnalysis, InstrumentationContext context) {

        List<String> linesToAdd = new ArrayList<>();

        if (TaintSink.isEmpty()) {
            return linesToAdd;
        }

        String receiverReg = null;
        String receiverRegTaint = null;
        if (!instruction.contains("static") && !line.contains("{}")) { // has receiver register
            receiverReg = passedRegs[0];
            receiverRegTaint = taintRegMap.get(receiverReg);
        }
        String calledMethodClass = calledMethodInfo.getClassName();
        String calledMethodName = calledMethodInfo.getNameAndDesc();
        List<String> params = calledMethodInfo.getParams();
        Set<String> classesOfMethod = classAnalysis.getImplementingClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc());
        // Set<String> classesOfMethod = classAnalysis.getClassOfMethod(calledMethodInfo.getClassName(), calledMethodInfo.getNameAndDesc());
        // AnalysisLogger.log(calledMethodInfo.signature().equals("Landroid/view/MenuItem;->getIntent()Landroid/content/Intent;"), "Inspecting line: %s%n", line);
        // AnalysisLogger.log(calledMethodInfo.signature().equals("Landroid/view/MenuItem;->getIntent()Landroid/content/Intent;"), "    Classes %s%n", classesOfMethod);
        if (calledMethodClass.equals("Landroid/content/Intent;")) {
            if (calledMethodName.equals("putExtras") || calledMethodName.equals("writeToParcel") || calledMethodName.equals("replaceExtras")) {
                String newLine = "    invoke-static {" + passedRegs[0] + ", " + receiverRegTaint + ", " +
                    "}, " + tool.addParcelTaint();
                Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                linesToAdd = rangedInvoke.first;
                return linesToAdd;
            } else if (calledMethodName.startsWith("put")) {
                if (params.get(1).equals("Ljava/lang/String;")) {
                    if (params.get(2).startsWith("L") || params.get(2).startsWith("[")) {
                        // System.out.println("Should add taint parcels to: " + line);
                        String newLine = "    invoke-static {" + passedRegs[0] + ", " + receiverRegTaint + ", " + passedRegs[2] +
                            "}, " + tool.addParcelTaintAndObject();
                        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                        linesToAdd = rangedInvoke.first;
                        return linesToAdd;
                    } else {
                        String newLine = "    invoke-static {" + passedRegs[0] + ", " + receiverRegTaint + ", " +
                            "}, " + tool.addParcelTaint();
                        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                        linesToAdd = rangedInvoke.first;
                        return linesToAdd;
                    }
                }
            }

            if (returnTaintReg != null) {
                if (calledMethodName.startsWith("get")) {
                    // System.out.println("----------------");
                    // System.out.println(line);
                    // System.out.println(passedRegs[0]);
                    // System.out.println(returnTaintReg);

                    String newLine = "    invoke-static {" + returnTaintReg + "}, " + tool.getSetReturnTaintInstr();
                    Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);

                    newLine = "    invoke-static {" + passedRegs[0] +
                        "}, " + tool.getParcelTaint();
                    rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);
                    linesToAdd.add("    " + tool.getMoveResultTaint() + " " + returnTaintReg);
                    return linesToAdd;
                }
            }
        } else if (calledMethodClass.equals("Landroid/os/Bundle;")) {
            if (calledMethodName.startsWith("put")) {
                if (params.get(1).equals("Ljava/lang/String;")) {
                    if (params.get(2).startsWith("L") || params.get(2).startsWith("[")) {
                        // System.out.println("Should add taint parcels to: " + line);
                        String newLine = "    invoke-static {" + passedRegs[0] + ", " + receiverRegTaint + ", " + passedRegs[2] +
                            "}, " + tool.addBundleTaintAndObject();
                        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                        linesToAdd = rangedInvoke.first;
                        return linesToAdd;
                    } else {
                        String newLine = "    invoke-static {" + passedRegs[0] + ", " + receiverRegTaint + ", " +
                            "}, " + tool.addBundleTaint();
                        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                        linesToAdd = rangedInvoke.first;
                        return linesToAdd;
                    }
                }
            }

            if (returnTaintReg != null) {
                if (calledMethodName.startsWith("get")) {
                    // System.out.println("----------------");
                    // System.out.println(line);
                    // System.out.println(passedRegs[0]);
                    // System.out.println(returnTaintReg);

                    String newLine = "    invoke-static {" + returnTaintReg + "}, " + tool.getSetReturnTaintInstr();
                    Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);

                    if (getRegNumFromRef(returnTaintReg) > 255) {
                        linesToAdd.add("    move-object/16 v" + taintTempReg + ", " + passedRegs[0]);
                    }

                    newLine = "    invoke-static {" + passedRegs[0] + "}, " + tool.getBundleTaint();
                    rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);
                    if (getRegNumFromRef(returnTaintReg) > 255) {
                        linesToAdd.add("    " + tool.getMoveResultTaint() + " " + passedRegs[0]);
                        linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + returnTaintReg + ", " + passedRegs[0]);
                        linesToAdd.add("    move-object/16 " + passedRegs[0] + ", v" + taintTempReg);
                    } else {
                        linesToAdd.add("    " + tool.getMoveResultTaint() + " " + returnTaintReg);
                    }

                    return linesToAdd;
                }
            }
        } else if (classesOfMethod.contains("Landroid/content/BroadcastReceiver;")) {
            // System.out.println("    Method is " + calledMethodName);
            if (calledMethodName.startsWith("setResultData")) {
                // AnalysisLogger.log(true, "Adding set result data taint 1, method: %s, calls %s%n", context.currentMethod.signature(), calledMethodInfo.signature());
                // System.out.println("    Adding taint");
                String newLine = "    invoke-static {" + receiverRegTaint + ", " +
                    "}, " + tool.setOrderedIntentParam();
                Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                linesToAdd = rangedInvoke.first;
                return linesToAdd;
            }
            if (returnTaintReg != null) {
                if (calledMethodName.startsWith("getResultData")) {
                    // AnalysisLogger.log(true, "Adding set result data taint 2, method: %s, calls %s%n", context.currentMethod.signature(), calledMethodInfo.signature());
                    // System.out.println("----------------");
                    // System.out.println(line);
                    // System.out.println(passedRegs[0]);
                    // System.out.println(returnTaintReg);

                    if (getRegNumFromRef(returnTaintReg) > 255) {
                        linesToAdd.add("    move-object/16 v" + taintTempReg + ", " + passedRegs[0]);
                    }

                    linesToAdd.add("    invoke-static {}, " + tool.getOrderedIntentParam());
                    if (getRegNumFromRef(returnTaintReg) > 255) {
                        linesToAdd.add("    " + tool.getMoveResultTaint() + " " + passedRegs[0]);
                        linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + returnTaintReg + ", " + passedRegs[0]);
                        linesToAdd.add("    move-object/16 " + passedRegs[0] + ", v" + taintTempReg);
                    } else {
                        linesToAdd.add("    " + tool.getMoveResultTaint() + " " + returnTaintReg);
                    }
                    String newLine = "    invoke-static {" + returnTaintReg + "}, " + tool.getSetReturnTaintInstr();
                    Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);
                    return linesToAdd;
                }
            }
        } else if (classesOfMethod.contains("Landroid/app/Activity;")) {
            // System.out.println("----------------");
            if (returnTaintReg != null) {
                if (calledMethodName.equals("getIntent()Landroid/content/Intent;")) {
                    // AnalysisLogger.log(true, "Adding set result data taint 3, method: %s, calls %s%n", context.currentMethod.signature(), calledMethodInfo.signature());
                    // System.out.println("----------------");
                    // System.out.println(line);
                    // System.out.println(passedRegs[0]);
                    // System.out.println(returnTaintReg);

                    String newLine = "    invoke-virtual {" + passedRegs[0] +
                        "}, " + tool.getIntent();
                    Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);

                    linesToAdd.add("    " + tool.getMoveResultTaint() + " " + returnTaintReg);

                    newLine = "    invoke-static {" + returnTaintReg +
                        "}, " + tool.getParcelTaint();
                    rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);

                    linesToAdd.add("    " + tool.getMoveResultTaint() + " " + returnTaintReg);
                    newLine = "    invoke-static {" + returnTaintReg + "}, " + tool.getSetReturnTaintInstr();
                    rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);

                    return linesToAdd;
                }
            }
        }
        return linesToAdd;
    }


}
