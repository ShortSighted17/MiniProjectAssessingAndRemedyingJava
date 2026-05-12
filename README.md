# coverage-project (Java port)

Java port of the reference implementation of **Asudeh, Jin, Jagadish et al.,
"Assessing and Remedying Coverage for a Given Dataset"** (ICDE 2019 / arXiv 1810.06742).
Replicates the four MUP-identification / coverage-enhancement algorithms from the paper, the
GREEDY and NAIVE coverage-enhancement algorithms, and the Figure 10 / 11 / 16 / 17 experiments.

The point of this port is **faithful replication**, not performance. None of the speedups
described in the paper's appendices (bit-vector coverage, inverted-index MUP dominance, tree-pruned
greedy) are implemented here. Everything follows the paper's pseudocode and prose as written, with
the two corrections to Algorithm 3 (DEEPDIVER) explained in `DeepDiver.java`.

## Layout

```
src/main/java/com/coverageproject/
  core/         Pattern · Patterns · Coverage · CoverageCache · MupIndex · ValidationOracle
  data/         DatasetConfig · DatasetBundle · DatasetLoader · C45Parser
  mups/         PatternBreaker (Alg. 1) · PatternCombiner (Alg. 2) · DeepDiver (Alg. 3)
  enhancement/  Greedy · Naive
  ml/           DecisionTree · Metrics      (used by Figure 10 in place of scikit-learn)
  experiments/  Figure10 · Figure11 · Figure16 · Figure17
  plotting/     PlotFigure10 · PlotFigure11 · PlotFigure16 · PlotFigure17 · CsvWriter
  Main.java
src/test/java/...                            (23 unit tests across 4 files)
pom.xml
```

This is a flatter layout than the Python project — one directory per concern, no `coverage_project/`
shell folder, no separate `performance/` package (since there are no performance backends to plug
in). The algorithmic content per file is the same.

## Build

Requires JDK 17+. Maven 3.6+.

```sh
mvn package
java -jar target/coverage-project-1.0.0.jar <dataset_dir> [figures]
```

Dependencies (all on Maven Central):

| dep | purpose |
| --- | --- |
| `com.fasterxml.jackson.core:jackson-databind` | reads `config.json` |
| `org.apache.commons:commons-csv` | reads UCI-style delimited files |
| `org.jfree:jfreechart` | renders Figure 10/11/16/17 PNGs |
| `org.junit.jupiter:junit-jupiter` (test) | unit tests |

## Datasets

Each dataset lives in its own directory under `datasets/`:

```
datasets/
  <name>/
    config.json
    data.csv               (or whatever the config points at)
    [<name>.names]         (optional C4.5 metadata)
```

Minimal `config.json`:

```json
{
  "name": "compas",
  "format": "uci_delimited",
  "data_file": "compas.csv",
  "delimiter": ",",
  "has_header": true,
  "columns": ["sex", "age", "race", "marital_status", "recid"],
  "feature_cols": ["sex", "age", "race", "marital_status"],
  "label_col": "recid",
  "domains": {
    "sex": ["0", "1"],
    "age": ["0", "1", "2", "3"],
    "race": ["0", "1", "2", "3"],
    "marital_status": ["0", "1", "2", "3", "4", "5", "6"]
  }
}
```

Optional per-figure overrides:

```json
"figure_10": {
  "tau": 30,
  "max_candidate_mups": 100,
  "num_subgroups_to_plot": 5,
  "subgroup_train_sizes": [0, 10, 20, 30, 40]
},
"figure_11": { "tau_values": [1, 2, 5, 10, 20, 30] },
"figure_16": { "tau_values": [1, 2, 5, 10], "levels": [2, 3], "naive_level": 2 },
"figure_17": { "tau": 5, "levels": [2, 3] }
```

If you have a C4.5 `.names` file alongside the data, you can ask the loader to harvest the
attribute domains from it:

```json
{
  "name": "nursery",
  "format": "uci_delimited",
  "data_file": "nursery.data",
  "metadata_file": "nursery.names",
  "metadata_format": "c45"
}
```

## Running experiments

```sh
java -jar target/coverage-project-1.0.0.jar nursery all
java -jar target/coverage-project-1.0.0.jar compas 11,16
java -jar target/coverage-project-1.0.0.jar bluenile 10
```

Outputs (CSV + PNG per figure) land in `./<dataset_name>_graphs/`. CSVs contain the raw numbers
the plots are drawn from, so you can re-plot them in matplotlib/pandas if you'd rather not use
the JFreeChart output.

## What the algorithms do

