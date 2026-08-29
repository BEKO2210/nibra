# Release — Nibra 2.1 (versionCode 12)

Everything that goes into the Play Store, in one place. Rebuilt by

```
./gradlew packeAbgabe
```

which empties `paket/`, `texte/` and `unterlagen/` first. What is here is the
current state — otherwise there would be two truths again, and that already
went wrong once: the walkthrough said "upload the bundle from `abgabe/`", and
version 1.0 was still sitting there while 2.1 was current. An old bundle
looks exactly like a new one.

`grafik/` and `bilder/` are produced separately and are **not** wiped by the
task; they change only when the artwork changes.

## What is here

| Folder | Content |
|---|---|
| `paket/` | the signed bundle for Play and the APK for direct installation |
| `grafik/` | store icon 512×512, feature graphic 1024×500 |
| `bilder/` | phone screenshots with English headlines |
| `texte/` | title, short and full description in de, en, fr, es, it, tr, pl |
| `unterlagen/` | privacy policy, data-safety answers |

The default store language is **English**; the other six are translations of
the same listing.

## Before uploading

The packaging task refuses to finish if any listing text exceeds what Play
accepts (title 30, short 80, full 4000 characters). It also depends on both
gates, so the bundle in `paket/` has passed them:

- `pruefeNetzfreiheit` — the shipping flavour carries no network permission
- `pruefeLizenzen` — no dependency arrived with unexpected licence terms

Verified on the artefact itself, not on the source:

```
package de.ithandwerkstuttgart.nibra
versionCode 12, versionName 2.1, targetSdk 36
permissions: RECORD_AUDIO
no INTERNET, no ACCESS_NETWORK_STATE
no research-flavour code in the dex
signature v2, SHA-256 16:9B:99:08:…:BE:11
```

The signing certificate reads `CN=Loqui` — the earlier name of the app. It
stays that way: changing it would mean a new upload key, and the upload key
must not change.

## Upload

Step by step in `../store/UEBERGABE.md`. The short form:

1. Play Console → Testing → Internal testing → Create new release
2. Drag in `paket/nibra-2.1-12.aab`
3. **Check that Play shows 2.1 / versionCode 12.** If it shows anything
   else, it is the wrong bundle — stop, do not release
4. Store listing from `texte/`, artwork from `grafik/` and `bilder/`
5. Data safety form from `unterlagen/data-safety.json`; privacy policy URL
   pointing at the published `unterlagen/privacy-policy.md`

## Still open

The app is sold, so it is a commercial offering. The stored imprint data
says "private individual, non-commercial" — that no longer fits and is a
decision for the developer, not for the build.
