#!/usr/bin/env python3
"""
Export the scikit-learn RandomForest (model.p) used by the Python prototype
into a compact binary file that the Android app evaluates natively in Kotlin
(app/src/main/java/.../RandomForestModel.kt). No TFLite conversion involved,
so the app predicts exactly what sklearn predicts on your PC.

Usage:
    python tools/export_rf_model.py "path/to/model.p" \
        [--out app/src/main/assets/rf_model.bin] \
        [--golden app/src/test/resources/golden.csv --data path/to/data.pickle]

Binary layout (big-endian, read with DataInputStream):
    int   magic 0x52464D31 ("RFM1")
    int   nFeatures
    int   nClasses
    int[] classLabels            (int(classes_[i]); 0 -> 'A' ... 25 -> 'Z')
    int   nTrees
    per tree:
        int      nNodes
        short[]  feature          (-1 for a leaf)
        double[] threshold        (split: go left if x[feature] <= threshold)
        int[]    left             (leaf: index into classLabels of the winning class)
        int[]    right            (unused for leaves)
"""
import argparse, pickle, struct, sys, warnings
import numpy as np

warnings.simplefilter("ignore")
MAGIC = 0x52464D31


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model_p")
    ap.add_argument("--out", default="app/src/main/assets/rf_model.bin")
    ap.add_argument("--golden", help="also write a CSV of test vectors + sklearn predictions")
    ap.add_argument("--data", help="data.pickle (needed for --golden)")
    ap.add_argument("--golden-rows", type=int, default=300)
    a = ap.parse_args()

    model = pickle.load(open(a.model_p, "rb"))["model"]
    classes = np.array([int(c) for c in model.classes_])
    out = bytearray()
    out += struct.pack(">iii", MAGIC, model.n_features_in_, len(classes))
    out += struct.pack(">%di" % len(classes), *classes)
    out += struct.pack(">i", len(model.estimators_))
    for est in model.estimators_:
        t = est.tree_
        n = t.node_count
        leaf = t.children_left == -1
        feat = np.where(leaf, -1, t.feature).astype(">i2")
        thr = np.where(leaf, 0.0, t.threshold).astype(">f8")
        votes = t.value[:, 0, :]
        left = np.where(leaf, votes.argmax(axis=1), t.children_left).astype(">i4")
        right = np.where(leaf, 0, t.children_right).astype(">i4")
        if leaf.any() and ((votes[leaf] > 0).sum(axis=1) > 1).any():
            # Non-pure leaves: a hard vote would differ slightly from predict_proba.
            print("WARNING: non-pure leaves found; exported as hard vote", file=sys.stderr)
        out += struct.pack(">i", n) + feat.tobytes() + thr.tobytes() + left.tobytes() + right.tobytes()
    open(a.out, "wb").write(out)
    print(f"wrote {a.out}: {len(out)/1024:.1f} KiB, {len(model.estimators_)} trees")

    if a.golden:
        if not a.data:
            sys.exit("--golden needs --data")
        d = pickle.load(open(a.data, "rb"))
        X = np.asarray(d["data"], dtype=np.float32)
        rng = np.random.default_rng(0)
        idx = rng.choice(len(X), size=min(a.golden_rows, len(X)), replace=False)
        Xs = X[idx]
        # also add jittered rows so the test covers off-training points
        Xj = Xs + rng.normal(0, 0.01, Xs.shape).astype(np.float32)
        Xall = np.vstack([Xs, Xj])
        pred = model.predict(Xall)
        with open(a.golden, "w") as f:
            for row, p in zip(Xall, pred):
                f.write(",".join(repr(float(v)) for v in row) + "," + str(int(p)) + "\n")
        print(f"wrote {a.golden}: {len(Xall)} rows")


if __name__ == "__main__":
    main()
