# Übergabe: der offene Diktatfehler

Stand 28.08.2026, Fassung 1.8. Für den, der hier weitermacht.

## Was funktioniert

- **A15 (SM-A156B):** Diktat und Dauerdiktat laufen durch. Vollständige
  Kette gemessen, `stopListening` wird in ~100 ms beantwortet.
- Auslieferung ohne `INTERNET`, an der gebauten APK geprüft.
- 88 Tests offline, 100 Forschung, alle grün. CI auf GitHub grün.

## Was **nicht** funktioniert

**S23 Ultra, Dauerdiktat:** Sätze kommen an, dann bricht es ab. Zuletzt
gemessen (Fassung 1.7, vor der Rücknahme):

```
9441 ms  -> startListening   (Schein 6)
9477 ms  <- onResults        36 ms später, ohne onReadyForSpeech dazwischen
9490 ms  -> startListening   (Schein 7)
9608 ms  <- onError code=5   ERROR_CLIENT
```

Der Erkenner wird zwischen zwei Sätzen **übernommen** statt zerstört
(`Erkennerhalter.leihe(vorrang = true)` bei gleichem Zweck). Seine
Warteschlange liefert dann offenbar ein Ergebnis der **alten** Sitzung an
den neuen Zuhörer nach. Nibra hält das für ein Satzende, startet sofort
wieder — und der Doppelstart bricht mit `ERROR_CLIENT` ab.

## Was ich versucht habe und warum es falsch war

Ein Filter, der Ergebnisse **ohne vorheriges `onReadyForSpeech`** als
Nachzügler verwarf. Am Gerät hat er echte Sätze weggeworfen:

```
2287 ms  -> stopListening
2416 ms  <- onResults  lesarten=2      der echte Satz
2417 ms  <- onResults verworfen
```

**Die Annahme war falsch:** Nach `stopListening` liefert der Erkenner sein
Ergebnis, ohne noch einmal bereit zu melden. `onReadyForSpeech` taugt
nicht als Unterscheidungsmerkmal. Zurückgenommen in 1.8; der Kommentar
steht an der Stelle im Code.

## Wo ich als Nächstes ansetzen würde

Der Kern ist die Übernahme des warmen Erkenners zwischen zwei Sätzen.
Drei Wege, keiner geprüft:

1. **Pro Satz ein frischer Erkenner** — also die Übernahme aufgeben und
   in `Erkennerhalter.leihe` auch bei gleichem Zweck zerstören. Kostet die
   Lücke zwischen den Sätzen (deswegen gab es die Übernahme), ist aber der
   direkteste Weg zur Klärung: verschwindet der Fehler damit, ist die
   Ursache bestätigt.
2. **Nach `stopListening` erst das Ergebnis abwarten, dann neu starten.**
   Aktuell startet die Schleife den nächsten Satz, sobald der Fluss
   schließt. Ein ausdrückliches Warten auf `onResults`/`onError` **und**
   eine kurze Pause davor könnte die Nachlieferung ins Leere laufen lassen.
3. **Sitzungen unterscheiden** — aber an einem tauglichen Merkmal, nicht
   an `onReadyForSpeech`. Denkbar: den Zuhörer je Sitzung neu erzeugen und
   im alten Zuhörer alles verwerfen, sobald er abgelöst wurde. Das ist
   dasselbe Muster wie die Leihscheine im `Erkennerhalter` und
   wahrscheinlich der sauberste Weg.

## Wo die Diagnose steht

```
adb logcat -s NibraDiktat
```

`Erkennungsprotokoll` schreibt jeden Rückruf mit monotonem Zeitstempel,
jede Ausleihe des Erkenners mit Zweck und Leihschein, jeden
Zustandswechsel. **Niemals gesprochenen Inhalt** — nur Namen, Zeiten,
Fehlercodes, Anzahlen.

Ohne dieses Protokoll ist der Fehler nicht zu finden; er hängt an der
Reihenfolge von Rückrufen im Millisekundenbereich.

## Fehlercodes, die hier vorkamen

| Code | Bedeutung | in Nibra |
|---|---|---|
| 5 | `ERROR_CLIENT` | `KEIN_ERGEBNIS` |
| 7 | `ERROR_NO_MATCH` | `NICHTS_VERSTANDEN` |
| 8 | `ERROR_RECOGNIZER_BUSY` | `KEIN_ERGEBNIS` |
| 11 | `ERROR_SERVER_DISCONNECTED` | `KEIN_ERGEBNIS` |
| 12, 13 | Sprache fehlt | `SPRACHE_NICHT_AUF_GERAET` |

Vorübergehende Störungen dürfen **nicht** als „dieses Gerät kann keine
Sprache erkennen" erscheinen — diese Meldung stand nach zwei erkannten
Sätzen auf dem Bildschirm und war beweisbar falsch.

## Gerätebesonderheiten

- **S23 Ultra:** `checkRecognitionSupport` antwortet **nie** — weder
  Ergebnis noch Fehler. Die Sprachliste bleibt deshalb leer. Ursache
  unbekannt. Das Diktat ist davon seit 1.3 nicht mehr betroffen (die
  Abfrage gibt den Erkenner in einem `finally` zurück).
- **S23 Ultra:** Der System-Intelligence-Dienst (`com.google.android.as`)
  kann festfahren und liefert dann dauerhaft `RECORDER_BUSY`.
  `adb shell am force-stop com.google.android.as` löst das.
- **Seit Android 11** braucht die App `<queries>` für
  `android.speech.RecognitionService`. Ohne das blockiert der `AppsFilter`
  die Verbindung kommentarlos und es kommt `BUSY`. Steht seit 1.6 im
  Manifest — ein Fund, der von Anfang an fehlte.

## Nicht belegt

- Ob die Übernahme des warmen Erkenners wirklich die Ursache ist.
- Warum `checkRecognitionSupport` auf dem S23 Ultra schweigt.
- Jede Aussage über Erkennungsqualität. Es gibt bis heute keinen gültigen
  Messlauf mit Sprache; siehe `ROADMAP.md`, Phase P1.
