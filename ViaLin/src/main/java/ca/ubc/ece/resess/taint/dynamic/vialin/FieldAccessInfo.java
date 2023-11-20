package ca.ubc.ece.resess.taint.dynamic.vialin;

public class FieldAccessInfo {
    String fieldRef;
    String fieldType;
    String fieldClass;
    String fieldName;
    String taintField;
    String targetReg;
    String taintTargReg;
    String refType;
    String whereIsField;
    String baseRegRef;

    public FieldAccessInfo(String fieldRef, String fieldType, String fieldClass, String fieldName, String taintField,
        String targetReg, String taintTargReg, String refType, String whereIsField, String baseRegRef) {
        this.fieldRef = fieldRef;
        this.fieldType = fieldType;
        this.fieldClass = fieldClass;
        this.fieldName = fieldName;
        this.taintField = taintField;
        this.targetReg = targetReg;
        this.taintTargReg = taintTargReg;
        this.refType = refType;
        this.whereIsField = whereIsField;
        this.baseRegRef = baseRegRef;
    }
}
