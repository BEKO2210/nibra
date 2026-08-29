<img src="bilder/symbol-256.png" alt="" width="80" align="left" hspace="14">

# Guide

Set up and use, build, and measure. For using the app, part 1 is enough.

<br clear="left">

---

## 1. Using it

### Setup

On first launch Nibra walks through three steps. Skip them and each one is
still reachable later under **Settings**.

| Step | Why |
|---|---|
| Welcome | What the app does, and that it does it on the device |
| Microphone | Without this permission Nibra cannot listen |
| Accessibility service | Only needed to insert text into **other** apps |

The third step is **optional**. Without it Nibra still dictates; the text
simply lands in its own history instead of directly in the foreign input
field.

Before the service, Nibra shows a disclosure: what the service sees, what
for, and that none of it leaves the device. You have to confirm it. A button
then leads into the Android settings where the service is switched on —
Android requires that itself; no app can grant the service to itself.

> After every reinstall the service is off again. Android disables
> accessibility services when an app is replaced. That is intent, not a
> fault.

### Dictating

1. Tap the large surface.
2. Speak. The surface shows the level.
3. Tap again, or pause — with **stop on silence** enabled, Nibra stops by
   itself.

The text appears in two parts: confirmed and provisional. Provisional text is
underlined and set at full colour strength, not greyed out. It is not "less
valid", it is merely not final yet.

### Inserting into other apps

With the service on, Nibra shows a bubble as soon as an input field takes
focus. Tap, speak, done — the text lands at the caret.

**In password fields the bubble does not appear.** Not even if you wanted it
there.

### History, snippets, language

- **History** — every dictation, grouped by date, searchable. Each entry can
  be opened, edited and copied.
- **Text snippets** — your own replacements: spoken phrase in, written text
  out.
- **Dictation language** — what Nibra should hear. Separate from the
  **interface language**, what Nibra should display. The two are independent:
  German interface with English dictation works.

Recognition itself comes from Android. If a language pack is missing, Nibra
says so **before** recording, not after — otherwise you would have spoken for
nothing.

### What it does not do

No `INTERNET` permission. No account, no cloud, no ads, no analytics.
Dictations sit in a local database; the audio is discarded after
transcription.

That the shipping flavour is network-free is checked on every build (see
below) — it is not a promise but a condition the build fails on.

---

## 2. Building

**JDK 17 is required.** The runtime bundled with Android Studio is newer and
makes the Kotlin compiler abort.

```bash
export JAVA_HOME=/home/belkis/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
./gradlew assembleOfflineDebug
```

### Two flavours

| | `offline` | `forschung` |
|---|---|---|
| Purpose | the shipping app | the measurement bench |
| Package | `de.ithandwerkstuttgart.nibra` | `…nibra.forschung` |
| Network | no network permission | may fetch language packs |
| Release | yes | **never** — the release variant is disabled |

Both install side by side. The bench is a separate app with its own storage;
it does not know Nibra's settings and must not guess them.

### Store bundle

```bash
./gradlew bundleOfflineRelease
```

It lands in `app/build/outputs/bundle/offlineRelease/`. Do **not** upload
from `abgabe/` — that folder only collects old builds, and an old build looks
exactly like a new one.

---

## 3. Checking

```bash
./gradlew testOfflineDebugUnitTest      # shipping flavour
./gradlew testForschungDebugUnitTest    # measurement bench
./gradlew pruefeNetzfreiheit            # gate: no network permission
./gradlew pruefeLizenzen                # gate: licence situation unchanged
```

All four run in CI on every push. If one fails, the run is red; there is no
"almost green".

### The two gates

**`pruefeNetzfreiheit`** reads the merged manifest of the shipping flavour.
If it finds `INTERNET` or `ACCESS_NETWORK_STATE`, the build fails. If it
finds **no manifest at all**, it fails as well — a check that finds nothing
because it looked in the wrong place would otherwise report success.

**`pruefeLizenzen`** reads the runtime classpath of the shipping flavour —
not the test classpath, because what is only needed for checking is not
shipped — and takes each licence from the dependency's own POM, not from a
maintained list. A list goes stale quietly, and precisely when it would
matter.

It raises an alarm on copyleft, on unexpected terms, on a missing licence
entry, on a shipped dependency absent from `lizenzen.txt`, on a missing OFL
notice for the bundled typefaces, and if the template's original MIT notice
disappears.

> This is a **technical early warning, not legal advice**, and it makes
> nothing legally safe. It ensures a changed licence situation surfaces while
> it can still be changed — and not first in the store.

### Construction rules

Besides the ordinary tests there are rules that check the **source itself**:
that the clock starts before the first read, that no foreign store presence
sits in the tree, that an aborted measurement run does not look green.

Every such rule comes with a **counter-test** that makes the old, wrong
version fail. Without it nobody would know whether the rule checks anything
at all.

---

## 4. Measuring

The bench is a separate app and is started through instrumentation, not by a
button. Reason: the bench activity is not exported, and it should stay that
way.

```bash
adb -s <device> shell am instrument -w -r \
  -e versuch transport \
  -e class de.ithandwerkstuttgart.nibra.forschung.Messplatzstart \
  de.ithandwerkstuttgart.nibra.forschung.test/androidx.test.runner.AndroidJUnitRunner
```

Experiments: `transport`, `sitzungen`, `vorlauf`, `verzug`, `lebenslauf`,
`livestrecke`, `tonquelle`, `still`, `vergleich`, `diagnose`, `sprachpaket`.
Extras such as `-e anzahl 300`, `-e sekunden 900`, `-e spur en`,
`-e sprache en-US` are passed through.

Reports land in
`/sdcard/Android/data/de.ithandwerkstuttgart.nibra.forschung/files/`, with
the running state in `fortschritt.txt` next to them.

### What audio-driven experiments need

Some experiments feed a stored recording instead of listening. It has to be
on the device first:

```bash
adb push messungen/ton/vorlauf-en.pcm \
  /sdcard/Android/data/de.ithandwerkstuttgart.nibra.forschung/files/
```

If it is missing, the experiment aborts and the report carries
`**ABGEBROCHEN**` — the instrumentation turns red. That was once different: a
run over three hundred sessions reported "OK (1 test)" after 2.4 seconds
because the abort message did not carry the marker. Nothing was measured;
success was reported.

### Rules that hold while measuring

- **Nothing spoken goes into the logs.** Reports contain recognised text; the
  system log is collected by bug reports and outlives the app. Only the fact
  that a report was written, and its length, goes to `logcat`.
- **No audio is archived.** The shipping app processes the recording in
  memory and discards it.
- **No volume is changed.** An experiment reads it and aborts if it is too
  quiet. Audible playback requires `-e tonErlaubt true`.
- **Emulator results say nothing about real microphones.** They show that the
  path holds, not how well a device hears.

What was measured, which measurement errors were found and fixed along the
way, and what is still open is recorded under
[`docs/messungen/`](messungen/MESSSYSTEM.md) and
[`docs/berichte/`](berichte/BERICHT-PIPELINE.md) — in German, because that is
the language the evidence was written in.
