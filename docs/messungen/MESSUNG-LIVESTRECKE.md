# Experiment 3 — die Zielarchitektur trägt

Stand 28.08.2026. Auf beiden Geräten mit echter Stimme gemessen.

```
AudioRecord ──▶ Warteschlange ──▶ Rohrschreiber ──▶ SpeechRecognizer
  (Leser)         (begrenzt, 64)     (eigener Faden)     EXTRA_AUDIO_SOURCE
                                                         EXTRA_SEGMENTED_SESSION
```

Nibra besitzt das Mikrofon **allein**. Der Erkenner bekommt nur, was Nibra
ihm gibt.

## Der Ton kommt vollständig durch

| | A15 | S23 Ultra |
|---|---|---|
| gelesene Rahmen | 217088 | 217088 |
| **verworfene Blöcke** | **0** | **0** |
| **größte Warteschlange** | **1 von 64** | **1 von 64** |
| leere Warteversuche | 1 | 1 |
| Lesefehler | 0 | 0 |
| Verlust gegen die Uhr | −70 ms | −68 ms |
| an das Rohr | 385024 B | 382976 B |
| größter Ausschlag | 1642 | 6388 |

**Kein Rückstau.** Die Warteschlange wurde nie tiefer als **ein** Block —
der Erkennungsdienst liest so schnell, wie das Mikrofon liefert. Die
Trennung von Mikrofonleser und Rohrschreiber war trotzdem richtig: Sie
kostet nichts und macht den Fall, dass der Dienst einmal stockt,
messbar statt unsichtbar.

Der negative Verlust bedeutet, dass **mehr** Abtastwerte ankamen als Zeit
verging — Randungenauigkeit der Messung, kein fehlender Ton. Entscheidend
ist die Null bei den verworfenen Blöcken.

## Die Neustartlücke verschwindet

Das ist der wichtigste Befund. Auf dem S23 Ultra, **eine** Sitzung:

```
 1286 ms  startListening          ← einmal, mehr nicht
 1344 ms  onReadyForSpeech
 2903 ms  onBeginningOfSpeech
 6185 ms  onEndOfSpeech
 6188 ms  onSegmentResults        "guten Morgen hier spricht belgis aslani"
 7504 ms  onBeginningOfSpeech     ← ohne neues startListening
10860 ms  onEndOfSpeech
10861 ms  onSegmentResults        "guten Morgen hier spricht bernkes asslani"
12624 ms  onBeginningOfSpeech
13762 ms  onSegmentResults        "guten Morgen"
```

**Ein `startListening`, drei Segmente.** Die Sitzung läuft durch; zwischen
den Sätzen wird nichts abgerissen und neu aufgebaut.

Zum Vergleich, die bisherige Architektur — je Satz eine neue Sitzung:

| | alt | neu |
|---|---|---|
| A15 | 275 ms taub | keine Sitzungsgrenze |
| S23 Ultra | 291 ms taub | keine Sitzungsgrenze |

Der Abstand zwischen einem Segment und dem nächsten Sprachbeginn ist jetzt
die **Sprechpause** des Nutzers, keine technische Lücke mehr. Damit
entfällt die Ursache für verschluckte Wortanfänge beim Dauerdiktat.

## Erkennungsqualität

| | Wortfehlerrate |
|---|---|
| S23 Ultra, Durchgang 1 | **16,7 %** |
| S23 Ultra, über Mikrofon (P1) | 19,7 % |

Der eingespeiste Weg ist **nicht schlechter** als der bisherige. Ein
Durchgang mit einem kurzen Satz -- als Beleg für Gleichstand tauglich,
nicht für eine Rangfolge.

Erkannt wurde `guten Morgen hier spricht belgis aslani`. Der einzige
Fehler ist wieder ein Eigenname.

## Der Vorlauf ist **nicht** belegt

Beide Durchgänge hatten den Wortanfang — auch der ohne Vorlauf. Die
Verzögerung von 1200 ms war zu kurz, um einen Verlust zu erzeugen.

**Das ist kein Erfolg, sondern ein misslungener Versuch.** Der Vorlauf ist
damit unschädlich, aber sein Nutzen ist ungezeigt. Zu wiederholen mit
deutlich größerer Verzögerung.

## Zwei Fehler in der eigenen Auswertung

1. **„ES FEHLT TON" bei −70 ms.** Geprüft wurde der Betrag statt der
   Richtung. Ein negativer Wert ist kein Verlust.
2. **133 % Wortfehlerrate.** Alle Lesarten wurden mit Trennstrich verkettet,
   dann zählte jede Alternative als zusätzliche Wörter — für einen Satz,
   der fast richtig erkannt war.

Beide berichtigt. Es sind die dritte und vierte Fehlmessung an einem Tag,
die eine funktionierende Sache als kaputt gemeldet hätte.

## Stand des Entscheidungsgates

| Bedingung | Stand |
|---|---|
| Live-AudioRecord funktioniert | **erfüllt** |
| keine Samples verloren | **erfüllt** (0 verworfen, beide Geräte) |
| Capture nicht durch Rückstau beschädigt | **erfüllt** (Tiefe 1 von 64) |
| segmentierte Sitzung stabil | **erfüllt** (3 Segmente, eine Sitzung) |
| Segmentlücke beseitigt | **erfüllt** |
| Erkennungsqualität ≥ Baseline | **erfüllt** (16,7 % gegen 19,7 %) |
| Vorlauf funktioniert | **nicht belegt** |
| Langdiktat | offen |
| kein neuer Absturz | offen |
| Latenz akzeptabel | offen |
