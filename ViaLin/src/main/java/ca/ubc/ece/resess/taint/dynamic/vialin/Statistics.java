package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.util.HashMap;
import java.util.Map;

public class Statistics {

    enum StatisticType {
        sources, sinks, taintedMethods, notTaintedMethods
    }

    Map<StatisticType, Integer> counts;
    public Statistics() {
        counts = new HashMap<>();
        counts.put(StatisticType.sources, 0);
        counts.put(StatisticType.sinks, 0);
        counts.put(StatisticType.taintedMethods, 0);
        counts.put(StatisticType.notTaintedMethods, 0);
    }

    public void addNotTainted() {
        counts.put(StatisticType.notTaintedMethods, counts.get(StatisticType.notTaintedMethods) + 1);
    }

    public void addTainted() {
        counts.put(StatisticType.taintedMethods, counts.get(StatisticType.taintedMethods) + 1);
    }

    public void addSource() {
        counts.put(StatisticType.sources, counts.get(StatisticType.sources) + 1);
    }

    public void addSink() {
        counts.put(StatisticType.sinks, counts.get(StatisticType.sinks) + 1);
    }

    public void print() {
        System.out.format("# sources %s%n", counts.get(StatisticType.sources));
        System.out.format("# sinks %s%n", counts.get(StatisticType.sinks));
        System.out.format("# tainted methods %s%n", counts.get(StatisticType.taintedMethods));
        System.out.format("# not tainted methods %s%n", counts.get(StatisticType.notTaintedMethods));
    }
}
