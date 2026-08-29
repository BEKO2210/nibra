# Nibra — Roadmap

Ausgerichtet auf den Masterplan vom 28.08.2026.

**Baseline offline:** `1248cc8` (Fassung 1.9) — 77 Tests grün, gebaute APK führt nur
`RECORD_AUDIO`, kein `INTERNET`.
**Baseline Forschung:** `938cbec` — 89 Tests grün.
**Letzter bekannt guter Rollback-Punkt vor dem Blasenumbau:** `85aa81d`.

Die vorige Fassung dieses Dokuments liegt als
[ROADMAP-bis-2026-08-28.md](ROADMAP-bis-2026-08-28.md); die dort erledigten
Läufe sind nicht verloren, nur nicht mehr die Gliederung.

## Wie ein Status hier zu lesen ist

| Status | Bedeutung |
|---|---|
| `OFFEN` | noch nicht begonnen |
| `IN_ARBEIT` | wird gerade gebaut |
| `BLOCKIERT` | wartet auf etwas Benanntes |
| `GEMESSEN` | Zahlen liegen vor, Entscheidung offen |
| `BESTANDEN` | Beweis erbracht, Beleg verlinkt |
| `VERWORFEN` | geprüft und bewusst nicht weiterverfolgt |
| `PROMOTED` | aus der Forschung in die Auslieferung übernommen |

„Fast fertig" gibt es nicht.

## Woher ein Beleg stammt

Jede Aussage trägt, woher sie kommt. Das ist keine Förmlichkeit: ein
Emulator hat keine Samsung-Audiokette und keinen Adreno-Treiber.

| Marke | Bedeutung |
|---|---|
| **EMU** | auf dem Emulator ausgeführt (Android 34, x86_64, swiftshader) |
| **TEST** | automatisiert geprüft, ohne Gerät |
| **HW-OFFEN** | muss auf A15 und S23 Ultra nachgeholt werden |
| **NICHT BELEGT** | Vermutung, kein Nachweis |

---

## P-1 — Diktat funktioniert wieder `BESTANDEN`

Am 28.08.2026 diktierte die Auslieferung auf beiden Geräten nicht mehr.
Fünf Ursachen, alle am Gerät gemessen und behoben:

| Ursache | Fassung | Beleg |
|---|---|---|
| Blase setzte Uniforms, die es nicht gibt | 1.0 | **EMU** + Gegenprobe |
| „Wandelt" ohne Ausgang, Erkenner schwieg | 1.1 | **TEST** + Gegenprobe |
| Vier Erkenner je Prozess statt einem | 1.2 | **HW** |
| Ausleihe ohne garantierte Rückgabe | 1.3 | **HW** |
| Dauerdiktat bat um zehn Minuten Stille | 1.4 | **HW** |
| Verspätete Rückgabe räumte fremde Ausleihe ab | 1.5 | **TEST** + **HW** |
| `<queries>` fehlte seit dem ersten Tag | 1.6 | **HW** |

**Gehärtet gegen Rückfall** (`BauartTest.kt`, jede Prüfung mit belegter
Gegenprobe):

- nur der `Erkennerhalter` legt einen `SpeechRecognizer` an
- das Manifest erklärt den Bedarf an Erkennungsdiensten
- vorübergehende Störungen gelten nicht als Geräteunfähigkeit
- die Sprachabfrage gibt den Erkenner in jedem Fall zurück
- das Protokoll schreibt niemals gesprochenen Inhalt
- die Auslieferung kennt keine Forschungsklassen und keinen Netzcode

Dazu Lint als Gate (`abortOnError`) — es fand vier Aufrufe, die auf
Android 8 bis 12 abgestürzt wären.

**97 Tests offline, 109 Forschung.** Übergabe für die offenen Punkte:
[UEBERGABE-ASR.md](UEBERGABE-ASR.md).

---

## P0 — RuntimeShader-Stabilität `IN_ARBEIT`

**Problem:** Die Blase setzte Uniforms, die `Blobquelle.AGSL` nicht
deklariert. AGSL wirft dafür eine `IllegalArgumentException`, deren Meldung
die Plattform mit einem freigegebenen Namenszeiger formatiert — daraus wird
ein harter Abbruch der Laufzeit. Die Blase starb beim ersten Zeichnen und
war deshalb nie zu sehen.

