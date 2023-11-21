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

public class PathTaint {
    public PathTaint left;
    public PathTaint right;
    public String site;
    public int delta;
    public long timeStamp;

    private static final int MAX_ENTRIES = 100*1024;

    private static Map<Integer, Long> pathTaintCache = Collections.synchronizedMap(new LinkedHashMap<Integer, Long>(MAX_ENTRIES+1, .75F, true) {
        // This method is called just after a new entry has been added
        @Override
        public boolean removeEldestEntry(Map.Entry<Integer, Long> eldest) {
            return size() > MAX_ENTRIES;
        }
    });

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


            for (Object arg: args) {
                if (arg instanceof PathTaint) {
                    PathTaint pathTaint = (PathTaint) arg;
                    if (pathTaint != null) {
                        tainted = tainted || PathTaint.dumpTraverseLoop(pathTaint, num, visitedTaints, os, dos);
                    }
                } else {
                    if (arg != null) {
                        tainted = tainted || PathTaint.dumpObjectLoop(arg, num, visitedTaints, visitedObjects, os, dos);
                    }
                }
            }

            if (toPrint != null && tainted == true) {
                System.out.println(toPrint);
            }

            if (tainted) {
                long endTime = Thread.getNativeCurrentTime();
                long elapsed = endTime - startTime;
                System.out.println("DumpTaintTime: " + elapsed);
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
            String str = String.format("StackTrace: T(%s), at %s", threadName, System.nanoTime());
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
    }

    public static void printSinkFound(String signature, String sink, int delta) {
        System.out.format("PathTaint: SinkFound: %s(%s), %s%n", signature, delta, sink);
    }

    public static PathTaint addTaintSource(PathTaint pathTaint, String site, int delta) {
        PathTaint newTaint = new PathTaint();
        newTaint.site = site;
        newTaint.delta = delta;
        if (pathTaint != null) {
            newTaint.left = pathTaint.left;
            newTaint.right = pathTaint.right;
        }
        newTaint.timeStamp = System.nanoTime();
        System.out.format("PathTaint: SourceFound: %s(%s)id(%s)%n", newTaint.site, newTaint.delta, newTaint.timeStamp);
        return newTaint;
    }

