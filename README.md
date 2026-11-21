# Sign Language Translator (Android)

Two features:

* **Start** – live camera → hand tracking (MediaPipe) → letter classifier → text
* **Search** – type text → sign-language letter images

## How recognition works

```
camera frame ─► rotate upright ─► MediaPipe HandLandmarker (21 points)
             ─► HandFeatures (42 numbers: x-min(x), y-min(y))
             ─► RandomForestModel (your scikit-learn forest, ported to Kotlin)
             ─► LetterStabilizer ─► typed text
```

The classifier is **the same RandomForest you trained in Python** (`model v1_1/model.p`),
exported to `app/src/main/assets/rf_model.bin` and evaluated natively – no TFLite
conversion, so predictions are identical to the PC (verified on 600 test vectors, see
`RandomForestModelTest`).

### Re-exporting after you retrain

```bash
pip install scikit-learn numpy
python tools/export_rf_model.py "path/to/model.p" \
    --golden app/src/test/resources/golden.csv --data "path/to/data.pickle"
```

Commit the new `app/src/main/assets/rf_model.bin` (and `golden.csv`).

### Tuning (`LetterStabilizer.kt`)

| Setting | Default | Meaning |
|---|---|---|
| `framesToCommit` | 6 | frames (~0.5 s) a letter must stay stable before it is typed |
| `framesToRelease` | 6 | frames without a hand before the same letter can be typed again |
| `minConfidence` | 0.5 | share of the 100 trees that must agree |

## Building

### On GitHub (APK)

Push to `main` (or run **Actions → Build APK → Run workflow**). When it finishes, open the run
and download the **SignLanguageTranslator-apk** artifact (a zip containing the installable APK).
Pushing a tag such as `v1.1` additionally attaches the APK to a GitHub Release.

### Locally

Open in Android Studio, or `./gradlew assembleDebug`.
The APK is `app/build/outputs/apk/debug/app-debug.apk`.

`hand_landmarker.task` (Google's official MediaPipe model) is downloaded automatically by the
build into `app/src/main/assets/` the first time (internet needed once). It is git-ignored.
