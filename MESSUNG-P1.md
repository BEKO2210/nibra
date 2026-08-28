# P1 — Der erste gültige Messwert von Nibra

Stand 28.08.2026, Fassung 2.0. Belkis las auf beiden Geräten denselben
Bezugstext (66 Wörter) zweimal vor: einmal mit dem Erkenner allein, einmal
mit einer eigenen Aufnahme daneben.

Rohberichte: `messungen/mess2-a15.txt`, `messungen/mess2-s23.txt`.

## Erkennungsqualität — Erkenner allein

| | A15 (SM-A156B) | S23 Ultra (SM-S918B) |
|---|---|---|
| Bezugsworte | 66 | 66 |
| erkannte Worte | 63 | 66 |
| wörtlich getroffen | 51 | **54** |
| ersetzt | 11 | 11 |
| ausgelassen | 4 | 1 |
| zusätzlich | 1 | 1 |
| **Wortfehlerrate** | **24,2 %** | **19,7 %** |
| **Trefferquote** | **77,3 %** | **81,8 %** |

Das S23 Ultra erkennt besser. Ob das am Mikrofon, an der Audiokette oder
an der Sprechweise in diesem einen Durchgang lag, ist **nicht belegt** —
es ist ein Durchgang je Gerät.

### Woran die Erkennung scheitert

Auf **beiden** Geräten dieselben Stellen — und es sind fast ausschließlich
Eigennamen:

```
~ belkis    -> bergs
~ aslani    -> des / lanias
~ nibra     -> nebra
~ richte    -> riecht
~ doktor    -> dr
~ weinreich -> weinrich
~ rechnung  -> rechte
```

Der Fließtext kommt fast vollständig durch. **Eigennamen sind der
Schwachpunkt**, nicht die Sprache an sich. Das ist eine Aussage, auf der
sich aufbauen lässt: ein persönliches Wörterbuch würde hier mehr bringen
als jeder Wechsel des Erkenners.

Ein Teil der gemeldeten Fehler ist zudem Schreibweise, kein Hörfehler:
`vierzehn Uhr dreißig` gegen `14:30`, `doktor` gegen `dr`. Die
Wortfehlerrate ist dadurch nach oben verzerrt; die **echte** Hörleistung
liegt besser als 19,7 %.

## Nebenlauf — das Ergebnis ist eindeutig

| | A15 | S23 Ultra |
|---|---|---|
| Wortfehlerrate allein | 24,2 % | 19,7 % |
| Wortfehlerrate nebenläufig | **100 %** | **100 %** |
| erkannte Worte nebenläufig | **0** | **0** |

Im zweiten Lauf, mit eigener `AudioRecord`-Aufnahme daneben, kam vom
Erkenner **überhaupt nichts**:

```
bereit           -
Sprache beginnt  -
Ergebnis         -
Fehler           keiner
```

Kein `onReadyForSpeech`, kein Fehler, keine Meldung. Der Erkenner
startete nicht einmal.

**Die eigene Aufnahme lief dabei einwandfrei:**

```
A15   1814400 Abtastwerte (37800 ms)   Verlust -10 ms gegen die Uhr
S23   1820160 Abtastwerte (37920 ms)   Verlust  -3 ms gegen die Uhr
```

Lückenlos, ohne Übersteuerung, mit klarem Sprachpegel (A15 Effektivwert
142, S23 1055 im Sprachabschnitt gegen 8 bzw. 47 in der Stille).

**Damit ist die Frage aus P2 beantwortet, und zwar negativ:** Eine eigene
Aufnahmestrecke und der `SpeechRecognizer` können auf diesen Geräten
**nicht gleichzeitig** laufen. Wer das Mikrofon zuerst nimmt, bekommt es;
der andere bekommt nichts — und nicht einmal einen Fehler.

Das ist eine Architekturentscheidung, keine Feinheit: **Solange Nibra den
Android-Erkenner nutzt, kann sie den Ton nicht selbst mitschneiden.** Pre-Roll,
eigene Segmentierung und ein Messkorpus aus echten Aufnahmen sind damit
erst möglich, wenn ein eigener Erkenner den Android-Erkenner ersetzt --
nicht parallel dazu.

## Der Pegelsprung ist geklärt

Frühere Messung: der Pegel schien im Nebenlauf zehnfach anzusteigen.
Jetzt mit den vier Abschnitten sauber getrennt:

| Abschnitt | A15 | S23 Ultra |
|---|---|---|
| A still, Erkenner aus | 27,5 | 52,6 |
| B still, Erkenner **an** | 11,1 | 43,5 |
| C **Sprache**, Erkenner an | 142,2 | 1054,8 |
| D still, Erkenner aus | 8,0 | 47,3 |

Von A nach B **fällt** der Pegel sogar leicht. Der Erkenner schaltet den
Aufnahmepfad also **nicht** um; der frühere Anstieg kam schlicht von der
Stimme. Damit ist ein offener Punkt aus `MESSUNG-AUDIO.md` erledigt.

## Weitere Messwerte

| | A15 | S23 Ultra |
|---|---|---|
| bereit nach `startListening` | 354 ms | 226 ms |
| erster Zwischenstand | 4638 ms | 4375 ms |
| Ergebnis nach Sprachende | 92 ms | 34 ms |
| **Neustartlücke zwischen Sätzen** | **275 ms** | **291 ms** |

Die Neustartlücke ist das Fenster, in dem zwischen zwei Sätzen niemand
zuhört. Wer ohne Pause weiterspricht, verliert dort Wortanfänge.

## Was ausdrücklich nicht belegt ist

- Ein Durchgang je Gerät. Sprechweise und Umgebung schwanken; Unterschiede
  von wenigen Prozentpunkten tragen keine Entscheidung.
- Der Vergleich A15 gegen S23 Ultra. Dafür bräuchte es mehrere Durchgänge.
- Ob das leere Endergebnis (siehe unten) die Rate beeinflusst hat.

## Nebenbefund: das leere Endergebnis

In beiden Läufen lieferte der Erkenner am Ende ein **leeres** Ergebnis
ohne Fehler; der erkannte Text stammt aus dem geretteten Zwischenstand.
Ohne diese Rettung wäre die Wortfehlerrate beide Male 100 % gewesen —
und das Diktat im Alltag verloren. Der Fix dazu steckt in Fassung 2.0.
