# Golden Fixtures

Hand-authored synthetic chat transcripts that exercise the full pipeline —
speaker alignment (T12), data cleaning (T11), style profiling (T14), and the
6-dimension `StyleFingerprint` schema (ADR-0001).

## Privacy red line — read this before adding fixtures

**Never paste real chat history into this directory.** Not your own, not a
friend's, not "with names redacted". Once a transcript lands in `git`, it is
effectively unrecoverable, and even synthetic-looking text scraped from a real
exchange leaks distribution information about real people.

Concretely, every new fixture must satisfy all of the following:

- All names are invented (`小明` / `Lily` / `阿杰` style — not initials or
  partial real names).
- No 11-digit phone numbers, no 18-digit ID numbers, no 16+-digit account
  numbers, no email addresses, no URLs pointing to real social profiles.
- No addresses that resolve in a map service.
- No timestamps that would let someone correlate against a real conversation
  log they may have access to.

`GoldenLoaderTest` runs lightweight regex checks for the easy classes of leak
(phones, ID-card runs, emails). They are a backstop, not a replacement for
authoring discipline.

## Fixture schema

Each `*.yml` file is one fixture. Required fields:

| Field                  | Type                  | Notes                                                                 |
| ---------------------- | --------------------- | --------------------------------------------------------------------- |
| `id`                   | string                | Matches the file name (without extension). Stable across renames.     |
| `description`          | string                | What scenario this fixture covers, in 1–3 sentences.                  |
| `my_aliases`           | list&lt;string&gt;    | All visible labels Me ever appears under in this fixture.             |
| `partner_id`           | string                | Becomes the `PartnerId` for the synthesized `ConversationContext`.    |
| `expected_style_cues`  | list&lt;string&gt;    | Natural-language hints describing Me's style — assertion targets.     |
| `messages`             | list&lt;message&gt;   | At least 30 entries, chronologically ordered.                         |

Each `message` entry:

| Field          | Required when           | Notes                                                              |
| -------------- | ----------------------- | ------------------------------------------------------------------ |
| `speaker`      | always                  | `me` or `theirs` — ground truth for speaker-alignment tests.       |
| `id`           | always                  | Unique within the fixture. By convention `m-NN` / `t-NN`.          |
| `sent_at`      | always                  | ISO-8601 instant in UTC.                                           |
| `content`      | always                  | Single-line or block-scalar.                                       |
| `display_name` | required for `theirs`   | Visible label of the sender. Optional for `me`.                    |

Optional fields (per fixture, top-level):

- `metadata`: free-form map for hints that downstream tests may consume — e.g.
  `cross_device_aliases` lists, alias-collision warnings.

## Fixture inventory

| File                          | Coverage                                                  | Messages |
| ----------------------------- | --------------------------------------------------------- | -------- |
| `01-1on1-baseline.yml`        | Single thread, no surprises — establishes a control case. | 32       |
| `02-1on1-emoji-mixed.yml`     | Heavy emoji + Chinese-English code-switching.             | 32       |
| `03-1on1-long-short-mix.yml`  | Asymmetric lengths (Me long, partner one-word).           | 30       |
| `04-cross-device.yml`         | Same partner under two display names across devices.      | 40       |
| `05-nickname-change.yml`      | Me's own nickname swaps mid-thread (alias resolution).    | 32       |
| `06-group-3people.yml`        | Group with three distinct Theirs identities.              | 35       |
| `07-group-with-aliases.yml`   | Group + alias collision (`Lily` vs `Liam`).               | 37       |

## Encoding

`.gitattributes` pins these files to `text eol=lf`. Do not commit a CRLF copy
from a Windows editor — Kotest assertions compare exact byte content and a
silent line-ending flip will look like a "fixture changed" failure.

## Loading from tests

Use `com.stylemirror.domain.testing.GoldenLoader`:

```kotlin
val baseline = GoldenLoader.load("01-1on1-baseline")
val all = GoldenLoader.loadAll()
```

The loader synthesizes a `ConversationContext` whose `messages` are typed
`Message.Mine` / `Message.Theirs`, so style-fingerprint aggregation can rely on
the type system to enforce the privacy red line (ADR-0001 / `core-domain`
`FingerprintAggregator`).
