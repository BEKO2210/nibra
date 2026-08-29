# Messung: was die Mikrofone wirklich melden

Stand 28.08.2026. Alle Zahlen stammen aus `Mikrofonbefund` und
`Nebenlaufversuch` in der Forschungsausprägung, ausgeführt auf beiden
Geräten. Rohberichte: `messungen/audiobefund-a15.txt`,
`messungen/audiobefund-s23.txt`.

Nichts hier ist aus der Anzahl physischer Mikrofone geschlossen.

> **Nachtrag vom 28.08.2026 — was von diesen Zahlen trägt und was nicht.**
>
> Während dieser Messungen stürzte der Prozess wiederholt ab: die Blase
> setzte Shader-Uniforms, die es nicht gibt, und riss die Laufzeit mit
> (behoben in `21ba826`). Deshalb hier die Trennung:
>
> **Trägt weiter** — alles, was das Gerät auf Anfrage meldet und was in
> Sekundenbruchteilen erhoben wird: die Widersprüche zwischen
> `getMicrophones()` und `getDevices()`, die Kanalzuordnung `PROCESSED`
> bei `UNPROCESSED` auf dem S23 Ultra, die nativ unterstützten
> Abtastraten. Diese Berichte wurden vollständig geschrieben, und die
> Werte hängen nicht daran, wie lange der Prozess lebt.
>
> **Trägt nicht mehr** — die Pegelzahlen des Nebenlaufversuchs und jede
> Aussage über Erkennungsqualität. Sie stammen aus Läufen über mehrere
> Sekunden, in deren Zeitraum Abstürze fallen. Der zehnfache Pegelanstieg
> im Nebenlauf ist damit **offen**, nicht widerlegt und nicht bestätigt.
>
> Neu gemessen wird erst, wenn der Absturznachweis steht.

## Die Geräte

| | A15 | S23 Ultra |
|---|---|---|
| Modell | SM-A156B | SM-S918B |
| Android | 16 (API 36) | s. Rohbericht |
| Hardware | Mediatek `mt6835` | Qualcomm |
| Abtastraten am Eingang | 8000, 16000, 32000, 44100, 48000 | zusätzlich 11025, 12000, 22050, 24000 |

## Befund 1 — `getMicrophones()` zählt anders als `getDevices()`

**A15:** `getDevices()` meldet **zwei** eingebaute Mikrofone (`bottom`, `back`).
`getMicrophones()` meldet nur **eines** (`SPH1642HT5H_REV_B`, `bottom`) plus
einen Platzhalter-Eintrag ohne Angaben.

**S23 Ultra:** `getDevices()` meldet **zwei** eingebaute (`bottom`, `back`).
`getMicrophones()` meldet **vier** — aber mit doppelten Kennungen:

| Name | Kennung | Adresse |
|---|---|---|
| `builtin_mic_1` | 22 | bottom |
| `builtin_mic_3` | 22 | bottom |
| `builtin_mic_2` | 24 | back |
| `builtin_mic_4` | 24 | back |

Vier Einträge, zwei Kennungen. Wer die Liste zählt, zählt falsch.

**Folge:** Beide Abfragen sind keine verlässliche Quelle für „wie viele
Mikrofone hat das Gerät". Kein Code darf darauf bauen.

## Befund 2 — die Kanalzuordnung ist der eigentliche Unterschied

`getActiveMicrophones()` während laufender Aufnahme:

| | A15 | S23 Ultra |
|---|---|---|
| aktives Mikrofon | `SPH1642HT5H_REV_B` (bottom) | `builtin_mic_1` (bottom) |
| gemeldete Kanäle | 16 | 1 |
| Zuordnung | **alle DIRECT** | **PROCESSED** |

Der A15 meldet für einen **einkanaligen** Strom sechzehn Kanalzuordnungen.
Das ist offensichtlich kein sinnvoller Wert — der Hersteller füllt das Feld
schlicht auf. Auch dieses Feld trägt also keine Entscheidung.

Der S23 Ultra meldet ehrlicher: ein Kanal, `PROCESSED`.

## Befund 3 — `UNPROCESSED` liefert auf dem S23 Ultra kein unbearbeitetes Signal

Das war die Frage, die den größten Unterschied gemacht hätte, und sie ist
beantwortet: auf dem S23 Ultra meldet `getActiveMicrophones()` auch bei
`MediaRecorder.AudioSource.UNPROCESSED` die Kanalzuordnung **`PROCESSED`** —
bei **allen** fünf Abtastraten.

Die Quelle lässt sich öffnen, sie liefert Signal, aber sie liefert nicht das,
wonach ihr Name klingt.

