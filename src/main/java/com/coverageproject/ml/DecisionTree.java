package com.coverageproject.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Minimal categorical decision-tree classifier used by Figure 10.
 *
 * <p>The Python project trains a scikit-learn {@code DecisionTreeClassifier} on
 * one-hot-encoded features. We don't bother one-hot-encoding here: every feature in our datasets
 * is already a categorical string, and a tree that splits on raw categorical equality is the
 * natural fit. The tree:
 *
 * <ul>
 *   <li>Splits on attribute equality: at each interior node we pick the attribute that maximises
 *       Gini gain, and create one child per observed value plus one "other" child for unseen
 *       values at predict time.
 *   <li>Stops splitting when the node is pure, or when no remaining attribute provides any
 *       positive Gini gain, or when there are no attributes left to split on.
 *   <li>Predicts by walking down to a leaf and returning the majority label there. Unseen values
 *       fall through the "other" child, which is the leaf of the rows whose attribute value
 *       didn't match any of the training-time branches.
 * </ul>
 *
 * <p>This won't reproduce sklearn's results bit-for-bit (sklearn uses CART on one-hot-encoded
 * features, breaks ties by feature index, etc.), but the qualitative behavior the experiment is
 * supposed to demonstrate — accuracy drops on subgroups absent from the training set, and
 * recovers as the subgroup is added back — is faithfully reproduced.
 */
public final class DecisionTree {

    private final Node root;
    private final List<String> featureCols;

    private DecisionTree(Node root, List<String> featureCols) {
        this.root = root;
        this.featureCols = featureCols;
    }

    public static DecisionTree fit(
            List<List<String>> features, List<String> labels, List<String> featureCols, Random rng) {
        if (features.size() != labels.size()) {
            throw new IllegalArgumentException("features and labels must have the same length");
        }
        // Track which feature indices are still available; we won't split on the same attribute
        // twice along a root-to-leaf path (it's pointless for categorical equality splits).
        boolean[] usable = new boolean[featureCols.size()];
        for (int i = 0; i < usable.length; i++) {
            usable[i] = true;
        }
        Node root = build(features, labels, usable, rng);
        return new DecisionTree(root, List.copyOf(featureCols));
    }

    public String predict(List<String> row) {
        Node node = root;
        while (node.splitFeature >= 0) {
            String value = row.get(node.splitFeature);
            Node child = node.children.get(value);
            if (child == null) {
                child = node.otherChild;
            }
            node = child;
        }
        return node.majorityLabel;
    }

    public List<String> predictAll(List<List<String>> rows) {
        List<String> out = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            out.add(predict(row));
        }
        return out;
    }

    public List<String> featureCols() {
        return featureCols;
    }

    private static Node build(
            List<List<String>> features, List<String> labels, boolean[] usable, Random rng) {
        Map<String, Integer> labelCounts = countLabels(labels);
        String majority = majorityLabel(labelCounts, rng);
        Node node = new Node();
        node.majorityLabel = majority;

        // Pure node, or no attributes left: leaf.
        if (labelCounts.size() <= 1 || !anyUsable(usable)) {
            return node;
        }

        double parentGini = gini(labelCounts, labels.size());
        int bestFeature = -1;
        double bestGain = 0.0;
        Map<String, List<Integer>> bestPartition = null;

        for (int featureIdx = 0; featureIdx < usable.length; featureIdx++) {
            if (!usable[featureIdx]) {
                continue;
            }
            Map<String, List<Integer>> partition = partitionByFeature(features, featureIdx);
            if (partition.size() <= 1) {
                // No discriminative power at this node from this feature.
                continue;
            }
            double weightedChildGini = 0.0;
            for (List<Integer> indices : partition.values()) {
                Map<String, Integer> childCounts = new HashMap<>();
                for (int idx : indices) {
                    childCounts.merge(labels.get(idx), 1, Integer::sum);
                }
                weightedChildGini += ((double) indices.size() / features.size())
                        * gini(childCounts, indices.size());
            }
            double gain = parentGini - weightedChildGini;
            if (gain > bestGain) {
                bestGain = gain;
                bestFeature = featureIdx;
                bestPartition = partition;
            }
        }

        if (bestFeature < 0 || bestPartition == null) {
            return node;
        }

        node.splitFeature = bestFeature;
        node.children = new LinkedHashMap<>();
        boolean[] childUsable = usable.clone();
        childUsable[bestFeature] = false;
        for (Map.Entry<String, List<Integer>> entry : bestPartition.entrySet()) {
            List<List<String>> childFeatures = new ArrayList<>(entry.getValue().size());
            List<String> childLabels = new ArrayList<>(entry.getValue().size());
            for (int idx : entry.getValue()) {
                childFeatures.add(features.get(idx));
                childLabels.add(labels.get(idx));
            }
            Node child = build(childFeatures, childLabels, childUsable, rng);
            node.children.put(entry.getKey(), child);
        }
        // "Other" fallback for unseen attribute values at predict time: returns the parent's
        // majority label. We allocate a dedicated leaf rather than reusing the parent so that
        // walk-down logic stays uniform.
        Node otherLeaf = new Node();
        otherLeaf.majorityLabel = majority;
        node.otherChild = otherLeaf;
        return node;
    }

    private static Map<String, List<Integer>> partitionByFeature(
            List<List<String>> features, int featureIdx) {
        // TreeMap so partition order is deterministic; useful for reproducibility across runs.
        Map<String, List<Integer>> result = new TreeMap<>();
        for (int i = 0; i < features.size(); i++) {
            result.computeIfAbsent(features.get(i).get(featureIdx), k -> new ArrayList<>()).add(i);
        }
        return result;
    }

    private static Map<String, Integer> countLabels(List<String> labels) {
        Map<String, Integer> counts = new HashMap<>();
        for (String label : labels) {
            counts.merge(label, 1, Integer::sum);
        }
        return counts;
    }

    private static double gini(Map<String, Integer> counts, int total) {
        if (total == 0) {
            return 0.0;
        }
        double sumSq = 0.0;
        for (int count : counts.values()) {
            double p = (double) count / total;
            sumSq += p * p;
        }
        return 1.0 - sumSq;
    }

    private static String majorityLabel(Map<String, Integer> counts, Random rng) {
        String best = null;
        int bestCount = -1;
        // Deterministic tie-break: scan in sorted order so two equally-good labels always pick the
        // same one regardless of HashMap iteration order.
        TreeMap<String, Integer> sorted = new TreeMap<>(counts);
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private static boolean anyUsable(boolean[] usable) {
        for (boolean u : usable) {
            if (u) {
                return true;
            }
        }
        return false;
    }

    private static final class Node {
        int splitFeature = -1;
        Map<String, Node> children;
        Node otherChild;
        String majorityLabel;
    }
}
