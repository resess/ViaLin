/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (c) 1994, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang;

import dalvik.annotation.optimization.FastNative;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.LockSupport;
import sun.nio.ch.Interruptible;
import sun.reflect.CallerSensitive;
import dalvik.system.VMStack;
import libcore.util.EmptyArray;
import java.util.function.Supplier;

import java.util.List;
import java.util.ArrayList;

/**
 * A <i>thread</i> is a thread of execution in a program. The Java
 * Virtual Machine allows an application to have multiple threads of
 * execution running concurrently.
 * <p>
 * Every thread has a priority. Threads with higher priority are
 * executed in preference to threads with lower priority. Each thread
 * may or may not also be marked as a daemon. When code running in
 * some thread creates a new <code>Thread</code> object, the new
 * thread has its priority initially set equal to the priority of the
 * creating thread, and is a daemon thread if and only if the
 * creating thread is a daemon.
 * <p>
 * When a Java Virtual Machine starts up, there is usually a single
 * non-daemon thread (which typically calls the method named
 * <code>main</code> of some designated class). The Java Virtual
 * Machine continues to execute threads until either of the following
 * occurs:
 * <ul>
 * <li>The <code>exit</code> method of class <code>Runtime</code> has been
 *     called and the security manager has permitted the exit operation
 *     to take place.
 * <li>All threads that are not daemon threads have died, either by
 *     returning from the call to the <code>run</code> method or by
 *     throwing an exception that propagates beyond the <code>run</code>
 *     method.
 * </ul>
 * <p>
 * There are two ways to create a new thread of execution. One is to
 * declare a class to be a subclass of <code>Thread</code>. This
 * subclass should override the <code>run</code> method of class
 * <code>Thread</code>. An instance of the subclass can then be
 * allocated and started. For example, a thread that computes primes
 * larger than a stated value could be written as follows:
 * <hr><blockquote><pre>
 *     class PrimeThread extends Thread {
 *         long minPrime;
 *         PrimeThread(long minPrime) {
 *             this.minPrime = minPrime;
 *         }
 *
 *         public void run() {
 *             // compute primes larger than minPrime
 *             &nbsp;.&nbsp;.&nbsp;.
 *         }
 *     }
 * </pre></blockquote><hr>
 * <p>
 * The following code would then create a thread and start it running:
 * <blockquote><pre>
 *     PrimeThread p = new PrimeThread(143);
 *     p.start();
 * </pre></blockquote>
 * <p>
 * The other way to create a thread is to declare a class that
 * implements the <code>Runnable</code> interface. That class then
 * implements the <code>run</code> method. An instance of the class can
 * then be allocated, passed as an argument when creating
 * <code>Thread</code>, and started. The same example in this other
 * style looks like the following:
 * <hr><blockquote><pre>
 *     class PrimeRun implements Runnable {
 *         long minPrime;
 *         PrimeRun(long minPrime) {
 *             this.minPrime = minPrime;
 *         }
 *
 *         public void run() {
 *             // compute primes larger than minPrime
 *             &nbsp;.&nbsp;.&nbsp;.
 *         }
 *     }
 * </pre></blockquote><hr>
 * <p>
 * The following code would then create a thread and start it running:
 * <blockquote><pre>
 *     PrimeRun p = new PrimeRun(143);
 *     new Thread(p).start();
 * </pre></blockquote>
 * <p>
 * Every thread has a name for identification purposes. More than
 * one thread may have the same name. If a name is not specified when
 * a thread is created, a new name is generated for it.
 * <p>
 * Unless otherwise noted, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be
 * thrown.
 *
 * @author  unascribed
 * @see     Runnable
 * @see     Runtime#exit(int)
 * @see     #run()
 * @see     #stop()
 * @since   JDK1.0
 */
public
class Thread implements Runnable {
    /* Make sure registerNatives is the first thing <clinit> does. */

    /**
     * The synchronization object responsible for this thread's join/sleep/park operations.
     */
    private final Object lock = new Object();

    private volatile long nativePeer;

    boolean started = false;

    private volatile String name;

    private int         priority;
    private Thread      threadQ;
    private long        eetop;

    /* Whether or not to single_step this thread. */
    private boolean     single_step;

    /* Whether or not the thread is a daemon thread. */
    private boolean     daemon = false;

    /* JVM state */
    private boolean     stillborn = false;

    /* What will be run. */
    private Runnable target;

    /* The group of this thread */
    private ThreadGroup group;

    /* The context ClassLoader for this thread */
    private ClassLoader contextClassLoader;

    /* The inherited AccessControlContext of this thread */
    private AccessControlContext inheritedAccessControlContext;

    /* For autonumbering anonymous threads. */
    private static int threadInitNumber;
    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

    /* ThreadLocal values pertaining to this thread. This map is maintained
     * by the ThreadLocal class. */
    ThreadLocal.ThreadLocalMap threadLocals = null;

    /*
     * InheritableThreadLocal values pertaining to this thread. This map is
     * maintained by the InheritableThreadLocal class.
     */
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;

    /*
     * The requested stack size for this thread, or 0 if the creator did
     * not specify a stack size.  It is up to the VM to do whatever it
     * likes with this number; some VMs will ignore it.
     */
    private long stackSize;

    /*
     * JVM-private state that persists after native thread termination.
     */
    private long nativeParkEventPointer;

    /*
     * Thread ID
     */
    private long tid;

    /* For generating thread ID */
    private static long threadSeqNumber;

    /* Java thread status for tools,
     * initialized to indicate thread 'not yet started'
     */

    private volatile int threadStatus = 0;


    private static synchronized long nextThreadID() {
        return ++threadSeqNumber;
    }

    /**
     * The argument supplied to the current call to
     * java.util.concurrent.locks.LockSupport.park.
     * Set by (private) java.util.concurrent.locks.LockSupport.setBlocker
     * Accessed using java.util.concurrent.locks.LockSupport.getBlocker
     */
    volatile Object parkBlocker;

    /* The object in which this thread is blocked in an interruptible I/O
     * operation, if any.  The blocker's interrupt method should be invoked
     * after setting this thread's interrupt status.
     */
    private volatile Interruptible blocker;
    private final Object blockerLock = new Object();

    /**
     * Khaled: Added paramter array
     */

    public PathTaint[] paramTaintArray = new PathTaint[256];
    public String taintSite;
    public int taintDelta;
    public PathTaint returnTaint;
    public PathTaint throwTaint;
    public static PathTaint asyncTaskParam;
    public static PathTaint orderedIntentParam;
    public PathTaint paramTaint0;
    public PathTaint paramTaint1;
    public PathTaint paramTaint2;
    public PathTaint paramTaint3;
    public PathTaint paramTaint4;
    public PathTaint paramTaint5;
    public PathTaint paramTaint6;
    public PathTaint paramTaint7;
    public PathTaint paramTaint8;
    public PathTaint paramTaint9;
    public PathTaint paramTaint10;
    public PathTaint paramTaint11;
    public PathTaint paramTaint12;
    public PathTaint paramTaint13;
    public PathTaint paramTaint14;
    public PathTaint paramTaint15;
    public PathTaint paramTaint16;
    public PathTaint paramTaint17;
    public PathTaint paramTaint18;
    public PathTaint paramTaint19;
    public PathTaint paramTaint20;
    public PathTaint paramTaint21;
    public PathTaint paramTaint22;
    public PathTaint paramTaint23;
    public PathTaint paramTaint24;
    public PathTaint paramTaint25;
    public PathTaint paramTaint26;
    public PathTaint paramTaint27;
    public PathTaint paramTaint28;
    public PathTaint paramTaint29;
    public PathTaint paramTaint30;
    public PathTaint paramTaint31;
    public PathTaint paramTaint32;
    public PathTaint paramTaint33;
    public PathTaint paramTaint34;
    public PathTaint paramTaint35;
    public PathTaint paramTaint36;
    public PathTaint paramTaint37;
    public PathTaint paramTaint38;
    public PathTaint paramTaint39;
    public PathTaint paramTaint40;
    public PathTaint paramTaint41;
    public PathTaint paramTaint42;
    public PathTaint paramTaint43;
    public PathTaint paramTaint44;
    public PathTaint paramTaint45;
    public PathTaint paramTaint46;
    public PathTaint paramTaint47;
    public PathTaint paramTaint48;
    public PathTaint paramTaint49;
    public PathTaint paramTaint50;
    public PathTaint paramTaint51;
    public PathTaint paramTaint52;
    public PathTaint paramTaint53;
    public PathTaint paramTaint54;
    public PathTaint paramTaint55;
    public PathTaint paramTaint56;
    public PathTaint paramTaint57;
    public PathTaint paramTaint58;
    public PathTaint paramTaint59;
    public PathTaint paramTaint60;
    public PathTaint paramTaint61;
    public PathTaint paramTaint62;
    public PathTaint paramTaint63;
    public PathTaint paramTaint64;
    public PathTaint paramTaint65;
    public PathTaint paramTaint66;
    public PathTaint paramTaint67;
    public PathTaint paramTaint68;
    public PathTaint paramTaint69;
    public PathTaint paramTaint70;
    public PathTaint paramTaint71;
    public PathTaint paramTaint72;
    public PathTaint paramTaint73;
    public PathTaint paramTaint74;
    public PathTaint paramTaint75;
    public PathTaint paramTaint76;
    public PathTaint paramTaint77;
    public PathTaint paramTaint78;
    public PathTaint paramTaint79;
    public PathTaint paramTaint80;
    public PathTaint paramTaint81;
    public PathTaint paramTaint82;
    public PathTaint paramTaint83;
    public PathTaint paramTaint84;
    public PathTaint paramTaint85;
    public PathTaint paramTaint86;
    public PathTaint paramTaint87;
    public PathTaint paramTaint88;
    public PathTaint paramTaint89;
    public PathTaint paramTaint90;
    public PathTaint paramTaint91;
    public PathTaint paramTaint92;
    public PathTaint paramTaint93;
    public PathTaint paramTaint94;
    public PathTaint paramTaint95;
    public PathTaint paramTaint96;
    public PathTaint paramTaint97;
    public PathTaint paramTaint98;
    public PathTaint paramTaint99;
    public PathTaint paramTaint100;
    public PathTaint paramTaint101;
    public PathTaint paramTaint102;
    public PathTaint paramTaint103;
    public PathTaint paramTaint104;
    public PathTaint paramTaint105;
    public PathTaint paramTaint106;
    public PathTaint paramTaint107;
    public PathTaint paramTaint108;
    public PathTaint paramTaint109;
    public PathTaint paramTaint110;
    public PathTaint paramTaint111;
    public PathTaint paramTaint112;
    public PathTaint paramTaint113;
    public PathTaint paramTaint114;
    public PathTaint paramTaint115;
    public PathTaint paramTaint116;
    public PathTaint paramTaint117;
    public PathTaint paramTaint118;
    public PathTaint paramTaint119;
    public PathTaint paramTaint120;
    public PathTaint paramTaint121;
    public PathTaint paramTaint122;
    public PathTaint paramTaint123;
    public PathTaint paramTaint124;
    public PathTaint paramTaint125;
    public PathTaint paramTaint126;
    public PathTaint paramTaint127;
    public PathTaint paramTaint128;
    public PathTaint paramTaint129;
    public PathTaint paramTaint130;
    public PathTaint paramTaint131;
    public PathTaint paramTaint132;
    public PathTaint paramTaint133;
    public PathTaint paramTaint134;
    public PathTaint paramTaint135;
    public PathTaint paramTaint136;
    public PathTaint paramTaint137;
    public PathTaint paramTaint138;
    public PathTaint paramTaint139;
    public PathTaint paramTaint140;
    public PathTaint paramTaint141;
    public PathTaint paramTaint142;
    public PathTaint paramTaint143;
    public PathTaint paramTaint144;
    public PathTaint paramTaint145;
    public PathTaint paramTaint146;
    public PathTaint paramTaint147;
    public PathTaint paramTaint148;
    public PathTaint paramTaint149;
    public PathTaint paramTaint150;
    public PathTaint paramTaint151;
    public PathTaint paramTaint152;
    public PathTaint paramTaint153;
    public PathTaint paramTaint154;
    public PathTaint paramTaint155;
    public PathTaint paramTaint156;
    public PathTaint paramTaint157;
    public PathTaint paramTaint158;
    public PathTaint paramTaint159;
    public PathTaint taintContainer;


    public int[] paramTaintTaintDroidArray = new int[256];
    public int returnTaintTaintDroidint;
    public int throwTaintTaintDroidint;
    public static int asyncTaskParamTaintDroidint;
    public static int orderedIntentParamInt;
    public int paramTaintTaintDroid0int;
    public int paramTaintTaintDroid1int;
    public int paramTaintTaintDroid2int;
    public int paramTaintTaintDroid3int;
    public int paramTaintTaintDroid4int;
    public int paramTaintTaintDroid5int;
    public int paramTaintTaintDroid6int;
    public int paramTaintTaintDroid7int;
    public int paramTaintTaintDroid8int;
    public int paramTaintTaintDroid9int;
    public int paramTaintTaintDroid10int;
    public int paramTaintTaintDroid11int;
    public int paramTaintTaintDroid12int;
    public int paramTaintTaintDroid13int;
    public int paramTaintTaintDroid14int;
    public int paramTaintTaintDroid15int;
    public int paramTaintTaintDroid16int;
    public int paramTaintTaintDroid17int;
    public int paramTaintTaintDroid18int;
    public int paramTaintTaintDroid19int;
    public int paramTaintTaintDroid20int;
    public int paramTaintTaintDroid21int;
    public int paramTaintTaintDroid22int;
    public int paramTaintTaintDroid23int;
    public int paramTaintTaintDroid24int;
    public int paramTaintTaintDroid25int;
    public int paramTaintTaintDroid26int;
    public int paramTaintTaintDroid27int;
    public int paramTaintTaintDroid28int;
    public int paramTaintTaintDroid29int;
    public int paramTaintTaintDroid30int;
    public int paramTaintTaintDroid31int;
    public int paramTaintTaintDroid32int;
    public int paramTaintTaintDroid33int;
    public int paramTaintTaintDroid34int;
    public int paramTaintTaintDroid35int;
    public int paramTaintTaintDroid36int;
    public int paramTaintTaintDroid37int;
    public int paramTaintTaintDroid38int;
    public int paramTaintTaintDroid39int;
    public int paramTaintTaintDroid40int;
    public int paramTaintTaintDroid41int;
    public int paramTaintTaintDroid42int;
    public int paramTaintTaintDroid43int;
    public int paramTaintTaintDroid44int;
    public int paramTaintTaintDroid45int;
    public int paramTaintTaintDroid46int;
    public int paramTaintTaintDroid47int;
    public int paramTaintTaintDroid48int;
    public int paramTaintTaintDroid49int;
    public int paramTaintTaintDroid50int;
    public int paramTaintTaintDroid51int;
    public int paramTaintTaintDroid52int;
    public int paramTaintTaintDroid53int;
    public int paramTaintTaintDroid54int;
    public int paramTaintTaintDroid55int;
    public int paramTaintTaintDroid56int;
    public int paramTaintTaintDroid57int;
    public int paramTaintTaintDroid58int;
    public int paramTaintTaintDroid59int;
    public int paramTaintTaintDroid60int;
    public int paramTaintTaintDroid61int;
    public int paramTaintTaintDroid62int;
    public int paramTaintTaintDroid63int;
    public int paramTaintTaintDroid64int;
    public int paramTaintTaintDroid65int;
    public int paramTaintTaintDroid66int;
    public int paramTaintTaintDroid67int;
    public int paramTaintTaintDroid68int;
    public int paramTaintTaintDroid69int;
    public int paramTaintTaintDroid70int;
    public int paramTaintTaintDroid71int;
    public int paramTaintTaintDroid72int;
    public int paramTaintTaintDroid73int;
    public int paramTaintTaintDroid74int;
    public int paramTaintTaintDroid75int;
    public int paramTaintTaintDroid76int;
    public int paramTaintTaintDroid77int;
    public int paramTaintTaintDroid78int;
    public int paramTaintTaintDroid79int;
    public int paramTaintTaintDroid80int;
    public int paramTaintTaintDroid81int;
    public int paramTaintTaintDroid82int;
    public int paramTaintTaintDroid83int;
    public int paramTaintTaintDroid84int;
    public int paramTaintTaintDroid85int;
    public int paramTaintTaintDroid86int;
    public int paramTaintTaintDroid87int;
    public int paramTaintTaintDroid88int;
    public int paramTaintTaintDroid89int;
    public int paramTaintTaintDroid90int;
    public int paramTaintTaintDroid91int;
    public int paramTaintTaintDroid92int;
    public int paramTaintTaintDroid93int;
    public int paramTaintTaintDroid94int;
    public int paramTaintTaintDroid95int;
    public int paramTaintTaintDroid96int;
    public int paramTaintTaintDroid97int;
    public int paramTaintTaintDroid98int;
    public int paramTaintTaintDroid99int;
    public int paramTaintTaintDroid100int;