    public static PathTaint propagateOneArg(PathTaint other, String site, int delta) {
        try {
            if (other.site == null) {
                StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
                System.out.format("PathTaint: in method %s->%s(%s), trying to propagate-one from null left %s(%s)%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), site, delta);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        PathTaint newTaint = new PathTaint();
        newTaint.site = site;
        newTaint.delta = delta;
        newTaint.left = other;
        newTaint.timeStamp = System.nanoTime();
        // System.out.format("PathTaint: propagateOneArg %s <-- %s%n", newTaint, other);
        return newTaint;
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
        try {
            if (left != null && left.site == null) {
                StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
                System.out.format("PathTaint: in method %s->%s(%s), trying to propagate-two from null left %s(%s)%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), site, delta);
                left = null;
            }
            if (right != null && right.site == null) {
                StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
                System.out.format("PathTaint: in method %s->%s(%s), trying to propagate-two from null right %s(%s)%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), site, delta);
                right = null;
            }
            if (left == null && right == null) {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        PathTaint newTaint = new PathTaint();
        newTaint.site = site;
        newTaint.delta = delta;
        newTaint.left = left;
        newTaint.right = right;
        newTaint.timeStamp = System.nanoTime();
        // System.out.format("PathTaint: propagateTwoArgs %s <-- %s ^ %s %n", newTaint, left, right);
        return newTaint;
    }

    public String toString() {
        return site + "(" + delta + ")";
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
            try {
                if(visitedObjects.contains(next)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            visitedObjects.add(next);
            try {
                for (Field field : next.getClass().getDeclaredFields()) {
                    tainted = tainted || processField("DeclaredField", stack, next, field, num, visitedTaints, os, dos);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return tainted;
    }

    private static boolean processField(String type, Stack<Object> stack, Object next, Field field, int num, Set<Integer> visitedTaints, ByteArrayOutputStream os, DeflaterOutputStream dos) throws Exception {
        boolean tainted = false;
        field.setAccessible(true);
        if (field.getType().equals(PathTaint.class)) {
            PathTaint pathTaint = (PathTaint) field.get(next);
            if (pathTaint != null && pathTaint.left != null) {
                // System.out.println("DumpTaint: FieldAccessPath: ");
                tainted = tainted || dumpTraverseLoop(pathTaint.left, num, visitedTaints, os, dos);
            }
        } else {
            Object value = field.get(next);
            if (value != null) {
                stack.push(value);
            }
        }
        return tainted;
    }

    public static boolean dumpTraverseLoopPath(PathTaint pathTaint, int num, Set<Integer> visitedTaints, ByteArrayOutputStream os, DeflaterOutputStream dos) {

        String header = "DumpTaint-path-"+ num + ": ->";
        boolean tainted = false;

        if (pathTaint.site.startsWith("Ljava") || pathTaint.site.startsWith("Landroid") || pathTaint.site.startsWith("Lcom/google") || pathTaint.site.startsWith("Lcom/android") || pathTaint.site.startsWith("Lkotlin"))
        {
            return tainted;
        }


        String sourcesHeader = "DumpTaint-sources-"+ num + ": ->";
        StringBuilder sources = new StringBuilder();

        Deque<PathTaint> stack = new ArrayDeque<>(10*1024);
        Set<PathTaint> visitedPathsTaints = new HashSet<>(20*1024);
        StringBuilder sb = new StringBuilder(1024);
        String currentMethod = "";
        long pathLength = 0;

        stack.push(pathTaint);

        int maxStackSize = 0;

        while (!stack.isEmpty()) {
            maxStackSize = Math.max(maxStackSize, stack.size());
            PathTaint next = stack.pop();
            int stringCode = (next.site + next.delta).hashCode();

            visitedPathsTaints.add(next);

            if (!visitedTaints.contains(stringCode)) {
                visitedTaints.add(stringCode);

                String nextSite = next.site;

                if (nextSite == null) {
                    System.out.println("PathTaint: Null taint");
                }
                if (!(nextSite.startsWith("Ljava") || nextSite.startsWith("Landroid") || nextSite.startsWith("Lcom/google") || nextSite.startsWith("Lcom/android") || nextSite.startsWith("Lkotlin")))
                {

                    int hashCode = System.identityHashCode(next);
                    if (nextSite.equals(currentMethod)) {
                        sb.append(":");
                        sb.append(next.delta);
                    } else {
                        sb.append("\n");
                        sb.append(header);
                        sb.append(nextSite);
                        sb.append(":");
                        sb.append(next.delta);
                    }
                    currentMethod = nextSite;

                    if (next.left == null && next.right == null) {
                        sb.append(":STARTPATH");
                        if (next.delta != -2) {
                            sources.append(sourcesHeader);
                            sources.append(nextSite);
                            sources.append("(");
                            sources.append(next.delta);
                            sources.append(")id(");
                            sources.append(hashCode);
                            sources.append(")\n");
                        }
                    }

                    pathLength += 1;
                }
                // else {
                //     ignoredLength += 1;
                // }
            }
            PathTaint left = next.left;
            if (left != null) {
                if (!visitedPathsTaints.contains(left)) {
                    stack.push(left);
                }
            }
            PathTaint right = next.right;
            if (right != null) {
                if (!visitedPathsTaints.contains(right)) {
                    stack.push(right);
                }
            }
            tainted = true;


        }

        System.out.println("PathTaint: visitedPathsTaints.size = " + visitedPathsTaints.size());
        System.out.println("PathTaint: visitedTaints.size = " + visitedTaints.size());
        System.out.println("PathTaint: stack.size = " + maxStackSize);
        System.out.println("PathTaint: sources.size = " + sources.length());


        if (tainted)
        {
            System.out.println(sb.toString());
            System.out.println(sources.toString());
            System.out.println(sourcesHeader + "PathLength: " + pathLength);
        }

        return tainted;
    }


    public static boolean dumpTraverseLoop(PathTaint pathTaint, int num, Set<Integer> visitedTaints, ByteArrayOutputStream os, DeflaterOutputStream dos) {
        String header = "DumpTaint-"+ num + ": ->";
        boolean tainted = false;
        Deque<PathTaint> stack = new ArrayDeque<>(1024);
        int cacheHit = 0;
        int localCacheHit = 0;
        int cacheMiss = 0;
        int localCacheMiss = 0;
        Set<Integer> visitedCacheless = new HashSet<>();
        boolean fastMode = false;

        int numIters = 0;
        stack.push(pathTaint);
        while (!stack.isEmpty()) {

            PathTaint next = stack.pop();

            if (numIters > 10 * 1024) {
                stack.clear();
            }

            if (numIters > 10 * 1024) {
                // stack.clear();
                if (cacheHit > 0) {
                    System.out.println("PathTaint-debug-" + num + ": Swiching to cacheless mode, numIters = " + numIters);
                    break;
                }
                fastMode = true;
            }

            int hashCode = System.identityHashCode(next);

            boolean inCache = false;
            boolean isFramework = true;
            String nextSite = next.site;


            if (pathTaintCache.containsKey(hashCode) && pathTaintCache.get(hashCode).equals(next.timeStamp)) {
                cacheHit += 1;
                continue;
            }
            pathTaintCache.put(hashCode, next.timeStamp);
            cacheMiss += 1;


            if (!inFramework(nextSite)) {
                isFramework = false;
            }



            int leftHashCode = 0;
            int rightHashCode = 0;

            PathTaint left = next.left;
            if (left != null) {
                String leftSite = left.site;
                leftHashCode = System.identityHashCode(left);
                if (!inFramework(leftSite))
                {
                    isFramework = false;
                }
            }
            PathTaint right = next.right;
            if (right != null) {
                String rightSite = right.site;
                rightHashCode = System.identityHashCode(right);
                if (!inFramework(rightSite))
                {
                    isFramework = false;
                }
            }
            try {
                if (!isFramework && !fastMode) {
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
                    System.out.println(header + dumpString);
                    numIters += 1;
                } else if (!isFramework && fastMode) {
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
                        System.out.println(header + dumpString);
                        numIters += 1;
                    }
                }

                if (!inCache) {
                    if (left != null) {
                        if (!visitedTaints.contains(leftHashCode)) {
                            stack.push(left);
                        }
                    }
                    if (right != null) {
                        if (!visitedTaints.contains(rightHashCode)) {
                            stack.push(right);
                        }
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

        if (pathTaintCache.size() == MAX_ENTRIES) { // for non-cold cache
            if (cacheHit < 5) {
                System.out.println("PathTaint-debug-" + num + ": Cache is useless, erasing it at the end");
                pathTaintCache.clear();
            }
        }

        return tainted;
    }

    private static boolean inFramework(String site) {
        return site.startsWith("Ljava") || site.startsWith("Landroid")
        || site.startsWith("Lcom/google") || site.startsWith("Lcom/android")
        || site.startsWith("Lkotlin");
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
                return "";
            }

            int num = ((new Random()).nextInt()) & Integer.MAX_VALUE;
            String toPrint = "DumpTaint for parcel: " + num;
            List <Object> args = new ArrayList<>();
            args.add(pathTaint);
            TaintDump taintDump = new TaintDump(args, num, toPrint);
            // taintDump.start();
            // return String.valueOf(num);
            taintDump.run();
            if (taintDump.tainted) {
                return String.valueOf(num);
            }
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
            return "";
        }

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
            return String.valueOf(num);
        }
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
                return "";
            }

            int num = ((new Random()).nextInt()) & Integer.MAX_VALUE;
            String toPrint = "DumpTaint for file: " + num;
            List <Object> args = new ArrayList<>();
            args.add(pathTaint);
            TaintDump taintDump = new TaintDump(args, num, toPrint);
            // taintDump.start();
            // return String.valueOf(num);
            taintDump.run();
            if (taintDump.tainted) {
                return String.valueOf(num);
            }
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
            return "";
        }

        int num = ((new Random()).nextInt()) & Integer.MAX_VALUE;

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
            return String.valueOf(num);
        }
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

}