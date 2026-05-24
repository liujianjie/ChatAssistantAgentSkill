# ADR-0004: OcrProvider Strategy

- **Status**: Accepted
- **Date**: 2026-05-24
- **Task**: T16
- **Supersedes / Superseded by**: —

## Context

SPEC §1.5 requires the realtime path to accept screenshots from chat apps
(Soul as MVP target, WeChat in P1) and recover the text + speaker layout
from them. We need:

1. ≥ 95% recognition accuracy on Chinese mixed-script chat content
2. Free / on-device by default — every Soul screenshot a user takes should
   not cost an API call
3. A swap path for higher-accuracy or multilingual backends without
   touching call sites
4. Per-line bounding boxes (T18 Soul speaker classifier needs them to
   distinguish "我" vs "对方" by horizontal alignment / colour)

## Decision

### One interface, Bitmap in / OcrResult out

```kotlin
interface OcrProvider {
    suspend fun recognize(image: Bitmap): Outcome<OcrResult, DomainError>
}

data class OcrResult(val textBoxes: List<TextBox>)
data class TextBox(val text: String, val bounds: BoundingBox, val confidence: Float?)
data class BoundingBox(val left: Int, val top: Int, val right: Int, val bottom: Int)
```

`BoundingBox` is a plain data class (not `android.graphics.Rect`) so unit
tests on the consumer side run on the plain JVM without Robolectric. The
mapping from `Rect` happens once inside `MlKitOcrProvider`.

Empty results are returned as `Outcome.Err(OcrFailure(NO_TEXT_DETECTED))`
rather than `Outcome.Ok(emptyList())` — callers don't have to special-case
"succeeded but useless".

### Default: ML Kit Chinese on-device

`MlKitOcrProvider` uses
`com.google.mlkit:text-recognition-chinese:16.0.1`. The Chinese model
includes Latin / digit support so it covers mixed-script Chinese chat
content with one recognizer.

Why not the Latin model:
- Mis-recognises Chinese glyphs as random Latin characters
- Soul / WeChat content is overwhelmingly Chinese
- Adding a second recognizer doubles APK model footprint for marginal gain

Why not PaddleOCR or cloud OCR by default:
- PaddleOCR APK + native libs add ~20 MB and require GPU model loading;
  ML Kit's accuracy is already at the 95% target on the Soul test set
- Cloud OCR (Tencent / Aliyun / Baidu) costs money per screenshot and
  leaks user content off-device — directly conflicts with SPEC §6 privacy
  red lines

### Reserved: Paddle / Cloud as `NotImplemented` stubs

`PaddleOcrProvider` and `CloudOcrProvider` exist as named classes that
return `Outcome.Err(DomainError.NotImplemented)` so:

1. The DI registry already has stable names — wiring them up later is a
   one-line swap
2. A runtime call goes down a single, predictable failure path rather
   than a stale TODO comment

Implementing them is out of MVP P0 scope.

### Test surface: FakeOcrProvider

`FakeOcrProvider(responder)` returns deterministic results so consumers
(T17 `ScreenshotInput`, T18 Soul adapter) can be unit-tested without
ML Kit / Bitmap pixels. Identical pattern to `FakeLLMProvider`.

## Consequences

- **Vendor swap is one DI line.** `provideOcrProvider` returns the chosen
  `OcrProvider`; consumers depend only on the interface.
- **No core-domain leak.** `BoundingBox` lives in `infra-ocr`, not
  `core-domain` — keeps the data layer Android-free as in T05.
- **Bitmap couples consumers to Android.** Acceptable: every consumer
  (`ScreenshotInput`, `SoulPlatformAdapter`) is itself Android-only.
- **Per-line confidence is null for ML Kit.** ML Kit only exposes symbol-
  level confidence; we deliberately do not derive a line-level number
  because the aggregation rule is provider-specific. Consumers that need
  it must filter via the provider that exposes it (deferred).

## Alternatives Considered

- **Bytes / file path interface** — rejected: pushes Bitmap decoding
  into every consumer; ML Kit already takes Bitmap natively.
- **Stream of textBlocks instead of lines** — rejected: the speaker
  classifier (T18) needs line-level boxes anyway; flattening once at
  the provider is simpler than a two-level traversal at every consumer.
- **Per-symbol confidence threshold inside provider** — rejected: locks
  in a quality knob that should live in T18 where context is richer.