| Aufgabe | Status | Beleg | Commit |
|---|---|---|---|
| Uniform-Namen berichtigen | `BESTANDEN` | **TEST** + **EMU** | `21ba826` |
| Eigene Ansicht statt Hintergrundzeichnung | `BESTANDEN` | **TEST** | `0a7ed16` |
| Jede Blase mit eigenem Shader | `BESTANDEN` | **TEST** | `0a7ed16` |
| Uniforms nur im Zeichenpfad | `BESTANDEN` | **TEST** | `0a7ed16` |
| Rückfallweg ohne Grafikeinheit erhalten | `BESTANDEN` | **TEST** | `0a7ed16` |
| Kein Takt bei unsichtbarer Blase | `BESTANDEN` | **TEST** | `0a7ed16` |
| Zeichenweg im Bericht sichtbar | `BESTANDEN` | **EMU** | `d00310e` |
| Gegenprobe: alter Fehler löst aus | `BESTANDEN` | **EMU** | `d00310e` |
| Dauerlauf ohne Dienst, 506 s | `BESTANDEN` | **EMU** | `938cbec` |
| Dauerlauf **mit** Dienst und echter Blase | `BESTANDEN` | **EMU** | `78fc693` |
| Ziehen, Werfen, Randfeder | `BESTANDEN` | **EMU** | `78fc693` |
| Dienst an/aus im Lauf | `BESTANDEN` | **EMU** | `78fc693` |
| Blase **im Dienst** unter Dauerbewegung | `OFFEN` | siehe Lücke unten | — |
| Nachweis auf echter Hardware | `BLOCKIERT` | **HW-OFFEN** | — |

**Belegte Zahlen, Dauerlauf ohne Dienst (EMU):**

```
laufzeit_s=506  weg=shader  zeichenvorgaenge=82492  bilder_je_sekunde=40,8
Total frames rendered: 20688      (Plattformzählung)
unable to find uniform: 0         JNI DETECTED ERROR: 0
RuntimeShader:          0         FATAL EXCEPTION:    0
Prozessabbrueche:       0
```

**Belegte Zahlen, Blase im Dienst (EMU), 10 Runden über 9 Minuten** mit
Ziehen, Werfen, Wurf über den Rand hinaus, Antippen, Bildschirm aus/an,
Drehung und Dienst aus/ein in jeder dritten Runde:

```
Total frames rendered: 848        Crashed services: {}
unable to find uniform: 0         JNI DETECTED ERROR: 0
RuntimeShader:          0         FATAL EXCEPTION:    0
Prozessabbrueche:       0         ANR in:             0
```

### Die Lücke, die dieser Lauf offen lässt

848 Bilder sind wenig — und das hat einen Grund: die Blase bewegt sich nur
während der Aufnahme, sonst steht sie. Genau so ist sie gebaut. Im Emulator
gibt es aber keine Stimme, die Erkennung bricht ab, und damit stand die
Blase die meiste Zeit still.

**Also getrennt gelesen:**

| Was | Wo belegt |
|---|---|
| Shader unter Dauerbewegung, 82492 Zeichenvorgänge | Probestand, **EMU** |
| Blase im Overlay-Fenster des Dienstes, Ziehen/Werfen/Lebenszyklus | Dienstlauf, **EMU** |
| **Beides zusammen** — Blase im Dienst *und* dauernd in Bewegung | **NICHT BELEGT** |

Der Zeichenpfad ist in beiden Fällen derselbe (`Blasenansicht.onDraw`), nur
das Fenster ist ein anderes. Der Absturz hing am Uniform-Namen, nicht am
Fenstertyp — die Lücke ist deshalb klein, aber sie ist da und wird beim
Hardware-Gate mitgeschlossen, wo es eine echte Stimme gibt.

Der hohe Anteil ruckelnder Bilder (78,9 %) stammt aus der Software-
rasterung des Emulators und ist **keine** Aussage über echte Geräte.

---

## P1 — Speech-Messstrecke valide machen `BLOCKIERT`

Blockiert durch P0. Die bisherigen Sprachdaten sind **ungültig**: der
Prozess starb während der Läufe.

**Befund:** `onResults` kam ohne Fehler und ohne Text, auf beiden Geräten.
`checkRecognitionSupport` zeigt `de-DE` als auf dem Gerät installiert — an
der Sprache liegt es nicht (**HW-belegt**, vor dem Blasenfix erhoben).

| Aufgabe | Status | Beleg |
|---|---|---|
| Absichtsversuch A/B/C/D gebaut | `BESTANDEN` | **TEST** |
| Ursache des leeren Ergebnisses benannt | `BESTANDEN` | **HW** — siehe P-1 |
| Bezugstext folgt der Diktatsprache | `BESTANDEN` | **TEST** |
| Sprachlauf mit echter Stimme | `BLOCKIERT` | braucht Vorlesen |

**Die Ursache der leeren Ergebnisse ist gefunden** — sie war keine
Eigenheit der Messstrecke, sondern der Diktatfehler selbst (P-1): vier
konkurrierende Erkenner, fehlende Paketsichtbarkeit, Ausleihen ohne
Rückgabe. Der Absichtsversuch A/B/C/D wird damit nicht mehr gebraucht, um
die Ursache zu finden; er bleibt als Werkzeug erhalten.