    public long returnTaintTaintDroidlong;
    public static long asyncTaskParamTaintDroidlong;
    public long paramTaintTaintDroid0long;
    public long paramTaintTaintDroid1long;
    public long paramTaintTaintDroid2long;
    public long paramTaintTaintDroid3long;
    public long paramTaintTaintDroid4long;
    public long paramTaintTaintDroid5long;
    public long paramTaintTaintDroid6long;
    public long paramTaintTaintDroid7long;
    public long paramTaintTaintDroid8long;
    public long paramTaintTaintDroid9long;
    public long paramTaintTaintDroid10long;
    public long paramTaintTaintDroid11long;
    public long paramTaintTaintDroid12long;
    public long paramTaintTaintDroid13long;
    public long paramTaintTaintDroid14long;
    public long paramTaintTaintDroid15long;
    public long paramTaintTaintDroid16long;
    public long paramTaintTaintDroid17long;
    public long paramTaintTaintDroid18long;
    public long paramTaintTaintDroid19long;
    public long paramTaintTaintDroid20long;
    public long paramTaintTaintDroid21long;
    public long paramTaintTaintDroid22long;
    public long paramTaintTaintDroid23long;
    public long paramTaintTaintDroid24long;
    public long paramTaintTaintDroid25long;
    public long paramTaintTaintDroid26long;
    public long paramTaintTaintDroid27long;
    public long paramTaintTaintDroid28long;
    public long paramTaintTaintDroid29long;
    public long paramTaintTaintDroid30long;
    public long paramTaintTaintDroid31long;
    public long paramTaintTaintDroid32long;
    public long paramTaintTaintDroid33long;
    public long paramTaintTaintDroid34long;
    public long paramTaintTaintDroid35long;
    public long paramTaintTaintDroid36long;
    public long paramTaintTaintDroid37long;
    public long paramTaintTaintDroid38long;
    public long paramTaintTaintDroid39long;
    public long paramTaintTaintDroid40long;
    public long paramTaintTaintDroid41long;
    public long paramTaintTaintDroid42long;
    public long paramTaintTaintDroid43long;
    public long paramTaintTaintDroid44long;
    public long paramTaintTaintDroid45long;
    public long paramTaintTaintDroid46long;
    public long paramTaintTaintDroid47long;
    public long paramTaintTaintDroid48long;
    public long paramTaintTaintDroid49long;
    public long paramTaintTaintDroid50long;
    public long paramTaintTaintDroid51long;
    public long paramTaintTaintDroid52long;
    public long paramTaintTaintDroid53long;
    public long paramTaintTaintDroid54long;
    public long paramTaintTaintDroid55long;
    public long paramTaintTaintDroid56long;
    public long paramTaintTaintDroid57long;
    public long paramTaintTaintDroid58long;
    public long paramTaintTaintDroid59long;
    public long paramTaintTaintDroid60long;
    public long paramTaintTaintDroid61long;
    public long paramTaintTaintDroid62long;
    public long paramTaintTaintDroid63long;
    public long paramTaintTaintDroid64long;
    public long paramTaintTaintDroid65long;
    public long paramTaintTaintDroid66long;
    public long paramTaintTaintDroid67long;
    public long paramTaintTaintDroid68long;
    public long paramTaintTaintDroid69long;
    public long paramTaintTaintDroid70long;
    public long paramTaintTaintDroid71long;
    public long paramTaintTaintDroid72long;
    public long paramTaintTaintDroid73long;
    public long paramTaintTaintDroid74long;
    public long paramTaintTaintDroid75long;
    public long paramTaintTaintDroid76long;
    public long paramTaintTaintDroid77long;
    public long paramTaintTaintDroid78long;
    public long paramTaintTaintDroid79long;
    public long paramTaintTaintDroid80long;
    public long paramTaintTaintDroid81long;
    public long paramTaintTaintDroid82long;
    public long paramTaintTaintDroid83long;
    public long paramTaintTaintDroid84long;
    public long paramTaintTaintDroid85long;
    public long paramTaintTaintDroid86long;
    public long paramTaintTaintDroid87long;
    public long paramTaintTaintDroid88long;
    public long paramTaintTaintDroid89long;
    public long paramTaintTaintDroid90long;
    public long paramTaintTaintDroid91long;
    public long paramTaintTaintDroid92long;
    public long paramTaintTaintDroid93long;
    public long paramTaintTaintDroid94long;
    public long paramTaintTaintDroid95long;
    public long paramTaintTaintDroid96long;
    public long paramTaintTaintDroid97long;
    public long paramTaintTaintDroid98long;
    public long paramTaintTaintDroid99long;
    public long paramTaintTaintDroid100long;


    public List<Object> dumpTaintArgs = new ArrayList<>();

    public static void addToTaintDump(PathTaint pt) {
        // PathTaint shadow = new PathTaint();
        // System.out.printf("PathTaint: addToTaintDump, pt: %s%n", pt);
        // System.out.printf("PathTaint: addToTaintDump, pt.left: %s%n", pt.left);
        // System.out.printf("PathTaint: addToTaintDump, pt.right: %s%n", pt.right);
        // shadow.left = pt.left;
        // shadow.right = pt.right;
        // shadow.site = pt.site;
        // shadow.delta = pt.delta;
        // Thread.currentThread().dumpTaintArgs.add(shadow);
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.printf("PathTaint: in method %s->%s(%s), Added %s (%s) to taint dump, args now is %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), shadow, shadow.left, Thread.currentThread().dumpTaintArgs);
        Thread.currentThread().dumpTaintArgs.add(pt);
    }

    public static void addToTaintDump(PathTaint pt, Object obj) {
        List<Object> args = Thread.currentThread().dumpTaintArgs;
        // PathTaint shadow = new PathTaint();
        // // System.out.printf("PathTaint: addToTaintDump, pt: %s%n", pt);
        // // System.out.printf("PathTaint: addToTaintDump, pt.left: %s%n", pt.left);
        // // System.out.printf("PathTaint: addToTaintDump, pt.right: %s%n", pt.right);
        // shadow.left = pt.left;
        // shadow.right = pt.right;
        // shadow.site = pt.site;
        // shadow.delta = pt.delta;
        // args.add(shadow);
        args.add(pt);
        args.add(obj);
        // StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // System.out.printf("PathTaint: in method %s->%s(%s), Added %s (%s) and %s to taint dump, args now is %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), shadow, shadow.left, obj, Thread.currentThread().dumpTaintArgs);
    }


    public static int[] getParamArrayTaintDroid() {
        return Thread.currentThread().paramTaintTaintDroidArray;
    }

    public static PathTaint[] getParamArray() {
        return Thread.currentThread().paramTaintArray;
    }

    public static PathTaint getTaintContainer() {
        return Thread.currentThread().taintContainer;
    }

    public static PathTaint getTaintContainer(PathTaint taint) {
        if (taint == null) {
            return Thread.currentThread().taintContainer;
        }
        return PathTaint.propagateTwoArgs(Thread.currentThread().taintContainer, taint, taint.site, taint.delta) ;
    }

    public static void setTaintContainer(PathTaint taint) {
        Thread.currentThread().taintContainer = taint;
    }

    public static void setAsyncTaskParam(PathTaint asyncTaskParam) {
        Thread.asyncTaskParam = asyncTaskParam;
        // if (asyncTaskParam != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), asyncTaskParam passed%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
    }

    public static void setOrderedIntentParam(PathTaint orderedIntentParam) {
        Thread.orderedIntentParam = orderedIntentParam;
        // if (asyncTaskParam != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), orderedIntentParam passed%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
    }

    public static void setOrderedIntentParam(int orderedIntentParam) {
        Thread.orderedIntentParamInt = orderedIntentParam;
        // if (asyncTaskParam != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), orderedIntentParam passed%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
    }


    public static void setAsyncTaskParamTaintDroid(int asyncTaskParam) {
        Thread.asyncTaskParamTaintDroidint = asyncTaskParam;
        // if (asyncTaskParam.taint != 0) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("TaintDroid: in method %s->%s(%s), setAsyncTaskParamTaintDroid%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
    }

    public static void setAsyncTaskParamTaintDroidLong(long asyncTaskParam) {
        Thread.asyncTaskParamTaintDroidlong = asyncTaskParam;
        // if (asyncTaskParam.taint != 0) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("TaintDroid: in method %s->%s(%s), setAsyncTaskParamTaintDroid%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
    }

    public static PathTaint getAsyncTaskParam() {
        // if (asyncTaskParam != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), asyncTaskParam received%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
        return asyncTaskParam;
    }

    public static PathTaint getOrderedIntentParam() {
        // if (orderedIntentParam != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), orderedIntentParam received%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
        return orderedIntentParam;
    }

    public static int getOrderedIntentParamTaintDroid() {
        // if (orderedIntentParam != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), orderedIntentParam received%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
        return orderedIntentParamInt;
    }

    public static int getAsyncTaskParamTaintDroidInt() {
        // if (asyncTaskParamTaintDroid.taint != 0) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("TaintDroid: in method %s->%s(%s), getAsyncTaskParam%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
        return asyncTaskParamTaintDroidint;
    }

    public static long getAsyncTaskParamTaintDroidLong() {
        // if (asyncTaskParamTaintDroid.taint != 0) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("TaintDroid: in method %s->%s(%s), getAsyncTaskParam%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber());
        // }
        return asyncTaskParamTaintDroidlong;
    }

    public static PathTaint getReturnTaint() {
        PathTaint taint = Thread.currentThread().returnTaint;
        // if (taint != null) {
            // synchronized (PathTaint.whereOpened) {
            //     if (PathTaint.whereOpened.size() < 2048) {
            //         PathTaint.whereOpened.add("GetRetTaint: " + taint.site + ":" + taint.delta + "\n");
            //     }
            // }
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: getReturnTaint: %s->%s, %s%n", ste.getClassName(), ste.getMethodName(), taint);
        // }
        return taint;
    }

    public static PathTaint getThrowTaint() {
        PathTaint taint = Thread.currentThread().throwTaint;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: getReturnTaint: %s->%s, %s%n", ste.getClassName(), ste.getMethodName(), taint);
        // }
        return taint;
    }

    public static int getThrowTaintInt() {
        int taint = Thread.currentThread().throwTaintTaintDroidint;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: getReturnTaint: %s->%s, %s%n", ste.getClassName(), ste.getMethodName(), taint);
        // }
        return taint;
    }


    public static int getReturnTaintTaintDroidInt() {
        return Thread.currentThread().returnTaintTaintDroidint;
    }

    public static PathTaint getReturnTaint(Thread currentThread) {
        PathTaint taint = currentThread.returnTaint;
        // if (taint != null) {
        //     synchronized (PathTaint.whereOpened) {
        //         if (PathTaint.whereOpened.size() < 2048) {
        //             PathTaint.whereOpened.add("GetRetTaint: " + taint.site + ":" + taint.delta + "\n");
        //         }
        //     }
        // //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // //     System.out.format("PathTaint: getReturnTaint: %s->%s, %s%n", ste.getClassName(), ste.getMethodName(), taint);
        // }
        return taint;
    }

    public static PathTaint getThrowTaint(Thread currentThread) {
        PathTaint taint = currentThread.throwTaint;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: getReturnTaint: %s->%s, %s%n", ste.getClassName(), ste.getMethodName(), taint);
        // }
        return taint;
    }

    public static int getThrowTaintInt(Thread currentThread) {
        int taint = currentThread.throwTaintTaintDroidint;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: getReturnTaint: %s->%s, %s%n", ste.getClassName(), ste.getMethodName(), taint);
        // }
        return taint;
    }


    public static int getReturnTaintTaintDroidInt(Thread currentThread) {
        return currentThread.returnTaintTaintDroidint;
    }

    public static long getReturnTaintTaintDroidLong() {
        return Thread.currentThread().returnTaintTaintDroidlong;
    }

    public static PathTaint getParamTaint0(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint0;
        // if (taint != null) {
        //     synchronized (PathTaint.whereOpened) {
        //         if (PathTaint.whereOpened.size() < 2048) {
        //             PathTaint.whereOpened.add("GetParam0: " + taint.site + ":" + taint.delta + "\n");
        //         }
        //     }
        // //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint0 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint1(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint1;
        // if (taint != null) {
        //     synchronized (PathTaint.whereOpened) {
        //         if (PathTaint.whereOpened.size() < 2048) {
        //             PathTaint.whereOpened.add("GetParam1: " + taint.site + ":" + taint.delta + "\n");
        //         }
        //     }
        // //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint1 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint2(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint2;
        // if (taint != null) {
        //     synchronized (PathTaint.whereOpened) {
        //         if (PathTaint.whereOpened.size() < 2048) {
        //             PathTaint.whereOpened.add("GetParam2: " + taint.site + ":" + taint.delta + "\n");
        //         }
        //     }
        // //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint2 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint3(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint3;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint3 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint4(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint4;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint4 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint5(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint5;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint5 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint6(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint6;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint6 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint7(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint7;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint7 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint8(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint8;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint8 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint9(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint9;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint9 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint10(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint10;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint10 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint11(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint11;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint11 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint12(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint12;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint12 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint13(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint13;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint13 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint14(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint14;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint14 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint15(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint15;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint15 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint16(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint16;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint16 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint17(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint17;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint17 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint18(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint18;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint18 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint19(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint19;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint19 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint20(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint20;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint20 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint21(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint21;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint21 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint22(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint22;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint22 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint23(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint23;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint23 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint24(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint24;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint24 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint25(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint25;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint25 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint26(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint26;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint26 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint27(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint27;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint27 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint28(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint28;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint28 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint29(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint29;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint29 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint30(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint30;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint30 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint31(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint31;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint31 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint32(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint32;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint32 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint33(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint33;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint33 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint34(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint34;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint34 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint35(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint35;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint35 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint36(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint36;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint36 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint37(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint37;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint37 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint38(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint38;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint38 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint39(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint39;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint39 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint40(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint40;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint40 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint41(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint41;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint41 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint42(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint42;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint42 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint43(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint43;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint43 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint44(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint44;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint44 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint45(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint45;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint45 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint46(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint46;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint46 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint47(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint47;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint47 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint48(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint48;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint48 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint49(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint49;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint49 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint50(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint50;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint50 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint51(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint51;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint51 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint52(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint52;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint52 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint53(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint53;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint53 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint54(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint54;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint54 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint55(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint55;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint55 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint56(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint56;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint56 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint57(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint57;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint57 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint58(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint58;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint58 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint59(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint59;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint59 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint60(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint60;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint60 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint61(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint61;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint61 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint62(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint62;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint62 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint63(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint63;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint63 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint64(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint64;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint64 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint65(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint65;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint65 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint66(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint66;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint66 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint67(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint67;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint67 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint68(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint68;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint68 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint69(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint69;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint69 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint70(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint70;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint70 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint71(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint71;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint71 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint72(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint72;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint72 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint73(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint73;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint73 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint74(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint74;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint74 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint75(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint75;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint75 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint76(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint76;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint76 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint77(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint77;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint77 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint78(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint78;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint78 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint79(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint79;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint79 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint80(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint80;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint80 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint81(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint81;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint81 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint82(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint82;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint82 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint83(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint83;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint83 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint84(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint84;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint84 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint85(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint85;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint85 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint86(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint86;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint86 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint87(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint87;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint87 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint88(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint88;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint88 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint89(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint89;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint89 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint90(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint90;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint91(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint91;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint92(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint92;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint93(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint93;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint94(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint94;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint95(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint95;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint96(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint96;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint97(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint97;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint98(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint98;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint99(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint99;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }
    public static PathTaint getParamTaint100(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint100;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint101(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint101;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint102(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint102;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint103(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint103;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint104(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint104;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint105(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint105;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint106(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint106;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint107(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint107;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint108(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint108;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint109(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint109;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint110(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint110;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint111(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint111;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint112(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint112;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint113(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint113;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint114(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint114;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint115(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint115;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint116(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint116;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint117(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint117;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint118(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint118;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint119(Thread currentThread) {
        PathTaint taint = currentThread.paramTaint119;
        // if (taint != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), getParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), taint);
        // }
        return taint;
    }

    public static PathTaint getParamTaint120(Thread currentThread) {
        return currentThread.paramTaint120;
    }
    public static PathTaint getParamTaint121(Thread currentThread) {
        return currentThread.paramTaint121;
    }
    public static PathTaint getParamTaint122(Thread currentThread) {
        return currentThread.paramTaint122;
    }
    public static PathTaint getParamTaint123(Thread currentThread) {
        return currentThread.paramTaint123;
    }
    public static PathTaint getParamTaint124(Thread currentThread) {
        return currentThread.paramTaint124;
    }
    public static PathTaint getParamTaint125(Thread currentThread) {
        return currentThread.paramTaint125;
    }
    public static PathTaint getParamTaint126(Thread currentThread) {
        return currentThread.paramTaint126;
    }
    public static PathTaint getParamTaint127(Thread currentThread) {
        return currentThread.paramTaint127;
    }
    public static PathTaint getParamTaint128(Thread currentThread) {
        return currentThread.paramTaint128;
    }
    public static PathTaint getParamTaint129(Thread currentThread) {
        return currentThread.paramTaint129;
    }
    public static PathTaint getParamTaint130(Thread currentThread) {
        return currentThread.paramTaint130;
    }
    public static PathTaint getParamTaint131(Thread currentThread) {
        return currentThread.paramTaint131;
    }
    public static PathTaint getParamTaint132(Thread currentThread) {
        return currentThread.paramTaint132;
    }
    public static PathTaint getParamTaint133(Thread currentThread) {
        return currentThread.paramTaint133;
    }
    public static PathTaint getParamTaint134(Thread currentThread) {
        return currentThread.paramTaint134;
    }
    public static PathTaint getParamTaint135(Thread currentThread) {
        return currentThread.paramTaint135;
    }
    public static PathTaint getParamTaint136(Thread currentThread) {
        return currentThread.paramTaint136;
    }
    public static PathTaint getParamTaint137(Thread currentThread) {
        return currentThread.paramTaint137;
    }
    public static PathTaint getParamTaint138(Thread currentThread) {
        return currentThread.paramTaint138;
    }
    public static PathTaint getParamTaint139(Thread currentThread) {
        return currentThread.paramTaint139;
    }
    public static PathTaint getParamTaint140(Thread currentThread) {
        return currentThread.paramTaint140;
    }
    public static PathTaint getParamTaint141(Thread currentThread) {
        return currentThread.paramTaint141;
    }
    public static PathTaint getParamTaint142(Thread currentThread) {
        return currentThread.paramTaint142;
    }
    public static PathTaint getParamTaint143(Thread currentThread) {
        return currentThread.paramTaint143;
    }
    public static PathTaint getParamTaint144(Thread currentThread) {
        return currentThread.paramTaint144;
    }
    public static PathTaint getParamTaint145(Thread currentThread) {
        return currentThread.paramTaint145;
    }
    public static PathTaint getParamTaint146(Thread currentThread) {
        return currentThread.paramTaint146;
    }
    public static PathTaint getParamTaint147(Thread currentThread) {
        return currentThread.paramTaint147;
    }
    public static PathTaint getParamTaint148(Thread currentThread) {
        return currentThread.paramTaint148;
    }
    public static PathTaint getParamTaint149(Thread currentThread) {
        return currentThread.paramTaint149;
    }
    public static PathTaint getParamTaint150(Thread currentThread) {
        return currentThread.paramTaint150;
    }
    public static PathTaint getParamTaint151(Thread currentThread) {
        return currentThread.paramTaint151;
    }
    public static PathTaint getParamTaint152(Thread currentThread) {
        return currentThread.paramTaint152;
    }
    public static PathTaint getParamTaint153(Thread currentThread) {
        return currentThread.paramTaint153;
    }
    public static PathTaint getParamTaint154(Thread currentThread) {
        return currentThread.paramTaint154;
    }
    public static PathTaint getParamTaint155(Thread currentThread) {
        return currentThread.paramTaint155;
    }
    public static PathTaint getParamTaint156(Thread currentThread) {
        return currentThread.paramTaint156;
    }
    public static PathTaint getParamTaint157(Thread currentThread) {
        return currentThread.paramTaint157;
    }
    public static PathTaint getParamTaint158(Thread currentThread) {
        return currentThread.paramTaint158;
    }
    public static PathTaint getParamTaint159(Thread currentThread) {
        return currentThread.paramTaint159;
    }


    public static int getParamTaintTaintDroid0int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid0int;
    }

    public static int getParamTaintTaintDroid1int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid1int;
    }

    public static int getParamTaintTaintDroid2int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid2int;
    }

    public static int getParamTaintTaintDroid3int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid3int;
    }

    public static int getParamTaintTaintDroid4int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid4int;
    }

    public static int getParamTaintTaintDroid5int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid5int;
    }

    public static int getParamTaintTaintDroid6int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid6int;
    }

    public static int getParamTaintTaintDroid7int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid7int;
    }

    public static int getParamTaintTaintDroid8int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid8int;
    }

    public static int getParamTaintTaintDroid9int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid9int;
    }

    public static int getParamTaintTaintDroid10int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid10int;
    }

    public static int getParamTaintTaintDroid11int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid11int;
    }

    public static int getParamTaintTaintDroid12int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid12int;
    }

    public static int getParamTaintTaintDroid13int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid13int;
    }

    public static int getParamTaintTaintDroid14int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid14int;
    }

    public static int getParamTaintTaintDroid15int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid15int;
    }

    public static int getParamTaintTaintDroid16int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid16int;
    }

    public static int getParamTaintTaintDroid17int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid17int;
    }

    public static int getParamTaintTaintDroid18int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid18int;
    }

    public static int getParamTaintTaintDroid19int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid19int;
    }

    public static int getParamTaintTaintDroid20int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid20int;
    }

