# Entscheidungsvorlage: neue Aufnahmestrecke übernehmen?

Stand 29.08.2026, 04:30. Gemessen auf Samsung A15 (Android 16).

## Der Prüfstand

**80 Aufnahmen echter menschlicher Sprache** aus Google FLEURS `de_de test`
(CC-BY-4.0), 14,6 Minuten, 4,4 bis 19,9 Sekunden je Aufnahme, gleichmäßig
über den ganzen Testsplit verteilt. Jede Aufnahme mit Datensatz, Fassung,
Kennung, Bezugstext, Dauer, Lizenz und je einem Hash vor und nach der
Umwandlung. Das Original liegt unverändert daneben.

Alle drei Varianten bekommen **denselben eingespeisten Ton**, denselben
Erkenner, dieselbe Spracheinstellung. Verändert wird nur die geprüfte
Größe. Kein Lautsprecher, kein Mikrofon, kein Raum.

## Die Zahlen

| | ALT | NEU | NEU + Vorgabe |
|---|---|---|---|
| rohe Wortfehlerrate | 15,9 % | **12,8 %** | 12,8 % |
| bereinigte Wortfehlerrate | 15,6 % | **12,5 %** | 12,5 % |
| Zeichenfehlerrate | 8,2 % | **4,9 %** | 4,9 % |
| Auslassungen | 6,1 % | **2,7 %** | 2,7 % |
| Einfügungen | 1,0 % | 1,1 % | 1,1 % |
| Verlust am Satzanfang | 0,85 Wörter | **0,13** | 0,13 |
| Verlust am Satzende | 0,16 | 0,16 | 0,16 |
| Halluzinationen je Lauf | 0,18 | 0,19 | 0,19 |
| leere Ergebnisse | 0 von 80 | 0 von 80 | 0 von 80 |
| erster Text (P50) | 1578 ms | 1575 ms | 1573 ms |
| bestätigter Text (P50) | — (keine Segmente) | 10 147 ms | 10 162 ms |

Trefferquote je Wortklasse: normale Wörter 85 % → **88 %**, Fachbegriffe
100 % → 100 %, Zahlen 100 % → 100 %. Eigennamen kommen im FLEURS-Korpus im
Sinne unserer Klassenliste nicht vor und stehen deshalb mit „—".

## Je Aufnahme

| | NEU besser | Gleichstand | NEU schlechter |
|---|---|---|---|
| Segmentsitzung | **6** | 74 | **0** |
| Vorgabeliste | 0 | 80 | 0 |

**Keine einzige Verschlechterung auf 80 Aufnahmen.** Das wiegt schwerer als
der Mittelwert: der Vorteil ist nicht dünn über alles verteilt, sondern er
rettet sechs Aufnahmen, die sonst halb verloren wären, und schadet keiner.

Alle sechs sind derselbe Fehler:

```
Bezug: Mehrere große Fernsehschirme wurden an verschiedenen Orten in Rom
       aufgestellt, so dass die Leute die Zeremonie ansehen konnten.
ALT  : so dass die Leute die Zeremonie ansehen konnten
NEU  : mehrere große fernsehschirme wurden an verschiedenen Orten in Rom
       aufgestellt so dass die Leute die Zeremonie ansehen konnten
```

56 % Fehlerrate auf **0 %**. Bei einer zweiten Aufnahme 65 % auf 0 %. Ohne
Segmentsitzung kommt nur das Satzende zurück, der Anfang fehlt vollständig.

## GEWINNT NEU

Bei Wortfehlerrate, Zeichenfehlerrate, Auslassungen, Verlust am Satzanfang
und normalen Wörtern. Ohne Preis: Einfügungen +0,1 Punkte, Halluzinationen
+0,01 je Lauf, leere Ergebnisse unverändert null, erster sichtbarer Text
3 ms schneller.

## GLEICHSTAND

Verlust am Satzende, Fachbegriffe, Zahlen, Latenz bis zum ersten Text.

## GEWINNT ALT

Nichts.

## Die Vorgabeliste ist wirkungslos

`EXTRA_BIASING_STRINGS`, zweimal geprüft:

- Sprachausgabe, 5 Wörter, 36 gegen 36 Läufe: alle Kennzahlen identisch
- echte Sprache, 60 Wörter aus den Bezugstexten, 80 gegen 80: alle
  Kennzahlen identisch, **80 von 80 Aufnahmen Zeichen für Zeichen gleich**

Dass das kein Zufall ist, zeigt die Gegenprobe: der Erkenner arbeitet bei
eingespeistem Ton vollständig bestimmt -- 26 Gruppen aus je drei
Wiederholungen ohne eine einzige Abweichung. Bei bestimmtem Verhalten heißt
gleiche Ausgabe bei verschiedener Vorgabe, dass die Vorgabe keine Wirkung
hatte.

**Folge:** kein persönliches Wörterbuch über diese Schnittstelle. Es wäre
Arbeit ohne Wirkung und ein Versprechen, das die Technik nicht hält. Zurück
auf die Liste darf es erst, wenn ein Weg gefunden ist, der messbar etwas
ändert.

*Grenze:* ein Gerät, Erkenner auf dem Gerät, ohne Netz. Ob die Liste über
das Netz wirkt, ist ungeprüft -- für Nibra ohne Belang.

---

# NICHT PROMOTEN — noch nicht

Alles, was ohne Belkis' Stimme messbar ist, spricht für die neue Strecke.
Genau eine Bedingung fehlt, und sie ist die, die er selbst gesetzt hat.

## Was für die Übernahme spricht

| Bedingung | Stand |
|---|---|
| Erkennungsqualität ≥ alt | **erfüllt**, −3,1 Punkte, 6:74:0 je Aufnahme |
| keine höhere Halluzinationsrate | **erfüllt**, +0,01 je Lauf |
| keine Verschlechterung normaler Wörter | **erfüllt**, +3 Punkte |
| Satzanfänge ≥ alt | **erfüllt**, 0,85 → 0,13 Wörter Verlust |
| Satzenden ≥ alt | **erfüllt**, unverändert |
| Latenz annehmbar | **erfüllt**, erster Text 3 ms schneller |
| eigener kontrollierter Tonstrom | **belegt** |
| Vorlauf | **belegt**, drei Geräte, zwei Hersteller |
| keine technische Segmentlücke | **belegt** |
| Erholung nach Störung | **belegt**, 15 Fälle, zwei Geräte |
| Beständigkeit | **belegt**, 300 Sitzungen, kein Leck |
| offline, kein INTERNET | **belegt** am gebauten Erzeugnis |
| kein Tonverlust | **belegt**, bytegenau bis 15 Minuten |

## Was fehlt

**Der End-to-End-Vergleich über das echte Mikrofon.** Alle Zahlen oben
stammen aus eingespeistem Ton. Sie beantworten: *erkennt die neue
Einstellung dieselbe Aufnahme besser?* — ja, deutlich.

Sie beantworten **nicht**: *ist die vollständige Strecke am echten Mikrofon,
im Raum, mit Belkis' Stimme ebenfalls besser?* Dafür braucht es hörbaren
Ton, und der Aufbau dafür steht bereit und ist gegen versehentliches Starten
verriegelt.

**Empfehlung:** übernehmen, sobald dieser eine Vergleich gefahren ist. Fällt
er ebenfalls zugunsten der neuen Strecke aus, ist keine Bedingung mehr offen.
