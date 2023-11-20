package ca.ubc.ece.resess.taint.dynamic.vialin;

public class ViaLinTool implements TaintTool{
    private static String MOVE_TAINT = "move-object";
    private static String FIELD_NAME_DESC = "_pathTaint:Ljava/lang/PathTaint;";

    public String putInstanceFieldInstr() {
        return "iput-object";
    }

    public String putStaticFieldInstr() {
        return "sput-object";
    }

    public String getInstanceFieldInstr() {
        return "iget-object";
    }

    public String getStaticFieldInstr() {
        return "sget-object";
    }

    public String fieldNameAndDesc() {
        return FIELD_NAME_DESC;
    }

    public String getMoveResultTaint() {
        return "move-result-object";
    }

    public String getMoveTaint(){
        return MOVE_TAINT;
    }

    public String getTaintDumpInstrOneArg() {
        return "Ljava/lang/Thread;->addToTaintDump(Ljava/lang/PathTaint;)V";
    }

    public String getTaintDumpInstrTwoArg() {
        return "Ljava/lang/Thread;->addToTaintDump(Ljava/lang/PathTaint;Ljava/lang/Object;)V";
    }

    public String getGetParamTaintInstr(int i) {
        return "Ljava/lang/Thread;->getParamTaint" + i + "(Ljava/lang/Thread;)Ljava/lang/PathTaint;";
    }

    public String getGetReturnTaintInstr() {
        return "Ljava/lang/Thread;->getReturnTaint(Ljava/lang/Thread;)Ljava/lang/PathTaint;";
    }

    public String getGetAsyncTaskParam(){
        return "Ljava/lang/Thread;->getAsyncTaskParam()Ljava/lang/PathTaint;";
    }

    public String getSetAsyncTaskParam() {
        return "Ljava/lang/Thread;->setAsyncTaskParam(Ljava/lang/PathTaint;)V";
    }

    public String getSetParamTaintInstr(int i) {
        return "Ljava/lang/Thread;->setParamTaint" + i + "(Ljava/lang/Thread;Ljava/lang/PathTaint;)V";
    }

    public String getGetThrowTaintInstr() {
        return "Ljava/lang/Thread;->getThrowTaint()Ljava/lang/PathTaint;";
    }
    public String getSetReturnTaintInstr() {
        return "Ljava/lang/Thread;->setReturnTaint(Ljava/lang/PathTaint;)V";
    }

    public String getSetThrowTaintInstr() {
        return "Ljava/lang/Thread;->setThrowTaint(Ljava/lang/PathTaint;)V";
    }

    public String getSharedPrefsTaintAll() {
        return "Landroid/content/SharedPreferences;->getSharedPrefsTaintAll(Landroid/content/SharedPreferences;)Ljava/lang/PathTaint;";
    }

    public String getSharedPrefsTaint() {
        return "Landroid/content/SharedPreferences;->getSharedPrefsTaint(Landroid/content/SharedPreferences;Ljava/lang/String;)Ljava/lang/PathTaint;";
    }

    public String addSharedPrefsTaint() {
        return "Landroid/content/SharedPreferences$Editor;->addSharedPrefsTaint(Landroid/content/SharedPreferences$Editor;Ljava/lang/String;Ljava/lang/PathTaint;)V";
    }

    public String addSharedPrefsTaintAndObject() {
        return "Landroid/content/SharedPreferences$Editor;->addSharedPrefsTaint(Landroid/content/SharedPreferences$Editor;Ljava/lang/String;Ljava/lang/PathTaint;Ljava/lang/Object;)V";
    }

    public String formatterAddTaint() {
        return "Ljava/util/Formatter;->addTaint()V";
    }

    @Override
    public String addFileTaint() {
        return "Ljava/lang/PathTaint;->addFileTaint(Ljava/io/File;Ljava/lang/PathTaint;)V";
    }

    @Override
    public String addFileTaintAndObject() {
        return "Ljava/lang/PathTaint;->addFileTaint(Ljava/io/File;Ljava/lang/PathTaint;Ljava/lang/Object;)V";
    }

    @Override
    public String getFileTaint() {
        return "Ljava/lang/PathTaint;->getFileTaint(Ljava/io/File;)Ljava/lang/PathTaint;";
    }

    @Override
    public String returnInstr() {
        return "return-object";
    }

    @Override
    public String addParcelTaint() {
        return "Landroid/content/Intent;->addParcelTaint(Landroid/content/Intent;Ljava/lang/PathTaint;)V";
    }

    @Override
    public String addMapTaint() {
        return "Ljava/util/Map;->addTaint(Ljava/lang/PathTaint;)V";
    }

    @Override
    public String getMapTaint() {
        return "Ljava/util/Map;->getTaint()Ljava/lang/PathTaint;";
    }

    @Override
    public String addParcelTaintAndObject() {
        return "Landroid/content/Intent;->addParcelTaint(Landroid/content/Intent;Ljava/lang/PathTaint;Ljava/lang/Object;)V";
    }

    @Override
    public String getParcelTaint() {
        return "Landroid/content/Intent;->getParcelTaint(Landroid/content/Intent;)Ljava/lang/PathTaint;";
    }

    public String addBundleTaint() {
        return "Landroid/os/Bundle;->addBundleTaint(Landroid/os/Bundle;Ljava/lang/PathTaint;)V";
    }

    @Override
    public String addBundleTaintAndObject() {
        return "Landroid/os/Bundle;->addBundleTaint(Landroid/os/Bundle;Ljava/lang/PathTaint;Ljava/lang/Object;)V";
    }

    @Override
    public String getBundleTaint() {
        return "Landroid/os/Bundle;->getBundleTaint(Landroid/os/Bundle;)Ljava/lang/PathTaint;";
    }

    @Override
    public String setOrderedIntentParam() {
        return "Ljava/lang/Thread;->setOrderedIntentParam(Ljava/lang/PathTaint;)V";
    }

    @Override
    public String getOrderedIntentParam() {
        return "Ljava/lang/Thread;->getOrderedIntentParam()Ljava/lang/PathTaint;";
    }

    @Override
    public String arraySet() {
        return "aput-object";
    }

    @Override
    public String paramArray() {
        return "Ljava/lang/Thread;->paramTaintArray:[Ljava/lang/PathTaint;";
    }

    @Override
    public String getParamArray() {
        return "Ljava/lang/Thread;->getParamArray()[Ljava/lang/PathTaint;";
    }

    @Override
    public String paramTaint(int i) {
        return "Ljava/lang/Thread;->paramTaint" + i + ":Ljava/lang/PathTaint;";
    }

    @Override
    public String getIntent() {
        return "Landroid/app/Activity;->getIntent()Landroid/content/Intent;";
    }

}

