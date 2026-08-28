# Nibra — Roadmap

Ausgerichtet auf den Masterplan vom 28.08.2026.

**Baseline offline:** `938cbec` — 77 Tests grün, gebaute APK führt nur
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
| Dauerlauf **mit** Dienst und echter Blase | `IN_ARBEIT` | — | — |
| Ziehen, Werfen, Randfeder | `OFFEN` | — | — |
| Dienst an/aus im Lauf | `OFFEN` | — | — |
| Nachweis auf echter Hardware | `BLOCKIERT` | **HW-OFFEN** | — |

**Belegte Zahlen, Dauerlauf ohne Dienst (EMU):**

```
laufzeit_s=506  weg=shader  zeichenvorgaenge=82492  bilder_je_sekunde=40,8
Total frames rendered: 20688      (Plattformzählung)
unable to find uniform: 0         JNI DETECTED ERROR: 0
RuntimeShader:          0         FATAL EXCEPTION:    0
Prozessabbrueche:       0
```

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
| A/B/C/D auf dem Emulator ausführen | `OFFEN` | — |
| Ursache des leeren Ergebnisses benennen | `OFFEN` | — |
| Sprachlauf gegen die Baseline messen | `OFFEN` | — |

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
