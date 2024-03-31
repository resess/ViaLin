package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class for analyzing and storing information about classes, methods, fields,
 * and their relationships in a codebase. This class extends {@link TaintAnalysis}.
 */
public class ClassAnalysis extends TaintAnalysis {

    /**
     * Serializable class representing information about a class field.
     * The class includes details such as the field name and its access modifiers.
     */
    static class FieldInfo implements Serializable {

        // Unique identifier for serialization
        private static final long serialVersionUID = -123260758267107913L;

        // Field to store the name of the field
        String fieldName;

        // Array to store the access modifiers of the field
        String[] accessModifiers;

        /**
         * Constructor for creating a FieldInfo object with the specified field name and access modifiers.
         *
         * @param fieldName        The name of the field.
         * @param accessModifiers  An array of strings representing access modifiers of the field.
         */
        FieldInfo(String fieldName, String[] accessModifiers) {
            this.fieldName = fieldName;
            this.accessModifiers = accessModifiers;
        }

        /**
         * Overrides the default toString method to provide a formatted string representation of the field information.
         *
         * @return A string representation of the access modifiers and field name.
         */
        @Override
        public String toString() {
            return Arrays.toString(accessModifiers) + " " + fieldName;
        }

        /**
         * Overrides the default hashCode method to generate a hash code based on the field name.
         *
         * @return The hash code for the FieldInfo object.
         */
        @Override
        public int hashCode() {
            return fieldName.hashCode();
        }