- **`PatternBreaker.patternBreaker`** (Algorithm 1) — top-down BFS. Walks the pattern graph from
  the root, level by level, computing coverages and adding any below-threshold node to the MUP
  set. Children are generated under Rule 1 to make sure each MUP candidate is visited once.

- **`PatternCombiner.patternCombiner`** (Algorithm 2) — bottom-up. Counts leaf coverages in one
  dataset pass, then sweeps up the graph computing parent coverages as sums of partition children.
  Rule 2 ensures each candidate parent is generated once. MUPs are the level-l uncovered patterns
  whose level-(l-1) parents are all covered.

- **`DeepDiver.deepdiver`** (Algorithm 3) — DFS with a climb-up step. Dives down through covered
  nodes via Rule 1 until it hits an uncovered one, then climbs up to the topmost uncovered
  ancestor (which is a MUP), records it, and prunes future descendants. Two paper bugs corrected
  here; see the class Javadoc and the section below.

- **`Greedy.greedyCoverageEnhancement`** — given a level-λ MUP set, greedily pick value
  combinations that hit the most still-deficient patterns, until every pattern reaches τ.

- **`Naive.naiveCoverageEnhancement`** — the baseline from § IV-A: for each uncovered pattern,
  add enough tuples to fix it on its own, ignoring overlaps.

## Two corrections to the paper's DEEPDIVER pseudocode

While running the cross-algorithm agreement tests I confirmed two typos in Algorithm 3 (also
present in the IEEE conference version's Algorithm 1). Both are fixed in this port and in the
original Python project; they are documented at the top of `DeepDiver.java`.

1. **Spurious `else if P dominates M` branch (lines 8-9).** Sets the uncovered flag whenever the
   current pattern dominates some known MUP. That's wrong — dominating a MUP only tells us P is
   not itself a MUP (because MUPs are maximal), not that P is uncovered. An ancestor of an
   uncovered node is usually covered. With this branch in place, DEEPDIVER climbs up from a
   "dominates-MUP" node and emits a spurious super-MUP that strictly dominates a real MUP,
   breaking maximality. The fix is to drop the branch and just check coverage. Two of the
   cross-validation tests caught this — `agreementAcrossSeveralTauValuesOnTernaryAttribute` and
   `allThreeAlgorithmsAgreeOnRichExample`.

2. **Empty climb-up stack (lines 15-24).** The inner loop creates `S' = empty stack`, then loops
   while `S'` is non-empty, then on line 23 adds the *original* `P` to `M`. Two problems: the loop
   never starts (`S'` was just emptied), and even if it did, the variable being added to `M` is
   the pre-climb node rather than the topmost uncovered ancestor. The prose right above the
   pseudocode describes what the algorithm should do: climb to a MUP via uncovered parents. We
   implement that.

## What's intentionally **not** here

The paper describes several speedups for the literal algorithms:

- **Appendix A** — bit-vector coverage queries (inverted indices over `(column, value)` pairs).
- **Appendix B** — bit-vector MUP dominance for DEEPDIVER.
- **§ IV-B** — tree-pruned greedy that walks an attribute-by-attribute DFS instead of enumerating
  every value combination.

None of these are implemented. They are not different algorithms; they are different data
structures used inside the same algorithms. Adding them would not change any MUP set or any
greedy output; it would only make things faster. Per the brief, this port is a literal
replication, so it does the obvious thing in each case (linear coverage scan, set membership for
dominance, full Cartesian product for greedy).

If you want to bolt them on later, the natural extension points are:

- `Coverage.coverage` — swap in a bit-vector backend keyed on the dataset.
- `MupIndex` — back the `Set<Pattern>` with the `B_v` / `B_X` bitmask construction from
  Appendix B.
- `Greedy` — replace `cartesianProduct` + matches-counting with the tree-of-attributes DFS.

## Tests

```sh
mvn test
```

23 tests across:

- `core/PatternTest` — `level`, `asString`, equality, `matchesRow`, `parents`,
  `allPatternsAtLevel`, dominance.
- `core/CoverageTest` — paper's Example 1, with `CoverageCache`.
- `mups/MupAlgorithmsTest` — Example 1 worked out for each of the three algorithms; cross-algorithm
  agreement on a richer 8-row binary dataset, on a ternary-attribute example across multiple τ
  values, on a fully-covered dataset (no MUPs), and on the empty dataset (root is the only MUP).
  These tests are what caught typo #1 above.
- `enhancement/EnhancementTest` — `Greedy` covers every level-2 pattern when starting from an
  empty dataset; returns nothing when already covered; respects a validation oracle. `Naive` adds
  one row per deficit.
