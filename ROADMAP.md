# Nibra — Roadmap

Stand: 28.08.2026, fortgeschrieben am selben Tag um 01:45. Grundlage: Gerätetests auf SM-A156B (Android 16,
API 36), Review-Läufe von Codex und Kimi, Rückmeldungen aus dem Einsatz,
Vorgaben aus `AUFTRAG.md`.

Jede Stufe besteht aus mehreren Läufen. Ein Lauf gilt erst als fertig, wenn
er auf dem Gerät belegt ist — Screenshot oder Geräteausgabe, nicht „müsste
gehen“.

Zeichen: ✅ fertig und belegt · 🔄 läuft gerade · ⏳ offen · ⏸ wartet auf
eine Entscheidung oder auf Rückmeldung vom Gerät.

**Gerade in Arbeit: Lauf 4.4 (Suchtreffer hervorheben).**

---

## Stufe 0 — Grundlage (nachgetragen 28.08.2026)

**Warum:** Beim Wiederaufnehmen ließ sich das Projekt nicht bauen und die
Testsuite hing. Ohne belastbare Grundlage ist jede weitere Zusage wertlos.

| Lauf | Inhalt | Stand |
|---|---|---|
| 0.1 | Build repariert: `compileSdk`/`targetSdk` standen auf 37, das es als Plattform nicht gibt — zurück auf 36 wie in `AUFTRAG.md`. Bauen mit JDK 17; die JBR von Android Studio ist Java 25, daran stirbt der Kotlin-Compiler | ✅ |
| 0.2 | Testsuite entstockt: der Uhr-Auftrag verglich `delay` (virtuelle Zeit) mit `System.currentTimeMillis()` (echte Zeit) und drehte zehn Minuten lang. Zugleich ein Produktivfehler bei stehender Systemzeit | ✅ über 10 min → 18 s |
| 0.3 | Tests voneinander getrennt: Room am Test-Dispatcher, eigene Einstellungs-Ablage je Test. Vorher erbte ein Test die Einstellungen des vorigen | ✅ 3 Läufe hintereinander grün |
| 0.4 | Markenschriften wirklich beigelegt: Fraunces und Inter, vier Schnitte aus den variablen Originalen fest eingestellt und auf die sieben Sprachen beschnitten — 203 KB statt 1,2 MB. Der Bildschirm „Fremdsoftware“ nannte sie vorher, ohne dass sie ausgeliefert wurden | ✅ |
| 0.5 | App-Zeichen: das bisherige ist Raster statt Vektor, hat einen verrauschten Verlauf, eine asymmetrische Welle, und der Adaptive-Icon-Vordergrund enthält den Hintergrund — die Launcher-Maske schneidet die Feder an. Drei Vektorentwürfe liegen in `marke/entwuerfe/`, A ist gewählt | 🔄 A braucht exakte Spiegelsymmetrie, Sicherheitszone und Wandlung nach VectorDrawable |

## Stufe 1 — Das Diktat wird sichtbar

**Warum:** Beim Sprechen sah man nur Timer und Pegelkurve. Der erkannte Text
erschien erst im Verlauf. Das fühlte sich blind an.

| Lauf | Inhalt | Stand |
|---|---|---|
| 1.1 | `Aufnahmezustand.Laeuft` bekommt `teiltext`; ViewModel reicht `Erkennungsereignis.Teiltext` durch statt es zu verwerfen | ✅ |
| 1.2 | Aufnahmefläche zeigt den Text mit, während gesprochen wird | ✅ |
| 1.3 | Nach dem Stopp bleibt das Ergebnis stehen („Zuletzt diktiert“) mit Kopieren / Einfügen / Öffnen | ✅ |
| 1.4 | Textbausteine wirken sichtbar im Ergebnis | ⏸ Code steht, Beleg am Gerät fehlt |

## Stufe 2 — Überall diktieren (die Blase)

**Warum:** Der eigentliche Zweck. Die Blase erschien nur nach einem
Fokus-Ereignis und verschwand beim Fensterwechsel — in fremden Apps also
fast nie brauchbar.

| Lauf | Inhalt | Stand |
|---|---|---|
| 2.1 | Sichtbarkeit repariert: bei Fensterwechsel neu prüfen statt verbergen | ✅ Blase steht in fremden Apps |
| 2.2 | Live-Schreiben: Teiltexte laufend am Cursor ersetzen, Endergebnis festschreiben | ✅ im Einsatz bestätigt |
| 2.3 | Blase verschiebbar, merkt sich den Platz, Zustände sichtbar | ✅ |
| 2.4 | Kein Datenverlust: Hinweisband an der Blase statt Kurzmeldung (Android unterdrückt Toasts stummgeschalteter Apps) | ✅ |
| 2.5 | Passwortfelder ausgenommen, keine Blase über gesperrtem Bildschirm | ✅ Code: eine Sperre in `fokussiertesEingabefeld()` statt vier verstreuter Prüfungen; `Feldschutz` fängt auch Felder ohne `isPassword` (WebView, PIN); `KeyguardManager` sperrt die Blase über der Bildschirmsperre; 4 Tests. Am Gerät belegt: `device-shots/lauf-2-5-feldschutz/` — Blase steht am normalen Feld, verschwindet am Passwortfeld |

## Stufe 3 — Erkennung, die durchhält