        /**
         * Overrides the default equals method to compare two FieldInfo objects based on their string representations.
         *
         * @param other The object to compare with.
         * @return True if the objects are equal, false otherwise.
         */
        @Override
        public boolean equals(Object other) {
            if (other instanceof FieldInfo) {
                FieldInfo otherField = (FieldInfo) other;
                if (this.toString().equals(otherField.toString())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Custom serialization method to write the object to an ObjectOutputStream.
         *
         * @param oos The ObjectOutputStream to write the object to.
         * @throws IOException If an I/O error occurs during serialization.
         */
        private void writeObject(ObjectOutputStream oos) throws IOException {
            oos.defaultWriteObject();
            oos.writeObject(fieldName);
            oos.writeObject(accessModifiers);
        }

        /**
         * Custom deserialization method to read the object from an ObjectInputStream.
         *
         * @param ois The ObjectInputStream to read the object from.
         * @throws ClassNotFoundException If the class of the serialized object cannot be found.
         * @throws IOException            If an I/O error occurs during deserialization.
         */
        private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
            ois.defaultReadObject();
            fieldName = (String) ois.readObject();
            accessModifiers = (String[]) ois.readObject();
        }
    }

    // Directory path for framework analysis
    private final String frameworkAnalysisDir;

    // Directory path for application analysis
    private final String appAnalysisDir;

    // Mapping of class names to the set of method names in each class
    private final Map<String, Set<String>> methodsInClass = new HashMap<>();

    // Mapping of class names to their superclasses
    private final Map<String, String> classSuper = new HashMap<>();

    // Mapping of class names to the set of interfaces they implement
    private final Map<String, Set<String>> implementedClass = new HashMap<>();

    // Mapping of interface names to the set of classes that implement them
    private final Map<String, Set<String>> implementingClass = new HashMap<>();

    // Set of native methods in the codebase
    private final Set<String> nativeMethods = new HashSet<>();

    // Mapping of class names to the set of field information in each class
    private final Map<String, Set<FieldInfo>> fieldsInClass = new HashMap<>();

    /**
     * Constructs a ClassAnalysis object for analyzing classes in the framework.
     *
     * @param frameworkAnalysisDir The directory path for framework analysis.
     * @param methodModelDir       The directory path for method models.
     */
    public ClassAnalysis (String frameworkAnalysisDir) {
        this.frameworkAnalysisDir = frameworkAnalysisDir;
        this.appAnalysisDir = null;
        load(this.frameworkAnalysisDir);
    }

    /**
     * Constructs a ClassAnalysis object for analyzing classes in the framework and application.
     *
     * @param frameworkAnalysisDir The directory path for framework analysis.
     * @param methodModelDir       The directory path for method models.
     * @param outDir               The directory path for application analysis.
     */
    public ClassAnalysis (String frameworkAnalysisDir, String outDir) {
        this.frameworkAnalysisDir = frameworkAnalysisDir;
        load(this.frameworkAnalysisDir);

        this.appAnalysisDir = outDir + "/class_analysis/";
        File appAnalysisDirFile = new File(this.appAnalysisDir);

        // Load application analysis if the directory exists; otherwise, create the directory
        if (appAnalysisDirFile.exists()) {
            load(this.appAnalysisDir);
        } else {
            appAnalysisDirFile.mkdir();
        }
    }

    /**
     * Adds a method to the set of methods in a class.
     *
     * @param methodName The name of the method to be added.
     * @param className  The name of the class to which the method belongs.
     */
    private void addMethodToClass(String methodName, String className) {
        Set<String> methodSet = methodsInClass.getOrDefault(className, new HashSet<>());
        methodSet.add(methodName);
        methodsInClass.put(className, methodSet);
    }

    /**
     * Adds a field to the set of fields in a class.
     *
     * @param fieldInfo  The FieldInfo object representing the field to be added.
     * @param className  The name of the class to which the field belongs.
     */
    private void addFieldToClass(FieldInfo fieldInfo, String className) {
        Set<FieldInfo> fieldSet = fieldsInClass.getOrDefault(className, new HashSet<>());
        fieldSet.add(fieldInfo);
        fieldsInClass.put(className, fieldSet);
    }

    /**
     * Adds a superclass to a class.
     *
     * @param superName The name of the superclass.
     * @param className The name of the class.
     */
    private void addSuperToClass(String superName, String className) {
        classSuper.put(className, superName);
    }

    /**
     * Adds an implemented interface to a class.
     *
     * @param interfaceName The name of the implemented interface.
     * @param className     The name of the class.
     */
    private void addImplementedToClass(String superName, String className) {
        Set<String> classSet = implementedClass.getOrDefault(className, new HashSet<>());
        classSet.add(superName);
        implementedClass.put(className, classSet);
    }


    /**
     * Analyzes a list of Smali files to extract information about classes, methods, fields,
     * and their relationships. This method populates internal data structures with the analysis results.
     *
     * @param smaliFiles The list of Smali files to be analyzed.
     */
    public void analyze(List<String> smaliFiles) {
        for (String file : smaliFiles) {
            List<String> classLines;
            try {
                classLines = Files.readAllLines(Paths.get(file));
            } catch (IOException e) {
                throw new Error("Cannot open class file: " + file);
            }

            String className = getLastToken(classLines.get(0));
            analyzeClassLines(classLines, className);
        }
        createImplementingClass();
    }

    /**
     * Analyzes the lines of a class and adds relevant information to the corresponding data structures.
     * @param classLines the lines of the class to be analyzed
     * @param className the name of the class being analyzed
     */
    private void analyzeClassLines(List<String> classLines, String className) {
        // Iterate through the lines of the class
        // and add relevant information to the corresponding data structures
        for (String line : classLines) {
            if (isNativeMethod(line)) {
                addNativeMethod(className, line);
            }
            if (isSuper(line)) {
                addSuperToClass(getLastToken(line), className);
            }
            if (isImplements(line)) {
                addImplementedToClass(getLastToken(line), className);
            }
            if (isMethod(line)) {
                addMethodToClass(getLastToken(line), className);
            }
            if (isField(line)) {
                addFieldToClass(getFieldFromDeclaration(line), className);
            }
        }
    }

    /**
     * Checks if a given line of code represents a native method.
     *
     * @param line the line of code to check
     * @return true if the line represents a native method, false otherwise
     */
    private boolean isNativeMethod(String line) {
        return line.startsWith(".method") && line.contains("native");
    }


    /**
     * Checks if the given line starts with ".super".
     *
     * @param line the line to check
     * @return true if the line starts with ".super", false otherwise
     */
    private boolean isSuper(String line) {
        return line.startsWith(".super");
    }

    /**
     * Checks if the given line starts with ".implements".
     *
     * @param line the line to check
     * @return true if the line starts with ".implements", false otherwise
     */
    private boolean isImplements(String line) {
        return line.startsWith(".implements");
    }

    /**
     * Checks if the given line starts with ".method".
     *
     * @param line the line to check
     * @return true if the line starts with ".method", false otherwise
     */
    private boolean isMethod(String line) {
        return line.startsWith(".method");
    }

    /**
     * Checks if the given line starts with ".field".
     *
     * @param line the line to check
     * @return true if the line starts with ".field", false otherwise
     */
    private boolean isField(String line) {
        return line.startsWith(".field");
    }


    /**
     * Extracts a FieldInfo object from a Smali field declaration line.
     *
     * @param line The Smali field declaration line.
     * @return A FieldInfo object representing the field information.
     */
    private FieldInfo getFieldFromDeclaration(String line) {
        String[] parse = line.split(":");
        String left = parse[0];
        parse = left.split("\\s+");
        String [] accessModifierArray = Arrays.copyOfRange(parse, 0, parse.length-1);
        String fieldName = parse[parse.length-1];
        return new FieldInfo(fieldName, accessModifierArray) ;
    }

    /**
     * Adds a native method to the set of native methods.
     *
     * @param className The name of the class to which the native method belongs.
     * @param line      The Smali line containing information about the native method.
     */
    private void addNativeMethod(String className, String line) {
        String methodName = className + "->" + getLastToken(line);
        nativeMethods.add(methodName);
    }

    /**
     * Checks if a given method is marked as native.
     *
     * @param methodName The name of the method to check.
     * @return True if the method is native, false otherwise.
     */
    public boolean isNative(String methodName) {
        return nativeMethods.contains(methodName);
    }

    /**
     * Gets the set of classes that inherit a given class and method.
     *
     * @param className  The name of the class.
     * @param methodName The name of the method.
     * @return A set of class names that inherit the specified class and method.
     */
    public Set<String> getInheriterClasses(String className, String methodName) {
        Set<String> classes = new HashSet<>();
        Deque<String> classesToGet = new ArrayDeque<>();
        classesToGet.push(className);
        while (!classesToGet.isEmpty()) {
            String classToLookForNow = classesToGet.pop();
            Set<String> set = implementingClass.get(classToLookForNow);
            if (set != null ) {
                for (String c : set) {
                    if (!classes.contains(c)) {
                        classesToGet.add(c);
                        classes.add(c);
                    }
                }
            }
        }
        return classes;
    }

    /**
     * Retrieves a set of all superclasses (including the class itself) for a given class recursively.
     * This method explores both the direct superclass and implemented interfaces of the class.
     *
     * @param className       The name of the class for which to retrieve superclasses.
     * @param classesSearched A set to keep track of classes already searched to avoid infinite loops.
     * @return A set of class names representing the superclasses of the given class.
     */
    public Set<String> getSuperClasses(String className, Set<String> classesSearched) {
        Set<String> superClasses = new HashSet<>();

        // If the class has already been searched, return an empty set to avoid infinite loops
        if (classesSearched.contains(className)) {
            return superClasses;
        }

        // Add the current class to the set of superclasses and mark it as searched
        superClasses.add(className);
        classesSearched.add(className);

        // Retrieve implemented classes/interfaces for the current class
        Set<String> implementedClasses = implementedClass.getOrDefault(className, new HashSet<>());

        // Recursively get superclasses for each implemented class/interface
        for (String c : implementedClasses) {
            superClasses.addAll(getSuperClasses(c, classesSearched));
        }

        // Get the direct superclass of the current class
        String superClass = classSuper.get(className);

        // Recursively get superclasses for the direct superclass
        if (superClass != null) {
            superClasses.addAll(getSuperClasses(superClass, classesSearched));
        }

        // Return the set of superclasses for the given class
        return superClasses;
    }

    /**
     * Retrieves a set of classes that implement the specified method in the given class.
     * This method explores the class hierarchy to find all implementing classes.
     *
     * @param className   The name of the class in which the method is defined.
     * @param methodName  The name of the method for which to find implementing classes.
     * @return A set of class names representing the classes that implement the specified method.
     */
    public Set<String> getImplementingClassOfMethod(String className, String methodName) {
        // Set to keep track of classes already searched to avoid infinite loops
        Set<String> classesSearched = new HashSet<>();

        // Set to store the classes that implement the specified method
        Set<String> classOfMethod = new HashSet<>();

        // If the class name represents an array, add it to the set of classes
        if (className.startsWith("[")) {
            classOfMethod.add("[" + className);
        }

        // Attempt to retrieve implementing classes by exploring the class hierarchy
        try {
            classOfMethod.addAll(getClassOfMethodSearch(className, methodName, classesSearched));
        } catch (Exception e) {
            // Ignored
        }

        // If the method is native, return an empty set to indicate that there are no implementing classes
        if (isNative(classOfMethod + "->" + methodName)) {
            return Collections.emptySet();
        }

        // Remove the default Object class from the set of implementing classes
        classOfMethod.remove("Ljava/lang/Object;");

        // Return the set of classes implementing the specified method
        return classOfMethod;
    }

    /**
     * Retrieves a set of classes that contain the specified method within their class hierarchy.
     * This method explores the class hierarchy, including inherited classes and interfaces.
     *
     * @param className   The name of the class in which the method is defined.
     * @param methodName  The name of the method for which to find containing classes.
     * @return A set of class names representing the classes containing the specified method.
     */
    public Set<String> getClassOfMethod(String className, String methodName) {
        // Set to keep track of classes already searched to avoid infinite loops
        Set<String> classesSearched = new HashSet<>();

        // Set to store the classes that contain the specified method
        Set<String> classOfMethod = new HashSet<>();

        // If the class name represents an array, add it to the set of classes
        if (className.startsWith("[")) {
            classOfMethod.add("[" + className);
        }

        // Get a set of inheriting classes and interfaces for the specified class and method
        Set<String> classToLookFor = getInheriterClasses(className, methodName);

        // Include the original class in the set to ensure it is considered during the search
        classToLookFor.add(className);

        // Explore the class hierarchy for each inheriting class or interface
        for (String inheritingClass : classToLookFor) {
            try {
                classOfMethod.addAll(getClassOfMethodSearch(inheritingClass, methodName, classesSearched));
            } catch (Exception e) {
                // Handle any exceptions during the class hierarchy exploration (optional)
            }
        }

        // If the method is native, return an empty set to indicate that there are no implementing classes
        if (isNative(classOfMethod + "->" + methodName)) {
            return Collections.emptySet();
        }

        // Remove the default Object class from the set of containing classes
        classOfMethod.remove("Ljava/lang/Object;");

        // Return the set of classes containing the specified method
        return classOfMethod;
    }

    /**
     * Recursive helper method for searching and collecting classes containing a specified method.
     * This method explores the class hierarchy, including inherited classes and interfaces.
     *
     * @param className       The name of the class to search for containing the method.
     * @param methodName      The name of the method to find within the class hierarchy.
     * @param classesSearched A set to keep track of classes already searched to avoid infinite loops.
     * @return A set of class names representing the classes containing the specified method.
     */
    private Set<String> getClassOfMethodSearch(String className, String methodName, Set<String> classesSearched) {
        // Set to store the classes containing the specified method
        Set<String> classOfMethod = new HashSet<>();

        // If the class has already been searched, return an empty set to avoid infinite loops
        if (classesSearched.contains(className)) {
            return classOfMethod;
        }

        // Get the set of methods in the current class
        Set<String> classMethods = methodsInClass.get(className);

        // If the class contains the specified method, add it to the set of containing classes
        if (classMethods != null && classMethods.contains(methodName)) {
            classOfMethod.add(className);
        }

        // Mark the current class as searched
        classesSearched.add(className);

        // Retrieve implemented classes/interfaces for the current class
        Set<String> implementedClasses = implementedClass.getOrDefault(className, new HashSet<>());

        // Recursively search for containing classes in implemented classes/interfaces
        for (String implemented : implementedClasses) {
            classOfMethod.addAll(getClassOfMethodSearch(implemented, methodName, classesSearched));
        }

        // Get the direct superclass of the current class
        String superClass = classSuper.get(className);

        // Recursively search for containing classes in the direct superclass
        if (superClass != null) {
            classOfMethod.addAll(getClassOfMethodSearch(superClass, methodName, classesSearched));
        }

        // Return the set of classes containing the specified method
        return classOfMethod;
    }


    /**
     * Retrieves the class that contains the specified field within its class hierarchy.
     * This method explores the class hierarchy, including inherited classes and interfaces.
     *
     * @param className  The name of the class to search for containing the field.
     * @param fieldName  The name of the field to find within the class hierarchy.
     * @return The name of the class containing the specified field, or null if not found.
     */
    public String getClassOfField(String className, String fieldName) {
        String classOfField = null;

        // Attempt to retrieve the class containing the field by exploring the class hierarchy
        try {
            classOfField = getClassOfFieldSearch(className, fieldName, new HashSet<>());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Return the name of the class containing the specified field (or null if not found)
        return classOfField;
    }


    /**
     * Recursive helper method for searching and determining the class containing a specified field.
     * This method explores the class hierarchy, including inherited classes and interfaces.
     *
     * @param className       The name of the class to search for containing the field.
     * @param fieldName       The name of the field to find within the class hierarchy.
     * @param classesSearched A set to keep track of classes already searched to avoid infinite loops.
     * @return The name of the class containing the specified field, or null if not found.
     */
    private String getClassOfFieldSearch(String className, String fieldName, Set<String> classesSearched) {
        // Get the set of candidate fields for the current class
        Set<FieldInfo> candidateFields = fieldsInClass.get(className);

        // If candidate fields exist, check if the field is among them
        if (candidateFields != null) {
            Set<String> candidateFieldNames = new HashSet<>();
            candidateFields.forEach(fieldInfo -> candidateFieldNames.add(fieldInfo.fieldName));
            if (candidateFieldNames.contains(fieldName)) {
                return className;
            }
        }

        // If the class has already been searched, return null to avoid redundant searches
        if (classesSearched.contains(className)) {
            return null;
        }

        // Mark the current class as searched
        classesSearched.add(className);

        // Retrieve implemented classes/interfaces for the current class
        Set<String> implementedClasses = implementedClass.getOrDefault(className, new HashSet<>());

        // Recursively search for the containing class in implemented classes/interfaces
        for (String implemented : implementedClasses) {
            String searchResult = getClassOfFieldSearch(implemented, fieldName, classesSearched);
            if (searchResult != null) {
                return searchResult;
            }
        }

        // Get the direct superclass of the current class
        String superClass = classSuper.get(className);

        // If there is no superclass, return null
        if (superClass == null) {
            return null;
        }

        // Recursively search for the containing class in the direct superclass
        return getClassOfFieldSearch(superClass, fieldName, classesSearched);
    }

    /**
     * Loads analysis files from the specified directory, populating data structures for analysis.
     * This method deserializes information about methods, fields, class hierarchy, implemented classes,
     * and native methods. It also performs necessary filtering and merging operations.
     *
     * @param dir The directory from which to load analysis files.
     */
    private void load(String dir) {
        // Temporary data structure for loading fields before filtering and merging
        Map<String, Set<FieldInfo>> tempFieldInClass = new HashMap<>();

        try {
            // Load methods
            methodsInClass.putAll(deSerialize("methodsInClass", dir));

            // Load fields
            tempFieldInClass.putAll(deSerialize("fieldsInClass", dir));

            // Load class hierarchy information
            classSuper.putAll(deSerialize("classSuper", dir));

            // Load implemented class information
            implementedClass.putAll(deSerialize("implementedClass", dir));
            createImplementingClass();

            // Load native methods
            nativeMethods.addAll(deSerialize("nativeMethods", dir));
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        // Filter and merge fields in the final data structure
        for (Map.Entry<String, Set<FieldInfo>> entry : tempFieldInClass.entrySet()) {
            HashSet<FieldInfo> fieldsInThisClass = new HashSet<>();
            for (FieldInfo f : entry.getValue()) {
                if (!fieldsInThisClass.contains(f)) {
                    fieldsInThisClass.add(f);
                }
            }
            if (!fieldsInClass.containsKey(entry.getKey())) {
                fieldsInClass.put(entry.getKey(), fieldsInThisClass);
            }
        }

    }

    /**
     * Creates a mapping of classes to the set of classes that implement them.
     * This method iterates through the implemented classes and updates the
     * implementingClass data structure accordingly.
     */
    private void createImplementingClass() {
        for (Map.Entry<String, Set<String>> e : implementedClass.entrySet()) {
            for (String c : e.getValue()) {
                // Get the set of classes that implement the current implementingClass
                Set<String> set = implementingClass.getOrDefault(c, new HashSet<>());
                // Add the current class to the set
                set.add(e.getKey());
                // Update the implementingClass mapping
                implementingClass.put(c, set);
            }
        }
    }


    /**
     * Saves the current state of analysis data to files in the specified directory.
     * This method serializes information about methods, fields, class hierarchy,
     * implemented classes, and native methods.
     */
    public void save() {
        // Determine the directory to save the files
        String dir = frameworkAnalysisDir;
        if (appAnalysisDir != null) {
            dir = appAnalysisDir;
        }

        // Serialize and save methodsInClass
        serialize("methodsInClass", methodsInClass, dir);

        // Serialize and save fieldsInClass
        serialize("fieldsInClass", fieldsInClass, dir);

        // Serialize and save classSuper
        serialize("classSuper", classSuper, dir);

        // Serialize and save implementedClass
        serialize("implementedClass", implementedClass, dir);

        // Serialize and save nativeMethods
        serialize("nativeMethods", nativeMethods, dir);
    }

    /**
     * Serializes an object and saves it to a file in the specified directory.
     *
     * @param name   The name of the file to save.
     * @param object The object to be serialized and saved.
     * @param dir    The directory in which to save the file.
     * @param <T>    The type of the object being serialized.
     */
    private <T> void serialize(String name, T object, String dir) {
        try (FileOutputStream fos = new FileOutputStream(dir + "/" + name);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            // Write the object to the file
            oos.writeObject(object);
        } catch (IOException ioe) {
            // Handle IOException by printing the stack trace
            ioe.printStackTrace();
        }
    }

    /**
     * Deserializes an object from a file in the specified directory.
     *
     * @param name The name of the file to load.
     * @param dir  The directory from which to load the file.
     * @param <T>  The type of the object being deserialized.
     * @return The deserialized object.
     */
    @SuppressWarnings("unchecked")
    private <T> T deSerialize(String name, String dir) {
        T ret = null;
        try (FileInputStream fis = new FileInputStream(dir + "/" + name);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            // Read and cast the object from the file
            ret = (T) ois.readObject();
        } catch (IOException ioe) {
            // Handle IOException and ClassNotFoundException by printing the stack trace
            ioe.printStackTrace();
        } catch (ClassNotFoundException c) {
            // Handle ClassNotFoundException by printing the stack trace
            AnalysisLogger.log(true, "Class not found");
            c.printStackTrace();
            return ret;
        }
        return ret;
    }

    public MethodModel getMethodModel(String methodSignature, boolean isStatic) {
        return MethodModel.getModel(methodSignature, isStatic);
    }
}
