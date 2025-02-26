/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualvm.lib.jfluid.instrumentation;

import org.graalvm.visualvm.lib.jfluid.ProfilerEngineSettings;
import org.graalvm.visualvm.lib.jfluid.classfile.DynamicClassInfo;
import org.graalvm.visualvm.lib.jfluid.global.ProfilingSessionStatus;
import org.graalvm.visualvm.lib.jfluid.utils.Wildcards;
import org.graalvm.visualvm.lib.jfluid.wireprotocol.RootClassLoadedCommand;


/**
 * Recursive method scanner that implements the total instrumentation scheme.
 * In fact, it's not even a scanner, since it just instruments absolutely everything -
 * but it uses the same interface.
 *
 * @author Tomas Hurka
 * @author Misha Dmitriev
 */
public class RecursiveMethodInstrumentor3 extends RecursiveMethodInstrumentor {
    //~ Instance fields ----------------------------------------------------------------------------------------------------------

    // Attributes to hold saved values for root classes, methods and signatures
    private boolean noExplicitRootsSpecified = false, mainMethodInstrumented = false;

    //~ Constructors -------------------------------------------------------------------------------------------------------------

    public RecursiveMethodInstrumentor3(ProfilingSessionStatus status, ProfilerEngineSettings settings) {
        super(status, settings);
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public Object[] getMethodsToInstrumentUponClassLoad(String className, int classLoaderId, boolean threadInCallGraph) {
        boolean DEBUG = false;

        /*    if (className.startsWith("java2d")) {
           DEBUG = true;
           }
         */
        if (DEBUG) {
            System.out.println("*** MS2: instr. upon CL: " + className); // NOI18N
        }

        className = className.replace('.', '/').intern(); // NOI18N

        DynamicClassInfo clazz = javaClassForName(className, classLoaderId);

        if (clazz == null) {
            return null;
        }

        if (DEBUG) {
            System.out.println("*** MS2: instr. upon CL 2: " + clazz.getNameAndLoader()); // NOI18N
        }

        clazz.setLoaded(true);
        addToSubclassList(clazz, clazz); // Have to call this in advance to determine if a class implements Runnable (possibly indirectly)

        if (clazz.isInterface()) {
            return null;
        }

        initInstrMethodData();

        // Mark as roots all methods that the total instrumentation method views as implicit roots
        boolean isRootClass = false;
        int rootIdxForAll = -1;

        markProfilingPonitForInstrumentation(clazz);
        isRootClass = tryInstrumentSpawnedThreads(clazz); // This only checks for Runnable.run()

        if (noExplicitRootsSpecified && !mainMethodInstrumented) { // Check if this class has main method. The first loaded class with main method should be main class.

            if (tryMainMethodInstrumentation(clazz)) {
                isRootClass = true;
                // allow to instrument two main classes if one of them is from sun.launcher package
                if (!clazz.getName().startsWith("sun/launcher/Launcher")) {     // NOI18N
                    mainMethodInstrumented = true;
                }
            }
        }

        // Check to see if this class has been marked as root by the user:
        if (!isRootClass) {
            for (int rIdx = 0; rIdx < rootMethods.classNames.length; rIdx++) {
                String rootClassName = rootMethods.classNames[rIdx];

                if (rootMethods.classesWildcard[rIdx]) {
                    if (Wildcards.matchesWildcard(rootClassName, className)) {
                        //            System.out.println("Matched package wildcard - " + rootClassName);
                        isRootClass = true;

                        break;
                    }
                } else {
                    if (className.equals(rootClassName)) {
                        isRootClass = true;

                        break;
                    }
                }
            }
        }

        boolean normallyFilteredOut = !instrFilter.passes(className);

        if (!isRootClass) {
            if (normallyFilteredOut) {
                return createInstrumentedMethodPack(); // profile points !
            }
        }

        // Check to see if this class has been marked as root by the user:
        for (int rIdx = 0; rIdx < rootMethods.classNames.length; rIdx++) {
            String rootClassName = rootMethods.classNames[rIdx];
            boolean isMatch = false;

            if (rootMethods.classesWildcard[rIdx]) {
                if (Wildcards.matchesWildcard(rootClassName, className)) {
                    //            System.out.println("Matched package wildcard - " + rootClassName);
                    isMatch = true;
                }
            } else {
                if (className.equals(rootClassName)) { // precise match
                    isMatch = true;
                }
            }

            if (isMatch) { // it is indeed a root class

                if (Wildcards.isPackageWildcard(rootClassName) || Wildcards.isMethodWildcard(rootMethods.methodNames[rIdx])) {
                    if (rootMethods.markerMethods[rIdx]) {
                        markAllMethodsMarker(clazz);
                    } else {
                        markAllMethodsRoot(clazz);
                    }
                } else {
                    markMethod(clazz, rIdx);
                    checkAndMarkMethodForInstrumentation(clazz, rootMethods.methodNames[rIdx], rootMethods.methodSignatures[rIdx]);
                }
            }
        }

        if (!normallyFilteredOut || clazz.getAllMethodsMarkers() || clazz.getAllMethodsRoots()) {
            checkAndMarkAllMethodsForInstrumentation(clazz);
        }

        return createInstrumentedMethodPack();
    }

    public Object[] getMethodsToInstrumentUponMethodInvocation(String className, int classLoaderId, String methodName,
                                                               String methodSignature) {
        // This method is just not used with this flavour of MethodScanner
        throw new IllegalStateException("Class "+className+" method "+methodName);
    }

    public Object[] getMethodsToInstrumentUponReflectInvoke(String className, int classLoaderId, String methodName,
                                                            String methodSignature) {
        return null; // Doesn't have to do anything - everything is handled upon class load
    }

    protected void findAndMarkOverridingMethodsReachable(DynamicClassInfo superClass, DynamicClassInfo subClass) {
        // Doesn't do anything (actually not used/called at all) in this scaner
    }

    protected void processInvoke(DynamicClassInfo clazz, boolean virtualCall, int index) {
        // Doesn't do anything (not used) in this scaner
    }

    protected boolean tryInstrumentSpawnedThreads(DynamicClassInfo clazz) {
//        System.err.println("TryInstrumentSpawnedThreads: " + instrumentSpawnedThreads + "/" + noExplicitRootsSpecified);
        if (instrumentSpawnedThreads || noExplicitRootsSpecified) {
            if (clazz.implementsInterface("java/lang/Runnable") && (clazz.getName() != "java/lang/Thread")) { // NOI18N

                boolean res = markMethodRoot(clazz, "run", "()V"); // NOI18N
                checkAndMarkMethodForInstrumentation(clazz, "run", "()V"); // NOI18N

                return res;
            }
        }

        return false;
    }

    protected boolean tryMainMethodInstrumentation(DynamicClassInfo clazz) {
        int idx = clazz.getMethodIndex("main", "([Ljava/lang/String;)V"); // NOI18N

        if (idx == -1) {
            return false;
        }

        if (!(clazz.isMethodStatic(idx) && clazz.isMethodPublic(idx))) {
            return false;
        }

        markMethodRoot(clazz, "main", "([Ljava/lang/String;)V"); // NOI18N
        checkAndMarkMethodForInstrumentation(clazz, idx);

        return true;
    }

    Object[] getInitialMethodsToInstrument(RootClassLoadedCommand rootLoaded, RootMethods roots) {
        DynamicClassInfo[] loadedClassInfos = preGetInitialMethodsToInstrument(rootLoaded);

        rootMethods = roots;
        checkForNoRootsSpecified(roots);

        // Check which root classes have already been loaded, and mark their root methods accordingly
        for (DynamicClassInfo loadedClassInfo : loadedClassInfos) {
            if (loadedClassInfo == null) {
                continue; // Can this happen?
            }

            markProfilingPonitForInstrumentation(loadedClassInfo);
            tryInstrumentSpawnedThreads(loadedClassInfo); // This only checks for Runnable.run()

            for (int rIdx = 0; rIdx < rootMethods.classNames.length; rIdx++) {
                String rootClassName = rootMethods.classNames[rIdx];
                boolean isMatch = false;

                if (rootMethods.classesWildcard[rIdx]) {
                    if (Wildcards.matchesWildcard(rootClassName, loadedClassInfo.getName())) {
                        //            System.out.println("Matched package wildcard - " + rootClassName);
                        isMatch = true;
                    }
                } else {
                    if (loadedClassInfo.getName().equals(rootClassName)) { // precise match
                        isMatch = true;
                    }
                }

                if (isMatch) {
                    if (Wildcards.isPackageWildcard(rootClassName) || Wildcards.isMethodWildcard(rootMethods.methodNames[rIdx])) {
                        if (rootMethods.markerMethods[rIdx]) {
                            markAllMethodsMarker(loadedClassInfo);
                        } else {
                            markAllMethodsRoot(loadedClassInfo);
                        }
                    } else {
                        markMethod(loadedClassInfo, rIdx);
                        checkAndMarkMethodForInstrumentation(loadedClassInfo, rootMethods.methodNames[rIdx],
                                                             rootMethods.methodSignatures[rIdx]);
                    }
                }
            }

            checkAndMarkAllMethodsForInstrumentation(loadedClassInfo);
        }

        // So that class loading is measured correctly from the beginning
        checkAndMarkMethodForInstrumentation(javaClassForName("java/lang/ClassLoader", 0), "loadClass",  // NOI18N
                                             "(Ljava/lang/String;)Ljava/lang/Class;");  // NOI18N

        return createInstrumentedMethodPack();
    }

    private void checkAndMarkAllMethodsForInstrumentation(DynamicClassInfo clazz) {
        if (clazz.isInterface()) {
            return;
        }

        String[] methods = clazz.getMethodNames();

        for (int i = 0; i < methods.length; i++) {
            checkAndMarkMethodForInstrumentation(clazz, i);
        }
    }

    /** Mark the given method reachable, if there are no barriers for that (like native, empty, etc. method) */
    private void checkAndMarkMethodForInstrumentation(DynamicClassInfo clazz, String methodName, String methodSignature) {
        if (clazz == null) {
            return;
        }

        int idx = clazz.getMethodIndex(methodName, methodSignature);

        if (idx == -1) {
            return;
        }

        checkAndMarkMethodForInstrumentation(clazz, idx);
    }

    private void checkAndMarkMethodForInstrumentation(DynamicClassInfo clazz, int idx) {
        String className = clazz.getName();

        if (!clazz.isMethodReachable(idx)) {
            clazz.setMethodReachable(idx);

            if (clazz.isMethodNative(idx) || clazz.isMethodAbstract(idx)
                    || (!clazz.isMethodRoot(idx) && !clazz.isMethodMarker(idx) && !instrFilter.passes(className))
                    || (className == OBJECT_SLASHED_CLASS_NAME) // Actually, just the Object.<init> method?
            ) {
                clazz.setMethodUnscannable(idx);
            } else if (clazz.getMethodName(idx) == "<init>" && !status.canInstrumentConstructor && clazz.getMajorVersion()>50) {
                clazz.setMethodUnscannable(idx);
            } else {
                byte[] bytecode = clazz.getMethodBytecode(idx);

                if ((dontInstrumentEmptyMethods && isEmptyMethod(bytecode))
                        || (dontScanGetterSetterMethods && isGetterSetterMethod(bytecode))) {
                    clazz.setMethodUnscannable(idx);
                } else {
                    clazz.setMethodLeaf(idx);
                }
            }

            // Class is loaded, method is reachable and not unscannable are sufficient conditions for instrumenting method
            if (!clazz.isMethodUnscannable(idx)) {
                markClassAndMethodForInstrumentation(clazz, idx);
            }
        }
    }

    private void checkForNoRootsSpecified(RootMethods roots) {
//        System.err.println("Checking for no roots specified");
        // It may happen, for example when directly attaching to a remote application and choosing the Entire App CPU
        // profiling, that there are no explicitly specified root methods (because the main method is not known in advance).
        // To get sensible profiling results, we take special measures, by just guessing what the main class is.
        noExplicitRootsSpecified = true;
        
        if ((roots != null) && (roots.classNames.length != 0)) {
            int rootCount = roots.markerMethods.length;

            if (rootCount > 0) {
                for (int i = 0; i < rootCount; i++) {
                    if (!roots.markerMethods[i]) {
                        noExplicitRootsSpecified = false;

                        break;
                    }
                }
            }
        }
//        System.err.println("NoRootsSpecified = " + noExplicitRootsSpecified);
    }

    //----------------------------------- Private implementation ------------------------------------------------
    private DynamicClassInfo[] preGetInitialMethodsToInstrument(RootClassLoadedCommand rootLoaded) {
        //System.out.println("*** MS2: instr. initial");
        reflectInvokeInstrumented = true; // We don't need to instrument reflection specially in this mode

        resetLoadedClassData();
        storeClassFileBytesForCustomLoaderClasses(rootLoaded);
        initInstrMethodData();

        DynamicClassInfo[] loadedClassInfos;
        String[] loadedClasses = rootLoaded.getAllLoadedClassNames();
        int[] loadedClassLoaderIds = rootLoaded.getAllLoadedClassLoaderIds();
        loadedClassInfos = new DynamicClassInfo[loadedClasses.length]; //EJB Work: removed the condition in the above line as we need to return the temp array anyway (used to check for multiple roots, see the overloaded getInitialMethodsToInstrument )

        for (int i = 0; i < loadedClasses.length; i++) {
            DynamicClassInfo clazz = javaClassForName(loadedClasses[i], loadedClassLoaderIds[i]);

            if (clazz == null) {
                // warning already issued in ClassRepository.lookupClass method, no need to do it again
                continue;
            }

            clazz.setLoaded(true);
            addToSubclassList(clazz, clazz); // Needed basically only for methods like implementsInterface() to work correctly
            loadedClassInfos[i] = clazz;
        }

        return loadedClassInfos;
    }
}
