package java.lang;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.IllegalAccessException;

import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class TaintDroid {

    public static Map<String, Integer> statementIndexMap = new HashMap<>();
    public static AtomicInteger topStatementIndex = new AtomicInteger(0);


    public static void endTime(String str, long time) {
        long endTime = System.nanoTime();
        long timeDiff = endTime - time;
        System.out.format("MethodTiming: %s: %s%n", str, timeDiff);
    }

    public static void printSourceFound(String src) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        System.out.format("TaintDroid: SourceFound: %s->%s(%s), %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), src);
    }

    public static void printMethodName(String name) {
        System.out.println("MethodName: " + name);
    }

    public static void printFieldNameGet(String name) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        System.out.format("FieldGet: in method %s->%s(%s), FieldGet for %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), name);
    }

    public static void printFieldNameSet(String name) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        System.out.format("FieldSet: in method %s->%s(%s), FieldSet for %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), name);
    }

    public static void printSinkFound(String sink) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        System.out.format("TaintDroid: SinkFound: %s->%s(%s), %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), sink);
    }



    public static void dumpTaint(int taintDroid) {
        if (taintDroid != 0) {
            System.out.format("DumpTaint: %s%n", taintDroid);
        }
    }

    public static void dumpTaint(int taintDroid, Object object) {
        dumpTaint(taintDroid);
        if (object != null) {
            dumpObjectLoop(object);
        }
    }


    private static void dumpObjectLoop(Object object) {
        Set<Object> visited = new HashSet<>();
        Stack<Object> stack = new Stack<>();
        // Stack<String> accessPath = new Stack<>();
        stack.push(object);
        // accessPath.push(object.getClass().getName());
        int counter = -1;
        while(!stack.isEmpty()) {
            Object next = stack.pop();
            // String currentPath = accessPath.pop();
            if (next == null) {
                continue;
            }
            try {
                if(visited.contains(next)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            counter++;
            visited.add(next);
            try {
                // System.out.println("Class-" + counter + ": " + next.getClass().toString());
                for (Field field : next.getClass().getDeclaredFields()) {
                    processField("DeclaredField", stack, counter, next, field);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void processField(String type, Stack<Object> stack, int counter, Object next, Field field) throws Exception {
        // System.out.println("    " + type + ": " + field.toString());
        field.setAccessible(true);
        if (field.getName().endsWith("_taintdroid")) {
            int taintDroid = (Integer) field.get(next);
            if (taintDroid != 0) {
                // currentPath = currentPath + "/" + field.getName();
                // System.out.println("DumpTaint: FieldAccessPath: " + currentPath);
                dumpTaint(taintDroid);
            }
        } else {
            // System.out.println("FieldInObject-" + counter +": " + field.toString());
            Object value = field.get(next);
            if (value != null) {
                stack.push(value);
                // currentPath = currentPath + "/" + value.getClass().getName();
                // currentPath = value.getClass().getName();
                // accessPath.push(currentPath);
            }
        }
    }



    public static int fileTaint(int taint) {
        return taint;
    }

    public static int fileTaint(int taint, Object object) {
        if (object != null) {
            taint |= extractTaint(object);
        }
        return taint;
    }

    public static void addSerializedTaint(int[] container, int objectTaint, Object object) {
        int taint = TaintDroid.fileTaint(objectTaint, object);
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.format("PathTaint: addSerializedTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);
        container[0] |= taint;
    }

    public static void addSerializedTaint(int[] container, int objectTaint) {
        int taint = TaintDroid.fileTaint(objectTaint);
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.format("PathTaint: addSerializedTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);
        container[0] |= taint;
    }

    public static int getSerializedTaint(int[] container) {
        return container[0];
    }


    public static void addFileTaint(File file, int fileTaint, Object object) {
        String taintFileName = file.getAbsolutePath() + ".taint";
        int extra = 0;
        try {
            extra = Integer.valueOf(new String(Files.readAllBytes(Paths.get(taintFileName))));
        } catch (IOException e) {
            e.printStackTrace();
        }
        int taint = extra | TaintDroid.fileTaint(fileTaint, object);
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.format("PathTaint: addFileTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);

        if (taint !=0) {
            try {
                FileWriter fw = new FileWriter(taintFileName, false);
                fw.write(taint);
                fw.close();
                // System.out.format("PathTaint: addFileTaint: file writted %n");
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public static void addFileTaint(File file, int fileTaint) {
        String taintFileName = file.getAbsolutePath() + ".taint";
        int extra = 0;
        try {
            extra = Integer.valueOf(new String(Files.readAllBytes(Paths.get(taintFileName))));
        } catch (IOException e) {
            e.printStackTrace();
        }
        int taint = extra | fileTaint;
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.format("PathTaint: addFileTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);
        if (taint !=0) {
            try {
                FileWriter fw = new FileWriter(taintFileName, false);
                fw.write(taint);
                fw.close();
                // System.out.format("PathTaint: addFileTaint: file writted %n");
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public static int getFileTaint(File file) {
        String taintFileName = file.getAbsolutePath() + ".taint";
        int taint = 0;
        try {
            taint = Integer.valueOf(new String(Files.readAllBytes(Paths.get(taintFileName))));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return taint;
    }



    public static int extractTaint(Object object) {
        Set<Object> visited = new HashSet<>();
        Stack<Object> stack = new Stack<>();
        // Stack<String> accessPath = new Stack<>();
        stack.push(object);
        // accessPath.push(object.getClass().getName());
        int counter = -1;
        int taint = 0;
        while(!stack.isEmpty()) {
            Object next = stack.pop();
            // String currentPath = accessPath.pop();
            if (next == null) {
                continue;
            }
            try {
                if(visited.contains(next)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            counter++;
            visited.add(next);
            try {
                // System.out.println("Class-" + counter + ": " + next.getClass().toString());
                for (Field field : next.getClass().getDeclaredFields()) {
                    taint |= extractTaintField("DeclaredField", stack, counter, next, field);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return taint;
    }

    private static int extractTaintField(String type, Stack<Object> stack, int counter, Object next, Field field) throws Exception {
        // System.out.println("    " + type + ": " + field.toString());
        int taint = 0;
        field.setAccessible(true);
        if (field.getType().equals(PathTaint.class)) {
            return taint;
        } else if (field.getName().endsWith("_taintdroid")) {
            taint |= (Integer) field.get(next);
        } else {
            Object value = field.get(next);
            if (value != null) {
                stack.push(value);
            }
        }
        return taint;
    }

}