Messbar ist der Unterschied trotzdem, am Pegel (größter Ausschlag von 32767,
gleicher Raum, ruhig):

| Quelle | A15 (48 kHz) | S23 Ultra (48 kHz) |
|---|---|---|
| `MIC` | 273 | 318 |
| `VOICE_RECOGNITION` | 136 | 327 |
| `UNPROCESSED` | **39** | **59** |

`UNPROCESSED` ist auf beiden Geräten um ein Vielfaches leiser. Es passiert
also etwas anderes als bei den anderen Quellen — nur eben nicht „nichts".

**Was daraus folgt:** Eine eigene Aufnahmestrecke kann sich nicht darauf
verlassen, ein rohes Signal zu bekommen. Was ein späterer eigener Erkenner
zu hören bekäme, ist auf dem S23 Ultra bereits durch die Kette des Herstellers
gelaufen. Ob das hilft oder schadet, ist damit noch nicht gesagt — aber es ist
nicht mehr unbekannt.

## Befund 4 — Alle Abtastraten funktionieren nativ

Beide Geräte öffnen 8000, 16000, 32000, 44100 und 48000 Hz und melden die
angeforderte Rate unverändert zurück (`AudioRecord.sampleRate`). Kein
stilles Hochrechnen, kein Ausweichen.

**Folge:** 16 kHz — die Rate, mit der praktisch alle Spracherkenner arbeiten —
lässt sich direkt aufnehmen. Kein eigener Umrechner nötig.

## Befund 5 — Nebenlauf funktioniert auf beiden Geräten

Der Versuch: Erkenner allein, eigene Aufnahme allein, beide gleichzeitig.

| | A15 | S23 Ultra |
|---|---|---|
| eigene Aufnahme allein liefert Signal | ja | ja |
| eigene Aufnahme **neben** dem Erkenner | **ja** | **ja**  |
| stille Rahmen dabei | 0 | 0 |
| aktives Mikrofon ändert sich | nein | nein |
| Kanalzuordnung ändert sich | nein | nein |
| Konfliktfehler des Erkenners | keiner | keiner |
| Ereignisfolge des Erkenners | **identisch zum Alleinlauf** | **identisch zum Alleinlauf** |

Android schaltet den zweiten Aufnehmer also nicht stumm. Das war die
eigentliche Frage, und die Antwort ist auf beiden Geräten dieselbe.

Die Ereignisfolge war in allen vier Läufen exakt
`bereit, Sprache beginnt, Sprache endet, Sprache endet, Fehler 7`.
Fehler 7 ist `ERROR_NO_MATCH` und im stillen Raum der Normalfall — er sagt
nichts über Nebenlauf. Entscheidend ist, dass die Folge **gleich blieb**.

### Eine Auffälligkeit, die noch offen ist

Der Pegel der eigenen Aufnahme war im Nebenlauf auf **beiden** Geräten
deutlich höher als allein:

| | allein | nebenlaufend |
|---|---|---|
| A15 | 228 | 1904 |
| S23 Ultra | 139 | 1564 |

Rund das Zehnfache, in dieselbe Richtung, auf zwei verschiedenen Plattformen.
Für Umgebungsgeräusch ist das zu systematisch.

Naheliegende Erklärungen, **keine davon geprüft**: der Erkenner gibt beim
Start einen Signalton aus, den das Mikrofon mithört; oder der Start einer
Erkennungssitzung ändert Verstärkung oder Führung des Eingangs für alle
Mitlesenden. Das gehört gemessen, bevor irgendetwas darauf gebaut wird.

## Was diese Messung ausdrücklich **nicht** zeigt

In keinem Durchgang wurde gesprochen, also wurde in keinem Durchgang Text
erkannt. Belegt ist damit: beide Aufnahmepfade sind gleichzeitig offen und
führen echtes Signal. **Nicht** belegt ist, dass die Erkennungsqualität unter
Nebenlauf dieselbe bleibt. Dafür braucht es einen Lauf mit Sprache.

Der Bericht sagt das von sich aus — die Bewertung schreibt den Vorbehalt
selbst in die Ausgabe, damit er später nicht überlesen wird.

## Auswirkung auf die Auslieferungsfassung

Keine. Alles hier liegt in `app/src/forschung/`. An der gebauten
Auslieferungs-APK nachgeprüft, nicht am Manifest:

```
uses-permission: name='android.permission.RECORD_AUDIO'
uses-permission: name='de.ithandwerkstuttgart.nibra.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

Kein `INTERNET`. Die bestehende Offline-Erkennung ist unangetastet.
