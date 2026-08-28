# EXTRA_AUDIO_SOURCE — der Weg ist offen

Stand 28.08.2026. Auf **beiden** Geräten gemessen, mit einer bekannten
Testaufnahme statt Mikrofon.

## Die Frage

Nach P1 stand die Folgerung im Raum: eine eigene Aufnahmestrecke und der
Android-Erkenner können nicht gleichzeitig laufen, also müsse ein eigener
Erkenner den von Android ersetzen.

**Diese Folgerung war voreilig und ist zurückgenommen.** Ein offizieller
Weg blieb ungeprüft: `RecognizerIntent.EXTRA_AUDIO_SOURCE` nimmt ab
Android 13 einen bereits geöffneten Strom entgegen. Dann öffnet nur eine
Seite das Mikrofon.

## Der Aufbau

Damit das Ergebnis beweiskräftig ist, kam das Audio **nicht** vom Mikrofon:

```
TTS-Aufnahme (bekannter Text)
  → rohes PCM, 16000 Hz, 16 Bit, ein Kanal
  → ParcelFileDescriptor.createPipe()
  → Leseseite als EXTRA_AUDIO_SOURCE an den Erkenner
  → Schreibseite in Echtzeit befüllt, 2048 Bytes je Block
```

In Echtzeit geschrieben, nicht am Stück: ein Mikrofon liefert auch in
Häppchen, und ein Strom, der schneller kommt als die Wirklichkeit, wäre
kein ehrlicher Versuch.

Bezugstext: *„Guten Morgen, hier spricht Belkis. Das ist ein Test der
Spracherkennung."*

Kommt genau dieser Text zurück, hat der Erkenner den **Strom** gelesen —
das Mikrofon hörte in diesem Moment nur den stillen Raum.

## Das Ergebnis

| | A15 | S23 Ultra |
|---|---|---|
| `EXTRA_AUDIO_SOURCE` | **unterstützt** | **unterstützt** |
| gesendete Bytes | 137600 | 137600 |
| bereit nach | 832 ms | 421 ms |
| Sprache erkannt nach | — | 1119 ms |
| erkannter Text | vollständig | vollständig |
| **Wortfehlerrate** | **9,1 %** | **9,1 %** |
| `EXTRA_SEGMENTED_SESSION` | **funktioniert** | **funktioniert** |
| `onSegmentResults` | 1 Segment, voller Text | 1 Segment, voller Text |

Erkannt wurde auf beiden Geräten:

```
guten Morgen hier spricht belgis das ist ein Test der Spracherkennung
```

Der einzige Fehler ist `Belkis → belgis` — ein Eigenname, genau das Muster
aus P1.

**Damit ist bewiesen: Nibra kann das Mikrofon selbst besitzen und den
Android-Erkenner behalten.** Eigenes PCM, Vorlauf, eigene Pegelmessung und
ein Audioarchiv für den Messbetrieb sind möglich, ohne ein eigenes Modell
einzubauen.

## Das leere Endergebnis ist systematisch

Auch hier kam `onResults` mit **null** Lesarten. Der vollständige Text
stand ausschließlich in den Zwischenständen.

Das ist derselbe Befund wie beim Diktat über das Mikrofon — und er tritt
jetzt in einem Aufbau auf, in dem Nibra das Audio vollständig kontrolliert.
**Es liegt also nicht an der Aufnahme, sondern am Erkennungsdienst.**

**Ein Weg daran vorbei zeichnet sich ab:** Mit
`EXTRA_SEGMENTED_SESSION = EXTRA_AUDIO_SOURCE` liefert
`onSegmentResults()` den vollständigen Text — an `onResults` vorbei. Das
wäre ein Ergebnis, das der Dienst ausdrücklich meldet, statt eines
geretteten Zwischenstands.

## Fehler in der ersten Auswertung

Der erste Durchlauf urteilte **„NICHT UNTERSTÜTZT"** — weil er nur auf
`onResults` sah und dort nichts fand. Dabei hatte der Erkenner den Strom
vollständig erkannt und Wort für Wort gemeldet.

Das ist die zweite Auswertung an einem Tag, die am leeren Endergebnis
gescheitert wäre. Wer `onResults` für den einzigen Beleg hält, verwirft
ein funktionierendes Verfahren. Berichtigt; die Auswertung nimmt jetzt den
besten Text und weist aus, woher er stammt.

## Was noch nicht belegt ist

- Ein Durchgang je Gerät, mit **einer** TTS-Stimme. Die 9,1 % sind nicht
  mit den 19,7–24,2 % aus P1 vergleichbar: anderer Text, andere Stimme,
  kein Raumhall.
- Ob `AudioRecord` → Pipe → Erkenner ebenso funktioniert. Das ist der
  nächste Versuch; hier kam das PCM aus einer Datei.
- Ob mehrere Sätze mit Pausen mehrere `onSegmentResults` liefern und ob
  die Neustartlücke von ~280 ms damit verschwindet.
