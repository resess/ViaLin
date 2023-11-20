package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nonnull;

import org.jf.baksmali.Baksmali;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.reference.DexBackedTypeReference;
import org.jf.dexlib2.iface.DexFile;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;
import org.jf.util.ExceptionWithContext;

public class TaintInjector {

    enum FileType {JAR, DEX, APK};

    enum AnalysisDestination {APP, FRAMEWORK}

    private static final int JOBS = 8;
    private static final int BUFFER_SIZE = 4096;
    @Nonnull private final List<FileType> fileTypes;
    @Nonnull private final List<String> jarFiles;
    @Nonnull private final List<String> jarNames;
    @Nonnull private final String outDir;
    @Nonnull private final String jarDir;
    @Nonnull private final String dexDir;
    @Nonnull private final String newJardir;
    @Nonnull private List<String> dexFiles;
    @Nonnull private final String frameworkAnalysisDir;
    @Nonnull private final String coverageFile;
    @Nonnull private final TaintTool tool;
    @Nonnull private final AnalysisDestination analysisDestination;
    private List<String> cannotInstrument = new ArrayList<>();
    private final boolean isFramework;
    private boolean injected = false;
    private boolean bytecodeCov = false;

    public TaintInjector(List<String> jarFiles, String outDir, String analysisDir, String srcFile, String sinkFile, TaintTool tool, boolean isFramework, AnalysisDestination analysisDestination) {
        this.jarFiles = jarFiles;

        if (tool instanceof ViaLinTool || tool instanceof TaintDroidTool) {
            TaintSource.loadSources(srcFile);
            TaintSink.loadSinks(sinkFile);
        }

        this.jarNames = new ArrayList<>();
        this.fileTypes = new ArrayList<>();
        this.tool = tool;
        this.isFramework = isFramework;
        for (String f : this.jarFiles) {
            this.jarNames.add(new File(f).getName());
            String extension = getExtension(f);
            if (extension.equals("jar")) {
                this.fileTypes.add(FileType.JAR);
            } else if (extension.equals("dex")) {
                this.fileTypes.add(FileType.DEX);
            } else if (extension.equals("apk")) {
                this.fileTypes.add(FileType.APK);
            } else {
                throw new Error("Unsupported file type: " + extension);
            }
        }

        System.out.println("JarNames: " + this.jarNames);
        System.out.println("FileTypes: " + this.fileTypes);

        if (outDir.endsWith("/")) {
            this.outDir = outDir.substring(0, outDir.length()-1);
        } else {
            this.outDir = outDir;
        }

        this.jarDir = this.outDir + File.separator + "jar";
        File dir = new File(this.jarDir);
        if (dir.exists()) {
            deleteDirectory(dir);
        }
        this.dexDir = this.outDir + File.separator + "dex";
        dir = new File(this.dexDir);
        if (dir.exists()) {
            deleteDirectory(dir);
        }

        this.newJardir = this.outDir + File.separator + "new-jar";
        dir = new File(this.newJardir);
        if (dir.exists()) {
            boolean flag = deleteDirectory(dir);
            System.out.println("Deleted directory: " + flag);
        }
        this.frameworkAnalysisDir = analysisDir;
        this.coverageFile = this.outDir + File.separator + "full_coverage.log";
        try {
            Files.write(Paths.get(coverageFile), ("").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new Error("Cannot create coverage file: " + coverageFile);
        }

        this.analysisDestination = analysisDestination;
    }

    public void setBytecodeCov(boolean bytecodeCov) {
        this.bytecodeCov = bytecodeCov;
    }

    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    public boolean analyze() {
        try {
            extractJar();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        collectDexFiles();
        List<String> smaliFiles = extractDexFiles(dexFiles);
        analyzeDex(smaliFiles, frameworkAnalysisDir, analysisDestination);
        return true;
    }


    public boolean inject() {
        if (injected) {
            return true;
        }

        try {
            extractJar();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        collectDexFiles();

        if (dexFiles.size() == 0) {
            return true;
        }

        int fileNum = 0;
        int api = readDexFile(dexFiles.get(fileNum)).getOpcodes().api;


        for (int i = 0; i < dexFiles.size(); i++) {
            String dexFile = dexFiles.get(i);
            List<String> smaliFiles = extractDexFile(dexFile, i);
            long startTime = System.currentTimeMillis();
            addTaint(tool, smaliFiles, frameworkAnalysisDir);
            long endTime = System.currentTimeMillis();
            System.out.format("Taint addition took: %s%n", (endTime - startTime)/1000);
            while (!smaliFiles.isEmpty()) {
                try {
                    startTime = System.currentTimeMillis();
                    smaliFiles = packageDexFile(smaliFiles, api, fileNum);
                    endTime = System.currentTimeMillis();
                    System.out.format("Packaging took: %s%n", (endTime - startTime)/1000);

                    fileNum++;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }

            if (!cannotInstrument.isEmpty()) {
                throw new Error("Couldn't package dex file for classes: " + cannotInstrument);
            }
        }

        try {
            packageJar();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        injected = true;
        return true;
    }

    private void extractJar() throws IOException {
        File jarDirFile = new File(jarDir);
        if (jarDirFile.exists()) {
            jarDirFile.delete();
        }
        System.out.println("Will extract jar");
        jarDirFile.mkdir();
        for (int i = 0; i < jarFiles.size(); i++) {
            String jarFile = jarFiles.get(i);
            String fileName = jarNames.get(i);
            FileType fileType = fileTypes.get(i);
            if (fileType.equals(FileType.DEX)) {
                Files.copy(Paths.get(jarFile), Paths.get(jarDir, fileName));
                System.out.println("Copied from " + Paths.get(jarFile) + " to " + Paths.get(jarDir, fileName));
            } else if (fileType.equals(FileType.JAR) || fileType.equals(FileType.APK)) {
                System.out.println("Will unzip: " + jarFile);
                unzip(jarFile, jarDir);
            }
        }
        copyFolder(Paths.get(this.jarDir), Paths.get(this.newJardir));
    }



    private void collectDexFiles() {

        try (Stream<Path> walk = Files.walk(Paths.get(jarDir))) {
            // We want to find only regular files
            dexFiles = walk.filter(f -> f.toString().endsWith(".dex"))
                    .map(x -> x.toString()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        dexFiles.sort((a, b) -> a.compareTo(b));
        System.out.println("Dexfiles: " + dexFiles);
    }

    private static DexFile readDexFile(String fileName) {
        File srcFile = new File(fileName);
        System.out.println("readDexFile: " + srcFile);
        try {
            DexFile dexFile = DexFileFactory.loadDexFile(srcFile, Opcodes.forApi(27));
            return dexFile;
        } catch (IOException e) {
            e.printStackTrace();
            throw new Error("Cannot read dex file " + fileName);
        }
      }


    private List<String> extractDexFile(String dexFileStr, int i) {
        BaksmaliOptions options = new BaksmaliOptions();
        options.debugInfo = true;

        List<String> smaliFiles = new ArrayList<>();

        File dexDirFile = new File(dexDir + i);
        if (dexDirFile.exists()) {
            System.out.println("Deleted dex dir: " + dexDirFile);
            dexDirFile.delete();
        }
        dexDirFile.mkdir();
        System.out.println("Extracting dex file: " + dexDirFile.getName());
        DexFile dexFile = readDexFile(dexFileStr);
        options.apiLevel = dexFile.getOpcodes().api;
        Baksmali.disassembleDexFile(dexFile, dexDirFile, JOBS, options);
        try (Stream<Path> walk = Files.walk(Paths.get(dexDirFile.getPath()))) {
            List<String> smaliFilesInDexFile = walk.filter(Files::isRegularFile)
                    .map(x -> x.toString()).collect(Collectors.toList());
            for (String f : smaliFilesInDexFile) {
                if (!smaliFiles.contains(f)) {
                    smaliFiles.add(f);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return smaliFiles;
        }
        return smaliFiles;
    }

    private List<String> extractDexFiles(List<String> dexFiles) {
        BaksmaliOptions options = new BaksmaliOptions();
        options.debugInfo = true;

        List<String> smaliFiles = new ArrayList<>();
        int i = 0;
        for (String dexFileStr : dexFiles) {
            File dexDirFile = new File(dexDir + i);
            if (dexDirFile.exists()) {
                System.out.println("Deleted dex dir: " + dexDirFile);
                dexDirFile.delete();
            }
            i++;
            dexDirFile.mkdir();
            DexFile dexFile = readDexFile(dexFileStr);
            options.apiLevel = dexFile.getOpcodes().api;
            Baksmali.disassembleDexFile(dexFile, dexDirFile, JOBS, options);
            try (Stream<Path> walk = Files.walk(Paths.get(dexDirFile.getPath()))) {
                List<String> smaliFilesInDexFile = walk.filter(Files::isRegularFile)
                        .map(x -> x.toString()).collect(Collectors.toList());
                for (String f : smaliFilesInDexFile) {
                    if (!smaliFiles.contains(f)) {
                        smaliFiles.add(f);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                return smaliFiles;
            }
        }
        return smaliFiles;
    }

    private void addTaint (TaintTool tool, List<String> smaliFiles, String analysisDir) {
        System.out.println("Tainting " + smaliFiles.size() + " files from: " + this.jarNames);
        TaintAnalysis taintAnalysis;
        if (tool instanceof ViaLinTool || tool instanceof TaintDroidTool) {
            taintAnalysis = new ClassTaint(tool, smaliFiles, analysisDir, isFramework, outDir);
            taintAnalysis.analyze();
        } else if(tool instanceof NoTool) {
            throw new Error("Unsupported taint tool (NoTool)");
        } else if(tool instanceof CovTool) {
            taintAnalysis = new Coverage(smaliFiles, this.bytecodeCov, coverageFile);
        } else {
            throw new Error("Unsupported taint tool");
        }

        taintAnalysis.addTaint();

        TransformConds transformConds = new TransformConds(smaliFiles, analysisDir, analysisDir);
        transformConds.transform();
    };

    private void analyzeDex (List<String> smaliFiles, String frameworkAnalysisDir, AnalysisDestination destination) {
        System.out.println("Analyzing " + smaliFiles.size() + " files from: " + this.jarNames);
        ClassAnalysis classAnalysis;
        if (destination.equals(AnalysisDestination.APP)) {
            classAnalysis = new ClassAnalysis(frameworkAnalysisDir, outDir);
        } else {
            classAnalysis = new ClassAnalysis(frameworkAnalysisDir);
        }
        classAnalysis.analyze(smaliFiles);
        classAnalysis.save();
        System.out.println("Analyses done");
    };

    public static DexFile addType(DexFile dexFile) {
        DexBackedDexFile dexBackedDexFile = (DexBackedDexFile) dexFile;
        System.out.println("Types from references: ");
        for (DexBackedTypeReference dexBackedTypeReference : dexBackedDexFile.getTypeReferences()) {
            System.out.println("    " + dexBackedTypeReference.toString());
        }
        return dexBackedDexFile;
      }

    private List<String> packageDexFile(List<String> smaliFiles, int apiLevel, int fileNum) throws IOException {
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = this.newJardir + File.separator + "classes" + ((fileNum==0)? "" : String.valueOf(fileNum+1)) + ".dex";
        options.apiLevel = apiLevel;
        options.jobs = JOBS;
        options.verboseErrors = true;

        int divider = 1;

        System.out.format("-------------------%nTrying to pack %s, num smali files %s %n", options.outputDexFile, smaliFiles.size());
        while (divider <= smaliFiles.size()) {
            try {
                System.out.format("Divider: %s, # files %s%n", divider, smaliFiles.size());
                Smali.assemble(options, smaliFiles.subList(0, smaliFiles.size()/divider));
                if (divider == 1) {
                    return new ArrayList<>();
                }
                return smaliFiles.subList(smaliFiles.size()/divider, smaliFiles.size());
            } catch (ExceptionWithContext e) {
                divider = divider *2;
                System.out.println("Error when packaging, will retry with less files: " + e.getMessage());
            }

        }
        List<String> badFiles = smaliFiles.subList(0, 1);
        cannotInstrument.addAll(badFiles);
        return smaliFiles.subList(1, smaliFiles.size());
    }

    private void packageJar() throws IOException {
        for (int i = 0; i < fileTypes.size(); i++) {
            String jarName = jarNames.get(i);
            FileType fileType = fileTypes.get(i);
            if (fileType.equals(FileType.JAR)) {
                ProcessBuilder process = new ProcessBuilder();
                process.directory(new File(newJardir));
                process.command("jar", "cf", jarName, ".");
                process.start();
            } else if (fileType.equals(FileType.APK)) {
                System.out.println("Will zip back apk at: " + newJardir);
                System.out.println("jar name is: " + jarName);
                ProcessBuilder process = new ProcessBuilder();
                process.directory(new File(newJardir));
                process.command("zip", "-r", jarName, ".");
                Process started = process.start();
                InputStream inputStream = started.getInputStream();
                getStringFromInputStream(inputStream);
                getStringFromInputStream(started.getErrorStream());

            } else if (fileType.equals(FileType.DEX)) {
                // pass
            } else {
                throw new Error("Unsupported file type: " + fileType);
            }
        }
    }

    private String getStringFromInputStream(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ( (line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
        }
        String result = builder.toString();
        return result;
    }

    public void copyFolder(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".dex")) {
                    Files.copy(file, target.resolve(source.relativize(file)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            File parentPath = new File(filePath).getParentFile();
            if (!parentPath.exists()) {
                parentPath.mkdirs();
            }
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                if (!new File(filePath).isDirectory()) {
                    extractFile(zipIn, filePath);
                }
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdirs();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    private String getExtension(String filePath) {
        String[] splits = filePath.split("\\.");
        return splits[splits.length-1];
    }

}
