# Übernahme aus der Forschung in die Auslieferung

Nichts wandert aus `src/forschung` in die Auslieferung, weil es dort gut
aussieht. Jede Übernahme braucht ein Paket, das die Fragen unten
beantwortet — und danach einen eigenen Durchgang der **Auslieferungs**-App,
nicht der Forschungs-App.

## Statuswerte

`OFFEN` · `IN_ARBEIT` · `BLOCKIERT` · `GEMESSEN` · `BESTANDEN` ·
`VERWORFEN` · `PROMOTED`

## Was ein Übernahmepaket enthalten muss

| Feld | Frage |
|---|---|
| Änderung | Was genau soll übernommen werden? |
| Problem | Welches Problem löst es? |
| Ausgangslage | Wie war es vorher, in Zahlen? |
| Beweis | Was zeigt, dass es besser ist? |
| Tests | Welche automatisierten Tests kamen dazu? |
| Emulator | Welche Fälle bestanden? |
| Hardware-Lücke | Was muss auf echtem Gerät nachgeholt werden? |
| UI/UX | Verbesserung oder Rückschritt? |
| Barrierefreiheit | Bleibt der Weg mit TalkBack nutzbar? |
| Leistung | Rückschritt? |
| Datenschutz | Bleibt die Auslieferung ohne `INTERNET`? |
| Rückweg | Welcher Commit war vorher gut? |

## Was **nie** übernommen wird

- Messoberflächen (`ForschungActivity`, `Blasenprobestand`)
- Diagnosewerkzeuge (`Mikrofonbefund`, `Erkennerdiagnose`, `Absichtsversuch`)
- Versuchsaufbauten (`Nebenlaufversuch`, `Sprachlauf`, `Sprachbericht`)
- Forschungsschalter und tote Versuchstechnik
- alles, was `INTERNET` braucht

---

## Kandidaten

### K1 — `Wortvergleich` (Wortfehlerrate, Ausrichtung, Diff)

| Feld | Stand |
|---|---|
| Status | `OFFEN` |
| Änderung | Levenshtein mit Rückverfolgung, Umlautauflösung, Zahlwörter |
| Problem | Ohne ihn ist „gleich gut" ein Eindruck, keine Messung |
| Nutzen in der Auslieferung | **noch keiner** — reines Messwerkzeug |
| Tests | 12, grün |
| Urteil | Bleibt vorerst Forschung. Erst übernehmen, wenn die App selbst Abschriften vergleichen muss. |

### K2 — Endpunkterkennung immer setzen

| Feld | Stand |
|---|---|
| Status | `PROMOTED` |
| Änderung | Stillezeiten gehen in jedem Fall an den Erkenner |
| Problem | Vorher galt die Voreinstellung des Herstellers; Sätze wurden bei kurzem Nachdenken abgeschnitten |
| Beweis | **NICHT BELEGT** — übernommen aus Plausibilität, nicht aus einer Messung |
| Offen | Gehört mit gültiger Messstrecke nachgemessen (P1) |

### K3 — `Erkennungsergebnis` mit n-bester Liste und Konfidenz

| Feld | Stand |
|---|---|
| Status | `PROMOTED` |
| Änderung | Alternativen und Sicherheiten bleiben erhalten statt verworfen |
| Beweis | 6 Tests, grün. Kein Gerätebeleg nötig — reine Umformung |
| Offen | Wird in der Oberfläche noch nicht genutzt |

### K4 — `Blasenansicht` (Blase als eigene Ansicht)

| Feld | Stand |
|---|---|
| Status | `PROMOTED` |
| Änderung | Fläche und Symbol in einer Ansicht, Shader gehört genau einem Zeichenpfad |
| Problem | Absturz der Laufzeit, Blase nie sichtbar |
| Beweis | **EMU**: 506 s, 82492 Zeichenvorgänge, 0 Abbrüche. Gegenprobe löst aus |
| Hardware-Lücke | Adreno und Mali ungeprüft |
| Rückweg | `85aa81d` |

### K5 — Eigene Aufnahmestrecke (AudioRecord)

| Feld | Stand |
|---|---|
| Status | `BLOCKIERT` |
| Blocker | P1 und P2 |
| Grundsatz | Ersetzt die bestehende Offline-Erkennung erst, wenn sie **messbar** besser ist |

### K6 — Cloud-Erkenner

| Feld | Stand |
|---|---|
| Status | `OFFEN` |
| Grundsatz | Kommt nicht in die Auslieferung. Kein SDK, keine Abhängigkeit, kein `INTERNET`. Nur Vergleichsgröße in der Forschung |

---

## Trennung der Ausprägungen — laufende Prüfung

Das Manifest-Gate `pruefeNetzfreiheit` läuft bei jedem Bau der
Auslieferung. Es bricht ab bei `INTERNET` oder `ACCESS_NETWORK_STATE` —
**und auch dann, wenn es gar kein Manifest gefunden hat.** Eine Prüfung,
die nichts findet und trotzdem grün meldet, wäre schlimmer als keine.

Zuletzt an der gebauten APK geprüft:

```
uses-permission: android.permission.RECORD_AUDIO
uses-permission: de.ithandwerkstuttgart.nibra.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```
