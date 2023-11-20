package ca.ubc.ece.resess.taint.dynamic.vialin;

public class TaintDroidTool implements TaintTool{
    private static String MOVE_TAINT = "move";
    private static String FIELD_NAME_DESC = "_taintdroid:I";

    public String putInstanceFieldInstr() {
        return "iput";
    }

    public String putStaticFieldInstr() {
        return "sput";
    }

    public String getInstanceFieldInstr() {
        return "iget";
    }

    public String getStaticFieldInstr() {
        return "sget";
    }

    public String fieldNameAndDesc() {
        return FIELD_NAME_DESC;
    }

    public String getMoveTaint(){
        return MOVE_TAINT;
    }

    public String getMoveResultTaint() {
        return "move-result";
    }

    public String getTaintDumpInstrOneArg() {
        return "Ljava/lang/TaintDroid;->dumpTaint(I)V";
    }

    public String getTaintDumpInstrTwoArg() {
        return "Ljava/lang/TaintDroid;->dumpTaint(ILjava/lang/Object;)V";
    }

    public String getGetParamTaintInstr(int i) {
        return "Ljava/lang/Thread;->getParamTaintTaintDroid" + i + "int(Ljava/lang/Thread;)I";
    }

    public String getGetReturnTaintInstr() {
        return "Ljava/lang/Thread;->getReturnTaintTaintDroidInt(Ljava/lang/Thread;)I";
    }

    public String getGetAsyncTaskParam(){
        return "Ljava/lang/Thread;->getAsyncTaskParamTaintDroidInt()I";
    }

    public String getSetAsyncTaskParam() {
        return "Ljava/lang/Thread;->setAsyncTaskParamTaintDroid(I)V";
    }

    public String getSetParamTaintInstr(int i) {
        return "Ljava/lang/Thread;->setParamTaintTaintDroid" + i + "int(Ljava/lang/Thread;I)V";
    }

    public String getGetThrowTaintInstr() {
        return "Ljava/lang/Thread;->getThrowTaintInt()I";
    }

    public String getSetReturnTaintInstr() {
        return "Ljava/lang/Thread;->setReturnTaintTaintDroidInt(I)V";
    }

    public String getSetThrowTaintInstr() {
        return "Ljava/lang/Thread;->setThrowTaint(I)V";
    }

    public String getSharedPrefsTaintAll() {
        return "Landroid/content/SharedPreferences;->getSharedPrefsTaintAllTaintDroid(Landroid/content/SharedPreferences;)I";
    }

    public String getSharedPrefsTaint() {
        return "Landroid/content/SharedPreferences;->getSharedPrefsTaintTaintDroid(Landroid/content/SharedPreferences;Ljava/lang/String;)I";
    }

    public String addSharedPrefsTaint() {
        return "Landroid/content/SharedPreferences$Editor;->addSharedPrefsTaintTaintDroid(Landroid/content/SharedPreferences$Editor;Ljava/lang/String;I)V";
    }

    public String addSharedPrefsTaintAndObject() {
        return "Landroid/content/SharedPreferences$Editor;->addSharedPrefsTaintTaintDroid(Landroid/content/SharedPreferences$Editor;Ljava/lang/String;ILjava/lang/Object;)V";
    }

    public String formatterAddTaint() {
        return "Ljava/util/Formatter;->addTaintTaintDroid()V";
    }

    @Override
    public String addFileTaint() {
        return "Ljava/lang/TaintDroid;->addFileTaint(Ljava/io/File;I)V";
    }

    @Override
    public String addFileTaintAndObject() {
        throw new Error("addFileTaintAndObject unimplemented for TaintDroid");
    }

    @Override
    public String getFileTaint() {
        return "Ljava/lang/TaintDroid;->getFileTaint(Ljava/io/File;)I";
    }

    @Override
    public String returnInstr() {
        return "return";
    }

    @Override
    public String addParcelTaint() {
        return "Landroid/content/Intent;->addParcelTaint(Landroid/content/Intent;I)V";
    }

    @Override
    public String addMapTaint() {
        return "Ljava/util/Map;->addTaint(I)V";
    }

    @Override
    public String getMapTaint() {
        return "Ljava/util/Map;->getTaintTaintDroid()I";
    }

    @Override
    public String addParcelTaintAndObject() {
        return "Landroid/content/Intent;->addParcelTaint(Landroid/content/Intent;ILjava/lang/Object;)V";
    }

    @Override
    public String getParcelTaint() {
        return "Landroid/content/Intent;->getParcelTaintTaintDroid(Landroid/content/Intent;)I";
    }

    public String addBundleTaint() {
        return "Landroid/os/Bundle;->addBundleTaint(Landroid/os/Bundle;I)V";
    }

    @Override
    public String addBundleTaintAndObject() {
        return "Landroid/os/Bundle;->addBundleTaint(Landroid/os/Bundle;ILjava/lang/Object;)V";
    }

    @Override
    public String getBundleTaint() {
        return "Landroid/os/Bundle;->getBundleTaintTaintDroid(Landroid/os/Bundle;)I";
    }

    @Override
    public String setOrderedIntentParam() {
        return "Ljava/lang/Thread;->setOrderedIntentParam(I)V";
    }

    @Override
    public String getOrderedIntentParam() {
        return "Ljava/lang/Thread;->getOrderedIntentParamTaintDroid()I";
    }

    @Override
    public String arraySet() {
        return "aput";
    }



    @Override
    public String paramArray() {
        return "Ljava/lang/Thread;->paramTaintTaintDroidArray:[I";
    }

    @Override
    public String getParamArray() {
        return "Ljava/lang/Thread;->getParamArrayTaintDroid()[I";
    }

    @Override
    public String paramTaint(int i) {
        return "Ljava/lang/Thread;->paramTaintTaintDroid" + i + "int:I";
    }

    @Override
    public String getIntent() {
        return "Landroid/app/Activity;->getIntent()Landroid/content/Intent;";
    }
}
