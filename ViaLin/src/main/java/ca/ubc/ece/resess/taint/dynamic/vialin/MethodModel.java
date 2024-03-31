package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.XML;


import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class MethodModel {

    
    private static final String ACCESS_PATH = "AccessPath";
    static String methodModelsPath;
    static String extraPath;
    static String taintWrapperPath;
    static Map<String, MethodModel> manualModel = new HashMap<>();
    static Map<String, MethodModel> taintWrapperModel = new HashMap<>();
    static Map<String, String> taintWrapperType = new HashMap<>();

    private String modelType;
    private List<MethodModelAssignment> model;

    static class MethodModelAssignment {
        enum VariableType {
            RETURN, INSTANCE, PARAM, CLEAR, UNKNOW
        }

        Integer leftParam;
        Integer rightParam;
        String leftPath;
        String rightPath;
        VariableType leftType = VariableType.UNKNOW;
        VariableType rightType = VariableType.UNKNOW;

        public MethodModelAssignment(Integer leftParam, Integer rightParam) {
            this.leftParam = leftParam;
            this.rightParam = rightParam;
            this.leftPath = "";
            this.rightPath = "";
            getParamType(leftParam, rightParam);
        }

        public MethodModelAssignment(Integer leftParam, Integer rightParam, String leftPath, String rightPath) {
            this.leftParam = leftParam;
            this.rightParam = rightParam;
            this.leftPath = leftPath;
            this.rightPath = rightPath;
            getParamType(leftParam, rightParam);
        }

        private void getParamType(Integer leftParam, Integer rightParam) {
            if (leftParam.equals(Constants.THIS)) {
                leftType = VariableType.INSTANCE;
            } else if (leftParam.equals(Constants.RETURN)) {
                leftType = VariableType.RETURN;
            } else {
                leftType = VariableType.PARAM;
            }
    
            if (rightParam.equals(Constants.CLEAR)) {
                rightType = VariableType.CLEAR;
            } else if (rightParam.equals(Constants.THIS)) {
                rightType = VariableType.INSTANCE;
            } else {
                rightType = VariableType.PARAM;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(leftType);
            if (leftType.equals(VariableType.PARAM)) {
                sb.append("[");
                sb.append(leftParam);
                sb.append("] ");
            }

            if (!leftPath.isEmpty()) {
                sb.append("(");
                sb.append(leftPath);
                sb.append(") "); 
            }

            sb.append(" <- ");
            
            sb.append(rightType);
            if (rightType.equals(VariableType.PARAM)) {
                sb.append("[");
                sb.append(rightParam);
                sb.append("] ");
            }
            if (!rightPath.isEmpty()) {
                sb.append("(");
                sb.append(rightPath);
                sb.append(")");
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!MethodModelAssignment.class.isAssignableFrom(obj.getClass())) {
                return false;
            }
            final MethodModelAssignment other = (MethodModelAssignment) obj;
            if (this.leftParam != other.leftParam) {
                return false;
            }
            if (this.rightParam != other.rightParam) {
                return false;
            }
            if (!this.leftPath.equals(other.leftPath)) {
                return false;
            }
            if (!this.rightPath.equals(other.rightPath)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 53 * hash + this.leftParam;
            hash = 53 * hash + this.rightParam;
            hash = 53 * hash + this.leftPath.hashCode();
            hash = 53 * hash + this.rightPath.hashCode();
            return hash;
        }
    }

    public MethodModel(String modelType) {
        this.modelType = modelType;
        this.model = new ArrayList<>();
    }

    public void addAssign(MethodModelAssignment assign) {
        if (model.contains(assign)) {
            return;
        }
        if (assign.leftType.equals(MethodModelAssignment.VariableType.RETURN)) {
            model.add(assign);    
        } else {
            model.add(0, assign);
        }
    }

    public String getModelType() {
        return modelType;
    }

    public List<MethodModelAssignment> getModel() {
        return model;
    }

    private boolean isEmpty() {
        return model.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("ModelType: %s, Model: %s", modelType, model);
    }

    public boolean hasAssignToReceiver() {
        for (MethodModelAssignment assign : model) {
            if (assign.leftType.equals(MethodModelAssignment.VariableType.INSTANCE)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAssignToReturn() {
        for (MethodModelAssignment assign : model) {
            if (assign.leftType.equals(MethodModelAssignment.VariableType.RETURN)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasClearReceiver() {
        for (MethodModelAssignment assign : model) {
            if (assign.rightType.equals(MethodModelAssignment.VariableType.CLEAR) && assign.leftType.equals(MethodModelAssignment.VariableType.INSTANCE)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasClearReturn() {
        for (MethodModelAssignment assign : model) {
            if (assign.rightType.equals(MethodModelAssignment.VariableType.CLEAR) && assign.leftType.equals(MethodModelAssignment.VariableType.RETURN)) {
                return true;
            }
        }
        return false;
    }

    

    public static void readTaintWrapperFile(){
        try (BufferedReader br = new BufferedReader(new FileReader(MethodModel.taintWrapperPath))) {
            String line = "";
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("%") && !line.startsWith("^")) {
                    if (line.startsWith("~")) {
                        addExcludeModel(line.substring(1));
                    } else if (line.startsWith("-")) {
                        addKillModel(line.substring(1));
                    } else {
                        addPropagateModel(line);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private static void addExcludeModel(String methodSignature) {
        MethodModel model = new MethodModel("Exclude");
        MethodModelAssignment thisAssign = new MethodModelAssignment(Constants.THIS, Constants.THIS);
        model.addAssign(thisAssign);
        int numArgs = StringUtils.countMatches(methodSignature, ",")+1;
        for (int i = 0; i < numArgs; i++) {
            MethodModelAssignment assignIdentity = new MethodModelAssignment(i, i);
            model.addAssign(assignIdentity);
        }
        MethodModelAssignment retAssign = new MethodModelAssignment(Constants.RETURN, Constants.CLEAR);
        model.addAssign(retAssign);
        taintWrapperModel.put(methodSignature, model);
        taintWrapperType.put(methodSignature, "Exclude");
    }

    private static void addKillModel(String methodSignature) {
        MethodModel model = new MethodModel("Kill");
        MethodModelAssignment thisAssign = new MethodModelAssignment(Constants.THIS, Constants.CLEAR);
        model.addAssign(thisAssign);
        int numArgs = StringUtils.countMatches(methodSignature, ",")+1;
        if (StringUtils.countMatches(methodSignature, "()")==1) {
            numArgs = 0;
        }
        for (int i = 0; i < numArgs; i++) {
            MethodModelAssignment assign = new MethodModelAssignment(i, Constants.CLEAR);
            model.addAssign(assign);
        }
        MethodModelAssignment retAssign = new MethodModelAssignment(Constants.RETURN, Constants.CLEAR);
        model.addAssign(retAssign);
        taintWrapperModel.put(methodSignature, model);
        taintWrapperType.put(methodSignature, "Kill");
    }

    private static void addPropagateModel(String methodSignature) {
        MethodModel model = new MethodModel("Propagate");
        int numArgs = StringUtils.countMatches(methodSignature, ",")+1;
        if (StringUtils.countMatches(methodSignature, "()")==1) {
            numArgs = 0;
        }
        for (int i = 0; i < numArgs; i++) {
            MethodModelAssignment assignThis = new MethodModelAssignment(Constants.THIS, i);
            MethodModelAssignment assignReturn = new MethodModelAssignment(Constants.RETURN, i);
            model.addAssign(assignThis);
            model.addAssign(assignReturn);
        }
        MethodModelAssignment retAssignFromThis = new MethodModelAssignment(Constants.RETURN, Constants.THIS);
        model.addAssign(retAssignFromThis);
        taintWrapperModel.put(methodSignature, model);
        taintWrapperType.put(methodSignature, "Propagate");
    }


    public static void setMethodModelsPath(String methodModelsPath) {
        MethodModel.methodModelsPath = methodModelsPath;
        if (!new File(MethodModel.methodModelsPath).isDirectory()) {
            throw new Error(String.format("Model path (%s) does not exist", MethodModel.methodModelsPath), new Throwable());
        }
    }

    public static void setExtraPath(String extraPath) {
        MethodModel.extraPath = extraPath;
    }

    public static void setTaintWrapperFile(String taintWrapperPath) {
        MethodModel.taintWrapperPath = taintWrapperPath;
        if (!new File(MethodModel.taintWrapperPath).isFile()) {
            throw new Error(String.format("Taint wrapper path (%s) does not exist", MethodModel.taintWrapperPath), new Throwable());
        }
        readTaintWrapperFile();
    }

    public static MethodModel getModel(String methodSignature, boolean isStatic){
        JimpleSignature jimpleSignature = convertSignatureToJimple(methodSignature, isStatic);

        methodSignature = jimpleSignature.toString();

        if (!manualModel.containsKey(methodSignature)) {
            if(!taintWrapperModel.containsKey(methodSignature)){
                loadModel(methodSignature, jimpleSignature.className, MethodModel.methodModelsPath);
                if (MethodModel.extraPath != null) {
                    loadModel(methodSignature, jimpleSignature.className, MethodModel.extraPath);
                }
            }
        }
        MethodModel model = manualModel.get(methodSignature); // manual model
        if (model == null) {
            model = taintWrapperModel.get(methodSignature); // taintWrapper model
        }
        if (model == null) {
            // default taint wrapper model
            addPropagateModel(methodSignature);
            model = taintWrapperModel.get(methodSignature);
        }

        return model;
    }


    private static void loadModel(String methodSignature, String className, String path) {
        if (manualModel.containsKey(methodSignature)) {
            return;
        }

        // Check that path exists
        if (!new File(path).isDirectory()) {
            AnalysisLogger.log(true, "Warning: Path %s does not exist%n", path);
            return;
        }


        String fileName = path + File.separator + className + ".xml";
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line = "";
            String xmlStr = "";
            while ((line = br.readLine()) != null) {
                xmlStr += line;
            }
            JSONArray jsonArray = XML.toJSONObject(xmlStr).getJSONObject("summary").getJSONObject("methods").optJSONArray("method");
            if (jsonArray == null) {
                jsonArray = new JSONArray();
                jsonArray.put(XML.toJSONObject(xmlStr).getJSONObject("summary").getJSONObject("methods").getJSONObject("method"));
            }
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                MethodModel model = new MethodModel("Manual");
                String modelId = "<"+className+ ": " + jsonObject.getString("id") +">";
                extractClearsOrFlowsFromManualModel(jsonObject, model);
                if (!model.isEmpty()) {
                    manualModel.put(modelId, model);
                    taintWrapperType.put(modelId, "Manual");
                }
            }
        } catch (IOException | JSONException e) {
            // pass
        }
    }



    static class JimpleSignature {
        String className;
        String methodName;
        String returnType;
        List<String> params;
        boolean isStatic;

        public JimpleSignature(String className, String methodName, String returnType, List<String> params, boolean isStatic) {
            this.className = convertTypeToJimple(className);
            this.methodName = methodName;
            this.returnType = convertTypeToJimple(returnType);
            this.params = new ArrayList<>();
            for (String param : params) {
                if (!param.equals("*")) {
                    this.params.add(convertTypeToJimple(param));
                }
            }
            this.isStatic = isStatic;
        }

        @Override
        public String toString() {
            StringBuilder jimpleSignature = new StringBuilder();
            jimpleSignature.append("<");
            jimpleSignature.append(className);
            jimpleSignature.append(": ");
            jimpleSignature.append(returnType);
            jimpleSignature.append(" ");
            jimpleSignature.append(methodName);
            jimpleSignature.append("(");
            int firstParamIndex = isStatic ? 0 : 1;
            for (int i = firstParamIndex; i < params.size(); i++) {
                if (i != firstParamIndex) {
                    jimpleSignature.append(", ");
                }
                jimpleSignature.append(this.params.get(i));
            }
            jimpleSignature.append(")>");
            return jimpleSignature.toString();
        }
    }

    private static JimpleSignature convertSignatureToJimple(String methodSignature, boolean isStatic) {
        MethodInfo methodInfo = new MethodInfo(methodSignature, isStatic);
        return new JimpleSignature(methodInfo.getClassName(), methodInfo.getMethodName(), methodInfo.getReturnType(), methodInfo.getParams(), isStatic);
    }

    private static String convertTypeToJimple(String bytecodeType) {

        String newType;
        int numberofArrays = 0;
        while (bytecodeType.startsWith("[")) {
            bytecodeType = bytecodeType.substring(1);
            numberofArrays += 1;
        }
        if (bytecodeType.equals("V")){
            newType = "void";
        } else if (bytecodeType.equals("B")){
            newType = "byte";
        } else if (bytecodeType.equals("S")){
            newType = "short";
        } else if (bytecodeType.equals("I")){
            newType = "int";
        } else if (bytecodeType.equals("J")){
            newType = "long";
        } else if (bytecodeType.equals("D")){
            newType = "double";
        } else if (bytecodeType.equals("F")){
            newType = "float";
        } else if (bytecodeType.equals("Z")){
            newType = "boolean";
        } else if (bytecodeType.equals("C")){
            newType = "char";
        } else if (bytecodeType.startsWith("L")){
            newType = bytecodeType.substring(1, bytecodeType.length() - 1).replace("/", ".");
        } else {
            throw new Error("Un-supported type: "+ bytecodeType);
        }
            
        while (numberofArrays > 0) {
            newType = newType + "[]";
            numberofArrays -= 1;
        }
        return newType;
    }

    private static void extractClearsOrFlowsFromManualModel(JSONObject jsonObject, MethodModel model) {
        try {
            JSONObject clearObject = jsonObject.getJSONObject("clears").optJSONObject("clear");
            if (clearObject == null) {
                JSONArray clearArray = jsonObject.getJSONObject("clears").getJSONArray("clear");
                for (int j = 0; j < clearArray.length(); j++) {
                    JSONObject clear = clearArray.getJSONObject(j);
                    model.addAssign(getClear(clear));
                }
            } else {
                model.addAssign(getClear(clearObject));
            }
        } catch (JSONException e) {
            extractFlowsFromManualModel(jsonObject, model);
        }
    }

    private static void extractFlowsFromManualModel(JSONObject jsonObject, MethodModel model) {
        try {
            JSONObject flowObject = jsonObject.getJSONObject("flows").optJSONObject("flow");
            if (flowObject == null) {
                JSONArray flowArray = jsonObject.getJSONObject("flows").getJSONArray("flow");
                for (int j = 0; j < flowArray.length(); j++) {
                    JSONObject flow = flowArray.getJSONObject(j);
                    model.addAssign(getFlow(flow));
                }
            } else {
                model.addAssign(getFlow(flowObject));
            }
        } catch (JSONException e2) {
            // pass
        }
    }


    private static MethodModelAssignment getClear(JSONObject clear){
        String leftPath = "";
        Integer leftParam = Constants.RETURN;
        Integer rightParam = Constants.CLEAR;

        String leftType = clear.getString("sourceSinkType");
        if (leftType.equals("Field")) {
            leftParam = Constants.THIS;
        }
        
        String dstAp = clear.optString(ACCESS_PATH);
        if (dstAp != null && !dstAp.isEmpty()) {
            leftPath = dstAp;
        }

        return new MethodModelAssignment(leftParam, rightParam, leftPath, "");
    }

    private static MethodModelAssignment getFlow(JSONObject flow){
        String leftPath = "";
        String rightPath = "";
        Integer leftParam = Constants.RETURN;
        Integer rightParam = Constants.THIS;
        try {
            rightParam = flow.getJSONObject("from").getInt("ParameterIndex");
        } catch (JSONException e) {
            // pass
        }
        String srcAp = flow.getJSONObject("from").optString(ACCESS_PATH);
        if (srcAp != null && !srcAp.isEmpty()) {
            rightPath = srcAp;
        }

        
        try {
            leftParam = flow.getJSONObject("to").getInt("ParameterIndex");
        } catch (JSONException e) {
            String leftType = flow.getJSONObject("to").getString("sourceSinkType");
            if (leftType.equals("Field")) {
                leftParam = Constants.THIS;
            }
        }
        String dstAp = flow.getJSONObject("to").optString(ACCESS_PATH);
        if (dstAp != null && !dstAp.isEmpty()) {
            leftPath = dstAp;
        }

        return new MethodModelAssignment(leftParam, rightParam, leftPath, rightPath);
    }
}