    public static int getParamTaintTaintDroid21int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid21int;
    }

    public static int getParamTaintTaintDroid22int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid22int;
    }

    public static int getParamTaintTaintDroid23int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid23int;
    }

    public static int getParamTaintTaintDroid24int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid24int;
    }

    public static int getParamTaintTaintDroid25int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid25int;
    }

    public static int getParamTaintTaintDroid26int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid26int;
    }

    public static int getParamTaintTaintDroid27int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid27int;
    }

    public static int getParamTaintTaintDroid28int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid28int;
    }

    public static int getParamTaintTaintDroid29int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid29int;
    }

    public static int getParamTaintTaintDroid30int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid30int;
    }

    public static int getParamTaintTaintDroid31int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid31int;
    }

    public static int getParamTaintTaintDroid32int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid32int;
    }

    public static int getParamTaintTaintDroid33int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid33int;
    }

    public static int getParamTaintTaintDroid34int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid34int;
    }

    public static int getParamTaintTaintDroid35int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid35int;
    }

    public static int getParamTaintTaintDroid36int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid36int;
    }

    public static int getParamTaintTaintDroid37int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid37int;
    }

    public static int getParamTaintTaintDroid38int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid38int;
    }

    public static int getParamTaintTaintDroid39int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid39int;
    }

    public static int getParamTaintTaintDroid40int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid40int;
    }

    public static int getParamTaintTaintDroid41int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid41int;
    }

    public static int getParamTaintTaintDroid42int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid42int;
    }

    public static int getParamTaintTaintDroid43int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid43int;
    }

    public static int getParamTaintTaintDroid44int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid44int;
    }

    public static int getParamTaintTaintDroid45int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid45int;
    }

    public static int getParamTaintTaintDroid46int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid46int;
    }

    public static int getParamTaintTaintDroid47int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid47int;
    }

    public static int getParamTaintTaintDroid48int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid48int;
    }

    public static int getParamTaintTaintDroid49int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid49int;
    }

    public static int getParamTaintTaintDroid50int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid50int;
    }

    public static int getParamTaintTaintDroid51int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid51int;
    }

    public static int getParamTaintTaintDroid52int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid52int;
    }

    public static int getParamTaintTaintDroid53int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid53int;
    }

    public static int getParamTaintTaintDroid54int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid54int;
    }

    public static int getParamTaintTaintDroid55int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid55int;
    }

    public static int getParamTaintTaintDroid56int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid56int;
    }

    public static int getParamTaintTaintDroid57int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid57int;
    }

    public static int getParamTaintTaintDroid58int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid58int;
    }

    public static int getParamTaintTaintDroid59int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid59int;
    }

    public static int getParamTaintTaintDroid60int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid60int;
    }

    public static int getParamTaintTaintDroid61int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid61int;
    }

    public static int getParamTaintTaintDroid62int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid62int;
    }

    public static int getParamTaintTaintDroid63int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid63int;
    }

    public static int getParamTaintTaintDroid64int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid64int;
    }

    public static int getParamTaintTaintDroid65int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid65int;
    }

    public static int getParamTaintTaintDroid66int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid66int;
    }

    public static int getParamTaintTaintDroid67int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid67int;
    }

    public static int getParamTaintTaintDroid68int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid68int;
    }

    public static int getParamTaintTaintDroid69int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid69int;
    }

    public static int getParamTaintTaintDroid70int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid70int;
    }

    public static int getParamTaintTaintDroid71int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid71int;
    }

    public static int getParamTaintTaintDroid72int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid72int;
    }

    public static int getParamTaintTaintDroid73int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid73int;
    }

    public static int getParamTaintTaintDroid74int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid74int;
    }

    public static int getParamTaintTaintDroid75int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid75int;
    }

    public static int getParamTaintTaintDroid76int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid76int;
    }

    public static int getParamTaintTaintDroid77int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid77int;
    }

    public static int getParamTaintTaintDroid78int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid78int;
    }

    public static int getParamTaintTaintDroid79int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid79int;
    }

    public static int getParamTaintTaintDroid80int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid80int;
    }

    public static int getParamTaintTaintDroid81int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid81int;
    }

    public static int getParamTaintTaintDroid82int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid82int;
    }

    public static int getParamTaintTaintDroid83int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid83int;
    }

    public static int getParamTaintTaintDroid84int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid84int;
    }

    public static int getParamTaintTaintDroid85int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid85int;
    }

    public static int getParamTaintTaintDroid86int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid86int;
    }

    public static int getParamTaintTaintDroid87int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid87int;
    }

    public static int getParamTaintTaintDroid88int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid88int;
    }

    public static int getParamTaintTaintDroid89int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid89int;
    }

    public static int getParamTaintTaintDroid90int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid90int;
    }

    public static int getParamTaintTaintDroid91int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid91int;
    }

    public static int getParamTaintTaintDroid92int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid92int;
    }

    public static int getParamTaintTaintDroid93int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid93int;
    }

    public static int getParamTaintTaintDroid94int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid94int;
    }

    public static int getParamTaintTaintDroid95int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid95int;
    }

    public static int getParamTaintTaintDroid96int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid96int;
    }

    public static int getParamTaintTaintDroid97int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid97int;
    }

    public static int getParamTaintTaintDroid98int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid98int;
    }

    public static int getParamTaintTaintDroid99int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid99int;
    }

    public static int getParamTaintTaintDroid100int(Thread currentThread) {
        return currentThread.paramTaintTaintDroid100int;
    }

    public static long getParamTaintTaintDroid0long() {
        return Thread.currentThread().paramTaintTaintDroid0long;
    }

    public static long getParamTaintTaintDroid1long() {
        return Thread.currentThread().paramTaintTaintDroid1long;
    }

    public static long getParamTaintTaintDroid2long() {
        return Thread.currentThread().paramTaintTaintDroid2long;
    }

    public static long getParamTaintTaintDroid3long() {
        return Thread.currentThread().paramTaintTaintDroid3long;
    }

    public static long getParamTaintTaintDroid4long() {
        return Thread.currentThread().paramTaintTaintDroid4long;
    }

    public static long getParamTaintTaintDroid5long() {
        return Thread.currentThread().paramTaintTaintDroid5long;
    }

    public static long getParamTaintTaintDroid6long() {
        return Thread.currentThread().paramTaintTaintDroid6long;
    }

    public static long getParamTaintTaintDroid7long() {
        return Thread.currentThread().paramTaintTaintDroid7long;
    }

    public static long getParamTaintTaintDroid8long() {
        return Thread.currentThread().paramTaintTaintDroid8long;
    }

    public static long getParamTaintTaintDroid9long() {
        return Thread.currentThread().paramTaintTaintDroid9long;
    }

    public static long getParamTaintTaintDroid10long() {
        return Thread.currentThread().paramTaintTaintDroid10long;
    }

    public static long getParamTaintTaintDroid11long() {
        return Thread.currentThread().paramTaintTaintDroid11long;
    }

    public static long getParamTaintTaintDroid12long() {
        return Thread.currentThread().paramTaintTaintDroid12long;
    }

    public static long getParamTaintTaintDroid13long() {
        return Thread.currentThread().paramTaintTaintDroid13long;
    }

    public static long getParamTaintTaintDroid14long() {
        return Thread.currentThread().paramTaintTaintDroid14long;
    }

    public static long getParamTaintTaintDroid15long() {
        return Thread.currentThread().paramTaintTaintDroid15long;
    }

    public static long getParamTaintTaintDroid16long() {
        return Thread.currentThread().paramTaintTaintDroid16long;
    }

    public static long getParamTaintTaintDroid17long() {
        return Thread.currentThread().paramTaintTaintDroid17long;
    }

    public static long getParamTaintTaintDroid18long() {
        return Thread.currentThread().paramTaintTaintDroid18long;
    }

    public static long getParamTaintTaintDroid19long() {
        return Thread.currentThread().paramTaintTaintDroid19long;
    }

    public static long getParamTaintTaintDroid20long() {
        return Thread.currentThread().paramTaintTaintDroid20long;
    }

    public static long getParamTaintTaintDroid21long() {
        return Thread.currentThread().paramTaintTaintDroid21long;
    }

    public static long getParamTaintTaintDroid22long() {
        return Thread.currentThread().paramTaintTaintDroid22long;
    }

    public static long getParamTaintTaintDroid23long() {
        return Thread.currentThread().paramTaintTaintDroid23long;
    }

    public static long getParamTaintTaintDroid24long() {
        return Thread.currentThread().paramTaintTaintDroid24long;
    }

    public static long getParamTaintTaintDroid25long() {
        return Thread.currentThread().paramTaintTaintDroid25long;
    }

    public static long getParamTaintTaintDroid26long() {
        return Thread.currentThread().paramTaintTaintDroid26long;
    }

    public static long getParamTaintTaintDroid27long() {
        return Thread.currentThread().paramTaintTaintDroid27long;
    }

    public static long getParamTaintTaintDroid28long() {
        return Thread.currentThread().paramTaintTaintDroid28long;
    }

    public static long getParamTaintTaintDroid29long() {
        return Thread.currentThread().paramTaintTaintDroid29long;
    }

    public static long getParamTaintTaintDroid30long() {
        return Thread.currentThread().paramTaintTaintDroid30long;
    }

    public static long getParamTaintTaintDroid31long() {
        return Thread.currentThread().paramTaintTaintDroid31long;
    }

    public static long getParamTaintTaintDroid32long() {
        return Thread.currentThread().paramTaintTaintDroid32long;
    }

    public static long getParamTaintTaintDroid33long() {
        return Thread.currentThread().paramTaintTaintDroid33long;
    }

    public static long getParamTaintTaintDroid34long() {
        return Thread.currentThread().paramTaintTaintDroid34long;
    }

    public static long getParamTaintTaintDroid35long() {
        return Thread.currentThread().paramTaintTaintDroid35long;
    }

    public static long getParamTaintTaintDroid36long() {
        return Thread.currentThread().paramTaintTaintDroid36long;
    }

    public static long getParamTaintTaintDroid37long() {
        return Thread.currentThread().paramTaintTaintDroid37long;
    }

    public static long getParamTaintTaintDroid38long() {
        return Thread.currentThread().paramTaintTaintDroid38long;
    }

    public static long getParamTaintTaintDroid39long() {
        return Thread.currentThread().paramTaintTaintDroid39long;
    }

    public static long getParamTaintTaintDroid40long() {
        return Thread.currentThread().paramTaintTaintDroid40long;
    }

    public static long getParamTaintTaintDroid41long() {
        return Thread.currentThread().paramTaintTaintDroid41long;
    }

    public static long getParamTaintTaintDroid42long() {
        return Thread.currentThread().paramTaintTaintDroid42long;
    }

    public static long getParamTaintTaintDroid43long() {
        return Thread.currentThread().paramTaintTaintDroid43long;
    }

    public static long getParamTaintTaintDroid44long() {
        return Thread.currentThread().paramTaintTaintDroid44long;
    }

    public static long getParamTaintTaintDroid45long() {
        return Thread.currentThread().paramTaintTaintDroid45long;
    }

    public static long getParamTaintTaintDroid46long() {
        return Thread.currentThread().paramTaintTaintDroid46long;
    }

    public static long getParamTaintTaintDroid47long() {
        return Thread.currentThread().paramTaintTaintDroid47long;
    }

    public static long getParamTaintTaintDroid48long() {
        return Thread.currentThread().paramTaintTaintDroid48long;
    }

    public static long getParamTaintTaintDroid49long() {
        return Thread.currentThread().paramTaintTaintDroid49long;
    }

    public static long getParamTaintTaintDroid50long() {
        return Thread.currentThread().paramTaintTaintDroid50long;
    }

    public static long getParamTaintTaintDroid51long() {
        return Thread.currentThread().paramTaintTaintDroid51long;
    }

    public static long getParamTaintTaintDroid52long() {
        return Thread.currentThread().paramTaintTaintDroid52long;
    }

    public static long getParamTaintTaintDroid53long() {
        return Thread.currentThread().paramTaintTaintDroid53long;
    }

    public static long getParamTaintTaintDroid54long() {
        return Thread.currentThread().paramTaintTaintDroid54long;
    }

    public static long getParamTaintTaintDroid55long() {
        return Thread.currentThread().paramTaintTaintDroid55long;
    }

    public static long getParamTaintTaintDroid56long() {
        return Thread.currentThread().paramTaintTaintDroid56long;
    }

    public static long getParamTaintTaintDroid57long() {
        return Thread.currentThread().paramTaintTaintDroid57long;
    }

    public static long getParamTaintTaintDroid58long() {
        return Thread.currentThread().paramTaintTaintDroid58long;
    }

    public static long getParamTaintTaintDroid59long() {
        return Thread.currentThread().paramTaintTaintDroid59long;
    }

    public static long getParamTaintTaintDroid60long() {
        return Thread.currentThread().paramTaintTaintDroid60long;
    }

    public static long getParamTaintTaintDroid61long() {
        return Thread.currentThread().paramTaintTaintDroid61long;
    }

    public static long getParamTaintTaintDroid62long() {
        return Thread.currentThread().paramTaintTaintDroid62long;
    }

    public static long getParamTaintTaintDroid63long() {
        return Thread.currentThread().paramTaintTaintDroid63long;
    }

    public static long getParamTaintTaintDroid64long() {
        return Thread.currentThread().paramTaintTaintDroid64long;
    }

    public static long getParamTaintTaintDroid65long() {
        return Thread.currentThread().paramTaintTaintDroid65long;
    }

    public static long getParamTaintTaintDroid66long() {
        return Thread.currentThread().paramTaintTaintDroid66long;
    }

    public static long getParamTaintTaintDroid67long() {
        return Thread.currentThread().paramTaintTaintDroid67long;
    }

    public static long getParamTaintTaintDroid68long() {
        return Thread.currentThread().paramTaintTaintDroid68long;
    }

    public static long getParamTaintTaintDroid69long() {
        return Thread.currentThread().paramTaintTaintDroid69long;
    }

    public static long getParamTaintTaintDroid70long() {
        return Thread.currentThread().paramTaintTaintDroid70long;
    }

    public static long getParamTaintTaintDroid71long() {
        return Thread.currentThread().paramTaintTaintDroid71long;
    }

    public static long getParamTaintTaintDroid72long() {
        return Thread.currentThread().paramTaintTaintDroid72long;
    }

    public static long getParamTaintTaintDroid73long() {
        return Thread.currentThread().paramTaintTaintDroid73long;
    }

    public static long getParamTaintTaintDroid74long() {
        return Thread.currentThread().paramTaintTaintDroid74long;
    }

    public static long getParamTaintTaintDroid75long() {
        return Thread.currentThread().paramTaintTaintDroid75long;
    }

    public static long getParamTaintTaintDroid76long() {
        return Thread.currentThread().paramTaintTaintDroid76long;
    }

    public static long getParamTaintTaintDroid77long() {
        return Thread.currentThread().paramTaintTaintDroid77long;
    }

    public static long getParamTaintTaintDroid78long() {
        return Thread.currentThread().paramTaintTaintDroid78long;
    }

    public static long getParamTaintTaintDroid79long() {
        return Thread.currentThread().paramTaintTaintDroid79long;
    }

    public static long getParamTaintTaintDroid80long() {
        return Thread.currentThread().paramTaintTaintDroid80long;
    }

    public static long getParamTaintTaintDroid81long() {
        return Thread.currentThread().paramTaintTaintDroid81long;
    }

    public static long getParamTaintTaintDroid82long() {
        return Thread.currentThread().paramTaintTaintDroid82long;
    }

    public static long getParamTaintTaintDroid83long() {
        return Thread.currentThread().paramTaintTaintDroid83long;
    }

    public static long getParamTaintTaintDroid84long() {
        return Thread.currentThread().paramTaintTaintDroid84long;
    }

    public static long getParamTaintTaintDroid85long() {
        return Thread.currentThread().paramTaintTaintDroid85long;
    }

    public static long getParamTaintTaintDroid86long() {
        return Thread.currentThread().paramTaintTaintDroid86long;
    }

    public static long getParamTaintTaintDroid87long() {
        return Thread.currentThread().paramTaintTaintDroid87long;
    }

    public static long getParamTaintTaintDroid88long() {
        return Thread.currentThread().paramTaintTaintDroid88long;
    }

    public static long getParamTaintTaintDroid89long() {
        return Thread.currentThread().paramTaintTaintDroid89long;
    }

    public static long getParamTaintTaintDroid90long() {
        return Thread.currentThread().paramTaintTaintDroid90long;
    }

    public static long getParamTaintTaintDroid91long() {
        return Thread.currentThread().paramTaintTaintDroid91long;
    }

    public static long getParamTaintTaintDroid92long() {
        return Thread.currentThread().paramTaintTaintDroid92long;
    }

    public static long getParamTaintTaintDroid93long() {
        return Thread.currentThread().paramTaintTaintDroid93long;
    }

    public static long getParamTaintTaintDroid94long() {
        return Thread.currentThread().paramTaintTaintDroid94long;
    }

    public static long getParamTaintTaintDroid95long() {
        return Thread.currentThread().paramTaintTaintDroid95long;
    }

    public static long getParamTaintTaintDroid96long() {
        return Thread.currentThread().paramTaintTaintDroid96long;
    }

    public static long getParamTaintTaintDroid97long() {
        return Thread.currentThread().paramTaintTaintDroid97long;
    }

    public static long getParamTaintTaintDroid98long() {
        return Thread.currentThread().paramTaintTaintDroid98long;
    }

    public static long getParamTaintTaintDroid99long() {
        return Thread.currentThread().paramTaintTaintDroid99long;
    }

    public static long getParamTaintTaintDroid100long() {
        return Thread.currentThread().paramTaintTaintDroid100long;
    }

    public static void setSite(String site, int delta) {
        Thread.currentThread().taintSite = site;
        Thread.currentThread().taintDelta = delta;
    }

    public static void setReturnTaint(PathTaint o) {
        Thread.currentThread().returnTaint = o;

        // if (o != null) {
        //     synchronized (PathTaint.whereOpened) {
        //         if (PathTaint.whereOpened.size() < 2048) {
        //             PathTaint.whereOpened.add("SetRetTaint: " + o.site + ":" + o.delta+ "\n");
        //         }
        //     }
        // //     StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        // //     for (int i = 3; i < trace.length; i++) {
        // //         StackTraceElement ste = trace[i];
        // //         System.out.format("PathTaint: setReturnTaint-%s: %s->%s(%s), %s%n", i, ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // //     }
        // }
    }

    public static void setThrowTaint(PathTaint o) {
        Thread.currentThread().throwTaint = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: setThrowTaint: %s->%s(%s), %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setThrowTaint(int o) {
        Thread.currentThread().throwTaintTaintDroidint = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: setThrowTaint: %s->%s(%s), %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setReturnTaintTaintDroidInt(int val) {
        Thread.currentThread().returnTaintTaintDroidint = val;
        // if (o.taint != 0) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("TaintDroid: in method %s->%s, setReturnTaint%n", ste.getClassName(), ste.getMethodName());
        // }
    }

    public static void setReturnTaintTaintDroidlong(long val) {
        Thread.currentThread().returnTaintTaintDroidlong = val;
        // if (o.taint != 0) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("TaintDroid: in method %s->%s, setReturnTaint%n", ste.getClassName(), ste.getMethodName());
        // }
    }

    public static void setParamTaint0(Thread currentThread, PathTaint o) {
        currentThread.paramTaint0 = o;
        // if (o != null) {
        //     synchronized (PathTaint.whereOpened) {
        //         if (PathTaint.whereOpened.size() < 2048) {
        //             PathTaint.whereOpened.add("SetParam0: " + o.site + ":" + o.delta + "\n");
        //         }
        //     }
        // //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint0 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint1(Thread currentThread, PathTaint o) {
        try {
            currentThread.paramTaint1 = o;
            // if (o != null) {
            //     synchronized (PathTaint.whereOpened) {
            //         if (PathTaint.whereOpened.size() < 2048) {
            //             PathTaint.whereOpened.add("GetParam1: " + o.site + ":" + o.delta + "\n");
            //         }
            //     }
            // //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
            // //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint1 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
            // }
        } catch (Exception e) {
            System.out.format("PathTaint: Exception, will throw %s%n", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void setParamTaint2(Thread currentThread, PathTaint o) {
        currentThread.paramTaint2 = o;
        // if (o != null) {
        //     synchronized (PathTaint.whereOpened) {
        //         if (PathTaint.whereOpened.size() < 2048) {
        //             PathTaint.whereOpened.add("SetParam2: " + o.site + ":" + o.delta + "\n");
        //         }
        //     }
        // //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        // //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint2 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint3(Thread currentThread, PathTaint o) {
        currentThread.paramTaint3 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint3 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint4(Thread currentThread, PathTaint o) {
        currentThread.paramTaint4 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint4 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint5(Thread currentThread, PathTaint o) {
        currentThread.paramTaint5 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint5 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint6(Thread currentThread, PathTaint o) {
        currentThread.paramTaint6 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint6 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint7(Thread currentThread, PathTaint o) {
        currentThread.paramTaint7 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint7 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint8(Thread currentThread, PathTaint o) {
        currentThread.paramTaint8 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint8 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint9(Thread currentThread, PathTaint o) {
        currentThread.paramTaint9 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint9 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint10(Thread currentThread, PathTaint o) {
        currentThread.paramTaint10 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint10 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint11(Thread currentThread, PathTaint o) {
        currentThread.paramTaint11 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint11 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint12(Thread currentThread, PathTaint o) {
        currentThread.paramTaint12 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint12 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint13(Thread currentThread, PathTaint o) {
        currentThread.paramTaint13 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint13 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint14(Thread currentThread, PathTaint o) {
        currentThread.paramTaint14 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint14 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint15(Thread currentThread, PathTaint o) {
        currentThread.paramTaint15 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint15 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint16(Thread currentThread, PathTaint o) {
        currentThread.paramTaint16 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint16 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint17(Thread currentThread, PathTaint o) {
        currentThread.paramTaint17 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint17 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint18(Thread currentThread, PathTaint o) {
        currentThread.paramTaint18 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint18 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint19(Thread currentThread, PathTaint o) {
        currentThread.paramTaint19 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint19 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint20(Thread currentThread, PathTaint o) {
        currentThread.paramTaint20 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint20 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint21(Thread currentThread, PathTaint o) {
        currentThread.paramTaint21 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint21 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint22(Thread currentThread, PathTaint o) {
        currentThread.paramTaint22 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint22 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint23(Thread currentThread, PathTaint o) {
        currentThread.paramTaint23 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint23 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint24(Thread currentThread, PathTaint o) {
        currentThread.paramTaint24 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint24 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint25(Thread currentThread, PathTaint o) {
        currentThread.paramTaint25 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint25 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint26(Thread currentThread, PathTaint o) {
        currentThread.paramTaint26 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint26 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint27(Thread currentThread, PathTaint o) {
        currentThread.paramTaint27 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint27 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint28(Thread currentThread, PathTaint o) {
        currentThread.paramTaint28 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint28 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint29(Thread currentThread, PathTaint o) {
        currentThread.paramTaint29 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint29 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint30(Thread currentThread, PathTaint o) {
        currentThread.paramTaint30 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint30 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint31(Thread currentThread, PathTaint o) {
        currentThread.paramTaint31 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint31 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint32(Thread currentThread, PathTaint o) {
        currentThread.paramTaint32 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint32 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint33(Thread currentThread, PathTaint o) {
        currentThread.paramTaint33 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint33 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint34(Thread currentThread, PathTaint o) {
        currentThread.paramTaint34 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint34 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint35(Thread currentThread, PathTaint o) {
        currentThread.paramTaint35 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint35 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint36(Thread currentThread, PathTaint o) {
        currentThread.paramTaint36 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint36 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint37(Thread currentThread, PathTaint o) {
        currentThread.paramTaint37 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint37 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint38(Thread currentThread, PathTaint o) {
        currentThread.paramTaint38 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint38 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint39(Thread currentThread, PathTaint o) {
        currentThread.paramTaint39 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint39 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint40(Thread currentThread, PathTaint o) {
        currentThread.paramTaint40 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint40 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint41(Thread currentThread, PathTaint o) {
        currentThread.paramTaint41 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint41 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint42(Thread currentThread, PathTaint o) {
        currentThread.paramTaint42 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint42 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint43(Thread currentThread, PathTaint o) {
        currentThread.paramTaint43 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint43 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint44(Thread currentThread, PathTaint o) {
        currentThread.paramTaint44 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint44 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint45(Thread currentThread, PathTaint o) {
        currentThread.paramTaint45 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint45 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint46(Thread currentThread, PathTaint o) {
        currentThread.paramTaint46 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint46 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint47(Thread currentThread, PathTaint o) {
        currentThread.paramTaint47 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint47 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint48(Thread currentThread, PathTaint o) {
        currentThread.paramTaint48 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint48 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint49(Thread currentThread, PathTaint o) {
        currentThread.paramTaint49 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint49 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint50(Thread currentThread, PathTaint o) {
        currentThread.paramTaint50 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint50 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint51(Thread currentThread, PathTaint o) {
        currentThread.paramTaint51 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint51 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint52(Thread currentThread, PathTaint o) {
        currentThread.paramTaint52 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint52 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint53(Thread currentThread, PathTaint o) {
        currentThread.paramTaint53 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint53 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint54(Thread currentThread, PathTaint o) {
        currentThread.paramTaint54 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint54 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint55(Thread currentThread, PathTaint o) {
        currentThread.paramTaint55 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint55 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint56(Thread currentThread, PathTaint o) {
        currentThread.paramTaint56 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint56 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint57(Thread currentThread, PathTaint o) {
        currentThread.paramTaint57 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint57 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint58(Thread currentThread, PathTaint o) {
        currentThread.paramTaint58 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint58 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint59(Thread currentThread, PathTaint o) {
        currentThread.paramTaint59 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint59 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint60(Thread currentThread, PathTaint o) {
        currentThread.paramTaint60 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint60 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint61(Thread currentThread, PathTaint o) {
        currentThread.paramTaint61 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint61 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint62(Thread currentThread, PathTaint o) {
        currentThread.paramTaint62 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint62 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint63(Thread currentThread, PathTaint o) {
        currentThread.paramTaint63 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint63 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint64(Thread currentThread, PathTaint o) {
        currentThread.paramTaint64 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint64 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint65(Thread currentThread, PathTaint o) {
        currentThread.paramTaint65 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint65 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint66(Thread currentThread, PathTaint o) {
        currentThread.paramTaint66 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint66 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint67(Thread currentThread, PathTaint o) {
        currentThread.paramTaint67 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint67 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint68(Thread currentThread, PathTaint o) {
        currentThread.paramTaint68 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint68 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint69(Thread currentThread, PathTaint o) {
        currentThread.paramTaint69 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint69 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint70(Thread currentThread, PathTaint o) {
        currentThread.paramTaint70 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint70 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint71(Thread currentThread, PathTaint o) {
        currentThread.paramTaint71 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint71 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint72(Thread currentThread, PathTaint o) {
        currentThread.paramTaint72 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint72 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint73(Thread currentThread, PathTaint o) {
        currentThread.paramTaint73 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint73 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint74(Thread currentThread, PathTaint o) {
        currentThread.paramTaint74 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint74 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint75(Thread currentThread, PathTaint o) {
        currentThread.paramTaint75 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint75 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint76(Thread currentThread, PathTaint o) {
        currentThread.paramTaint76 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint76 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint77(Thread currentThread, PathTaint o) {
        currentThread.paramTaint77 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint77 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint78(Thread currentThread, PathTaint o) {
        currentThread.paramTaint78 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint78 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint79(Thread currentThread, PathTaint o) {
        currentThread.paramTaint79 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint79 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint80(Thread currentThread, PathTaint o) {
        currentThread.paramTaint80 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint80 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint81(Thread currentThread, PathTaint o) {
        currentThread.paramTaint81 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint81 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint82(Thread currentThread, PathTaint o) {
        currentThread.paramTaint82 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint82 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint83(Thread currentThread, PathTaint o) {
        currentThread.paramTaint83 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint83 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint84(Thread currentThread, PathTaint o) {
        currentThread.paramTaint84 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint84 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint85(Thread currentThread, PathTaint o) {
        currentThread.paramTaint85 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint85 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint86(Thread currentThread, PathTaint o) {
        currentThread.paramTaint86 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint86 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint87(Thread currentThread, PathTaint o) {
        currentThread.paramTaint87 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint87 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint88(Thread currentThread, PathTaint o) {
        currentThread.paramTaint88 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint88 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint89(Thread currentThread, PathTaint o) {
        currentThread.paramTaint89 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint89 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint90(Thread currentThread, PathTaint o) {
        currentThread.paramTaint90 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint90 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint91(Thread currentThread, PathTaint o) {
        currentThread.paramTaint91 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint91 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint92(Thread currentThread, PathTaint o) {
        currentThread.paramTaint92 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint92 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint93(Thread currentThread, PathTaint o) {
        currentThread.paramTaint93 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint93 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint94(Thread currentThread, PathTaint o) {
        currentThread.paramTaint94 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint94 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint95(Thread currentThread, PathTaint o) {
        currentThread.paramTaint95 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint95 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint96(Thread currentThread, PathTaint o) {
        currentThread.paramTaint96 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint96 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint97(Thread currentThread, PathTaint o) {
        currentThread.paramTaint97 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint97 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint98(Thread currentThread, PathTaint o) {
        currentThread.paramTaint98 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint98 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint99(Thread currentThread, PathTaint o) {
        currentThread.paramTaint99 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint99 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint100(Thread currentThread, PathTaint o) {
        currentThread.paramTaint100 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint100 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint101(Thread currentThread, PathTaint o) {
        currentThread.paramTaint101 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint101 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint102(Thread currentThread, PathTaint o) {
        currentThread.paramTaint102 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint102 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint103(Thread currentThread, PathTaint o) {
        currentThread.paramTaint103 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint103 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint104(Thread currentThread, PathTaint o) {
        currentThread.paramTaint104 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint104 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint105(Thread currentThread, PathTaint o) {
        currentThread.paramTaint105 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint105 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint106(Thread currentThread, PathTaint o) {
        currentThread.paramTaint106 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint106 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint107(Thread currentThread, PathTaint o) {
        currentThread.paramTaint107 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint107 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint108(Thread currentThread, PathTaint o) {
        currentThread.paramTaint108 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint108 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint109(Thread currentThread, PathTaint o) {
        currentThread.paramTaint109 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint109 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint110(Thread currentThread, PathTaint o) {
        currentThread.paramTaint110 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint110 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint111(Thread currentThread, PathTaint o) {
        currentThread.paramTaint111 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint111 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint112(Thread currentThread, PathTaint o) {
        currentThread.paramTaint112 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint112 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint113(Thread currentThread, PathTaint o) {
        currentThread.paramTaint113 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint113 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint114(Thread currentThread, PathTaint o) {
        currentThread.paramTaint114 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint114 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint115(Thread currentThread, PathTaint o) {
        currentThread.paramTaint115 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint115 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint116(Thread currentThread, PathTaint o) {
        currentThread.paramTaint116 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint116 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint117(Thread currentThread, PathTaint o) {
        currentThread.paramTaint117 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint117 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint118(Thread currentThread, PathTaint o) {
        currentThread.paramTaint118 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint118 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint119(Thread currentThread, PathTaint o) {
        currentThread.paramTaint119 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint119 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }
    public static void setParamTaint120(Thread currentThread, PathTaint o) {
        currentThread.paramTaint120 = o;
        // if (o != null) {
        //     StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        //     System.out.format("PathTaint: in method %s->%s(%s), setParamTaint120 from %s%n", ste.getClassName(), ste.getMethodName(), ste.getLineNumber(), o);
        // }
    }

    public static void setParamTaint121(Thread currentThread, PathTaint o) {
        currentThread.paramTaint121 = o;
    }
    public static void setParamTaint122(Thread currentThread, PathTaint o) {
        currentThread.paramTaint122 = o;
    }
    public static void setParamTaint123(Thread currentThread, PathTaint o) {
        currentThread.paramTaint123 = o;
    }
    public static void setParamTaint124(Thread currentThread, PathTaint o) {
        currentThread.paramTaint124 = o;
    }
    public static void setParamTaint125(Thread currentThread, PathTaint o) {
        currentThread.paramTaint125 = o;
    }
    public static void setParamTaint126(Thread currentThread, PathTaint o) {
        currentThread.paramTaint126 = o;
    }
    public static void setParamTaint127(Thread currentThread, PathTaint o) {
        currentThread.paramTaint127 = o;
    }
    public static void setParamTaint128(Thread currentThread, PathTaint o) {
        currentThread.paramTaint128 = o;
    }
    public static void setParamTaint129(Thread currentThread, PathTaint o) {
        currentThread.paramTaint129 = o;
    }
    public static void setParamTaint130(Thread currentThread, PathTaint o) {
        currentThread.paramTaint130 = o;
    }
    public static void setParamTaint131(Thread currentThread, PathTaint o) {
        currentThread.paramTaint131 = o;
    }
    public static void setParamTaint132(Thread currentThread, PathTaint o) {
        currentThread.paramTaint132 = o;
    }
    public static void setParamTaint133(Thread currentThread, PathTaint o) {
        currentThread.paramTaint133 = o;
    }
    public static void setParamTaint134(Thread currentThread, PathTaint o) {
        currentThread.paramTaint134 = o;
    }
    public static void setParamTaint135(Thread currentThread, PathTaint o) {
        currentThread.paramTaint135 = o;
    }
    public static void setParamTaint136(Thread currentThread, PathTaint o) {
        currentThread.paramTaint136 = o;
    }
    public static void setParamTaint137(Thread currentThread, PathTaint o) {
        currentThread.paramTaint137 = o;
    }
    public static void setParamTaint138(Thread currentThread, PathTaint o) {
        currentThread.paramTaint138 = o;
    }
    public static void setParamTaint139(Thread currentThread, PathTaint o) {
        currentThread.paramTaint139 = o;
    }
    public static void setParamTaint140(Thread currentThread, PathTaint o) {
        currentThread.paramTaint140 = o;
    }
    public static void setParamTaint141(Thread currentThread, PathTaint o) {
        currentThread.paramTaint141 = o;
    }
    public static void setParamTaint142(Thread currentThread, PathTaint o) {
        currentThread.paramTaint142 = o;
    }
    public static void setParamTaint143(Thread currentThread, PathTaint o) {
        currentThread.paramTaint143 = o;
    }
    public static void setParamTaint144(Thread currentThread, PathTaint o) {
        currentThread.paramTaint144 = o;
    }
    public static void setParamTaint145(Thread currentThread, PathTaint o) {
        currentThread.paramTaint145 = o;
    }
    public static void setParamTaint146(Thread currentThread, PathTaint o) {
        currentThread.paramTaint146 = o;
    }
    public static void setParamTaint147(Thread currentThread, PathTaint o) {
        currentThread.paramTaint147 = o;
    }
    public static void setParamTaint148(Thread currentThread, PathTaint o) {
        currentThread.paramTaint148 = o;
    }
    public static void setParamTaint149(Thread currentThread, PathTaint o) {
        currentThread.paramTaint149 = o;
    }
    public static void setParamTaint150(Thread currentThread, PathTaint o) {
        currentThread.paramTaint150 = o;
    }
    public static void setParamTaint151(Thread currentThread, PathTaint o) {
        currentThread.paramTaint151 = o;
    }
    public static void setParamTaint152(Thread currentThread, PathTaint o) {
        currentThread.paramTaint152 = o;
    }
    public static void setParamTaint153(Thread currentThread, PathTaint o) {
        currentThread.paramTaint153 = o;
    }
    public static void setParamTaint154(Thread currentThread, PathTaint o) {
        currentThread.paramTaint154 = o;
    }
    public static void setParamTaint155(Thread currentThread, PathTaint o) {
        currentThread.paramTaint155 = o;
    }
    public static void setParamTaint156(Thread currentThread, PathTaint o) {
        currentThread.paramTaint156 = o;
    }
    public static void setParamTaint157(Thread currentThread, PathTaint o) {
        currentThread.paramTaint157 = o;
    }
    public static void setParamTaint158(Thread currentThread, PathTaint o) {
        currentThread.paramTaint158 = o;
    }
    public static void setParamTaint159(Thread currentThread, PathTaint o) {
        currentThread.paramTaint159 = o;
    }



    public static void setParamTaintTaintDroid0int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid0int = val;
    }

    public static void setParamTaintTaintDroid1int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid1int = val;
    }

    public static void setParamTaintTaintDroid2int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid2int = val;
    }

    public static void setParamTaintTaintDroid3int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid3int = val;
    }

    public static void setParamTaintTaintDroid4int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid4int = val;
    }

    public static void setParamTaintTaintDroid5int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid5int = val;
    }

    public static void setParamTaintTaintDroid6int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid6int = val;
    }

    public static void setParamTaintTaintDroid7int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid7int = val;
    }

    public static void setParamTaintTaintDroid8int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid8int = val;
    }

    public static void setParamTaintTaintDroid9int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid9int = val;
    }

    public static void setParamTaintTaintDroid10int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid10int = val;
    }

    public static void setParamTaintTaintDroid11int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid11int = val;
    }

    public static void setParamTaintTaintDroid12int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid12int = val;
    }

    public static void setParamTaintTaintDroid13int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid13int = val;
    }

    public static void setParamTaintTaintDroid14int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid14int = val;
    }

    public static void setParamTaintTaintDroid15int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid15int = val;
    }

    public static void setParamTaintTaintDroid16int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid16int = val;
    }

    public static void setParamTaintTaintDroid17int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid17int = val;
    }

    public static void setParamTaintTaintDroid18int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid18int = val;
    }

    public static void setParamTaintTaintDroid19int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid19int = val;
    }

    public static void setParamTaintTaintDroid20int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid20int = val;
    }

    public static void setParamTaintTaintDroid21int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid21int = val;
    }

    public static void setParamTaintTaintDroid22int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid22int = val;
    }

    public static void setParamTaintTaintDroid23int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid23int = val;
    }

    public static void setParamTaintTaintDroid24int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid24int = val;
    }

    public static void setParamTaintTaintDroid25int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid25int = val;
    }

    public static void setParamTaintTaintDroid26int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid26int = val;
    }

    public static void setParamTaintTaintDroid27int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid27int = val;
    }

    public static void setParamTaintTaintDroid28int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid28int = val;
    }

    public static void setParamTaintTaintDroid29int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid29int = val;
    }

    public static void setParamTaintTaintDroid30int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid30int = val;
    }

    public static void setParamTaintTaintDroid31int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid31int = val;
    }

    public static void setParamTaintTaintDroid32int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid32int = val;
    }

    public static void setParamTaintTaintDroid33int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid33int = val;
    }

    public static void setParamTaintTaintDroid34int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid34int = val;
    }

    public static void setParamTaintTaintDroid35int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid35int = val;
    }

    public static void setParamTaintTaintDroid36int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid36int = val;
    }

    public static void setParamTaintTaintDroid37int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid37int = val;
    }

    public static void setParamTaintTaintDroid38int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid38int = val;
    }

    public static void setParamTaintTaintDroid39int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid39int = val;
    }

    public static void setParamTaintTaintDroid40int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid40int = val;
    }

    public static void setParamTaintTaintDroid41int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid41int = val;
    }

    public static void setParamTaintTaintDroid42int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid42int = val;
    }

    public static void setParamTaintTaintDroid43int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid43int = val;
    }

    public static void setParamTaintTaintDroid44int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid44int = val;
    }

    public static void setParamTaintTaintDroid45int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid45int = val;
    }

    public static void setParamTaintTaintDroid46int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid46int = val;
    }

    public static void setParamTaintTaintDroid47int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid47int = val;
    }

    public static void setParamTaintTaintDroid48int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid48int = val;
    }

    public static void setParamTaintTaintDroid49int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid49int = val;
    }

    public static void setParamTaintTaintDroid50int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid50int = val;
    }

    public static void setParamTaintTaintDroid51int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid51int = val;
    }

    public static void setParamTaintTaintDroid52int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid52int = val;
    }

    public static void setParamTaintTaintDroid53int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid53int = val;
    }

    public static void setParamTaintTaintDroid54int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid54int = val;
    }

    public static void setParamTaintTaintDroid55int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid55int = val;
    }

    public static void setParamTaintTaintDroid56int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid56int = val;
    }

    public static void setParamTaintTaintDroid57int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid57int = val;
    }

    public static void setParamTaintTaintDroid58int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid58int = val;
    }

    public static void setParamTaintTaintDroid59int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid59int = val;
    }

    public static void setParamTaintTaintDroid60int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid60int = val;
    }

    public static void setParamTaintTaintDroid61int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid61int = val;
    }

    public static void setParamTaintTaintDroid62int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid62int = val;
    }

    public static void setParamTaintTaintDroid63int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid63int = val;
    }

    public static void setParamTaintTaintDroid64int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid64int = val;
    }

    public static void setParamTaintTaintDroid65int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid65int = val;
    }

    public static void setParamTaintTaintDroid66int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid66int = val;
    }

    public static void setParamTaintTaintDroid67int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid67int = val;
    }

    public static void setParamTaintTaintDroid68int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid68int = val;
    }

    public static void setParamTaintTaintDroid69int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid69int = val;
    }

    public static void setParamTaintTaintDroid70int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid70int = val;
    }

    public static void setParamTaintTaintDroid71int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid71int = val;
    }

    public static void setParamTaintTaintDroid72int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid72int = val;
    }

    public static void setParamTaintTaintDroid73int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid73int = val;
    }

    public static void setParamTaintTaintDroid74int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid74int = val;
    }

    public static void setParamTaintTaintDroid75int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid75int = val;
    }

    public static void setParamTaintTaintDroid76int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid76int = val;
    }

    public static void setParamTaintTaintDroid77int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid77int = val;
    }

    public static void setParamTaintTaintDroid78int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid78int = val;
    }

    public static void setParamTaintTaintDroid79int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid79int = val;
    }

    public static void setParamTaintTaintDroid80int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid80int = val;
    }

    public static void setParamTaintTaintDroid81int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid81int = val;
    }

    public static void setParamTaintTaintDroid82int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid82int = val;
    }

    public static void setParamTaintTaintDroid83int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid83int = val;
    }

    public static void setParamTaintTaintDroid84int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid84int = val;
    }

    public static void setParamTaintTaintDroid85int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid85int = val;
    }

    public static void setParamTaintTaintDroid86int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid86int = val;
    }

    public static void setParamTaintTaintDroid87int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid87int = val;
    }

    public static void setParamTaintTaintDroid88int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid88int = val;
    }

    public static void setParamTaintTaintDroid89int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid89int = val;
    }

    public static void setParamTaintTaintDroid90int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid90int = val;
    }

    public static void setParamTaintTaintDroid91int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid91int = val;
    }

    public static void setParamTaintTaintDroid92int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid92int = val;
    }

    public static void setParamTaintTaintDroid93int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid93int = val;
    }

    public static void setParamTaintTaintDroid94int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid94int = val;
    }

    public static void setParamTaintTaintDroid95int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid95int = val;
    }

    public static void setParamTaintTaintDroid96int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid96int = val;
    }

    public static void setParamTaintTaintDroid97int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid97int = val;
    }

    public static void setParamTaintTaintDroid98int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid98int = val;
    }

    public static void setParamTaintTaintDroid99int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid99int = val;
    }

    public static void setParamTaintTaintDroid100int(Thread currentThread, int val) {
        currentThread.paramTaintTaintDroid100int = val;
    }


    public static void setParamTaintTaintDroid0long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid0long = val;
    }

    public static void setParamTaintTaintDroid1long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid1long = val;
    }

    public static void setParamTaintTaintDroid2long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid2long = val;
    }

    public static void setParamTaintTaintDroid3long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid3long = val;
    }

    public static void setParamTaintTaintDroid4long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid4long = val;
    }

    public static void setParamTaintTaintDroid5long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid5long = val;
    }

    public static void setParamTaintTaintDroid6long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid6long = val;
    }

    public static void setParamTaintTaintDroid7long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid7long = val;
    }

    public static void setParamTaintTaintDroid8long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid8long = val;
    }

    public static void setParamTaintTaintDroid9long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid9long = val;
    }

    public static void setParamTaintTaintDroid10long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid10long = val;
    }

    public static void setParamTaintTaintDroid11long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid11long = val;
    }

    public static void setParamTaintTaintDroid12long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid12long = val;
    }

    public static void setParamTaintTaintDroid13long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid13long = val;
    }

    public static void setParamTaintTaintDroid14long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid14long = val;
    }

    public static void setParamTaintTaintDroid15long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid15long = val;
    }

    public static void setParamTaintTaintDroid16long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid16long = val;
    }

    public static void setParamTaintTaintDroid17long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid17long = val;
    }

    public static void setParamTaintTaintDroid18long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid18long = val;
    }

    public static void setParamTaintTaintDroid19long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid19long = val;
    }

    public static void setParamTaintTaintDroid20long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid20long = val;
    }

    public static void setParamTaintTaintDroid21long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid21long = val;
    }

    public static void setParamTaintTaintDroid22long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid22long = val;
    }

    public static void setParamTaintTaintDroid23long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid23long = val;
    }

    public static void setParamTaintTaintDroid24long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid24long = val;
    }

    public static void setParamTaintTaintDroid25long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid25long = val;
    }

    public static void setParamTaintTaintDroid26long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid26long = val;
    }

    public static void setParamTaintTaintDroid27long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid27long = val;
    }

    public static void setParamTaintTaintDroid28long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid28long = val;
    }

    public static void setParamTaintTaintDroid29long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid29long = val;
    }

    public static void setParamTaintTaintDroid30long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid30long = val;
    }

    public static void setParamTaintTaintDroid31long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid31long = val;
    }

    public static void setParamTaintTaintDroid32long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid32long = val;
    }

    public static void setParamTaintTaintDroid33long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid33long = val;
    }

    public static void setParamTaintTaintDroid34long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid34long = val;
    }

    public static void setParamTaintTaintDroid35long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid35long = val;
    }

    public static void setParamTaintTaintDroid36long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid36long = val;
    }

    public static void setParamTaintTaintDroid37long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid37long = val;
    }

    public static void setParamTaintTaintDroid38long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid38long = val;
    }

    public static void setParamTaintTaintDroid39long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid39long = val;
    }

    public static void setParamTaintTaintDroid40long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid40long = val;
    }

    public static void setParamTaintTaintDroid41long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid41long = val;
    }

    public static void setParamTaintTaintDroid42long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid42long = val;
    }

    public static void setParamTaintTaintDroid43long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid43long = val;
    }

    public static void setParamTaintTaintDroid44long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid44long = val;
    }

    public static void setParamTaintTaintDroid45long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid45long = val;
    }

    public static void setParamTaintTaintDroid46long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid46long = val;
    }

    public static void setParamTaintTaintDroid47long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid47long = val;
    }

    public static void setParamTaintTaintDroid48long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid48long = val;
    }

    public static void setParamTaintTaintDroid49long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid49long = val;
    }

    public static void setParamTaintTaintDroid50long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid50long = val;
    }

    public static void setParamTaintTaintDroid51long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid51long = val;
    }

    public static void setParamTaintTaintDroid52long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid52long = val;
    }

    public static void setParamTaintTaintDroid53long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid53long = val;
    }

    public static void setParamTaintTaintDroid54long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid54long = val;
    }

    public static void setParamTaintTaintDroid55long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid55long = val;
    }

    public static void setParamTaintTaintDroid56long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid56long = val;
    }

    public static void setParamTaintTaintDroid57long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid57long = val;
    }

    public static void setParamTaintTaintDroid58long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid58long = val;
    }

    public static void setParamTaintTaintDroid59long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid59long = val;
    }

    public static void setParamTaintTaintDroid60long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid60long = val;
    }

    public static void setParamTaintTaintDroid61long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid61long = val;
    }

    public static void setParamTaintTaintDroid62long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid62long = val;
    }

    public static void setParamTaintTaintDroid63long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid63long = val;
    }

    public static void setParamTaintTaintDroid64long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid64long = val;
    }

    public static void setParamTaintTaintDroid65long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid65long = val;
    }

    public static void setParamTaintTaintDroid66long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid66long = val;
    }

    public static void setParamTaintTaintDroid67long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid67long = val;
    }

    public static void setParamTaintTaintDroid68long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid68long = val;
    }

    public static void setParamTaintTaintDroid69long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid69long = val;
    }

    public static void setParamTaintTaintDroid70long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid70long = val;
    }

    public static void setParamTaintTaintDroid71long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid71long = val;
    }

    public static void setParamTaintTaintDroid72long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid72long = val;
    }

    public static void setParamTaintTaintDroid73long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid73long = val;
    }

    public static void setParamTaintTaintDroid74long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid74long = val;
    }

    public static void setParamTaintTaintDroid75long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid75long = val;
    }

    public static void setParamTaintTaintDroid76long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid76long = val;
    }

    public static void setParamTaintTaintDroid77long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid77long = val;
    }

    public static void setParamTaintTaintDroid78long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid78long = val;
    }

    public static void setParamTaintTaintDroid79long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid79long = val;
    }

    public static void setParamTaintTaintDroid80long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid80long = val;
    }

    public static void setParamTaintTaintDroid81long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid81long = val;
    }

    public static void setParamTaintTaintDroid82long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid82long = val;
    }

    public static void setParamTaintTaintDroid83long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid83long = val;
    }

    public static void setParamTaintTaintDroid84long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid84long = val;
    }

    public static void setParamTaintTaintDroid85long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid85long = val;
    }

    public static void setParamTaintTaintDroid86long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid86long = val;
    }

    public static void setParamTaintTaintDroid87long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid87long = val;
    }

    public static void setParamTaintTaintDroid88long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid88long = val;
    }

    public static void setParamTaintTaintDroid89long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid89long = val;
    }

    public static void setParamTaintTaintDroid90long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid90long = val;
    }

    public static void setParamTaintTaintDroid91long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid91long = val;
    }

    public static void setParamTaintTaintDroid92long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid92long = val;
    }

    public static void setParamTaintTaintDroid93long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid93long = val;
    }

    public static void setParamTaintTaintDroid94long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid94long = val;
    }

    public static void setParamTaintTaintDroid95long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid95long = val;
    }

    public static void setParamTaintTaintDroid96long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid96long = val;
    }

    public static void setParamTaintTaintDroid97long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid97long = val;
    }

    public static void setParamTaintTaintDroid98long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid98long = val;
    }

    public static void setParamTaintTaintDroid99long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid99long = val;
    }

    public static void setParamTaintTaintDroid100long(Thread currentThread, long val) {
        currentThread.paramTaintTaintDroid100long = val;
    }

    public static long getNativeCurrentTime() {
        return currentTime();
    }

    private native static long currentTime();

    /**
     * Set the blocker field; invoked via sun.misc.SharedSecrets from java.nio code
     *
     * @hide
     */
    public void blockedOn(Interruptible b) {
        synchronized (blockerLock) {
            blocker = b;
        }
    }

    /**
     * The minimum priority that a thread can have.
     */
    public final static int MIN_PRIORITY = 1;

   /**
     * The default priority that is assigned to a thread.
     */
    public final static int NORM_PRIORITY = 5;

    /**
     * The maximum priority that a thread can have.
     */
    public final static int MAX_PRIORITY = 10;

    /**
     * Returns a reference to the currently executing thread object.
     *
     * @return  the currently executing thread.
     */
    @FastNative
    public static native Thread currentThread();

    /**
     * A hint to the scheduler that the current thread is willing to yield
     * its current use of a processor. The scheduler is free to ignore this
     * hint.
     *
     * <p> Yield is a heuristic attempt to improve relative progression
     * between threads that would otherwise over-utilise a CPU. Its use
     * should be combined with detailed profiling and benchmarking to
     * ensure that it actually has the desired effect.
     *
     * <p> It is rarely appropriate to use this method. It may be useful
     * for debugging or testing purposes, where it may help to reproduce
     * bugs due to race conditions. It may also be useful when designing
     * concurrency control constructs such as the ones in the
     * {@link java.util.concurrent.locks} package.
     */
    public static native void yield();

    /**
     * Causes the currently executing thread to sleep (temporarily cease
     * execution) for the specified number of milliseconds, subject to
     * the precision and accuracy of system timers and schedulers. The thread
     * does not lose ownership of any monitors.
     *
     * @param  millis
     *         the length of time to sleep in milliseconds
     *
     * @throws  IllegalArgumentException
     *          if the value of {@code millis} is negative
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis, 0);
    }

    @FastNative
    private static native void sleep(Object lock, long millis, int nanos)
        throws InterruptedException;

    /**
     * Causes the currently executing thread to sleep (temporarily cease
     * execution) for the specified number of milliseconds plus the specified
     * number of nanoseconds, subject to the precision and accuracy of system
     * timers and schedulers. The thread does not lose ownership of any
     * monitors.
     *
     * @param  millis
     *         the length of time to sleep in milliseconds
     *
     * @param  nanos
     *         {@code 0-999999} additional nanoseconds to sleep
     *
     * @throws  IllegalArgumentException
     *          if the value of {@code millis} is negative, or the value of
     *          {@code nanos} is not in the range {@code 0-999999}
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public static void sleep(long millis, int nanos)
    throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("millis < 0: " + millis);
        }
        if (nanos < 0) {
            throw new IllegalArgumentException("nanos < 0: " + nanos);
        }
        if (nanos > 999999) {
            throw new IllegalArgumentException("nanos > 999999: " + nanos);
        }

        // The JLS 3rd edition, section 17.9 says: "...sleep for zero
        // time...need not have observable effects."
        if (millis == 0 && nanos == 0) {
            // ...but we still have to handle being interrupted.
            if (Thread.interrupted()) {
              throw new InterruptedException();
            }
            return;
        }

        long start = System.nanoTime();
        long duration = (millis * NANOS_PER_MILLI) + nanos;

        Object lock = currentThread().lock;

        // Wait may return early, so loop until sleep duration passes.
        synchronized (lock) {
            while (true) {
                sleep(lock, millis, nanos);

                long now = System.nanoTime();
                long elapsed = now - start;

                if (elapsed >= duration) {
                    break;
                }

                duration -= elapsed;
                start = now;
                millis = duration / NANOS_PER_MILLI;
                nanos = (int) (duration % NANOS_PER_MILLI);
            }
        }
    }

    /**
     * Initializes a Thread.
     *
     * @param g the Thread group
     * @param target the object whose run() method gets called
     * @param name the name of the new Thread
     * @param stackSize the desired stack size for the new thread, or
     *        zero to indicate that this parameter is to be ignored.
     */
    private void init(ThreadGroup g, Runnable target, String name, long stackSize) {
        Thread parent = currentThread();
        if (g == null) {
            g = parent.getThreadGroup();
        }

        g.addUnstarted();
        this.group = g;

        this.target = target;
        this.priority = parent.getPriority();
        this.daemon = parent.isDaemon();
        setName(name);

        init2(parent);

        /* Stash the specified stack size in case the VM cares */
        this.stackSize = stackSize;
        tid = nextThreadID();
    }

    /**
     * Throws CloneNotSupportedException as a Thread can not be meaningfully
     * cloned. Construct a new Thread instead.
     *
     * @throws  CloneNotSupportedException
     *          always
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /**
     * Allocates a new {@code Thread} object. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (null, null, gname)}, where {@code gname} is a newly generated
     * name. Automatically generated names are of the form
     * {@code "Thread-"+}<i>n</i>, where <i>n</i> is an integer.
     */
    public Thread() {
        init(null, null, "Thread-" + nextThreadNum(), 0);
    }

    /**
     * Allocates a new {@code Thread} object. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (null, target, gname)}, where {@code gname} is a newly generated
     * name. Automatically generated names are of the form
     * {@code "Thread-"+}<i>n</i>, where <i>n</i> is an integer.
     *
     * @param  target
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this classes {@code run} method does
     *         nothing.
     */
    public Thread(Runnable target) {
        init(null, target, "Thread-" + nextThreadNum(), 0);
    }

    /**
     * Allocates a new {@code Thread} object. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (group, target, gname)} ,where {@code gname} is a newly generated
     * name. Automatically generated names are of the form
     * {@code "Thread-"+}<i>n</i>, where <i>n</i> is an integer.
     *
     * @param  group
     *         the thread group. If {@code null} and there is a security
     *         manager, the group is determined by {@linkplain
     *         SecurityManager#getThreadGroup SecurityManager.getThreadGroup()}.
     *         If there is not a security manager or {@code
     *         SecurityManager.getThreadGroup()} returns {@code null}, the group
     *         is set to the current thread's thread group.
     *
     * @param  target
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this thread's run method is invoked.
     *
     * @throws  SecurityException
     *          if the current thread cannot create a thread in the specified
     *          thread group
     */
    public Thread(ThreadGroup group, Runnable target) {
        init(group, target, "Thread-" + nextThreadNum(), 0);
    }

    /**
     * Allocates a new {@code Thread} object. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (null, null, name)}.
     *
     * @param   name
     *          the name of the new thread
     */
    public Thread(String name) {
        init(null, null, name, 0);
    }

    /**
     * Allocates a new {@code Thread} object. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (group, null, name)}.
     *
     * @param  group
     *         the thread group. If {@code null} and there is a security
     *         manager, the group is determined by {@linkplain
     *         SecurityManager#getThreadGroup SecurityManager.getThreadGroup()}.
     *         If there is not a security manager or {@code
     *         SecurityManager.getThreadGroup()} returns {@code null}, the group
     *         is set to the current thread's thread group.
     *
     * @param  name
     *         the name of the new thread
     *
     * @throws  SecurityException
     *          if the current thread cannot create a thread in the specified
     *          thread group
     */
    public Thread(ThreadGroup group, String name) {
        init(group, null, name, 0);
    }


    /** @hide */
    // Android-added: Private constructor - used by the runtime.
    Thread(ThreadGroup group, String name, int priority, boolean daemon) {
        this.group = group;
        this.group.addUnstarted();
        // Must be tolerant of threads without a name.
        if (name == null) {
            name = "Thread-" + nextThreadNum();
        }

        // NOTE: Resist the temptation to call setName() here. This constructor is only called
        // by the runtime to construct peers for threads that have attached via JNI and it's
        // undesirable to clobber their natively set name.
        this.name = name;

        this.priority = priority;
        this.daemon = daemon;
        init2(currentThread());
        tid = nextThreadID();
    }

    private void init2(Thread parent) {
        this.contextClassLoader = parent.getContextClassLoader();
        this.inheritedAccessControlContext = AccessController.getContext();
        if (parent.inheritableThreadLocals != null) {
            this.inheritableThreadLocals = ThreadLocal.createInheritedMap(
                    parent.inheritableThreadLocals);
        }
    }

    /**
     * Allocates a new {@code Thread} object. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (null, target, name)}.
     *
     * @param  target
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this thread's run method is invoked.
     *
     * @param  name
     *         the name of the new thread
     */
    public Thread(Runnable target, String name) {
        init(null, target, name, 0);
    }

    /**
     * Allocates a new {@code Thread} object so that it has {@code target}
     * as its run object, has the specified {@code name} as its name,
     * and belongs to the thread group referred to by {@code group}.
     *
     * <p>If there is a security manager, its
     * {@link SecurityManager#checkAccess(ThreadGroup) checkAccess}
     * method is invoked with the ThreadGroup as its argument.
     *
     * <p>In addition, its {@code checkPermission} method is invoked with
     * the {@code RuntimePermission("enableContextClassLoaderOverride")}
     * permission when invoked directly or indirectly by the constructor
     * of a subclass which overrides the {@code getContextClassLoader}
     * or {@code setContextClassLoader} methods.
     *
     * <p>The priority of the newly created thread is set equal to the
     * priority of the thread creating it, that is, the currently running
     * thread. The method {@linkplain #setPriority setPriority} may be
     * used to change the priority to a new value.
     *
     * <p>The newly created thread is initially marked as being a daemon
     * thread if and only if the thread creating it is currently marked
     * as a daemon thread. The method {@linkplain #setDaemon setDaemon}
     * may be used to change whether or not a thread is a daemon.
     *
     * @param  group
     *         the thread group. If {@code null} and there is a security
     *         manager, the group is determined by {@linkplain
     *         SecurityManager#getThreadGroup SecurityManager.getThreadGroup()}.
     *         If there is not a security manager or {@code
     *         SecurityManager.getThreadGroup()} returns {@code null}, the group
     *         is set to the current thread's thread group.
     *
     * @param  target
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this thread's run method is invoked.
     *
     * @param  name
     *         the name of the new thread
     *
     * @throws  SecurityException
     *          if the current thread cannot create a thread in the specified
     *          thread group or cannot override the context class loader methods.
     */
    public Thread(ThreadGroup group, Runnable target, String name) {
        init(group, target, name, 0);
    }

    /**
     * Allocates a new {@code Thread} object so that it has {@code target}
     * as its run object, has the specified {@code name} as its name,
     * and belongs to the thread group referred to by {@code group}, and has
     * the specified <i>stack size</i>.
     *
     * <p>This constructor is identical to {@link
     * #Thread(ThreadGroup,Runnable,String)} with the exception of the fact
     * that it allows the thread stack size to be specified.  The stack size
     * is the approximate number of bytes of address space that the virtual
     * machine is to allocate for this thread's stack.  <b>The effect of the
     * {@code stackSize} parameter, if any, is highly platform dependent.</b>
     *
     * <p>On some platforms, specifying a higher value for the
     * {@code stackSize} parameter may allow a thread to achieve greater
     * recursion depth before throwing a {@link StackOverflowError}.
     * Similarly, specifying a lower value may allow a greater number of
     * threads to exist concurrently without throwing an {@link
     * OutOfMemoryError} (or other internal error).  The details of
     * the relationship between the value of the <tt>stackSize</tt> parameter
     * and the maximum recursion depth and concurrency level are
     * platform-dependent.  <b>On some platforms, the value of the
     * {@code stackSize} parameter may have no effect whatsoever.</b>
     *
     * <p>The virtual machine is free to treat the {@code stackSize}
     * parameter as a suggestion.  If the specified value is unreasonably low
     * for the platform, the virtual machine may instead use some
     * platform-specific minimum value; if the specified value is unreasonably
     * high, the virtual machine may instead use some platform-specific
     * maximum.  Likewise, the virtual machine is free to round the specified
     * value up or down as it sees fit (or to ignore it completely).
     *
     * <p>Specifying a value of zero for the {@code stackSize} parameter will
     * cause this constructor to behave exactly like the
     * {@code Thread(ThreadGroup, Runnable, String)} constructor.
     *
     * <p><i>Due to the platform-dependent nature of the behavior of this
     * constructor, extreme care should be exercised in its use.
     * The thread stack size necessary to perform a given computation will
     * likely vary from one JRE implementation to another.  In light of this
     * variation, careful tuning of the stack size parameter may be required,
     * and the tuning may need to be repeated for each JRE implementation on
     * which an application is to run.</i>
     *
     * <p>Implementation note: Java platform implementers are encouraged to
     * document their implementation's behavior with respect to the
     * {@code stackSize} parameter.
     *
     *
     * @param  group
     *         the thread group. If {@code null} and there is a security
     *         manager, the group is determined by {@linkplain
     *         SecurityManager#getThreadGroup SecurityManager.getThreadGroup()}.
     *         If there is not a security manager or {@code
     *         SecurityManager.getThreadGroup()} returns {@code null}, the group
     *         is set to the current thread's thread group.
     *
     * @param  target
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this thread's run method is invoked.
     *
     * @param  name
     *         the name of the new thread
     *
     * @param  stackSize
     *         the desired stack size for the new thread, or zero to indicate
     *         that this parameter is to be ignored.
     *
     * @throws  SecurityException
     *          if the current thread cannot create a thread in the specified
     *          thread group
     *
     * @since 1.4
     */
    public Thread(ThreadGroup group, Runnable target, String name,
                  long stackSize) {
        init(group, target, name, stackSize);
    }

    /**
     * Causes this thread to begin execution; the Java Virtual Machine
     * calls the <code>run</code> method of this thread.
     * <p>
     * The result is that two threads are running concurrently: the
     * current thread (which returns from the call to the
     * <code>start</code> method) and the other thread (which executes its
     * <code>run</code> method).
     * <p>
     * It is never legal to start a thread more than once.
     * In particular, a thread may not be restarted once it has completed
     * execution.
     *
     * @exception  IllegalThreadStateException  if the thread was already
     *               started.
     * @see        #run()
     * @see        #stop()
     */
    public synchronized void start() {
        /**
         * This method is not invoked for the main method thread or "system"
         * group threads created/set up by the VM. Any new functionality added
         * to this method in the future may have to also be added to the VM.
         *
         * A zero status value corresponds to state "NEW".
         */
        // Android-changed: throw if 'started' is true
        if (threadStatus != 0 || started)
            throw new IllegalThreadStateException();

        /* Notify the group that this thread is about to be started
         * so that it can be added to the group's list of threads
         * and the group's unstarted count can be decremented. */
        group.add(this);

        started = false;
        try {
            nativeCreate(this, stackSize, daemon);
            started = true;
        } finally {
            try {
                if (!started) {
                    group.threadStartFailed(this);
                }
            } catch (Throwable ignore) {
                /* do nothing. If start0 threw a Throwable then
                  it will be passed up the call stack */
            }
        }
    }

    private native static void nativeCreate(Thread t, long stackSize, boolean daemon);

    /**
     * If this thread was constructed using a separate
     * <code>Runnable</code> run object, then that
     * <code>Runnable</code> object's <code>run</code> method is called;
     * otherwise, this method does nothing and returns.
     * <p>
     * Subclasses of <code>Thread</code> should override this method.
     *
     * @see     #start()
     * @see     #stop()
     * @see     #Thread(ThreadGroup, Runnable, String)
     */
    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    /**
     * This method is called by the system to give a Thread
     * a chance to clean up before it actually exits.
     */
    private void exit() {
        if (group != null) {
            group.threadTerminated(this);
            group = null;
        }
        /* Aggressively null out all reference fields: see bug 4006245 */
        target = null;
        /* Speed the release of some of these resources */
        threadLocals = null;
        inheritableThreadLocals = null;
        inheritedAccessControlContext = null;
        blocker = null;
        uncaughtExceptionHandler = null;
    }

    /**
     * Forces the thread to stop executing.
     * <p>
     * If there is a security manager installed, its <code>checkAccess</code>
     * method is called with <code>this</code>
     * as its argument. This may result in a
     * <code>SecurityException</code> being raised (in the current thread).
     * <p>
     * If this thread is different from the current thread (that is, the current
     * thread is trying to stop a thread other than itself), the
     * security manager's <code>checkPermission</code> method (with a
     * <code>RuntimePermission("stopThread")</code> argument) is called in
     * addition.
     * Again, this may result in throwing a
     * <code>SecurityException</code> (in the current thread).
     * <p>
     * The thread represented by this thread is forced to stop whatever
     * it is doing abnormally and to throw a newly created
     * <code>ThreadDeath</code> object as an exception.
     * <p>
     * It is permitted to stop a thread that has not yet been started.
     * If the thread is eventually started, it immediately terminates.
     * <p>
     * An application should not normally try to catch
     * <code>ThreadDeath</code> unless it must do some extraordinary
     * cleanup operation (note that the throwing of
     * <code>ThreadDeath</code> causes <code>finally</code> clauses of
     * <code>try</code> statements to be executed before the thread
     * officially dies).  If a <code>catch</code> clause catches a
     * <code>ThreadDeath</code> object, it is important to rethrow the
     * object so that the thread actually dies.
     * <p>
     * The top-level error handler that reacts to otherwise uncaught
     * exceptions does not print out a message or otherwise notify the
     * application if the uncaught exception is an instance of
     * <code>ThreadDeath</code>.
     *
     * @exception  SecurityException  if the current thread cannot
     *               modify this thread.
     * @see        #interrupt()
     * @see        #checkAccess()
     * @see        #run()
     * @see        #start()
     * @see        ThreadDeath
     * @see        ThreadGroup#uncaughtException(Thread,Throwable)
     * @see        SecurityManager#checkAccess(Thread)
     * @see        SecurityManager#checkPermission
     * @deprecated This method is inherently unsafe.  Stopping a thread with
     *       Thread.stop causes it to unlock all of the monitors that it
     *       has locked (as a natural consequence of the unchecked
     *       <code>ThreadDeath</code> exception propagating up the stack).  If
     *       any of the objects previously protected by these monitors were in
     *       an inconsistent state, the damaged objects become visible to
     *       other threads, potentially resulting in arbitrary behavior.  Many
     *       uses of <code>stop</code> should be replaced by code that simply
     *       modifies some variable to indicate that the target thread should
     *       stop running.  The target thread should check this variable
     *       regularly, and return from its run method in an orderly fashion
     *       if the variable indicates that it is to stop running.  If the
     *       target thread waits for long periods (on a condition variable,
     *       for example), the <code>interrupt</code> method should be used to
     *       interrupt the wait.
     *       For more information, see
     *       <a href="{@docRoot}openjdk-redirect.html?v=8&path=/technotes/guides/concurrency/threadPrimitiveDeprecation.html">Why
     *       are Thread.stop, Thread.suspend and Thread.resume Deprecated?</a>.
     */
    @Deprecated
    public final void stop() {
        stop(new ThreadDeath());
    }

    /**
     * Throws {@code UnsupportedOperationException}.
     *
     * @param obj ignored
     *
     * @deprecated This method was originally designed to force a thread to stop
     *        and throw a given {@code Throwable} as an exception. It was
     *        inherently unsafe (see {@link #stop()} for details), and furthermore
     *        could be used to generate exceptions that the target thread was
     *        not prepared to handle.
     *        For more information, see
     *        <a href="{@docRoot}openjdk-redirect.html?v=8&path=/technotes/guides/concurrency/threadPrimitiveDeprecation.html">Why
     *        are Thread.stop, Thread.suspend and Thread.resume Deprecated?</a>.
     */
    @Deprecated
    public final void stop(Throwable obj) {
        throw new UnsupportedOperationException();
    }

    /**
     * Interrupts this thread.
     *
     * <p> Unless the current thread is interrupting itself, which is
     * always permitted, the {@link #checkAccess() checkAccess} method
     * of this thread is invoked, which may cause a {@link
     * SecurityException} to be thrown.
     *
     * <p> If this thread is blocked in an invocation of the {@link
     * Object#wait() wait()}, {@link Object#wait(long) wait(long)}, or {@link
     * Object#wait(long, int) wait(long, int)} methods of the {@link Object}
     * class, or of the {@link #join()}, {@link #join(long)}, {@link
     * #join(long, int)}, {@link #sleep(long)}, or {@link #sleep(long, int)},
     * methods of this class, then its interrupt status will be cleared and it
     * will receive an {@link InterruptedException}.
     *
     * <p> If this thread is blocked in an I/O operation upon an {@link
     * java.nio.channels.InterruptibleChannel InterruptibleChannel}
     * then the channel will be closed, the thread's interrupt
     * status will be set, and the thread will receive a {@link
     * java.nio.channels.ClosedByInterruptException}.
     *
     * <p> If this thread is blocked in a {@link java.nio.channels.Selector}
     * then the thread's interrupt status will be set and it will return
     * immediately from the selection operation, possibly with a non-zero
     * value, just as if the selector's {@link
     * java.nio.channels.Selector#wakeup wakeup} method were invoked.
     *
     * <p> If none of the previous conditions hold then this thread's interrupt
     * status will be set. </p>
     *
     * <p> Interrupting a thread that is not alive need not have any effect.
     *
     * @throws  SecurityException
     *          if the current thread cannot modify this thread
     *
     * @revised 6.0
     * @spec JSR-51
     */
    public void interrupt() {
        if (this != Thread.currentThread())
            checkAccess();

        synchronized (blockerLock) {
            Interruptible b = blocker;
            if (b != null) {
                nativeInterrupt();
                b.interrupt(this);
                return;
            }
        }
        nativeInterrupt();
    }

    /**
     * Tests whether the current thread has been interrupted.  The
     * <i>interrupted status</i> of the thread is cleared by this method.  In
     * other words, if this method were to be called twice in succession, the
     * second call would return false (unless the current thread were
     * interrupted again, after the first call had cleared its interrupted
     * status and before the second call had examined it).
     *
     * <p>A thread interruption ignored because a thread was not alive
     * at the time of the interrupt will be reflected by this method
     * returning false.
     *
     * @return  <code>true</code> if the current thread has been interrupted;
     *          <code>false</code> otherwise.
     * @see #isInterrupted()
     * @revised 6.0
     */
    @FastNative
    public static native boolean interrupted();

    /**
     * Tests whether this thread has been interrupted.  The <i>interrupted
     * status</i> of the thread is unaffected by this method.
     *
     * <p>A thread interruption ignored because a thread was not alive
     * at the time of the interrupt will be reflected by this method
     * returning false.
     *
     * @return  <code>true</code> if this thread has been interrupted;
     *          <code>false</code> otherwise.
     * @see     #interrupted()
     * @revised 6.0
     */
    @FastNative
    public native boolean isInterrupted();

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated This method was originally designed to destroy this
     *     thread without any cleanup. Any monitors it held would have
     *     remained locked. However, the method was never implemented.
     *     If if were to be implemented, it would be deadlock-prone in
     *     much the manner of {@link #suspend}. If the target thread held
     *     a lock protecting a critical system resource when it was
     *     destroyed, no thread could ever access this resource again.
     *     If another thread ever attempted to lock this resource, deadlock
     *     would result. Such deadlocks typically manifest themselves as
     *     "frozen" processes. For more information, see
     *     <a href="{@docRoot}openjdk-redirect.html?v=8&path=/technotes/guides/concurrency/threadPrimitiveDeprecation.html">
     *     Why are Thread.stop, Thread.suspend and Thread.resume Deprecated?</a>.
     * @throws UnsupportedOperationException always
     */
    // Android-changed: Throw UnsupportedOperationException instead of
    // NoSuchMethodError.
    @Deprecated
    public void destroy() {
        throw new UnsupportedOperationException();
    }

    /**
     * Tests if this thread is alive. A thread is alive if it has
     * been started and has not yet died.
     *
     * @return  <code>true</code> if this thread is alive;
     *          <code>false</code> otherwise.
     */
    public final boolean isAlive() {
        return nativePeer != 0;
    }

    /**
     * Suspends this thread.
     * <p>
     * First, the <code>checkAccess</code> method of this thread is called
     * with no arguments. This may result in throwing a
     * <code>SecurityException </code>(in the current thread).
     * <p>
     * If the thread is alive, it is suspended and makes no further
     * progress unless and until it is resumed.
     *
     * @exception  SecurityException  if the current thread cannot modify
     *               this thread.
     * @see #checkAccess
     * @deprecated   This method has been deprecated, as it is
     *   inherently deadlock-prone.  If the target thread holds a lock on the
     *   monitor protecting a critical system resource when it is suspended, no
     *   thread can access this resource until the target thread is resumed. If
     *   the thread that would resume the target thread attempts to lock this
     *   monitor prior to calling <code>resume</code>, deadlock results.  Such
     *   deadlocks typically manifest themselves as "frozen" processes.
     *   For more information, see
     *   <a href="{@docRoot}openjdk-redirect.html?v=8&path=/technotes/guides/concurrency/threadPrimitiveDeprecation.html">Why
     *   are Thread.stop, Thread.suspend and Thread.resume Deprecated?</a>.
     */
    @Deprecated
    public final void suspend() {
        throw new UnsupportedOperationException();
    }

    /**
     * Resumes a suspended thread.
     * <p>
     * First, the <code>checkAccess</code> method of this thread is called
     * with no arguments. This may result in throwing a
     * <code>SecurityException</code> (in the current thread).
     * <p>
     * If the thread is alive but suspended, it is resumed and is
     * permitted to make progress in its execution.
     *
     * @exception  SecurityException  if the current thread cannot modify this
     *               thread.
     * @see        #checkAccess
     * @see        #suspend()
     * @deprecated This method exists solely for use with {@link #suspend},
     *     which has been deprecated because it is deadlock-prone.
     *     For more information, see
     *     <a href="{@docRoot}openjdk-redirect.html?v=8&path=/technotes/guides/concurrency/threadPrimitiveDeprecation.html">Why
     *     are Thread.stop, Thread.suspend and Thread.resume Deprecated?</a>.
     */
    @Deprecated
    public final void resume() {
        throw new UnsupportedOperationException();
    }

    /**
     * Changes the priority of this thread.
     * <p>
     * First the <code>checkAccess</code> method of this thread is called
     * with no arguments. This may result in throwing a
     * <code>SecurityException</code>.
     * <p>
     * Otherwise, the priority of this thread is set to the smaller of
     * the specified <code>newPriority</code> and the maximum permitted
     * priority of the thread's thread group.
     *
     * @param newPriority priority to set this thread to
     * @exception  IllegalArgumentException  If the priority is not in the
     *               range <code>MIN_PRIORITY</code> to
     *               <code>MAX_PRIORITY</code>.
     * @exception  SecurityException  if the current thread cannot modify
     *               this thread.
     * @see        #getPriority
     * @see        #checkAccess()
     * @see        #getThreadGroup()
     * @see        #MAX_PRIORITY
     * @see        #MIN_PRIORITY
     * @see        ThreadGroup#getMaxPriority()
     */
    public final void setPriority(int newPriority) {
        ThreadGroup g;
        checkAccess();
        if (newPriority > MAX_PRIORITY || newPriority < MIN_PRIORITY) {
            // Android-changed: Improve exception message when the new priority
            // is out of bounds.
            throw new IllegalArgumentException("Priority out of range: " + newPriority);
        }
        if((g = getThreadGroup()) != null) {
            if (newPriority > g.getMaxPriority()) {
                newPriority = g.getMaxPriority();
            }
            synchronized(this) {
                this.priority = newPriority;
                if (isAlive()) {
                    nativeSetPriority(newPriority);
                }
            }
        }
    }

    /**
     * Returns this thread's priority.
     *
     * @return  this thread's priority.
     * @see     #setPriority
     */
    public final int getPriority() {
        return priority;
    }

    /**
     * Changes the name of this thread to be equal to the argument
     * <code>name</code>.
     * <p>
     * First the <code>checkAccess</code> method of this thread is called
     * with no arguments. This may result in throwing a
     * <code>SecurityException</code>.
     *
     * @param      name   the new name for this thread.
     * @exception  SecurityException  if the current thread cannot modify this
     *               thread.
     * @see        #getName
     * @see        #checkAccess()
     */
    public final void setName(String name) {
        checkAccess();
        if (name == null) {
            throw new NullPointerException("name == null");
        }

        synchronized (this) {
            this.name = name;
            if (isAlive()) {
                nativeSetName(name);
            }
        }
    }

    /**
     * Returns this thread's name.
     *
     * @return  this thread's name.
     * @see     #setName(String)
     */
    public final String getName() {
        return name;
    }

    /**
     * Returns the thread group to which this thread belongs.
     * This method returns null if this thread has died
     * (been stopped).
     *
     * @return  this thread's thread group.
     */
    public final ThreadGroup getThreadGroup() {
        // Android-changed: Return null if the thread is terminated.
        if (getState() == Thread.State.TERMINATED) {
            return null;
        }
        return group;
    }

    /**
     * Returns an estimate of the number of active threads in the current
     * thread's {@linkplain java.lang.ThreadGroup thread group} and its
     * subgroups. Recursively iterates over all subgroups in the current
     * thread's thread group.
     *
     * <p> The value returned is only an estimate because the number of
     * threads may change dynamically while this method traverses internal
     * data structures, and might be affected by the presence of certain
     * system threads. This method is intended primarily for debugging
     * and monitoring purposes.
     *
     * @return  an estimate of the number of active threads in the current
     *          thread's thread group and in any other thread group that
     *          has the current thread's thread group as an ancestor
     */
    public static int activeCount() {
        return currentThread().getThreadGroup().activeCount();
    }

    /**
     * Copies into the specified array every active thread in the current
     * thread's thread group and its subgroups. This method simply
     * invokes the {@link java.lang.ThreadGroup#enumerate(Thread[])}
     * method of the current thread's thread group.
     *
     * <p> An application might use the {@linkplain #activeCount activeCount}
     * method to get an estimate of how big the array should be, however
     * <i>if the array is too short to hold all the threads, the extra threads
     * are silently ignored.</i>  If it is critical to obtain every active
     * thread in the current thread's thread group and its subgroups, the
     * invoker should verify that the returned int value is strictly less
     * than the length of {@code tarray}.
     *
     * <p> Due to the inherent race condition in this method, it is recommended
     * that the method only be used for debugging and monitoring purposes.
     *
     * @param  tarray
     *         an array into which to put the list of threads
     *
     * @return  the number of threads put into the array
     *
     * @throws  SecurityException
     *          if {@link java.lang.ThreadGroup#checkAccess} determines that
     *          the current thread cannot access its thread group
     */
    public static int enumerate(Thread tarray[]) {
        return currentThread().getThreadGroup().enumerate(tarray);
    }

    /**
     * Counts the number of stack frames in this thread. The thread must
     * be suspended.
     *
     * @return     the number of stack frames in this thread.
     * @exception  IllegalThreadStateException  if this thread is not
     *             suspended.
     * @deprecated The definition of this call depends on {@link #suspend},
     *             which is deprecated.  Further, the results of this call
     *             were never well-defined.
     */
    @Deprecated
    public int countStackFrames() {
        return getStackTrace().length;
    }

    /**
     * Waits at most {@code millis} milliseconds for this thread to
     * die. A timeout of {@code 0} means to wait forever.
     *
     * <p> This implementation uses a loop of {@code this.wait} calls
     * conditioned on {@code this.isAlive}. As a thread terminates the
     * {@code this.notifyAll} method is invoked. It is recommended that
     * applications not use {@code wait}, {@code notify}, or
     * {@code notifyAll} on {@code Thread} instances.
     *
     * @param  millis
     *         the time to wait in milliseconds
     *
     * @throws  IllegalArgumentException
     *          if the value of {@code millis} is negative
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public final void join(long millis) throws InterruptedException {
        synchronized(lock) {
        long base = System.currentTimeMillis();
        long now = 0;

        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (millis == 0) {
            while (isAlive()) {
                lock.wait(0);
            }
        } else {
            while (isAlive()) {
                long delay = millis - now;
                if (delay <= 0) {
                    break;
                }
                lock.wait(delay);
                now = System.currentTimeMillis() - base;
            }
        }
        }
    }

    /**
     * Waits at most {@code millis} milliseconds plus
     * {@code nanos} nanoseconds for this thread to die.
     *
     * <p> This implementation uses a loop of {@code this.wait} calls
     * conditioned on {@code this.isAlive}. As a thread terminates the
     * {@code this.notifyAll} method is invoked. It is recommended that
     * applications not use {@code wait}, {@code notify}, or
     * {@code notifyAll} on {@code Thread} instances.
     *
     * @param  millis
     *         the time to wait in milliseconds
     *
     * @param  nanos
     *         {@code 0-999999} additional nanoseconds to wait
     *
     * @throws  IllegalArgumentException
     *          if the value of {@code millis} is negative, or the value
     *          of {@code nanos} is not in the range {@code 0-999999}
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public final void join(long millis, int nanos)
    throws InterruptedException {
        synchronized(lock) {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException(
                                "nanosecond timeout value out of range");
        }

        if (nanos >= 500000 || (nanos != 0 && millis == 0)) {
            millis++;
        }

        join(millis);
        }
    }

    /**
     * Waits for this thread to die.
     *
     * <p> An invocation of this method behaves in exactly the same
     * way as the invocation
     *
     * <blockquote>
     * {@linkplain #join(long) join}{@code (0)}
     * </blockquote>
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public final void join() throws InterruptedException {
        join(0);
    }

    /**
     * Prints a stack trace of the current thread to the standard error stream.
     * This method is used only for debugging.
     *
     * @see     Throwable#printStackTrace()
     */
    public static void dumpStack() {
        new Exception("Stack trace").printStackTrace();
    }

    /**
     * Marks this thread as either a {@linkplain #isDaemon daemon} thread
     * or a user thread. The Java Virtual Machine exits when the only
     * threads running are all daemon threads.
     *
     * <p> This method must be invoked before the thread is started.
     *
     * @param  on
     *         if {@code true}, marks this thread as a daemon thread
     *
     * @throws  IllegalThreadStateException
     *          if this thread is {@linkplain #isAlive alive}
     *
     * @throws  SecurityException
     *          if {@link #checkAccess} determines that the current
     *          thread cannot modify this thread
     */
    public final void setDaemon(boolean on) {
        checkAccess();
        if (isAlive()) {
            throw new IllegalThreadStateException();
        }
        daemon = on;
    }

    /**
     * Tests if this thread is a daemon thread.
     *
     * @return  <code>true</code> if this thread is a daemon thread;
     *          <code>false</code> otherwise.
     * @see     #setDaemon(boolean)
     */
    public final boolean isDaemon() {
        return daemon;
    }

    /**
     * Determines if the currently running thread has permission to
     * modify this thread.
     * <p>
     * If there is a security manager, its <code>checkAccess</code> method
     * is called with this thread as its argument. This may result in
     * throwing a <code>SecurityException</code>.
     *
     * @exception  SecurityException  if the current thread is not allowed to
     *               access this thread.
     * @see        SecurityManager#checkAccess(Thread)
     */
    public final void checkAccess() {
    }

    /**
     * Returns a string representation of this thread, including the
     * thread's name, priority, and thread group.
     *
     * @return  a string representation of this thread.
     */
    public String toString() {
        ThreadGroup group = getThreadGroup();
        if (group != null) {
            return "Thread[" + getName() + "," + getPriority() + "," +
                           group.getName() + "]";
        } else {
            return "Thread[" + getName() + "," + getPriority() + "," +
                            "" + "]";
        }
    }

    /**
     * Returns the context ClassLoader for this Thread. The context
     * ClassLoader is provided by the creator of the thread for use
     * by code running in this thread when loading classes and resources.
     * If not {@linkplain #setContextClassLoader set}, the default is the
     * ClassLoader context of the parent Thread. The context ClassLoader of the
     * primordial thread is typically set to the class loader used to load the
     * application.
     *
     * <p>If a security manager is present, and the invoker's class loader is not
     * {@code null} and is not the same as or an ancestor of the context class
     * loader, then this method invokes the security manager's {@link
     * SecurityManager#checkPermission(java.security.Permission) checkPermission}
     * method with a {@link RuntimePermission RuntimePermission}{@code
     * ("getClassLoader")} permission to verify that retrieval of the context
     * class loader is permitted.
     *
     * @return  the context ClassLoader for this Thread, or {@code null}
     *          indicating the system class loader (or, failing that, the
     *          bootstrap class loader)
     *
     * @throws  SecurityException
     *          if the current thread cannot get the context ClassLoader
     *
     * @since 1.2
     */
    @CallerSensitive
    public ClassLoader getContextClassLoader() {
        return contextClassLoader;
    }

    /**
     * Sets the context ClassLoader for this Thread. The context
     * ClassLoader can be set when a thread is created, and allows
     * the creator of the thread to provide the appropriate class loader,
     * through {@code getContextClassLoader}, to code running in the thread
     * when loading classes and resources.
     *
     * <p>If a security manager is present, its {@link
     * SecurityManager#checkPermission(java.security.Permission) checkPermission}
     * method is invoked with a {@link RuntimePermission RuntimePermission}{@code
     * ("setContextClassLoader")} permission to see if setting the context
     * ClassLoader is permitted.
     *
     * @param  cl
     *         the context ClassLoader for this Thread, or null  indicating the
     *         system class loader (or, failing that, the bootstrap class loader)
     *
     * @throws  SecurityException
     *          if the current thread cannot set the context ClassLoader
     *
     * @since 1.2
     */
    public void setContextClassLoader(ClassLoader cl) {
        contextClassLoader = cl;
    }

    /**
     * Returns <tt>true</tt> if and only if the current thread holds the
     * monitor lock on the specified object.
     *
     * <p>This method is designed to allow a program to assert that
     * the current thread already holds a specified lock:
     * <pre>
     *     assert Thread.holdsLock(obj);
     * </pre>
     *
     * @param  obj the object on which to test lock ownership
     * @throws NullPointerException if obj is <tt>null</tt>
     * @return <tt>true</tt> if the current thread holds the monitor lock on
     *         the specified object.
     * @since 1.4
     */
    public static boolean holdsLock(Object obj) {
        return currentThread().nativeHoldsLock(obj);
    }

    private native boolean nativeHoldsLock(Object object);

    private static final StackTraceElement[] EMPTY_STACK_TRACE
        = new StackTraceElement[0];

    /**
     * Returns an array of stack trace elements representing the stack dump
     * of this thread.  This method will return a zero-length array if
     * this thread has not started, has started but has not yet been
     * scheduled to run by the system, or has terminated.
     * If the returned array is of non-zero length then the first element of
     * the array represents the top of the stack, which is the most recent
     * method invocation in the sequence.  The last element of the array
     * represents the bottom of the stack, which is the least recent method
     * invocation in the sequence.
     *
     * <p>If there is a security manager, and this thread is not
     * the current thread, then the security manager's
     * <tt>checkPermission</tt> method is called with a
     * <tt>RuntimePermission("getStackTrace")</tt> permission
     * to see if it's ok to get the stack trace.
     *
     * <p>Some virtual machines may, under some circumstances, omit one
     * or more stack frames from the stack trace.  In the extreme case,
     * a virtual machine that has no stack trace information concerning
     * this thread is permitted to return a zero-length array from this
     * method.
     *
     * @return an array of <tt>StackTraceElement</tt>,
     * each represents one stack frame.
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        <tt>checkPermission</tt> method doesn't allow
     *        getting the stack trace of thread.
     * @see SecurityManager#checkPermission
     * @see RuntimePermission
     * @see Throwable#getStackTrace
     *
     * @since 1.5
     */
    public StackTraceElement[] getStackTrace() {
        StackTraceElement ste[] = VMStack.getThreadStackTrace(this);
        return ste != null ? ste : EmptyArray.STACK_TRACE_ELEMENT;
    }

    /**
     * Returns a map of stack traces for all live threads.
     * The map keys are threads and each map value is an array of
     * <tt>StackTraceElement</tt> that represents the stack dump
     * of the corresponding <tt>Thread</tt>.
     * The returned stack traces are in the format specified for
     * the {@link #getStackTrace getStackTrace} method.
     *
     * <p>The threads may be executing while this method is called.
     * The stack trace of each thread only represents a snapshot and
     * each stack trace may be obtained at different time.  A zero-length
     * array will be returned in the map value if the virtual machine has
     * no stack trace information about a thread.
     *
     * <p>If there is a security manager, then the security manager's
     * <tt>checkPermission</tt> method is called with a
     * <tt>RuntimePermission("getStackTrace")</tt> permission as well as
     * <tt>RuntimePermission("modifyThreadGroup")</tt> permission
     * to see if it is ok to get the stack trace of all threads.
     *
     * @return a <tt>Map</tt> from <tt>Thread</tt> to an array of
     * <tt>StackTraceElement</tt> that represents the stack trace of
     * the corresponding thread.
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        <tt>checkPermission</tt> method doesn't allow
     *        getting the stack trace of thread.
     * @see #getStackTrace
     * @see SecurityManager#checkPermission
     * @see RuntimePermission
     * @see Throwable#getStackTrace
     *
     * @since 1.5
     */
    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        Map<Thread, StackTraceElement[]> map = new HashMap<Thread, StackTraceElement[]>();

        // Find out how many live threads we have. Allocate a bit more
        // space than needed, in case new ones are just being created.
        int count = ThreadGroup.systemThreadGroup.activeCount();
        Thread[] threads = new Thread[count + count / 2];

        // Enumerate the threads and collect the stacktraces.
        count = ThreadGroup.systemThreadGroup.enumerate(threads);
        for (int i = 0; i < count; i++) {
            map.put(threads[i], threads[i].getStackTrace());
        }

        return map;
    }


    private static final RuntimePermission SUBCLASS_IMPLEMENTATION_PERMISSION =
                    new RuntimePermission("enableContextClassLoaderOverride");

    /** cache of subclass security audit results */
    /* Replace with ConcurrentReferenceHashMap when/if it appears in a future
     * release */
    private static class Caches {
        /** cache of subclass security audit results */
        static final ConcurrentMap<WeakClassKey,Boolean> subclassAudits =
            new ConcurrentHashMap<>();

        /** queue for WeakReferences to audited subclasses */
        static final ReferenceQueue<Class<?>> subclassAuditsQueue =
            new ReferenceQueue<>();
    }

    /**
     * Verifies that this (possibly subclass) instance can be constructed
     * without violating security constraints: the subclass must not override
     * security-sensitive non-final methods, or else the
     * "enableContextClassLoaderOverride" RuntimePermission is checked.
     */
    private static boolean isCCLOverridden(Class<?> cl) {
        if (cl == Thread.class)
            return false;

        processQueue(Caches.subclassAuditsQueue, Caches.subclassAudits);
        WeakClassKey key = new WeakClassKey(cl, Caches.subclassAuditsQueue);
        Boolean result = Caches.subclassAudits.get(key);
        if (result == null) {
            result = Boolean.valueOf(auditSubclass(cl));
            Caches.subclassAudits.putIfAbsent(key, result);
        }

        return result.booleanValue();
    }

    /**
     * Performs reflective checks on given subclass to verify that it doesn't
     * override security-sensitive non-final methods.  Returns true if the
     * subclass overrides any of the methods, false otherwise.
     */
    private static boolean auditSubclass(final Class<?> subcl) {
        Boolean result = AccessController.doPrivileged(
            new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    for (Class<?> cl = subcl;
                         cl != Thread.class;
                         cl = cl.getSuperclass())
                    {
                        try {
                            cl.getDeclaredMethod("getContextClassLoader", new Class<?>[0]);
                            return Boolean.TRUE;
                        } catch (NoSuchMethodException ex) {
                        }
                        try {
                            Class<?>[] params = {ClassLoader.class};
                            cl.getDeclaredMethod("setContextClassLoader", params);
                            return Boolean.TRUE;
                        } catch (NoSuchMethodException ex) {
                        }
                    }
                    return Boolean.FALSE;
                }
            }
        );
        return result.booleanValue();
    }

    /**
     * Returns the identifier of this Thread.  The thread ID is a positive
     * <tt>long</tt> number generated when this thread was created.
     * The thread ID is unique and remains unchanged during its lifetime.
     * When a thread is terminated, this thread ID may be reused.
     *
     * @return this thread's ID.
     * @since 1.5
     */
    public long getId() {
        return tid;
    }

    /**
     * A thread state.  A thread can be in one of the following states:
     * <ul>
     * <li>{@link #NEW}<br>
     *     A thread that has not yet started is in this state.
     *     </li>
     * <li>{@link #RUNNABLE}<br>
     *     A thread executing in the Java virtual machine is in this state.
     *     </li>
     * <li>{@link #BLOCKED}<br>
     *     A thread that is blocked waiting for a monitor lock
     *     is in this state.
     *     </li>
     * <li>{@link #WAITING}<br>
     *     A thread that is waiting indefinitely for another thread to
     *     perform a particular action is in this state.
     *     </li>
     * <li>{@link #TIMED_WAITING}<br>
     *     A thread that is waiting for another thread to perform an action
     *     for up to a specified waiting time is in this state.
     *     </li>
     * <li>{@link #TERMINATED}<br>
     *     A thread that has exited is in this state.
     *     </li>
     * </ul>
     *
     * <p>
     * A thread can be in only one state at a given point in time.
     * These states are virtual machine states which do not reflect
     * any operating system thread states.
     *
     * @since   1.5
     * @see #getState
     */
    public enum State {
        /**
         * Thread state for a thread which has not yet started.
         */
        NEW,

        /**
         * Thread state for a runnable thread.  A thread in the runnable
         * state is executing in the Java virtual machine but it may
         * be waiting for other resources from the operating system
         * such as processor.
         */
        RUNNABLE,

        /**
         * Thread state for a thread blocked waiting for a monitor lock.
         * A thread in the blocked state is waiting for a monitor lock
         * to enter a synchronized block/method or
         * reenter a synchronized block/method after calling
         * {@link Object#wait() Object.wait}.
         */
        BLOCKED,

        /**
         * Thread state for a waiting thread.
         * A thread is in the waiting state due to calling one of the
         * following methods:
         * <ul>
         *   <li>{@link Object#wait() Object.wait} with no timeout</li>
         *   <li>{@link #join() Thread.join} with no timeout</li>
         *   <li>{@link LockSupport#park() LockSupport.park}</li>
         * </ul>
         *
         * <p>A thread in the waiting state is waiting for another thread to
         * perform a particular action.
         *
         * For example, a thread that has called <tt>Object.wait()</tt>
         * on an object is waiting for another thread to call
         * <tt>Object.notify()</tt> or <tt>Object.notifyAll()</tt> on
         * that object. A thread that has called <tt>Thread.join()</tt>
         * is waiting for a specified thread to terminate.
         */
        WAITING,

        /**
         * Thread state for a waiting thread with a specified waiting time.
         * A thread is in the timed waiting state due to calling one of
         * the following methods with a specified positive waiting time:
         * <ul>
         *   <li>{@link #sleep Thread.sleep}</li>
         *   <li>{@link Object#wait(long) Object.wait} with timeout</li>
         *   <li>{@link #join(long) Thread.join} with timeout</li>
         *   <li>{@link LockSupport#parkNanos LockSupport.parkNanos}</li>
         *   <li>{@link LockSupport#parkUntil LockSupport.parkUntil}</li>
         * </ul>
         */
        TIMED_WAITING,

        /**
         * Thread state for a terminated thread.
         * The thread has completed execution.
         */
        TERMINATED;
    }

    /**
     * Returns the state of this thread.
     * This method is designed for use in monitoring of the system state,
     * not for synchronization control.
     *
     * @return this thread's state.
     * @since 1.5
     */
    public State getState() {
        // get current thread state
        return State.values()[nativeGetStatus(started)];
    }

    // Added in JSR-166

    /**
     * Interface for handlers invoked when a <tt>Thread</tt> abruptly
     * terminates due to an uncaught exception.
     * <p>When a thread is about to terminate due to an uncaught exception
     * the Java Virtual Machine will query the thread for its
     * <tt>UncaughtExceptionHandler</tt> using
     * {@link #getUncaughtExceptionHandler} and will invoke the handler's
     * <tt>uncaughtException</tt> method, passing the thread and the
     * exception as arguments.
     * If a thread has not had its <tt>UncaughtExceptionHandler</tt>
     * explicitly set, then its <tt>ThreadGroup</tt> object acts as its
     * <tt>UncaughtExceptionHandler</tt>. If the <tt>ThreadGroup</tt> object
     * has no
     * special requirements for dealing with the exception, it can forward
     * the invocation to the {@linkplain #getDefaultUncaughtExceptionHandler
     * default uncaught exception handler}.
     *
     * @see #setDefaultUncaughtExceptionHandler
     * @see #setUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    @FunctionalInterface
    public interface UncaughtExceptionHandler {
        /**
         * Method invoked when the given thread terminates due to the
         * given uncaught exception.
         * <p>Any exception thrown by this method will be ignored by the
         * Java Virtual Machine.
         * @param t the thread
         * @param e the exception
         */
        void uncaughtException(Thread t, Throwable e);
    }

    // null unless explicitly set
    private volatile UncaughtExceptionHandler uncaughtExceptionHandler;

    // null unless explicitly set
    private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    /**
     * Set the default handler invoked when a thread abruptly terminates
     * due to an uncaught exception, and no other handler has been defined
     * for that thread.
     *
     * <p>Uncaught exception handling is controlled first by the thread, then
     * by the thread's {@link ThreadGroup} object and finally by the default
     * uncaught exception handler. If the thread does not have an explicit
     * uncaught exception handler set, and the thread's thread group
     * (including parent thread groups)  does not specialize its
     * <tt>uncaughtException</tt> method, then the default handler's
     * <tt>uncaughtException</tt> method will be invoked.
     * <p>By setting the default uncaught exception handler, an application
     * can change the way in which uncaught exceptions are handled (such as
     * logging to a specific device, or file) for those threads that would
     * already accept whatever &quot;default&quot; behavior the system
     * provided.
     *
     * <p>Note that the default uncaught exception handler should not usually
     * defer to the thread's <tt>ThreadGroup</tt> object, as that could cause
     * infinite recursion.
     *
     * @param eh the object to use as the default uncaught exception handler.
     * If <tt>null</tt> then there is no default handler.
     *
     * @throws SecurityException if a security manager is present and it
     *         denies <tt>{@link RuntimePermission}
     *         (&quot;setDefaultUncaughtExceptionHandler&quot;)</tt>
     *
     * @see #setUncaughtExceptionHandler
     * @see #getUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
         defaultUncaughtExceptionHandler = eh;
     }

    /**
     * Returns the default handler invoked when a thread abruptly terminates
     * due to an uncaught exception. If the returned value is <tt>null</tt>,
     * there is no default.
     * @since 1.5
     * @see #setDefaultUncaughtExceptionHandler
     * @return the default uncaught exception handler for all threads
     */
    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler(){
        return defaultUncaughtExceptionHandler;
    }

    // Android-changed: Added concept of an uncaughtExceptionPreHandler for use by platform.
    // null unless explicitly set
    private static volatile UncaughtExceptionHandler uncaughtExceptionPreHandler;

    /**
     * Sets an {@link UncaughtExceptionHandler} that will be called before any
     * returned by {@link #getUncaughtExceptionHandler()}. To allow the standard
     * handlers to run, this handler should never terminate this process. Any
     * throwables thrown by the handler will be ignored by
     * {@link #dispatchUncaughtException(Throwable)}.
     *
     * @hide only for use by the Android framework (RuntimeInit) b/29624607
     */
    public static void setUncaughtExceptionPreHandler(UncaughtExceptionHandler eh) {
        uncaughtExceptionPreHandler = eh;
    }

    /** @hide */
    public static UncaughtExceptionHandler getUncaughtExceptionPreHandler() {
        return uncaughtExceptionPreHandler;
    }

    /**
     * Returns the handler invoked when this thread abruptly terminates
     * due to an uncaught exception. If this thread has not had an
     * uncaught exception handler explicitly set then this thread's
     * <tt>ThreadGroup</tt> object is returned, unless this thread
     * has terminated, in which case <tt>null</tt> is returned.
     * @since 1.5
     * @return the uncaught exception handler for this thread
     */
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler != null ?
            uncaughtExceptionHandler : group;
    }

    /**
     * Set the handler invoked when this thread abruptly terminates
     * due to an uncaught exception.
     * <p>A thread can take full control of how it responds to uncaught
     * exceptions by having its uncaught exception handler explicitly set.
     * If no such handler is set then the thread's <tt>ThreadGroup</tt>
     * object acts as its handler.
     * @param eh the object to use as this thread's uncaught exception
     * handler. If <tt>null</tt> then this thread has no explicit handler.
     * @throws  SecurityException  if the current thread is not allowed to
     *          modify this thread.
     * @see #setDefaultUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        checkAccess();
        uncaughtExceptionHandler = eh;
    }

    /**
     * Dispatch an uncaught exception to the handler. This method is
     * intended to be called only by the runtime and by tests.
     *
     * @hide
     */
    // @VisibleForTesting (would be private if not for tests)
    public final void dispatchUncaughtException(Throwable e) {
        Thread.UncaughtExceptionHandler initialUeh =
                Thread.getUncaughtExceptionPreHandler();
        if (initialUeh != null) {
            try {
                initialUeh.uncaughtException(this, e);
            } catch (RuntimeException | Error ignored) {
                // Throwables thrown by the initial handler are ignored
            }
        }
        getUncaughtExceptionHandler().uncaughtException(this, e);
    }

    /**
     * Removes from the specified map any keys that have been enqueued
     * on the specified reference queue.
     */
    static void processQueue(ReferenceQueue<Class<?>> queue,
                             ConcurrentMap<? extends
                             WeakReference<Class<?>>, ?> map)
    {
        Reference<? extends Class<?>> ref;
        while((ref = queue.poll()) != null) {
            map.remove(ref);
        }
    }

    /**
     *  Weak key for Class objects.
     **/
    static class WeakClassKey extends WeakReference<Class<?>> {
        /**
         * saved value of the referent's identity hash code, to maintain
         * a consistent hash code after the referent has been cleared
         */
        private final int hash;

        /**
         * Create a new WeakClassKey to the given object, registered
         * with a queue.
         */
        WeakClassKey(Class<?> cl, ReferenceQueue<Class<?>> refQueue) {
            super(cl, refQueue);
            hash = System.identityHashCode(cl);
        }

        /**
         * Returns the identity hash code of the original referent.
         */
        @Override
        public int hashCode() {
            return hash;
        }

        /**
         * Returns true if the given object is this identical
         * WeakClassKey instance, or, if this object's referent has not
         * been cleared, if the given object is another WeakClassKey
         * instance with the identical non-null referent as this one.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;

            if (obj instanceof WeakClassKey) {
                Object referent = get();
                return (referent != null) &&
                       (referent == ((WeakClassKey) obj).get());
            } else {
                return false;
            }
        }
    }


    // The following three initially uninitialized fields are exclusively
    // managed by class java.util.concurrent.ThreadLocalRandom. These
    // fields are used to build the high-performance PRNGs in the
    // concurrent code, and we can not risk accidental false sharing.
    // Hence, the fields are isolated with @Contended.

    /** The current seed for a ThreadLocalRandom */
    // @sun.misc.Contended("tlr")
    long threadLocalRandomSeed;

    /** Probe hash value; nonzero if threadLocalRandomSeed initialized */
    // @sun.misc.Contended("tlr")
    int threadLocalRandomProbe;

    /** Secondary seed isolated from public ThreadLocalRandom sequence */
    //  @sun.misc.Contended("tlr")
    int threadLocalRandomSecondarySeed;

    /* Some private helper methods */
    private native void nativeSetName(String newName);

    private native void nativeSetPriority(int newPriority);

    private native int nativeGetStatus(boolean hasBeenStarted);

    @FastNative
    private native void nativeInterrupt();

    /** Park states */
    private static class ParkState {
        /** park state indicating unparked */
        private static final int UNPARKED = 1;

        /** park state indicating preemptively unparked */
        private static final int PREEMPTIVELY_UNPARKED = 2;

        /** park state indicating parked */
        private static final int PARKED = 3;
    }

    private static final int NANOS_PER_MILLI = 1000000;

    /** the park state of the thread */
    private int parkState = ParkState.UNPARKED;

    /**
     * Unparks this thread. This unblocks the thread it if it was
     * previously parked, or indicates that the thread is "preemptively
     * unparked" if it wasn't already parked. The latter means that the
     * next time the thread is told to park, it will merely clear its
     * latent park bit and carry on without blocking.
     *
     * <p>See {@link java.util.concurrent.locks.LockSupport} for more
     * in-depth information of the behavior of this method.</p>
     *
     * @hide for Unsafe
     */
    public final void unpark$() {
        synchronized(lock) {
        switch (parkState) {
            case ParkState.PREEMPTIVELY_UNPARKED: {
                /*
                 * Nothing to do in this case: By definition, a
                 * preemptively unparked thread is to remain in
                 * the preemptively unparked state if it is told
                 * to unpark.
                 */
                break;
            }
            case ParkState.UNPARKED: {
                parkState = ParkState.PREEMPTIVELY_UNPARKED;
                break;
            }
            default /*parked*/: {
                parkState = ParkState.UNPARKED;
                lock.notifyAll();
                break;
            }
        }
        }
    }

    /**
     * Parks the current thread for a particular number of nanoseconds, or
     * indefinitely. If not indefinitely, this method unparks the thread
     * after the given number of nanoseconds if no other thread unparks it
     * first. If the thread has been "preemptively unparked," this method
     * cancels that unparking and returns immediately. This method may
     * also return spuriously (that is, without the thread being told to
     * unpark and without the indicated amount of time elapsing).
     *
     * <p>See {@link java.util.concurrent.locks.LockSupport} for more
     * in-depth information of the behavior of this method.</p>
     *
     * <p>This method must only be called when <code>this</code> is the current
     * thread.
     *
     * @param nanos number of nanoseconds to park for or <code>0</code>
     * to park indefinitely
     * @throws IllegalArgumentException thrown if <code>nanos &lt; 0</code>
     *
     * @hide for Unsafe
     */
    public final void parkFor$(long nanos) {
        synchronized(lock) {
        switch (parkState) {
            case ParkState.PREEMPTIVELY_UNPARKED: {
                parkState = ParkState.UNPARKED;
                break;
            }
            case ParkState.UNPARKED: {
                long millis = nanos / NANOS_PER_MILLI;
                nanos %= NANOS_PER_MILLI;

                parkState = ParkState.PARKED;
                try {
                    lock.wait(millis, (int) nanos);
                } catch (InterruptedException ex) {
                    interrupt();
                } finally {
                    /*
                     * Note: If parkState manages to become
                     * PREEMPTIVELY_UNPARKED before hitting this
                     * code, it should left in that state.
                     */
                    if (parkState == ParkState.PARKED) {
                        parkState = ParkState.UNPARKED;
                    }
                }
                break;
            }
            default /*parked*/: {
                throw new AssertionError("Attempt to repark");
            }
        }
        }
    }

    /**
     * Parks the current thread until the specified system time. This
     * method attempts to unpark the current thread immediately after
     * <code>System.currentTimeMillis()</code> reaches the specified
     * value, if no other thread unparks it first. If the thread has
     * been "preemptively unparked," this method cancels that
     * unparking and returns immediately. This method may also return
     * spuriously (that is, without the thread being told to unpark
     * and without the indicated amount of time elapsing).
     *
     * <p>See {@link java.util.concurrent.locks.LockSupport} for more
     * in-depth information of the behavior of this method.</p>
     *
     * <p>This method must only be called when <code>this</code> is the
     * current thread.
     *
     * @param time the time after which the thread should be unparked,
     * in absolute milliseconds-since-the-epoch
     *
     * @hide for Unsafe
     */
    public final void parkUntil$(long time) {
        synchronized(lock) {
        /*
         * Note: This conflates the two time bases of "wall clock"
         * time and "monotonic uptime" time. However, given that
         * the underlying system can only wait on monotonic time,
         * it is unclear if there is any way to avoid the
         * conflation. The downside here is that if, having
         * calculated the delay, the wall clock gets moved ahead,
         * this method may not return until well after the wall
         * clock has reached the originally designated time. The
         * reverse problem (the wall clock being turned back)
         * isn't a big deal, since this method is allowed to
         * spuriously return for any reason, and this situation
         * can safely be construed as just such a spurious return.
         */
        final long currentTime = System.currentTimeMillis();
        if (time <= currentTime) {
            parkState = ParkState.UNPARKED;
        } else {
            long delayMillis = time - currentTime;
            // Long.MAX_VALUE / NANOS_PER_MILLI (0x8637BD05SF6) is the largest
            // long value that won't overflow to negative value when
            // multiplyed by NANOS_PER_MILLI (10^6).
            long maxValue = (Long.MAX_VALUE / NANOS_PER_MILLI);
            if (delayMillis > maxValue) {
                delayMillis = maxValue;
            }
            parkFor$(delayMillis * NANOS_PER_MILLI);
        }
        }
    }
}
