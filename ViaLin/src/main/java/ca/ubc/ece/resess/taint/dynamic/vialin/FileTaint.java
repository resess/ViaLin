package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class FileTaint extends TaintAnalysis {
    public static Pair<List<String>, Integer> addFileTaint(TaintTool tool, String line, String instruction, MethodInfo calledMethodInfo, String[] passedRegs, Map<String, String> taintRegMap, int taintTempReg, String returnTaintReg, String signatureRegister, int methodDelta, List<String> taintedClassLines, String className, String threadReg, Set<String> transformations) {
        if (TaintSink.isEmpty()) {
            return new Pair<>(new ArrayList<>(), 0);
        }

        List<String> linesToAdd = new ArrayList<>();
        String receiverReg = null;
        String receiverRegTaint = null;
        if (!instruction.contains("static") && !line.contains("{}")) { // has receiver register
            receiverReg = passedRegs[0];
            receiverRegTaint = taintRegMap.get(receiverReg);
        }
        String calledMethodClass = calledMethodInfo.getClassName();
        String calledMethodName = calledMethodInfo.getNameAndDesc();
        List<String> params = calledMethodInfo.getParams();

        if (calledMethodClass.equals("Landroid/content/SharedPreferences$Editor;")) {
            if (calledMethodName.startsWith("put")) {
                if (params.get(1).equals("Ljava/lang/String;")) {



                    // System.out.println("Should add taint files to: " + line);
                    if (params.get(2).startsWith("L") || params.get(2).startsWith("[")) {
                        // arguments: Editor editor, String key, PathTaint fileTaint, Object object
                        String newLine = "    invoke-static {" + passedRegs[0] + ", " + passedRegs[1] + ", " + receiverRegTaint + ", " + passedRegs[2] + "}, " + tool.addSharedPrefsTaintAndObject();
                        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                        linesToAdd = rangedInvoke.first;

                        // newMaxAregs is the highest register number currently in use
                        // rangedInvoke.second returns the # of additional registers used
                        int newMaxRegs = Math.max(taintTempReg, rangedInvoke.second);

                        // Khaled: diabled in main branch due to crash, may crash framework
                        // newMaxRegs = addSharedPreferencesTaint(tool, linesToAdd, taintRegMap, passedRegs, newMaxRegs, false);

                        // we need to know the newMaxRegs because we need to add the ".registers xxx" line to the bytecode
                        return new Pair<>(linesToAdd, newMaxRegs);
                    } else {
                        String newLine = "    invoke-static {" + passedRegs[0] + ", " + passedRegs[1] + ", " + receiverRegTaint + ", " + "}, " + tool.addSharedPrefsTaint();
                        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                        linesToAdd = rangedInvoke.first;
                        int newMaxRegs = rangedInvoke.second;
                        return new Pair<>(linesToAdd, newMaxRegs);
                    }
                }
            }
        }
        else if (calledMethodClass.equals("Landroid/content/SharedPreferences;")) {
            if (returnTaintReg != null) {
                if (calledMethodName.startsWith("getAll")) {
                    // System.out.println("----------------");
                    // System.out.println(line);
                    // System.out.println(passedRegs[0]);
                    // System.out.println(returnTaintReg);
                    if (getRegNumFromRef(returnTaintReg) > 255) {
                        linesToAdd.add("    move-object/16 v" + taintTempReg + ", " + passedRegs[0]);
                    }
                    String newLine = "    invoke-static {" + passedRegs[0] + "}, " + tool.getSharedPrefsTaintAll();
                    Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd = rangedInvoke.first;
                    int newMaxRegs = rangedInvoke.second;
                    if (getRegNumFromRef(returnTaintReg) > 255) {
                        linesToAdd.add("    " + tool.getMoveResultTaint() + " " + passedRegs[0]);
                        linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + returnTaintReg + ", " + passedRegs[0]);
                        linesToAdd.add("    move-object/16 " + passedRegs[0] + ", v" + taintTempReg);
                    } else {
                        linesToAdd.add("    " + tool.getMoveResultTaint() + " " + returnTaintReg);
                    }
                    return new Pair<>(linesToAdd, newMaxRegs);
                } else if (calledMethodName.startsWith("get")) {
                    if (getRegNumFromRef(returnTaintReg) > 255) {
                        linesToAdd.add("    move-object/16 v" + taintTempReg + ", " + passedRegs[0]);
                    }
                    String newLine = "    invoke-static {" + passedRegs[0] + ", " + passedRegs[1] + "}, " + tool.getSharedPrefsTaint();
                    Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd = rangedInvoke.first;
                    int newMaxRegs = rangedInvoke.second;
                    if (getRegNumFromRef(returnTaintReg) > 255) {
                        linesToAdd.add("    " + tool.getMoveResultTaint() + " " + passedRegs[0]);
                        linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + returnTaintReg + ", " + passedRegs[0]);
                        linesToAdd.add("    move-object/16 " + passedRegs[0] + ", v" + taintTempReg);
                    } else {
                        linesToAdd.add("    " + tool.getMoveResultTaint() + " " + returnTaintReg);
                    }
                    return new Pair<>(linesToAdd, newMaxRegs);
                }
            }

        }



        if (calledMethodInfo.signature().equals("Ljava/io/ObjectOutputStream;-><init>(Ljava/io/OutputStream;)V")) {
            String taintReg = taintRegMap.get(passedRegs[1]);
            String newLine = "    invoke-static {" + threadReg + ", " + taintReg + "}, " + tool.getSetParamTaintInstr(1);
            Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
            linesToAdd = rangedInvoke.first;
            int newMaxRegs = rangedInvoke.second;

            // linesToAdd.add(line);
            // transformations.add("Added line");

            // if (getRegNumFromRef(taintReg) > 255) {
            //     linesToAdd.add("    # need to handle larger taintReg");
            // } else {
            //     if (getRegNumFromRef(threadReg) > 15) {
            //         linesToAdd.add("    invoke-static/range {" + threadReg + " .. " + threadReg + "}, " + tool.getGetParamTaintInstr(1));
            //     } else {
            //         linesToAdd.add("    invoke-static {" + threadReg + "}, " + tool.getGetParamTaintInstr(1));
            //     }
            //     linesToAdd.add("     " + tool.getMoveResultTaint() + " " + taintReg);
            // }
            return new Pair<>(linesToAdd, newMaxRegs);
        } else if (calledMethodInfo.signature().equals("Ljava/io/ObjectOutputStream;->writeObject(Ljava/lang/Object;)V")) {
            String newLine = "    invoke-static {" + threadReg + ", " + taintRegMap.get(passedRegs[0]) + "}, " + tool.getSetParamTaintInstr(1);
            Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
            linesToAdd = rangedInvoke.first;
            int newMaxRegs = rangedInvoke.second;

            if (tool instanceof ViaLinTool) {
                if (taintTempReg < 256) {
                    linesToAdd.add("    const v" + taintTempReg + ", " + methodDelta);
                } else {
                    linesToAdd
                            .add("    move-object/16 v" + String.valueOf(taintTempReg + 1) + ", " + signatureRegister);
                    linesToAdd.add("    const " + signatureRegister + ", " + methodDelta);
                    linesToAdd.add("    move/16 v" + taintTempReg + ", " + signatureRegister);
                    linesToAdd
                            .add("    move-object/16 " + signatureRegister + ", v" + String.valueOf(taintTempReg + 1));
                }

                newLine = "    invoke-static {" + signatureRegister + ",v" + taintTempReg
                        + "}, Ljava/lang/Thread;->setSite(Ljava/lang/String;I)V";
                rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                linesToAdd.addAll(rangedInvoke.first);
                newMaxRegs = (newMaxRegs > rangedInvoke.second) ? newMaxRegs : rangedInvoke.second;
            }

            return new Pair<>(linesToAdd, newMaxRegs);
        }

        // System.out.println("Inspecting line: " + line);
        if (calledMethodInfo.signature().startsWith("Ljava/util/Formatter;-><init>") && (passedRegs.length > 1)) {
            String taintReg = taintRegMap.get(passedRegs[1]);
            String newLine = "    invoke-static {" + threadReg + ", " + taintReg + "}, "
                    + tool.getSetParamTaintInstr(1);
            Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
            linesToAdd = rangedInvoke.first;
            int newMaxRegs = rangedInvoke.second;

            // linesToAdd.add(line);
            // transformations.add("Added line");

            // if (getRegNumFromRef(taintReg) > 255) {
            //     linesToAdd.add("    # need to handle larger taintReg");
            // } else {
            //     if (getRegNumFromRef(threadReg) > 15) {
            //         linesToAdd.add("    invoke-static/range {" + threadReg + " .. " + threadReg + "}, " + tool.getGetParamTaintInstr(1));
            //     } else {
            //         linesToAdd.add("    invoke-static {" + threadReg + "}, " + tool.getGetParamTaintInstr(1));
            //     }
            //     linesToAdd.add("     " + tool.getMoveResultTaint() + " " + taintReg);
            // }

            return new Pair<>(linesToAdd, newMaxRegs);
        } else if (calledMethodInfo.signature().startsWith("Ljava/util/Formatter;->format")) {
            String newLine = "    invoke-static {" + threadReg + ", " + taintRegMap.get(passedRegs[0]) + "}, "
                    + tool.getSetParamTaintInstr(1);
            Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
            linesToAdd = rangedInvoke.first;
            int newMaxRegs = rangedInvoke.second;
            if (tool instanceof ViaLinTool) {
                linesToAdd.add("    const v" + taintTempReg + ", " + methodDelta);
                newLine = "    invoke-static {" + signatureRegister + ",v" + taintTempReg
                        + "}, Ljava/lang/Thread;->setSite(Ljava/lang/String;I)V";
                rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                linesToAdd.addAll(rangedInvoke.first);
                newMaxRegs = (newMaxRegs > rangedInvoke.second) ? newMaxRegs : rangedInvoke.second;
                newLine = "    invoke-virtual {" + passedRegs[0] + "}, " + tool.formatterAddTaint();
                rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                linesToAdd.addAll(rangedInvoke.first);
                newMaxRegs = (newMaxRegs > rangedInvoke.second) ? newMaxRegs : rangedInvoke.second;
            }

            return new Pair<>(linesToAdd, newMaxRegs);
        } else if (calledMethodInfo.signature().startsWith("Ljava/lang/StringBuffer;->toString()Ljava/lang/String;") ||
                   calledMethodInfo.signature().startsWith("Ljava/io/ByteArrayOutputStream;->toByteArray()[B")) {
            if (tool instanceof ViaLinTool) {
                if (getRegNumFromRef(returnTaintReg) > 255) {
                    linesToAdd.add("    move-object/16 v" + taintTempReg + ", " + passedRegs[0]);
                }
                String newLine = "    invoke-static {" + returnTaintReg + "}, Ljava/lang/Thread;->getTaintContainer(Ljava/lang/PathTaint;)Ljava/lang/PathTaint;";
                Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                linesToAdd.addAll(rangedInvoke.first);
                int newMaxRegs = rangedInvoke.second;
                if (getRegNumFromRef(returnTaintReg) > 255) {
                    linesToAdd.add("    " + tool.getMoveResultTaint() + " " + passedRegs[0]);
                    linesToAdd.add("    " + tool.getMoveTaint() + "/16 " + returnTaintReg + ", " + passedRegs[0]);
                    linesToAdd.add("    move-object/16 " + passedRegs[0] + ", v" + taintTempReg);
                } else {
                    linesToAdd.add("    " + tool.getMoveResultTaint() + " " + returnTaintReg);
                }
                return new Pair<>(linesToAdd, newMaxRegs);
            }
        }

        if (calledMethodClass.equals("Landroid/content/ContentResolver;")) {
            // System.out.println(" Content provider class: " + calledMethodClass);
            if (calledMethodName.startsWith("insert")) {
                // System.out.println(" Content provider method: " + calledMethodName);
                for (int i = taintedClassLines.size() - 1; i >= 0; i--) {
                    String lineBefore = taintedClassLines.get(i);
                    // System.out.println(" Checking line: " + lineBefore);
                    if (lineBefore.contains("getContentResolver()Landroid/content/ContentResolver;")) {
                        String contextParam = TaintAnalysis.parsePassedRegs(lineBefore)[0];
                        // System.out.printf("ContextParam: at line %s ---> %s%n", lineBefore,
                        // contextParam);

                        if (taintTempReg < 255) {
                            String label = methodDelta + "_" + linesToAdd.size();
                            String startLabel = "    :try_start_taint_context_" + label;
                            String endLabel = "    :try_end_taint_context_" + label;
                            String handlerLabel = "    :catch_taint_context_" + label;

                            linesToAdd.add(startLabel);

                            linesToAdd.add("    move-object/16 v" + taintTempReg + ", " + contextParam);
                            linesToAdd.add("    check-cast v" + taintTempReg + ", Landroid/content/Context;");

                            linesToAdd.add("    invoke-virtual/range {v" + taintTempReg + " .. v" + taintTempReg
                                    + "}, Landroid/content/Context;->getFilesDir()Ljava/io/File;");
                            linesToAdd.add("    move-result-object v" + taintTempReg);

                            String newLine = "    invoke-static {v" + taintTempReg + "," + taintRegMap.get(passedRegs[0])
                                    + "}, " + tool.addFileTaint();
                            Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                            linesToAdd.addAll(rangedInvoke.first);
                            int newMaxRegs = rangedInvoke.second;

                            linesToAdd.add(endLabel);
                            linesToAdd.add("    .catchall {" + startLabel + " .. " + endLabel + "} " + handlerLabel);

                            linesToAdd.add(handlerLabel);
                            return new Pair<>(linesToAdd, newMaxRegs);
                        }
                    } else if (lineBefore.startsWith(".method")) {
                        break;
                    }
                }
            }

            // use the sqlitequerybuilder as an example for reading getfiletaint
        } else if (calledMethodClass.equals("Landroid/database/sqlite/SQLiteQueryBuilder;")) {
            if (calledMethodName.startsWith("query")) {
                // System.out.println(" Content provider method: " + calledMethodName);
                for (int i = taintedClassLines.size() - 1; i >= 0; i--) {
                    String lineBefore = taintedClassLines.get(i);
                    // System.out.println(" Checking line: " + lineBefore);
                    if (lineBefore.contains("move-object/16") && lineBefore.contains(", p0")) {
                        String contextParam = TaintAnalysis.getRegReference(lineBefore, 1);
                        int newMaxRegs = addGetFileTaint(tool, passedRegs, taintRegMap, taintTempReg, linesToAdd,
                                lineBefore, contextParam, className);
                        return new Pair<>(linesToAdd, newMaxRegs);
                    } else if (lineBefore.startsWith(".method")) {
                        break;
                    }
                }
            }
        }

        // InputStream inputStream, byte[] buffer, int offset, int len
        if (calledMethodClass.equals("Ljava/io/InputStream;") || calledMethodClass.equals("Ljava/io/FileInputStream;")) {
            // Khaled: diabled in main branch due to crash, may crash framework
            if (calledMethodName.startsWith("read")) {
                // save passedRegs[0] (we have to use passedRegs[0] because invoke-... only allows registers between 0-15
                // we know that passedRegs[0] must be 0-15 because it's used previously in static calls
                if(passedRegs.length >= 2){
                    String label = methodDelta + "_" + linesToAdd.size();
                    String startLabel = ":try_start_taint_" + label;
                    String endLabel = ":try_end_taint_" + label;
                    String handlerLabel = ":catch_taint_" + label;

                    // save passedRegs[0] for use in invoke-... calls
                    linesToAdd.add("    move-object/16 v" + String.valueOf(taintTempReg) + ", " + passedRegs[0]);
                    linesToAdd.add(startLabel);

                    String bufferTaintReg = taintRegMap.get(passedRegs[1]);

                    // getFileTaint(inputStream.getFileInputStreamTaintFilePath())
                    linesToAdd.add("    check-cast " + passedRegs[0] + ", Ljava/io/FileInputStream;");
                    linesToAdd.add("    invoke-virtual {" + passedRegs[0] + "}, Ljava/io/FileInputStream;->getFileInputStreamTaintFilePath()Ljava/io/File;");
                    linesToAdd.add("    move-result-object " + passedRegs[0]);
                    linesToAdd.add("    invoke-static {" + passedRegs[0] +  "}, " + tool.getFileTaint());


                    // set the taint to the first param, because we're reading into the buffer `read(byte b[], int off, int len)`
                    // not receiverRegTaint, which is the taint register of the instance variable the function is being called on (paramTaint0)
                    linesToAdd.add("    move-result-object " + bufferTaintReg);

                    // linesToAdd.add("    move-result-object " + passedRegs[0]);
                    // linesToAdd.add("    move-object/16 " + receiverRegTaint + ", " + passedRegs[0]);
                    // linesToAdd.add("    move-object/16 " + bufferTaintReg + ", " + passedRegs[0]);

                    // linesToAdd.add("    invoke-static {" + threadReg + ", " + passedRegs[0] + "}, " + tool.getSetParamTaintInstr(0));
                    // linesToAdd.add("    invoke-static {" + threadReg + ", " + passedRegs[0] + "}, " + tool.getSetParamTaintInstr(1));

                    linesToAdd.add(endLabel);
                    linesToAdd.add(".catchall {" + startLabel + " .. " + endLabel + "} " + handlerLabel);

                    linesToAdd.add(handlerLabel);

                    // restore passedRegs[0]
                    linesToAdd.add("    move-object/16 "  + passedRegs[0] + ", v" + String.valueOf(taintTempReg) );


                    return new Pair<>(linesToAdd, taintTempReg + 1);
                }

            }
        }

        if (calledMethodClass.equals("Ljava/io/OutputStream;") || calledMethodClass.equals("Ljava/io/FileOutputStream;")) {
            if (calledMethodName.startsWith("write(I)")) {
                if(passedRegs.length >= 2){
                    String label = methodDelta + "_" + linesToAdd.size();
                    String startLabel = "    :try_start_taint_" + label;
                    String endLabel = "    :try_end_taint_" + label;
                    String handlerLabel = "    :catch_taint_" + label;


                    // save passedRegs[0] for use in invoke-... calls
                    linesToAdd.add("    move-object/16 v" + String.valueOf(taintTempReg) + ", " + passedRegs[0]);

                    linesToAdd.add(startLabel);

                    // get the File object corresponding to the FileOutputStream and move it into passedRegs[0]
                    linesToAdd.add("    check-cast " + passedRegs[0] + ", Ljava/io/FileOutputStream;");
                    linesToAdd.add("    invoke-virtual {" + passedRegs[0] + "}, Ljava/io/FileOutputStream;->getFileOutputStreamTaintFile()Ljava/io/File;");
                    linesToAdd.add("    move-result-object " + passedRegs[0]);

                    // add the buffer's taint to the file object w/ addFileTaint(Ljava/io/File;Ljava/lang/PathTaint;)
                    String newLine = "    invoke-static {" + passedRegs[0] + ", " + taintRegMap.get(passedRegs[1]) + "}, " + tool.addFileTaint();
                    Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);
                    int newMaxRegs = rangedInvoke.second;

                    linesToAdd.add(endLabel);
                    linesToAdd.add(".catchall {" + startLabel + " .. " + endLabel + "} " + handlerLabel);

                    linesToAdd.add(handlerLabel);
                    // restore passedRegs[0]
                    linesToAdd.add("    move-object/16 "  + passedRegs[0] + ", v" + String.valueOf(taintTempReg) );

                    return new Pair<>(linesToAdd, Math.max(newMaxRegs, taintTempReg + 1));
                }
            } else if (calledMethodName.startsWith("write")) {
                if(passedRegs.length >= 2){
                    String label = methodDelta + "_" + linesToAdd.size();
                    String startLabel = "    :try_start_taint_" + label;
                    String endLabel = "    :try_end_taint_" + label;
                    String handlerLabel = "    :catch_taint_" + label;


                    // save passedRegs[0] for use in invoke-... calls
                    linesToAdd.add("    move-object/16 v" + String.valueOf(taintTempReg) + ", " + passedRegs[0]);

                    linesToAdd.add(startLabel);

                    // get the File object corresponding to the FileOutputStream and move it into passedRegs[0]
                    linesToAdd.add("    check-cast " + passedRegs[0] + ", Ljava/io/FileOutputStream;");
                    linesToAdd.add("    invoke-virtual {" + passedRegs[0] + "}, Ljava/io/FileOutputStream;->getFileOutputStreamTaintFile()Ljava/io/File;");
                    linesToAdd.add("    move-result-object " + passedRegs[0]);

                    // add the buffer's taint to the file object w/ addFileTaint(Ljava/io/File;Ljava/lang/PathTaint;Ljava/lang/Object;)
                    String newLine = "    invoke-static {" + passedRegs[0] + ", " + taintRegMap.get(passedRegs[1]) + ", "
                            + passedRegs[1] + "}, " + tool.addFileTaintAndObject();
                    Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
                    linesToAdd.addAll(rangedInvoke.first);
                    int newMaxRegs = rangedInvoke.second;

                    linesToAdd.add(endLabel);
                    linesToAdd.add(".catchall {" + startLabel + " .. " + endLabel + "} " + handlerLabel);

                    linesToAdd.add(handlerLabel);
                    // restore passedRegs[0]
                    linesToAdd.add("    move-object/16 "  + passedRegs[0] + ", v" + String.valueOf(taintTempReg) );

                    return new Pair<>(linesToAdd, Math.max(newMaxRegs, taintTempReg + 1));
                }

            }
        }

        return new Pair<>(linesToAdd, 0);
    }


    // Updates the .taint file corresponding to a SharedPreferences store
    // Restores the original registers (Editor editor, String key, PathTaint fileTaint, Object object)
    // Appends lines to linesToAdd and returns the new taintTempReg
    private static int addSharedPreferencesTaint(TaintTool tool, List<String> linesToAdd, Map<String, String> taintRegMap, String[] passedRegs, int taintTempReg, boolean isPrimitive){
        // TODO: add the non-object (primitive) case

        // save passedRegs[0]
        linesToAdd.add("    move-object/16 v" + String.valueOf(taintTempReg) + ", " + passedRegs[0]);

        // we added getSharedPreferencesTaintFilePath to the impl file, so we must cast the Editor to EditorImpl first
        linesToAdd.add("    check-cast " + passedRegs[0] + ", Landroid/app/SharedPreferencesImpl$EditorImpl;");
        linesToAdd.add("    invoke-virtual {" + passedRegs[0] + "}, Landroid/app/SharedPreferencesImpl$EditorImpl;->getSharedPreferencesTaintFilePath()Ljava/io/File;");

        linesToAdd.add("    move-result-object " + passedRegs[0]);

        // we want to save the taint of the object-to-write, not of the SharedPreferencesImpl$Editor
        // so, the taint to save should be taintRegMap.get(passedRegs[2]) instead of receiverRegTaint

        String newLine = "    invoke-static {" + passedRegs[0] + ", " + taintRegMap.get(passedRegs[2]) + ", " + passedRegs[2] + "}, " + tool.addFileTaintAndObject();
        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
        linesToAdd.addAll(rangedInvoke.first);
        int newMaxRegs = rangedInvoke.second;

        // restore passedRegs[0]
        linesToAdd.add("    move-object/16 "  + passedRegs[0] + ", v" + String.valueOf(taintTempReg) );

        return Math.max(newMaxRegs, taintTempReg + 1);
    }



    private static int addGetFileTaint(TaintTool tool, String[] passedRegs, Map<String, String> taintRegMap, int taintTempReg,
            List<String> linesToAdd, String lineBefore, String contextParam, String className) {
        // System.out.printf("ContextParam: at line %s ---> %s%n", lineBefore, contextParam);
        if (getRegNumFromRef(contextParam) > 15) {
            linesToAdd.add("    invoke-virtual/range {" + contextParam + " .. " + contextParam + "}, " + className + "->getContext()Landroid/content/Context;");
        } else {
            linesToAdd.add("    invoke-virtual {" + contextParam + "}, " + className + "->getContext()Landroid/content/Context;");
        }


        linesToAdd.add("    move-result-object v" + taintTempReg);
        linesToAdd.add("    invoke-virtual/range {v" + taintTempReg + " .. v" + taintTempReg + "}, Landroid/content/Context;->getFilesDir()Ljava/io/File;");
        linesToAdd.add("    move-result-object v" + taintTempReg);

        String taintTargReg = taintRegMap.get(passedRegs[0]);
        String newLine = "    invoke-static {v" + taintTempReg + "}, " + tool.getFileTaint();;
        Pair<List<String>, Integer> rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
        linesToAdd.addAll(rangedInvoke.first);
        int newMaxRegs = rangedInvoke.second;

        if (getRegNumFromRef(taintTargReg) > 255) {
            linesToAdd.add("    move-result-object " + passedRegs[0]);
            linesToAdd.add("    move-object/16 " + taintTargReg + ", " + passedRegs[0]);
            linesToAdd.add("    move-object/16 " + passedRegs[0] + ", v" + taintTempReg);
        } else {
            linesToAdd.add("    move-result-object " + taintTargReg);
        }

        newLine = "    invoke-static {" + taintTargReg + "}, " + tool.getSetReturnTaintInstr();
        rangedInvoke = makeInvokeToRange(newLine, taintTempReg);
        linesToAdd.addAll(rangedInvoke.first);
        newMaxRegs = rangedInvoke.second;
        return newMaxRegs;
    }
}
