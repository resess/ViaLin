package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class MethodInfo {
    private String className;
    private String methodName;
    private String desc;
    private boolean isStatic;
    private List<String> params = new ArrayList<>();
    private String returnType;
    private String paramString;
    private Integer baseNumRegs;
    private Integer numParamsWithTaint;

    public String toString(){
        return "Method: " + this.className + "->" + this.methodName + this.desc + ", params: " + params + ", return: " + returnType + ", baseNumRegs:" + baseNumRegs;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MethodInfo) {
            return signature().equals(((MethodInfo) obj).signature());
        } else {
            return false;
        }
    }

    public boolean isStatic() {
        return isStatic;
    }

    public String signature() {
        return this.className + "->" + this.methodName + this.desc;
    }

    public String getNameAndDesc() {
        return this.methodName + this.desc;
    }

    public void setNumParamsWithTaint(Integer numParamsWithTaint) {
        this.numParamsWithTaint = numParamsWithTaint;
    }

    public Integer getNumParamsWithTaint () {
        return numParamsWithTaint;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getClassName() {
        return className;
    }

    public List<String> getParams() {
        return params;
    }

    public String getDesc() {
        return desc;
    }

    public MethodInfo (String signature, boolean isStatic) {
        String[] split = signature.split("->");
        this.className = split[0];
        split = split[1].split("\\(");
        this.methodName = split[0];
        this.desc = "(" + split[split.length-1];
        this.isStatic = isStatic;
        parseParams();
    }

    public void setBaseNumRegs(Integer baseNumRegs) {
        this.baseNumRegs = baseNumRegs;
    }

    public Integer getBaseNumRegs() {
        return baseNumRegs;
    }

    public Integer getNumBaseParams() {
        return params.size();
    }

    public Integer getNumBaseLocalRegs() {
        return baseNumRegs - getNumBaseParams();
    }



    private void parseParams() {
        paramString = desc.substring(desc.indexOf("(") + 1);
        paramString = paramString.substring(0, paramString.indexOf(")"));
        List<String> paramList = parseMethodDesc(paramString);
        returnType = desc.substring(desc.indexOf(")")+1);

        if (!isStatic) { // in this case add one object to account for the extra objectref
            params.add(className);
        }

        if (paramList != null) {
            for (String p : paramList) {
                params.add(p);
                if (p.equals("J")) {
                    params.add("*");
                }
                if (p.equals("D")) {
                    params.add("*");
                }
            }
        }
        setNumParamsWithTaint(params.size());
    }

    private List<String> parseMethodDesc(String paramString) {
        if (paramString.equals("")) {
            return new ArrayList<String>();
        }
        final String separator = " ";
        Boolean inObject = false;
        Boolean inArray = false;
        String ts = "";// Transformed string
        for (Character s: paramString.toCharArray())  {
            ts += s;

            inArray = false;
            if (s.equals('[')) {
                inArray = true;
            } else if (s.equals(';')) {
                inObject = false;
            } else if (!inObject && s.equals('L')) {
                inObject = true;
            }

            if (!inObject && !inArray) {
                ts += separator;
            }
        }

        List<String> paramList = new ArrayList<String>(Arrays.asList(ts.split(separator)));
        return paramList;
    }

    public String addTaintToDesc() {
        StringBuilder newMethodDesc = new StringBuilder("(");
        newMethodDesc.append(paramString);
        for (int i = 0; i < params.size(); i++) {
            newMethodDesc.append("LPathTaint;");
        }
        if (returnType.equals("V")) {
            setNumParamsWithTaint(2 * params.size());
        } else {
            newMethodDesc.append("LPathTaint;");
            setNumParamsWithTaint((2 * params.size())+1);
        }

        newMethodDesc.append(')');
        newMethodDesc.append(returnType);
        return newMethodDesc.toString();
    }

    public String getReturnType() {
        return returnType;
    }
}