| Lauf | Inhalt | Stand |
|---|---|---|
| 3.1 | **Dauerdiktat:** nach jedem Ergebnis weiterhören, statt die Aufnahme zu beenden; Sätze wachsen zusammen | ✅ Zwei Fehler behoben: der Bildschirm sprang zwischen den Sätzen auf „Wandelt“, und der schon verstandene Text verschwand. `Laeuft` trägt jetzt `festerText`; 2 Tests |
| 3.2 | Sprachpakete: fehlendes Paket anstoßen, Fortschritt melden, danach erneut versuchen | ✅ Anstoß gebaut, Fortschrittsanzeige ⏳ |
| 3.3 | Sprachliste bedienbar: Suchfeld, installierte oben, klare Trennung | ⏳ |
| 3.4 | Fehlertexte: jeder Fehlerpfad einmal echt ausgelöst und belegt | ⏳ 2 von 5 gesehen |

## Stufe 4 — Verlauf und Detail

| Lauf | Inhalt | Stand |
|---|---|---|
| 4.1 | Löschen mit „Rückgängig“ statt endgültig | ✅ Eintrag wird vor dem Löschen beiseitegelegt, Einblendung bietet „Rückgängig“, ein Schritt ohne Stapel; 2 Tests |
| 4.2 | Detail: Text bearbeitbar (tippen statt neu diktieren) | ✅ Der Text ist unmittelbar bearbeitbar — kein Stiftknopf, kein zweiter Zustand. „Sichern“ und „Verwerfen“ erscheinen erst, wenn sich etwas geändert hat; leerer oder unveränderter Text überschreibt nichts; 2 Tests |
| 4.3 | Verlauf: Wischen zum Löschen, Mehrfachauswahl, Export | ⏳ |
| 4.4 | Suche: Treffer hervorheben | ⏳ |

## Stufe 5 — Feinschliff

| Lauf | Inhalt | Stand |
|---|---|---|
| 5.1 | Bedienungshilfen: Beschriftungen, Tippziele, Kontraste, große Schrift | 🔄 Kontraste gemessen und Feldränder auf 3:1 gebracht; Schalterzeilen ganzflächig; Kopfzeile mittig mit Auslassungspunkten. Offen: Sprachausgabe durchgehen, größte Systemschrift prüfen |
| 5.2 | Dunkler Modus am Gerät durchsehen | ⏳ |
| 5.3 | Alle sieben Oberflächensprachen am Gerät ansehen | ⏳ tr geprüft |
| 5.4 | Erste Minute: Einrichtung kürzen, direkt ins erste Diktat | ⏳ |
| 5.5 | Abgabe: Signieren, `datenfluss.yaml`, Store-Texte, Bildmaterial | ⏳ |

## Stufe 6 — Sauberer Text (neu, aus dem Einsatz)

**Warum:** Aus dem Erkenner kam Kleintext ohne Satzzeichen, und beim
Anhängen fehlte das Leerzeichen. Der Cursor sprang nach dem Diktat an den
Anfang zurück.

| Lauf | Inhalt | Stand |
|---|---|---|
| 6.1 | Cursor bleibt hinter dem Text: Knoten auffrischen, Auswahl setzen, nach 120 ms nachfassen | ✅ |
| 6.2 | Leerzeichen beim Anhängen, kein Abstand vor `.,;:!?` und nach Klammer/Umbruch | ✅ |
| 6.3 | Androids eigene Formatierung einschalten (`EXTRA_ENABLE_FORMATTING`, ab API 33) — sie war schlicht nicht gesetzt | ✅ |
| 6.4 | Gesprochene Satzzeichen in sieben Sprachen („Punkt“, „Komma“, „neue Zeile“ …), Abstände, Satzanfang groß | ✅ 8 Tests grün |
| 6.5 | **Entscheidung:** reicht Androids Formatierung, oder kommt ein lokales Interpunktionsmodell? | ⏸ wartet auf einen Satz mit Nebensatz vom Gerät |
| 6.6 | Falls nötig: Interpunktionsmodell als ONNX (BERT-tiny-Klasse, int8, ~40–60 MB, einmaliger Download, danach netzfrei), Laufzeit ~50–150 ms je Satz | ⏳ hängt an 6.5 |
| 6.7 | Kein LLM im Live-Pfad. Ein kleines LLM (0,5 B, Q4, ~350 MB) braucht auf diesem Gerät Sekunden je Satz — höchstens als abschaltbarer Knopf „Text glätten“ nach dem Diktat | ⏳ nur bei Bedarf |

---

## Nicht in der Roadmap

- Konto, Bezahlung, Werbung, Tracker — bleibt draußen (`AUFTRAG.md`, Antwort 3).
- Cloud-Erkennung oder KI-Nachbearbeitung über fremde Endpunkte — bleibt draußen (Antwort 4).
- Eigene Tastatur — entfällt (Antwort 1).

## Bekanntes Systemverhalten (kein Fehler)

- Android schaltet den Bedienungshilfen-Dienst bei **jedem App-Update** ab.
  Nach einem neuen Build muss er von Hand wieder eingeschaltet werden.
- Kurzmeldungen (Toasts) einer App unterdrückt Android, wenn deren
  Benachrichtigungen aus sind. Deshalb meldet sich der Dienst über ein
  eigenes Band an der Blase.