**Der Sprachlauf ist sprachbewusst geworden:** der Bezugstext richtet sich
nach der Diktatsprache (deutsch auf den Geräten, englisch auf dem
Emulator). Ein Messlauf, dessen Text nicht zur Erkennersprache passt,
erzeugt nur `NO_MATCH` und misst nichts.

**Was fehlt: eine gesprochene Stimme.** Der Weg über den Emulator ist
gescheitert und das gehört festgehalten: Eine PulseAudio-Nullsenke als
virtuelles Mikrofon hat **einmal** funktioniert (Ausschlag 1904/32767,
`onBeginningOfSpeech` kam), danach nicht mehr reproduzierbar. Der Emulator
öffnet die Quelle nicht — `pactl list source-outputs` bleibt leer, der
Ausschlag steht bei 8/32767. Auch mit `QEMU_PA_SOURCE` und
`-audiodev pa,in.source=` unverändert. `NICHT GELÖST`.

Der Emulator hat kein Mikrofon mit Stimme. Für A/B/C/D braucht es
eingespeistes Audio; sonst bleibt nur der Nachweis, dass der Ablauf läuft.

---

## P2 — Concurrent Capture messen `BLOCKIERT`

Blockiert durch P1. Frühere Zahlen sind ungültig.

**Offen und ungeklärt:** der zehnfache Pegelanstieg im Nebenlauf.
`NICHT BELEGT`, weder bestätigt noch widerlegt.

---

## P3 — AudioRecord-Schicht `OFFEN`

Zielaufbau, nur in der Forschung:

```
Aufnahme → Strecke → Ringpuffer → Zerlegung → Erkenner → Abschrift
```

| Aufgabe | Status |
|---|---|
| `Mikrofonbefund` (Quellen, Raten, aktive Mikrofone) | `BESTANDEN` (**HW-belegt**) |
| Aufnahme mit vollständiger Buchführung | `OFFEN` |
| Ringpuffer mit Vorlauf | `OFFEN` |
| Zerlegung / Sprachentscheidung | `OFFEN` |
| Rohstrom als Bezug erhalten | `OFFEN` |

---

## P4 bis P10 `OFFEN`

| Phase | Inhalt |
|---|---|
| P4 | Diktatqualität messen: eigenes Korpus, WER/CER, Halluzinationen |
| P5 | Offline-Erkenner bewerten |
| P6 | UI/UX fortlaufend |
| P7 | Barrierefreiheit härten |
| P8 | Blase als Signature-Element, Farbsätze rechnen |
| P9 | Übernahme in die Auslieferung, siehe [PROMOTION.md](PROMOTION.md) |
| P10 | Freigabekandidat |

---

## Was ausdrücklich **nicht** belegt ist

- Alles über Erkennungsqualität. Die Sprachläufe sind ungültig.
- Der Pegelanstieg im Nebenlauf.
- Jedes Verhalten der Samsung-Audiokette, das über die reine Aufzählung
  aus `MESSUNG-AUDIO.md` hinausgeht.
- Leistung auf echter Grafikhardware. Der Emulator rastert in Software.

## Fest eingeplante Prüfwerkzeuge (alle kostenlos, lokal)

Reihenfolge bewusst: was ein Merkmal **töten** kann, kommt vor dem, was es
verbessert.

| Wann | Werkzeug | Warum für Nibra |
|---|---|---|
| **vor der nächsten Veröffentlichung** | `play-policy-insights` | Nibra nutzt einen Bedienungshilfen-Dienst für die Blase und dazu das Mikrofon. Das sind die zwei am schärfsten geprüften Bereiche im Play Store. Wird die Nutzung beanstandet, fällt die Blase weg -- das muss man wissen, **bevor** daran weitergebaut wird, nicht danach. |
| direkt danach | `android-intent-security` | Der Dienst nimmt Absichten entgegen, die Forschungsausprägung sogar mit Zusätzen von außen. Eine ungeschützte Absicht wäre ein Weg in die App hinein. |
| nach Gate 3/4 | `/code-review` | Alles, was in der Messnacht entstanden ist, hat noch niemand gelesen. |
| nach dem Gesamtbericht | `android-release-verifier` | Auslieferungsreife gegen Belege statt gegen Gefühl. |
| bei Bedarf | `android-profiler` | Ergänzt unsere eigenen Messungen um Werkzeuge, die wir nicht selbst gebaut haben -- ein zweiter Zeuge für dieselben Zahlen. |
| vor dem Verkleinern | `r8-analyzer` | Erst wenn über Auslieferungsgröße geredet wird. |
| dauerhaft | CodeRabbit | Läuft schon, hat die Forschungsausprägung aber noch nie gesehen. |

Nicht eingeplant, weil sie nicht passen: die SEO-Werkzeuge, `scroll-world`,
`playwright-cli` und `agent-browser` (Nibra hat keine Weboberfläche),
`play-billing-*` (Nibra verkauft nichts).

