Source: core-domain test fixture `golden/02-1on1-emoji-mixed.yml`.

Only a 5-line slice is hand-copied here as a paste-input sample to verify
that `PasteInput` preserves emoji + Chinese/English code-switched code points
without truncation or normalisation. Full-fixture reuse across modules is
deferred until the `testFixtures` source-set wiring lands in T11 (see
`GoldenLoader.kt` line 22-24); copying only the minimum text needed avoids
fixture-drift between modules in the meantime.
