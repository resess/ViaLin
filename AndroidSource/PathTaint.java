package java.lang;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.lang.reflect.Field;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.IllegalAccessException;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.ArrayDeque;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.lang.reflect.Method;


public class PathTaint {
    public PathTaint left;
    public PathTaint right;
    public String site;
    public int delta;
    public long timeStamp;

    // For user feedback tracking
    public String sinkId;

    // public static AtomicInteger taintNum = new AtomicInteger(0);
    // public static final AtomicBoolean lock = new AtomicBoolean(false);
    // static int openTaints = 0;
    // static List<String> whereOpened = new ArrayList<>();

    private static final Boolean trueBoolean = new Boolean(true);
    private static final int MAX_ENTRIES = 100*1024; //100*1024;

    private static Map<String, Boolean> dumpCache = Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(MAX_ENTRIES+1, .75F, true) {
        // This method is called just after a new entry has been added
        @Override
        public boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_ENTRIES;
        }
    });

    private static Map<Integer, Integer> viewCache = Collections.synchronizedMap(new LinkedHashMap<Integer, Integer>(MAX_ENTRIES+1, .75F, true) {
        // This method is called just after a new entry has been added
        @Override
        public boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
            return size() > MAX_ENTRIES;
        }
    });

    private static Map<Integer, Long> pathTaintCache = Collections.synchronizedMap(new LinkedHashMap<Integer, Long>(MAX_ENTRIES+1, .75F, true) {
        // This method is called just after a new entry has been added
        @Override
        public boolean removeEldestEntry(Map.Entry<Integer, Long> eldest) {
            return size() > MAX_ENTRIES;
        }
    });

    private static String packageName = null;



    static Set<Integer> taintCache = new HashSet<>();

    static long sizeCollectedBytes = 0;
    static long numCollected = 0;
    static long sizeAlloc = 0;
    static long numAlloc = 0;

    public static class TaintDump extends Thread {

        public List<Object> args;
        public String toPrint = null;
        public int num;
        public Set<Integer> visitedTaints;
        public Set<Object> visitedObjects;
        public boolean tainted = false;

        public TaintDump(List<Object> args, int taintNum, String toPrint) {
            this.args = new ArrayList<>(args);
            this.num = taintNum;
            this.toPrint = toPrint;
            this.visitedTaints = new HashSet<>(1024);
            this.visitedObjects = new HashSet<>(1024);
        }

        @Override
        public void run() {

            long startTime = Thread.getNativeCurrentTime();

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            DeflaterOutputStream dos = new DeflaterOutputStream(os);

            // synchronized (pathTaintCache) {
                for (Object arg: args) {
                    if (arg instanceof PathTaint) {
                        PathTaint pathTaint = (PathTaint) arg;
                        if (pathTaint != null) {
                            boolean tempTainted = PathTaint.dumpTraverseLoop(pathTaint, num, visitedTaints, os, dos);
                            if (tempTainted) {
                                tainted = true;
                            }
                        }
                    } else {
                        if (arg != null) {
                            printObject(arg);
                            boolean tempTainted = PathTaint.dumpObjectLoop(arg, num, visitedTaints, visitedObjects, os, dos);
                            if (tempTainted) {
                                tainted = true;
                            }
                        }
                    }
                }

                if (toPrint != null && tainted == true) {
                    // if (packageName != null) {
                    //     try {
                    //         OutputStream out = new BufferedOutputStream(new FileOutputStream("/data/data/" + packageName + "/dumpTaint-" + num + ".txt"));
                    //         out.write((toPrint + "\n").getBytes());
                    //         out.flush();
                    //     } catch (Exception e) {
                    //         e.printStackTrace();
                    //     }
                    // }
                    System.out.println(toPrint);
                }

                if (tainted) {
                    long endTime = Thread.getNativeCurrentTime();
                    long elapsed = endTime - startTime;
                    System.out.println("DumpTaintTime: " + elapsed);
                }
            // }
        }

        private void printObject(Object obj) {
            if (obj == null) {
                return;
            }
            try {
                System.out.println("PathTaint: Sink param: " + obj.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    public static PathTaint newInstance() {
            return new PathTaint();
    }

    public static void endTime(String str, long time) {
        long endTime = System.nanoTime();
        long timeDiff = endTime - time;
        System.out.format("MethodTiming: %s: %s%n", str, timeDiff);
    }

    public static void printSourceFound(String src) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        System.out.format("PathTaint: SourceFound: %s->%s(%s), %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), src);
    }

    public static void printMethodName(String name) {
        try {
            String threadName = Thread.currentThread().getName();
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String str = String.format("StackTrace: T(%s), of %s", threadName, name);
            StringBuilder sb = new StringBuilder(str);
            for (StackTraceElement ste : stackTrace) {
                sb.append("\n");
                sb.append(String.format("T(%s): %s", threadName, ste.toString()));
            }
            System.out.println(sb.toString());
        } catch(Exception e) {}
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
        System.out.format("PathTaint: SinkFound: %s->%s(%s), %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), sink);
        try {
            StackTraceElement [] stackTraceElements = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder("PathTaint: sink stacktrace " + ste.getClassName() + "->" + ste.getMethodName() + "(" + ste.getLineNumber() + ")" + " \n");
            for (StackTraceElement elem : stackTraceElements) {
                sb.append("    ");
                sb.append(elem.toString());
                sb.append("\n");
            }
            System.out.println(sb.toString());
        } catch (Exception e) {
            System.out.println("PathTaint: sink stacktrace failed");
            e.printStackTrace();
        }
    }

    public static void printSinkFound(String signature, String sink, int delta) {
        System.out.format("PathTaint: SinkFound: %s(%s), %s%n", signature, delta, sink);
        try {
            StackTraceElement [] stackTraceElements = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder("PathTaint: sink stacktrace " + signature + "\n");
            for (StackTraceElement elem : stackTraceElements) {
                sb.append("    ");
                sb.append(elem.toString());
                sb.append("\n");
            }
            System.out.println(sb.toString());
        } catch (Exception e) {
            System.out.println("PathTaint: sink stacktrace failed");
            e.printStackTrace();
        }
    }

    public static PathTaint addTaintSource(PathTaint pathTaint, String site, int delta) {

        if (packageName == null) {
            try {
                Class<?> clazz = Class.forName("android.app.ActivityThread");
                Method method  = clazz.getDeclaredMethod("currentPackageName", null);
                packageName = (String) method.invoke(clazz, null);
            } catch (Exception e) {
                System.out.println("PathTaint: could not get package name");
                e.printStackTrace();
                packageName = null;
            }
        }

        PathTaint newTaint = new PathTaint();
        newTaint.site = site;
        newTaint.delta = delta;
        if (pathTaint != null) {
            newTaint.left = pathTaint.left;
            newTaint.right = pathTaint.right;
        }
        newTaint.timeStamp = System.nanoTime();
        System.out.format("PathTaint: SourceFound: %s(%s)id(%s)%n", newTaint.site, newTaint.delta, newTaint.timeStamp);
        // printMethodName(String.valueOf(newTaint.timeStamp));
        try {
            StackTraceElement [] stackTraceElements = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder("PathTaint: source stacktrace " + newTaint.site + "(" + newTaint.delta + ")id(" + newTaint.timeStamp + ")\n");
            for (StackTraceElement elem : stackTraceElements) {
                sb.append("    ");
                sb.append(elem.toString());
                sb.append("\n");
            }
            System.out.println(sb.toString());
        } catch (Exception e) {
            System.out.println("PathTaint: source stacktrace failed");
            e.printStackTrace();
        }
        return newTaint;
    }

    public static PathTaint propagateOneArg(PathTaint other, String site, int delta) {
        // try {
        //     if (other.site == null) {
        //         StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //         System.out.format("PathTaint: in method %s->%s(%s), trying to propagate-one from null left %s(%s)%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), site, delta);
        //     }
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }

        PathTaint newTaint = new PathTaint();
        newTaint.site = site;
        newTaint.delta = delta;
        newTaint.left = other;
        newTaint.timeStamp = other.timeStamp + 1;
        newTaint.sinkId = other.sinkId;
        // if (newTaint.sinkId != null) {
        //     System.out.format("PathTaint: propArg after sink %s <-- %s%n", newTaint, other);    
        // }
        // if (!newTaint.site.startsWith("Landroid")) {
            // System.out.format("PathTaint: propArg %s <-- %s%n", newTaint, other);
        // }
        return newTaint;
    }


    private static int updateSet(Set<Object> visitedObjects, Object next) {
        try {
            if (visitedObjects.size() > MAX_ENTRIES) {
                // System.out.println("PathTaint: max visited objects size reached, will not add more");
                return -1;
            }
            if (next != null) {
                visitedObjects.add(System.identityHashCode(next));
            }
            return 1;
        } catch (Throwable e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static int checkInSet(Set<Object> visitedObjects, Object next) {
        try {
            if (visitedObjects.size() > MAX_ENTRIES) {
                // System.out.println("PathTaint: max visited objects size reached, assume object is visited");
                return 1;
            }
            // System.out.println("PathTaint: checkInSet for object " + next.getClass().getName());
            if (visitedObjects.contains(System.identityHashCode(next))) {
                return 1;
            }
            return 0;
        } catch (Throwable e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static PathTaint propagateSinkReturn(PathTaint other,  String site, int delta) {
        // try {
        //     if (other.site == null) {
        //         StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //         System.out.format("PathTaint: in method %s->%s(%s), trying to propagate-one from null left %s(%s)%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), site, delta);
        //     }
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }
        // System.out.format("PathTaint: propSinkReturn is called%n");
        PathTaint right = null;
        ArrayList<Object> args = Thread.getTempObjects();
        Set<Object> visitedObjects = new HashSet<>();
        for (Object arg: args) {
            if (arg instanceof PathTaint) {
                PathTaint pathTaint = (PathTaint) arg;
                if (pathTaint != null) {
                    // System.out.format("PathTaint: propSinkReturn checking a taint object%n");
                    right = pathTaint;
                    break;
                }
            } else {
                if (arg != null) {
                    // System.out.format("PathTaint: propSinkReturn checking a regular object%n");
                    right = getInternalObjectTaint(arg, visitedObjects);
                    if (right != null) {
                        break;
                    }
                }
            }
        }
        
        if (right != null) {
            PathTaint newTaint = propagateTwoArgs(other, right, site, delta);
            // System.out.format("PathTaint: propSinkReturn %s <-- %s ^ %s %n", newTaint, other, right);
            return newTaint;
        }
        return null;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

    public void setSiteDelta(String site, int delta) {
        this.site = site;
        this.delta = delta;
    }


    public void setLeft(PathTaint left) {
        this.left = left;
    }

    public void setRight(PathTaint right) {
        this.right = right;
    }


    public static PathTaint propagateTwoArgs(PathTaint left, PathTaint right, String site, int delta) {
        // try {
        //     if (left != null && left.site == null) {
        //         StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //         System.out.format("PathTaint: in method %s->%s(%s), trying to propagate-two from null left %s(%s)%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), site, delta);
        //         left = null;
        //     }
        //     if (right != null && right.site == null) {
        //         StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //         System.out.format("PathTaint: in method %s->%s(%s), trying to propagate-two from null right %s(%s)%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), site, delta);
        //         right = null;
        //     }
        //     if (left == null && right == null) {
        //         return null;
        //     }
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }


        PathTaint newTaint = new PathTaint();
        newTaint.site = site;
        newTaint.delta = delta;
        newTaint.left = left;
        newTaint.right = right;
        if (left == null) {
            newTaint.timeStamp = right.timeStamp + 1;
            newTaint.sinkId = right.sinkId;
        } else if (right == null) {
            newTaint.timeStamp = left.timeStamp + 1;
            newTaint.sinkId = left.sinkId;
        } else {
            newTaint.timeStamp = Math.max(left.timeStamp, right.timeStamp) + 1;
            if (left.sinkId == null) {
                newTaint.sinkId = right.sinkId;
                // if (newTaint.sinkId != null) {
                //     System.out.format("PathTaint: propTwoArgs after sink (left was null) %s%n", newTaint.sinkId);
                // }
            } else {
                newTaint.sinkId = left.sinkId;
                // if (newTaint.sinkId != null) {
                //     System.out.format("PathTaint: propTwoArgs after sink (right was null) %s%n", newTaint.sinkId);
                // }
            }
        }
        // if (newTaint.sinkId != null) {
        //     System.out.format("PathTaint: propTwoArgs after sink %s <-- %s ^ %s %n", newTaint, left, right);
        // }
        // if (!newTaint.site.startsWith("Landroid")) {
            // System.out.format("PathTaint: propTwoArgs %s <-- %s ^ %s %n", newTaint, left, right);
        // }
        return newTaint;
    }

    public String toString() {
        return site + "(" + delta + ")id(" + timeStamp + ")";
    }

    public static void finishDumpTaint() {
        List<Object> args = Thread.currentThread().dumpTaintArgs;
        int num = ((new Random()).nextInt()) & Integer.MAX_VALUE;
        String toPrint = "DumpTaint for sink: " + num;
        TaintDump taintDump = new TaintDump(args, num, toPrint);
        args.clear();
        taintDump.start();
    }

    private static boolean dumpObjectLoop(Object object, int num, Set<Integer> visitedTaints, Set<Object> visitedObjects, ByteArrayOutputStream os, DeflaterOutputStream dos) {
        boolean tainted = false;
        Stack<Object> stack = new Stack<>();
        stack.push(object);
        while(!stack.isEmpty()) {
            Object next = stack.pop();
            if (next == null) {
                continue;
            }
            // Integer nextHash = System.identityHashCode(next);
            // try {
            //     if(visitedObjects.contains(nextHash)) {
            //         continue;
            //     }
            // } catch (Exception e) {
            //     continue;
            // }
            // visitedObjects.add(nextHash);

            try {
                int checkInSetResult = checkInSet(visitedObjects, next);
                if(checkInSetResult != 0) {
                    continue;
                }

                // Check if the object is an array
                if (next.getClass().isArray()) {
                    if (next instanceof char[]) {
                        // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + new String((char[]) next));
                    } else if (next instanceof byte[]) {
                        // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + Arrays.toString((byte[]) next));
                    } else if (next instanceof int[]) {
                        // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + Arrays.toString((int[]) next));
                    } else if (next instanceof long[]) {
                        // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + Arrays.toString((long[]) next));
                    } else if (next instanceof float[]) {
                        // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + Arrays.toString((float[]) next));
                    } else if (next instanceof double[]) {
                        // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + Arrays.toString((double[]) next));
                    } else if (next instanceof boolean[]) {
                        // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + Arrays.toString((boolean[]) next));
                    } else if (next instanceof short[]) {
                        // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + Arrays.toString((short[]) next));
                    } else if (next instanceof char[]) {
                        // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + Arrays.toString((char[]) next));
                    } else if (next instanceof Object[]) {
                        for (Object obj : (Object[]) next) {
                            stack.push(obj);
                        }
                    }
                } else {
                    // System.out.println("PathTaint: Sink field: " + next.getClass() + ": " + next.toString());
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            int updateSetResult = updateSet(visitedObjects, next);
            if (updateSetResult == -1) {
                continue;
            }


            // System.out.println("PathTaint: check class " + next.getClass());
            Class superClass = next.getClass().getSuperclass();
            if (superClass != null && superClass != Object.class) {
                // System.out.println("PathTaint: added super " + superClass);
                stack.push((superClass.cast(next)));
            }
            
            try {
                for (Field field : next.getClass().getDeclaredFields()) {
                    boolean tempTainted = processField("DeclaredField", stack, next, field, num, visitedTaints, visitedObjects, os, dos);
                    if (tempTainted) {
                        tainted = true;
                    }
                    // if (tainted) {
                    //     System.out.println("DumpTaint-field-" + num + ": Tainted, will check next field, remaining objects in stack are: " + stack.size());
                    // }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // if (tainted) {
            //     System.out.println("DumpTaint-field-" + num + ": Tainted, will check next object, remaining objects in stack are: " + stack.size());
            // }
        }
        return tainted;
    }

    private static boolean processField(String type, Stack<Object> stack, Object next, Field field, int num, Set<Integer> visitedTaints, Set<Object> visitedObjects, ByteArrayOutputStream os, DeflaterOutputStream dos) throws Exception {
        boolean tainted = false;
        field.setAccessible(true);
        // System.out.println("PathTaint: check field " + next.getClass().getName() + "." + field.getName());
        if (field.getType().equals(PathTaint.class)) {
            PathTaint pathTaint = (PathTaint) field.get(next);
            if (pathTaint != null) {
                String thisAccessPath = next.getClass().getName() + "." + field.getName();
                System.out.println("DumpTaint-field-" + num + ": FieldAccessPath: " + thisAccessPath);
                boolean tempTainted = dumpTraverseLoop(pathTaint, num, visitedTaints, os, dos);
                if (tempTainted) {
                    tainted = true;
                }
                // System.out.println("DumpTaint-field-" + num + ": Returned: " + thisAccessPath);
            } else {
                // String thisAccessPath = next.getClass().getName() + "." + field.getName();
                // System.out.println("DumpTaint-field-No-TAINT-" + num + ": FieldAccessPath: " + thisAccessPath);
            }
        } else {
            Object value = field.get(next);
            if (value != null) {
                if (!value.getClass().equals(Thread.class)) {
                    stack.push(value);
                }
            }
        }
        // if (tainted) {
        //     System.out.println("DumpTaint-field-" + num + ": Tainted, remaining objects in stack are: " + stack.size());
        // }
        return tainted;
    }

    private static PathTaint getInternalObjectTaint(Object object, Set<Object> visitedObjects) {
        Stack<Object> stack = new Stack<>();
        stack.push(object);
        while(!stack.isEmpty()) {
            Object next = stack.pop();
            if (next == null) {
                continue;
            }
           
            try {
                int checkInSetResult = checkInSet(visitedObjects, next);
                if(checkInSetResult != 0) {
                    continue;
                }

            } catch (Exception e) {
                continue;
            }
            int updateSetResult = updateSet(visitedObjects, next);
            if (updateSetResult == -1) {
                continue;
            }

            // System.out.println("PathTaint: getInternalObjectTaint check class " + next.getClass());
            Class superClass = next.getClass().getSuperclass();
            if (superClass != null && superClass != Object.class) {
                // System.out.println("PathTaint: added super " + superClass);
                stack.push((superClass.cast(next)));
            }
            
            try {
                for (Field field : next.getClass().getDeclaredFields()) {
                    PathTaint taint = getInternalFieldTaint(stack, next, field);
                    if (taint != null) {
                        return taint;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static PathTaint getInternalFieldTaint(Stack<Object> stack, Object next, Field field) throws Exception {
        field.setAccessible(true);
        // System.out.println("PathTaint: check field " + next.getClass().getName() + "." + field.getName());
        if (field.getType().equals(PathTaint.class)) {
            PathTaint pathTaint = (PathTaint) field.get(next);
            if (pathTaint != null) {
                return pathTaint;
            }
        } else {
            Object value = field.get(next);
            if (value != null) {
                stack.push(value);
            }
        }
        return null;
    }


    // Fastest version so far
    public static boolean dumpTraverseLoop(PathTaint pathTaint, int num, Set<Integer> visitedTaints, ByteArrayOutputStream os, DeflaterOutputStream dos) {
        
        if (packageName != null) {
            System.out.println("PathTaint: packageName is " + packageName);
        } else {
            System.out.println("PathTaint: packageName is null");
        }

        String header = "DumpTaint-"+ num + ": ->";
        boolean tainted = false;
        Deque<PathTaint> stack = new ArrayDeque<>(1024);
        int cacheHit = 0;
        int localCacheHit = 0;
        int cacheMiss = 0;
        int localCacheMiss = 0;
        Set<Integer> visitedCacheless = new HashSet<>();
        boolean fastMode = false;

        // OutputStream out = new BufferedOutputStream (System.out);
        OutputStream out;
        if (packageName != null) {
            try {
                out = new BufferedOutputStream(new FileOutputStream("/data/data/" + packageName + "/dumpTaint-" + num + ".txt", true));
            } catch (Exception e) {
                out = new BufferedOutputStream(System.out);
            }
        } else {
            out = new BufferedOutputStream(System.out);
        }

        // PrintWriter pout = null;
        // try {
        //     pout = new PrintWriter(System.out, false);
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }
        

        // Get output directory by reflection

        int numIters = 0;
        stack.push(pathTaint);
        while (!stack.isEmpty()) {

            PathTaint next = stack.pop();

            // if (numIters > 10 * 1024) {
            //     System.out.println("PathTaint-debug-" + num + ": Number of iterations is large " + numIters);
            //     // stack.clear();
            // }

            // if (numIters > 10 * 1024) {
            //     // stack.clear();
            //     if (cacheHit > 0) {
            //         // System.out.println("PathTaint-debug-" + num + ": Swiching to cacheless mode, numIters = " + numIters);
            //         // break;
            //     }
            //     // fastMode = true;
            // }

            int hashCode = System.identityHashCode(next);

            boolean inCache = false;
            // boolean isFramework = true;
            String nextSite = next.site;


            if (pathTaintCache.containsKey(hashCode) && pathTaintCache.get(hashCode).equals(next.timeStamp)) {
                cacheHit += 1;
                continue;
            }
            pathTaintCache.put(hashCode, next.timeStamp);
            cacheMiss += 1;

            if (pathTaintCache.size() == MAX_ENTRIES) { // for non-cold cache
                if (cacheHit < 5) {
                    System.out.println("PathTaint-debug-" + num + ": Cache is useless, erasing it");
                    pathTaintCache.clear();
                }
            }
            
            int leftHashCode = 0;
            int rightHashCode = 0;

            PathTaint left = next.left;
            if (left != null) {
                String leftSite = left.site;
                leftHashCode = System.identityHashCode(left);
                // if (!inFramework(leftSite))
                // {
                //     isFramework = false;
                // }
            }
            PathTaint right = next.right;
            if (right != null) {
                String rightSite = right.site;
                rightHashCode = System.identityHashCode(right);
                // if (!inFramework(rightSite))
                // {
                //     isFramework = false;
                // }
            }
            try {
                if (/*!isFramework && */ !fastMode) {
                    // if (next.sinkId != null) {
                    //    if (!next.sinkId.contains(String.valueOf(next.timeStamp))) {
                    //        System.out.println("This flow already passed through the SinkID: " + next.sinkId);
                    //        System.out.println(header + "Cancelled by SinkID: " + next.sinkId);
                    //        continue;
                    //    }
                    // }
                    //  else {
                    //     System.out.println("SinkID: null");
                    // }
                    StringBuilder sb = new StringBuilder();
                    sb.append(nextSite);
                    sb.append("(");
                    sb.append(next.delta);
                    sb.append(")id(");
                    sb.append(next.timeStamp);
                    sb.append(")");
                    if (left != null) {
                        String leftSite = left.site;
                        sb.append("->left->");
                        sb.append(leftSite);
                        sb.append("(");
                        sb.append(left.delta);
                        sb.append(")id(");
                        sb.append(left.timeStamp);
                        sb.append(")");
                    } else {
                        sb.append("->left->STARTPATH(");
                        sb.append(next.timeStamp);
                        sb.append(")");
                    }
                    if (right != null) {
                        String rightSite = right.site;
                        sb.append("->right->");
                        sb.append(rightSite);
                        sb.append("(");
                        sb.append(right.delta);
                        sb.append(")id(");
                        sb.append(right.timeStamp);
                        sb.append(")");
                    }
                    String dumpString = sb.toString();
                    // System.out.println(header + dumpString);
                    out.write((header + dumpString + "\n").getBytes());
                    // pout.println(header + dumpString);
                    numIters += 1;
                } else if (/* !isFramework && */ fastMode) {
                    if (left == null) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(nextSite);
                        sb.append("(");
                        sb.append(next.delta);
                        sb.append(")id(");
                        sb.append(next.timeStamp);
                        sb.append(")");
                        sb.append("->left->STARTPATH(");
                        sb.append(next.timeStamp);
                        sb.append(")");
                        String dumpString = sb.toString();
                        // System.out.println(header + dumpString);
                        out.write((header + dumpString + "\n").getBytes());
                        // pout.println(header + dumpString);
                        numIters += 1;
                    }
                }

                if (!inCache) {
                    if (left != null) {
                        // if (!visitedTaints.contains(leftHashCode)) {
                        if (!stack.contains(left)) {
                            // stack.push(left);
                            stack.addLast(left);
                        }
                        // }
                    }
                    if (right != null) {
                        // if (!visitedTaints.contains(rightHashCode)) {
                        if (!stack.contains(right)) {
                            // stack.push(right);
                            stack.addLast(right);
                        }
                        // }
                    }
                }


            } catch (Exception e) {
                e.printStackTrace();
            }

            tainted = true;
        }
        System.out.println("PathTaint-debug-" + num + ": Number of iterations is " + numIters);
        System.out.println("PathTaint-debug-" + num + ": DumpCache size is " + pathTaintCache.size());
        System.out.println("PathTaint-debug-" + num + ": DumpCache cache hits are " + cacheHit);
        System.out.println("PathTaint-debug-" + num + ": DumpCache cache misses are " + cacheMiss);

        try {
            out.flush();
        } catch (Exception e) {
            System.out.println("PathTaint-debug-" + num + ": Could not print the graph!");
            e.printStackTrace();
        }
        // pout.flush();

        // synchronized (pathTaintCache) {
            if (pathTaintCache.size() == MAX_ENTRIES) { // for non-cold cache
                if (cacheHit < 5) {
                    System.out.println("PathTaint-debug-" + num + ": Cache is useless, erasing it at the end");
                    pathTaintCache.clear();
                }
            }
        // }

        return tainted;
    }

    public static String parcelTaint(PathTaint pathTaint) {
        if (pathTaint != null) {

            int taintCode = System.identityHashCode(pathTaint);
            if (!taintCache.contains(taintCode)) {
                if (taintCache.size() > 1024 * 1024) {
                    taintCache.clear();
                }
                taintCache.add(taintCode);
            } else {
                // System.out.format("PathTaint: Ignored ParcelTaint%n");
                return "";
            }

            // StringBuilder fullStackTrace = new StringBuilder();
            // for (StackTraceElement elem : Thread.currentThread().getStackTrace()) {
            //     fullStackTrace.append("     ");
            //     fullStackTrace.append(elem.toString());
            //     fullStackTrace.append("\n");
            // }
            // System.out.format("PathTaint: ParcelTaint: StackTrace: %s%n", fullStackTrace.toString());
            // System.out.format("PathTaint: ParcelTaint: %s(%s)%n", pathTaint.toString(), System.identityHashCode(pathTaint));

            int num = ((new Random()).nextInt()) & Integer.MAX_VALUE;
            String toPrint = "DumpTaint for parcel: " + num;
            List <Object> args = new ArrayList<>();
            args.add(pathTaint);
            TaintDump taintDump = new TaintDump(args, num, toPrint);
            // taintDump.start();
            // return String.valueOf(num);
            taintDump.run();
            if (taintDump.tainted) {
                System.out.println("PathTaint: ParcelTaint: Tainted");
                return String.valueOf(num);
            }
            System.out.println("PathTaint: ParcelTaint: Not tainted");
            return "";
        }
        return "";
    }

    public static String parcelTaint(PathTaint pathTaint, Object object) {
        int taintCode = System.identityHashCode(pathTaint);
        int objectCode = System.identityHashCode(object);
        if (!taintCache.contains(taintCode) && !taintCache.contains(objectCode)) {
            if (taintCache.size() > 1024 * 1024) {
                taintCache.clear();
            }
            taintCache.add(taintCode);
            taintCache.add(objectCode);
        } else {
            // System.out.format("PathTaint: Ignored ParcelTaint-obj%n");
            return "";
        }

        // StringBuilder fullStackTrace = new StringBuilder();
        // for (StackTraceElement elem : Thread.currentThread().getStackTrace()) {
        //     fullStackTrace.append("     ");
        //     fullStackTrace.append(elem.toString());
        //     fullStackTrace.append("\n");
        // }
        // System.out.format("PathTaint: ParcelTaint-obj: StackTrace: %s%n", fullStackTrace.toString());
        // if (pathTaint != null) {
        //     System.out.format("PathTaint: ParcelTaint-obj: %s(%s)%n", pathTaint.toString(), System.identityHashCode(pathTaint));
        // }


        int num = ((new Random()).nextInt()) & Integer.MAX_VALUE;
        String toPrint = "DumpTaint for parcel: " + num;
        List <Object> args = new ArrayList<>();
        if (pathTaint != null) {
            args.add(pathTaint);
        }
        args.add(object);
        TaintDump taintDump = new TaintDump(args, num, toPrint);
        // taintDump.start();
        // return String.valueOf(num);
        taintDump.run();
        if (taintDump.tainted) {
            System.out.println("PathTaint: ParcelTaint: Tainted");
            return String.valueOf(num);
        }
        System.out.println("PathTaint: ParcelTaint: Not tainted");
        return "";
    }


    public static String fileTaint(PathTaint pathTaint) {
        if (pathTaint != null) {

            int taintCode = System.identityHashCode(pathTaint);
            if (!taintCache.contains(taintCode)) {
                if (taintCache.size() > 1024 * 1024) {
                    taintCache.clear();
                }
                taintCache.add(taintCode);
            } else {
                // System.out.format("PathTaint: Ignored FileTaint%n");
                return "";
            }

            // StringBuilder fullStackTrace = new StringBuilder();
            // for (StackTraceElement elem : Thread.currentThread().getStackTrace()) {
            //     fullStackTrace.append("     ");
            //     fullStackTrace.append(elem.toString());
            //     fullStackTrace.append("\n");
            // }
            // System.out.format("PathTaint: FileTaint: StackTrace: %s%n", fullStackTrace.toString());
            // System.out.format("PathTaint: FileTaint: %s(%s)%n", pathTaint.toString(), System.identityHashCode(pathTaint));

            int num = ((new Random()).nextInt()) & Integer.MAX_VALUE;
            String toPrint = "DumpTaint for file: " + num;
            List <Object> args = new ArrayList<>();
            args.add(pathTaint);
            TaintDump taintDump = new TaintDump(args, num, toPrint);
            // taintDump.start();
            // return String.valueOf(num);
            taintDump.run();
            if (taintDump.tainted) {
                System.out.println("PathTaint: FileTaint: Tainted");
                return String.valueOf(num);
            }
            System.out.println("PathTaint: FileTaint: Not tainted");
            return "";
        }
        return "";
    }

    public static String fileTaint(PathTaint pathTaint, Object object) {

        int taintCode = System.identityHashCode(pathTaint);
        int objectCode = System.identityHashCode(object);
        if (!taintCache.contains(taintCode) && !taintCache.contains(objectCode)) {
            if (taintCache.size() > 1024 * 1024) {
                taintCache.clear();
            }
            taintCache.add(taintCode);
            taintCache.add(objectCode);
        } else {
            // System.out.format("PathTaint: Ignored FileTaint-obj%n");
            return "";
        }

        int num = ((new Random()).nextInt()) & Integer.MAX_VALUE;

        // StringBuilder fullStackTrace = new StringBuilder();
        // for (StackTraceElement elem : Thread.currentThread().getStackTrace()) {
        //     fullStackTrace.append("     ");
        //     fullStackTrace.append(elem.toString());
        //     fullStackTrace.append("\n");
        // }
        // System.out.format("PathTaint: FileTaint-obj: StackTrace: %s%n", fullStackTrace.toString());
        // if (pathTaint != null) {
        //     System.out.format("PathTaint: FileTaint-obj: %s(%s)%n", pathTaint.toString(), System.identityHashCode(pathTaint));
        // }

        String toPrint = "DumpTaint for file: " + num;
        List <Object> args = new ArrayList<>();
        if (pathTaint != null) {
            args.add(pathTaint);
        }
        args.add(object);
        TaintDump taintDump = new TaintDump(args, num, toPrint);
        // taintDump.start();
        // return String.valueOf(num);
        taintDump.run();
        if (taintDump.tainted) {
            System.out.println("PathTaint: FileTaint: Tainted");
            return String.valueOf(num);
        }
        System.out.println("PathTaint: FileTaint: Not tainted");
        return "";
    }

    public static void addSerializedTaint(String[] container, PathTaint objectTaint, Object object) {
        String taint = PathTaint.fileTaint(objectTaint, object);
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.format("PathTaint: addSerializedTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);

        String newTaint = null;
        if (container[0] != null && !container[0].isEmpty() && !taint.isEmpty()) {
            newTaint = container[0] + "-" + taint;
        } else if (container[0] != null && !container[0].isEmpty()) {
            newTaint = container[0];
        } else if (!taint.isEmpty()) {
            newTaint = taint;
        }
        if (newTaint != null && !newTaint.isEmpty()) {
            container[0] = newTaint;
        }
    }

    public static void addSerializedTaint(String[] container, PathTaint objectTaint) {
        String taint = PathTaint.fileTaint(objectTaint);
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.format("PathTaint: addSerializedTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);

        String newTaint = null;
        if (container[0] != null && !container[0].isEmpty() && !taint.isEmpty()) {
            newTaint = container[0] + "-" + taint;
        } else if (container[0] != null && !container[0].isEmpty()) {
            newTaint = container[0];
        } else if (!taint.isEmpty()) {
            newTaint = taint;
        }
        if (newTaint != null && !newTaint.isEmpty()) {
            container[0] = newTaint;
        }
    }

    public static PathTaint getSerializedTaint(String[] container) {
        String taint = container[0];
        if (taint != null && !taint.isEmpty()) {
            PathTaint contained = new PathTaint();
            contained.site = taint;
            contained.delta = -2;
            contained.timeStamp = System.nanoTime();
            // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
            // System.out.format("PathTaint: getSerializedTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);
            return contained;

        } else {
            return null;
            // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
            // System.out.format("PathTaint: getSerializedTaint: in method %s->%s, taint is null %n", ste.getClassName(), ste.getMethodName());
        }
    }


    /* -- Khaled: Taint -- */

    public static void addFileTaint(File file, PathTaint fileTaint, Object object) {
        String taintFileName = file.getAbsolutePath() + ".taint";
        String extra = null;
        try {
            extra = new String(Files.readAllBytes(Paths.get(taintFileName)));
        } catch (IOException e) {
            // e.printStackTrace();
        }
        String taint = PathTaint.fileTaint(fileTaint, object);
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.format("PathTaint: addFileTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);
        String newTaint = null;
        if (extra != null && !extra.isEmpty() && !taint.isEmpty()) {
            newTaint = extra + "-" + taint;
        } else if (extra != null && !extra.isEmpty()) {
            newTaint = extra;
        } else if (!taint.isEmpty()) {
            newTaint = taint;
        }
        if (newTaint != null && !newTaint.isEmpty()) {
            try {
                FileWriter fw = new FileWriter(taintFileName, false);
                fw.write(newTaint);
                fw.close();
                // System.out.format("PathTaint: addFileTaint: file writted %n");
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public static void addFileTaint(File file, PathTaint fileTaint) {
        String taintFileName = file.getAbsolutePath() + ".taint";
        String extra = null;
        try {
            extra = new String(Files.readAllBytes(Paths.get(taintFileName)));
        } catch (IOException e) {
            // e.printStackTrace();
        }
        String taint = PathTaint.fileTaint(fileTaint);
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.format("PathTaint: addFileTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);
        String newTaint = null;
        if (extra != null && !extra.isEmpty() && !taint.isEmpty()) {
            newTaint = extra + "-" + taint;
        } else if (extra != null && !extra.isEmpty()) {
            newTaint = extra;
        } else if (!taint.isEmpty()) {
            newTaint = taint;
        }
        if (newTaint != null && !newTaint.isEmpty()) {
            try {
                FileWriter fw = new FileWriter(taintFileName, false);
                fw.write(newTaint);
                fw.close();
                // System.out.format("PathTaint: addFileTaint: file writted %n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static PathTaint getFileTaint(File file) {
        String taintFileName = file.getAbsolutePath() + ".taint";
        String taint = null;
        try {
            taint = new String(Files.readAllBytes(Paths.get(taintFileName)));
        } catch (IOException e) {
            // e.printStackTrace();
        }
        if (taint != null && !taint.isEmpty()) {
            PathTaint contained = new PathTaint();
            contained.site = taint;
            contained.delta = -2;
            contained.timeStamp = System.nanoTime();
            // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
            // System.out.format("PathTaint: getFileTaint: in method %s->%s, taint is %s %n", ste.getClassName(), ste.getMethodName(), taint);
            return contained;

        } else {
            return null;
            // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
            // System.out.format("PathTaint: getFileTaint: in method %s->%s, taint is null %n", ste.getClassName(), ste.getMethodName());
        }
    }

    // @Override
    // protected void finalize() throws java.lang.Throwable {
    //     sizeCollectedBytes += 12 + (4*4);
    //     numCollected +=1;
    // }

    public static void setSinkId(PathTaint pathTaint) {
        if (pathTaint != null) {
            pathTaint.sinkId = "[" + pathTaint.timeStamp + "]: " + pathTaint.site + "(" + pathTaint.delta + ")";
        }
    }

    public static void logViewAPI(PathTaint taint) {
        if (taint != null && taint.sinkId != null) {
            Integer sinkHash = taint.sinkId.hashCode();
            if (viewCache.containsKey(sinkHash)) {
                return;
            }
            viewCache.put(sinkHash, sinkHash);
            String toPrint = "API:"  + taint.site + "(" + taint.delta + "), sink:" + taint.sinkId;
            System.out.println("PathTaint: ViewAPI: time: " + taint.timeStamp + ", " + toPrint);
            
            // String toPrint = "API:"  + taint.site + "(" + taint.delta + "), sink:" + taint.sinkId;
            // Integer toPrintHash = toPrint.hashCode();
            // Integer sinkHash = taint.sinkId.hashCode();
            // if (viewCache.containsKey(toPrintHash) && viewCache.get(toPrintHash).equals(sinkHash)) {
            //     return;
            // }
            // viewCache.put(toPrintHash, sinkHash);
            // System.out.println("PathTaint: ViewAPI: time: " + taint.timeStamp + ", " + toPrint);
        }
    }

}
