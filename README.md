# Nibra

**Sprechen. Schreiben. Fertig.**

Diktier-App für Android. Sprache aufnehmen, in Text umwandeln, in jedes
Eingabefeld einfügen — vollständig auf dem Gerät.

## Was sie kann

- Große Aufnahmefläche mit ruhiger Pegelanzeige, Stopp bei Stille
- Einfügen in **jede** App über den Bedienungshilfen-Dienst
- Durchsuchbarer lokaler Verlauf, nach Datum gruppiert
- Textbausteine: eigene Ersetzungen
- Diktatsprache je Eintrag umschaltbar
- Sieben Oberflächensprachen: de, en, fr, es, it, tr, pl
- Hell und dunkel

## Was sie nicht tut

**Keine `INTERNET`-Berechtigung.** Kein Konto, keine Cloud, keine Werbung,
keine Tracker, keine Analyse. Diktate bleiben in einer lokalen Datenbank;
Aufnahmen werden nach der Umwandlung verworfen, sofern man sie nicht
ausdrücklich behält.

## Technik

| | |
|---|---|
| Sprache | Kotlin, Jetpack Compose |
| Erkennung | Android-Bordmittel auf dem Gerät (`SpeechRecognizer`) |
| minSdk / targetSdk | 26 / 36 (Android 16) |
| Paket | `de.ithandwerkstuttgart.nibra` |
| APK | 2,0 MB signiert |
| AAB | 3,3 MB signiert |
| Berechtigungen | `RECORD_AUDIO`, Bedienungshilfen-Dienst |

Signatur-Fingerabdruck (SHA-256):
`16:9B:99:08:12:AE:A2:63:10:85:CB:97:CD:8C:C4:B3:CF:33:77:99:1A:27:6B:81:65:BC:9B:24:77:7F:BE:11`

Der Signaturschlüssel liegt ausschließlich im Tresor
(`NIBRA_UPLOAD_KEYSTORE_B64`, `NIBRA_KEYSTORE_PROPERTIES_B64`) — nie im Repo.

## Übergabe

`abgabe/` enthält das signierte AAB für Google Play und die signierte APK
zum direkten Installieren. `store/` enthält Texte in sieben Sprachen,
Feature-Grafik, Datenschutzerklärung, Datensicherheits-Antworten und die
Klick-für-Klick-Anleitung `store/UEBERGABE.md`.

## Herkunft

Neubau auf Grundlage von [writingmate/aidictation](https://github.com/writingmate/aidictation)
(MIT). Der Ansatz wurde übernommen, der Code neu geschrieben. Lizenzhinweise
in `FREMDSOFTWARE.md`.

## LICENSE

## Lizenz

Nibra ist **nicht quelloffen**. Copyright (c) 2026 Belkis Aslani, alle Rechte
vorbehalten — siehe [LICENSE](LICENSE).

Die verwendete Fremdsoftware unterliegt eigenen Lizenzen, die davon unberührt
bleiben:

| Bestandteil | Lizenz |
|---|---|
| 146 Programmbibliotheken (AndroidX, Kotlin, Dagger/Hilt, Okio) | Apache-Lizenz 2.0 |
| Schriften Inter und Fraunces | SIL Open Font License 1.1 |
| Vorlage `writingmate/aidictation` | MIT-Lizenz |

Die vollständigen Texte liegen der App bei und sind darin unter
**Fremdsoftware** einsehbar; im Quelltext stehen sie in
`app/src/main/res/raw/lizenzen.txt`. Eine Übersicht führt
[FREMDSOFTWARE.md](FREMDSOFTWARE.md).

Die Spracherkennung stellt Android bereit und wird nicht mitgeliefert.

## Unterlagen

Berichte und Messungen liegen unter [`docs/`](docs/README.md).
