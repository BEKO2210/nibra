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
| `../app/src/main/res/drawable/nb_zeichen_vordergrund.xml` | **the master.** The shipped mark, as a vector, on `nb_zeichen_hintergrund.xml` (`#2F6F63`). This is what the phone shows |
| `zeichen-aktuell-1024.png` | 1024×1024 raster of that master, rendered at the adaptive icon's visible area (72 of 108). Use this wherever a PNG is needed |
| `../docs/bilder/symbol-256.png` | the same image at 256×256 with rounded corners, for the README |
| `icon_1024.png` | **superseded draft.** Feather over a wavy line that runs into both edges — the horizontal stroke straight through the middle. Not the shipped mark. Kept for the record, do not derive from it |
| `zeichen-vorschau.svg` / `.png` | the mark on its own, without the tile |
| `entwuerfe/` | the three drafts A, B, C, the brief and the finding that led to the decision |

`store/grafik/icon-1024.png` is byte-identical to `icon_1024.png` and therefore
carries the same superseded draft. It must be replaced with the current mark at
the next store appearance. A third copy, `nibra-icon.png`, was removed —
three files, one content, and no way to tell which one was meant.

## When the icon changes

Change the vector master, then derive everything else. Not the other way round:
a store icon edited on its own drifts away from the app icon, and nobody
notices until both sit next to each other on a phone.

The PNGs here are renders of the vector, cropped to the visible 72/108 of the
adaptive icon, so they show exactly what sits on the home screen.

The icon is currently **frozen**. It changes at promotion, not before.

## What is not brand material

The orange waveform belongs to the template AIDictation. It is not ours and
must not come back — see `../device-shots/README.md`.
