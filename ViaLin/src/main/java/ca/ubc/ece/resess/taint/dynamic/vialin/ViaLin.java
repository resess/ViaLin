package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ca.ubc.ece.resess.taint.dynamic.vialin.TaintInjector.AnalysisDestination;




public class ViaLin {


  public static void main(String args[]) throws IOException {

    List<String> inFiles = new ArrayList<>();
    String mode = args[0];

    String isFrameWorkStr = "none";
    String toolStr = "none";
    String outDir = "none";
    String analysisDir = "none";
    String srcFile = "none";
    String sinkFile = "none";
    String extraConfig = "none";
    int nextArg = 0;
    if (mode.equals("cov")) {
      toolStr = "cov";
      outDir = args[1];
      extraConfig = args[2];
      nextArg = 3;
    } else {
      isFrameWorkStr = args[1];
      toolStr = args[2];
      outDir = args[3];
      analysisDir = args[4];
      srcFile = args[5];
      sinkFile = args[6];
      nextArg = 7;
    }


    System.out.println("Src: " + srcFile);
    System.out.println("Sink: " + sinkFile);

    for (int i = nextArg; i < args.length; i++) {
      inFiles.add(args[i]);
    }


    TaintTool tool;
    if (toolStr.equals("vl")) {
      tool = new ViaLinTool();
    } else if (toolStr.equals("td")) {
      tool = new TaintDroidTool();
    } else if (toolStr.equals("orig")) {
      tool = new NoTool();
    } else if (toolStr.equals("cov")) {
      tool = new CovTool();
    } else {
      throw new Error("Unsuporrted tool type");
    }

    boolean isFramework = Boolean.valueOf(isFrameWorkStr);

    long start = System.currentTimeMillis();


    if (mode.equals("t")) { // taint
      TaintInjector injector = new TaintInjector(inFiles, outDir, analysisDir, srcFile, sinkFile, tool, isFramework, AnalysisDestination.APP);
      injector.analyze();
      long afterAnalysis = System.currentTimeMillis() - start;
      System.out.println("Analysis done in " + afterAnalysis + " ms");
      injector = new TaintInjector(inFiles, outDir, analysisDir, srcFile, sinkFile, tool, isFramework, AnalysisDestination.APP);
      injector.inject();
      long afterInject = System.currentTimeMillis() - start;
      System.out.println("Analysis + injection done in " + afterInject + " ms");
    } else if (mode.equals("a")) { // analyze
      TaintInjector injector = new TaintInjector(inFiles, outDir, analysisDir, srcFile, sinkFile, tool, isFramework, AnalysisDestination.FRAMEWORK);
      injector.analyze();
      long afterAnalysis = System.currentTimeMillis() - start;
      System.out.println("Analysis done in " + afterAnalysis + " ms");
    } else if (mode.equals("cov")) { // coverage
      System.out.println("Will instrument for coverage");
      TaintInjector injector = new TaintInjector(inFiles, outDir, analysisDir, srcFile, sinkFile, tool, isFramework, AnalysisDestination.APP);
      if (extraConfig.equals("bytecode")) {
        injector.setBytecodeCov(true);
      } else {
        injector.setBytecodeCov(false);
      }
      injector.inject();
    } else {
      throw new Error("Unupported mode " + mode);
    }
  }

}