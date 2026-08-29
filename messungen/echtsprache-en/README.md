# English reference recordings (FLEURS en_us)

**The audio is not in the repository.** Only `verzeichnis.json` is tracked.

Same rule as on the German side: the recordings are third-party material
under CC-BY-4.0 and could be redistributed, but there is no reason to spread
them through this repository — and 180 MB of foreign data in the version
history helps nobody.

## The evidence does not depend on the audio

A run is fully reproducible without the files:

| From | What |
|---|---|
| `verzeichnis.json` | for all 200 clips: source id, reference text, duration, rate |
| `../en200/lauf.json` | dataset revision, seed, commit, and per clip the SHA-256 **before and after** conversion |

To repeat the run, fetch the clips by source id from `google/fleurs`, convert
them by the same rules, and compare the checksums. If they differ, something
else was measured — and you notice, instead of missing it.

That is exactly what the checksums were recorded for. Without them the audio
would have had to stay in the repository.

## On this machine

The files are still under `roh/`, `pcm/` and `pilot/`. They are merely
untracked. Deleting them loses nothing that cannot be fetched again.
