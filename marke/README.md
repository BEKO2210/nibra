# Brand assets

Sources for the mark, the icon and the colours. Everything shipped is derived
from what is here.

## Colours and type

From `../marke.json`:

| | |
|---|---|
| Accent | `#2F6F63` (warm teal) |
| Accent, dark | `#14332E` |
| Titles | Fraunces |
| Body | Inter |

Both typefaces are under the SIL Open Font License 1.1 and ship with the app.
The OFL notice is part of the licence gate: if the typefaces are bundled and
the notice is missing, `./gradlew pruefeLizenzen` fails.

## Files

| File | What it is |
|---|---|
| `icon_1024.png` | the master. Everything else derives from it |
| `zeichen-vorschau.svg` / `.png` | the mark on its own, without the tile |
| `entwuerfe/` | the three drafts A, B, C, the brief and the finding that led to the decision |

`store/grafik/icon-1024.png` is a byte-identical copy for the store; the app
icon sits in `app/src/main/res/mipmap-*/`. A third copy, `nibra-icon.png`,
was removed — three files, one content, and no way to tell which one was
meant.

## When the icon changes

Change the master, then derive everything else. Not the other way round:
a store icon edited on its own drifts away from the app icon, and nobody
notices until both sit next to each other on a phone.

The icon is currently **frozen**. It changes at promotion, not before.

## What is not brand material

The orange waveform belongs to the template AIDictation. It is not ours and
must not come back — see `../device-shots/README.md`.
