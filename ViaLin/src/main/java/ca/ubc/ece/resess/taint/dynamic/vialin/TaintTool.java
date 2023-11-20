package ca.ubc.ece.resess.taint.dynamic.vialin;

public interface TaintTool {
    public enum TaintToolType {
        PathTaint, TaintDroid, TaintDroidS, Original, Coverage
    }
    public String getMoveTaint();
    public String getMoveResultTaint();
    public String putInstanceFieldInstr();
    public String putStaticFieldInstr();
    public String getInstanceFieldInstr();
    public String getStaticFieldInstr();
    public String fieldNameAndDesc();
    public String getTaintDumpInstrOneArg();
    public String getTaintDumpInstrTwoArg();
    public String getGetParamTaintInstr(int i);
    public String getGetReturnTaintInstr();
    public String getGetAsyncTaskParam();
    public String getSetAsyncTaskParam();
    public String getSetParamTaintInstr(int i);
    public String getGetThrowTaintInstr();
    public String getSetReturnTaintInstr();
    public String getSetThrowTaintInstr();
    public String getSharedPrefsTaintAll();
    public String getSharedPrefsTaint();
    public String addSharedPrefsTaint();
    public String addSharedPrefsTaintAndObject();
    public String formatterAddTaint();
    public String addFileTaint();
    public String addFileTaintAndObject();
    public String getFileTaint();
    public String returnInstr();
    public String addParcelTaint();
    public String addParcelTaintAndObject();
    public String getParcelTaint();
    public String addBundleTaint();
    public String addBundleTaintAndObject();
    public String getBundleTaint();
    public String addMapTaint();
    public String getMapTaint();
    public String setOrderedIntentParam();
    public String getOrderedIntentParam();
    public String arraySet();
    public String paramArray();
    public String getParamArray();
    public String paramTaint(int i);
    public String getIntent();
}

