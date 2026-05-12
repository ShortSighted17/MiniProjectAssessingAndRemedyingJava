package com.coverageproject.ml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Classification metrics used by Figure 10: accuracy and weighted F1.
 *
 * <p>The weighted F1 matches scikit-learn's {@code f1_score(..., average='weighted',
 * zero_division=0)}: for each label, compute precision, recall, and F1 treating that label as the
 * positive class; aggregate by averaging F1 across labels weighted by the label's support in
 * {@code yTrue}. When precision and recall are both zero we use F1 = 0 (the {@code zero_division}
 * = 0 convention).
 */
public final class Metrics {

    private Metrics() {
        // utility class
    }

    public static double accuracy(List<String> yTrue, List<String> yPred) {
        if (yTrue.size() != yPred.size()) {
            throw new IllegalArgumentException("y_true and y_pred must have the same length");
        }
        if (yTrue.isEmpty()) {
            return 0.0;
        }
        int correct = 0;
        for (int i = 0; i < yTrue.size(); i++) {
            if (yTrue.get(i).equals(yPred.get(i))) {
                correct++;
            }
        }
        return (double) correct / yTrue.size();
    }

    public static double weightedF1(List<String> yTrue, List<String> yPred) {
        if (yTrue.size() != yPred.size()) {
            throw new IllegalArgumentException("y_true and y_pred must have the same length");
        }
        if (yTrue.isEmpty()) {
            return 0.0;
        }

        // Union of labels observed in either yTrue or yPred. We restrict the average over labels
        // present in yTrue (the support is zero for any other label, so they don't contribute).
        Set<String> labels = new HashSet<>(yTrue);
        labels.addAll(yPred);

        Map<String, Integer> support = new HashMap<>();
        for (String label : yTrue) {
            support.merge(label, 1, Integer::sum);
        }

        double weightedSum = 0.0;
        int totalSupport = yTrue.size();
        for (String label : labels) {
            int tp = 0;
            int fp = 0;
            int fn = 0;
            for (int i = 0; i < yTrue.size(); i++) {
                boolean trueIsLabel = yTrue.get(i).equals(label);
                boolean predIsLabel = yPred.get(i).equals(label);
                if (predIsLabel && trueIsLabel) {
                    tp++;
                } else if (predIsLabel) {
                    fp++;
                } else if (trueIsLabel) {
                    fn++;
                }
            }
            double precision = (tp + fp == 0) ? 0.0 : (double) tp / (tp + fp);
            double recall = (tp + fn == 0) ? 0.0 : (double) tp / (tp + fn);
            double f1 = (precision + recall == 0.0) ? 0.0
                    : 2 * precision * recall / (precision + recall);
            int labelSupport = support.getOrDefault(label, 0);
            weightedSum += f1 * labelSupport;
        }
        return totalSupport == 0 ? 0.0 : weightedSum / totalSupport;
    }
}
