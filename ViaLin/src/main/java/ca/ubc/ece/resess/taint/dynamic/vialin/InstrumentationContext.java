package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstrumentationContext {
    public MethodInfo currentMethod;
    public int maxRegs = 0;
    public Integer taintTempReg;
    public String signatureRegister;
    public String deltaReg;
    public String paramArrayReg = null;
    public String threadReg = null;
    public Integer timerRegister = null;
    public Integer methodDelta = 1;
    public List<String> taintedClassLines = new ArrayList<>();
    public List<String> extraTaintMethods = new ArrayList<>();
    public Map<Integer, Integer> newParams = new HashMap<>();
    public Map<String, String> taintRegMap = new HashMap<>();
    public Map<String, String> regType = new HashMap<>();
    public Set<String> taintedMethods = new HashSet<>();
    public Set<String> erasedTaintRegs = new HashSet<>();
    public Map<String, FieldAccessInfo> fieldArraysInMethod = new HashMap<>();

    public int maxOfCurrentMaxRegsAndNewMaxRegs(int newMaxRegs) {
        return (maxRegs > newMaxRegs)? maxRegs : newMaxRegs;
    }

    // public InstrumentationContext addToTaintTempReg() {

    // }
}
