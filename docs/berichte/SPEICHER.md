# Speicherverhalten über lange Läufe

Stand 29.08.2026. Alle Zahlen vom Emulator (Google sdk_gphone64_x86_64,
Android 14). **Emulatorzahlen sagen nichts über echte Geräte-Mikrofone**,
für die Frage nach dem Speicher spielt das Mikrofon aber keine Rolle: der
Ton kommt aus einer hinterlegten Aufnahme.

## Wie es anfing

Der Sitzungsdauerlauf über 900 Sitzungen meldete anhaltendes Wachstum:
Java-Halde 1,66 KB je Sitzung, RSS 3,37 KB je Sitzung, ohne Abflachen. Der
automatische Beurteiler hatte für die Java-Halde zunächst „pendelt sich
ein" gesagt — das war [ein Messfehler](../messungen/MESSSYSTEM.md), er
urteilte aus drei Stützpunkten.

Damit stand fest, **dass** etwas hängen bleibt. Nicht, was.

## ARM A — normal, ohne erzwungene Bereinigung

300 Sitzungen. „Lebend" ist hier eine **Obergrenze**: es steht nur fest,
dass noch nicht eingesammelt wurde.

| | Bestand | ältester | Median | P95 | >100 | >200 |
|---|---|---|---|---|---|---|
| SpeechRecognizer | 99 | 100 | 51 | 96 | 0 | 0 |
| Zuhörer | 43 | 96 | 23 | 42 | 0 | 0 |
| Rohr lesen | 44 | 96 | 24 | 43 | 0 | 0 |
| Rohr schreiben | 44 | 96 | 24 | 43 | 0 | 0 |
| Schreibfaden | 44 | 96 | 24 | 43 | 0 | 0 |
| Absicht | 44 | 96 | 24 | 43 | 0 | 0 |

Speicher: Java 4,67 KB/Sitzung bei 1,6 Standardfehlern, nativ 0,87 bei 2,0,
RSS 3,50 bei 1,7 — **alle unter der Schwelle**. Große Sägezähne, weil nichts
erzwungen wird.

Alle fünf **eigenen** Objektarten werden von der gewöhnlichen Bereinigung
geholt: begrenzter Rückstau, ältester höchstens 98 Sitzungen.

## ARM B — Bereinigung an den Haltepunkten erzwungen

**Nicht mit Arm A vermischen.** Andere Bedingungen, andere Frage.

### 300 Sitzungen

Der Bestand wächst um exakt 25 je Haltepunkt — **jede Sitzung lässt ihren
Erkenner zurück** — bis 158 bei Sitzung 175. Dann gibt das System 140 Stück
auf einmal frei, und derselbe Aufbau beginnt von vorn.

Zusammenhang der Zahl lebender Erkenner mit dem Speicher:

| | r |
|---|---|
| Java-Halde | **+0,97** |
| native Halde | **+0,95** |
| RSS | −0,15 |

Beim Einbruch von 158 auf 18 fallen 84 KB Java und 92 KB nativ: **0,60 bzw.
0,66 KB je Erkenner**. Innerhalb eines Zahns steigt die Java-Halde um
0,78 KB je Sitzung — bei einem Erkenner je Sitzung passt das zusammen.

Ein Zusammenhang ist keine Ursache. Aber zusammen mit dem Freigabesprung
und dem Haltepfad ist die Zuordnung eindeutig.

### 900 Sitzungen

Sechs Freigabezyklen. Alter des ältesten Überlebenden an den zwölf
Haltepunkten:

```
18 → 93 → 3 → 78 → 153 → 69 → 144 → 53 → 128 → 39 → 114 → 24
```

**Nie über 153.** Die Spalte „älter als 200 Sitzungen" ist an jedem
Haltepunkt leer — über 900 Sitzungen.

## Der Haltepfad

Abzug der Halde bei Sitzung 300, ausgewertet mit
[`scripts/haldenpfade.py`](../../scripts/haldenpfade.py):

```
JNI global  →  android.speech.SpeechRecognizer$2
                 : android.speech.IRecognitionServiceManagerCallback$Stub
                 : android.os.Binder
               .this$0  →  android.speech.SpeechRecognizer
```

120 von 120 Instanzen so verankert, alle 120 als **JNI global**. Die
einzigen Verweise auf die Erkenner kommen aus `SpeechRecognizer$1` und
`$2` — inneren Klassen des Erkenners selbst.

Nicht beteiligt: unser Zuhörer, eine statische Sammlung, eine Koroutine,
Compose-Zustand, irgendein Feld unseres Codes.

Eine JNI-globale Wurzel ist genau das, womit die native Binder-Laufzeit ein
Stub-Objekt festhält, solange ein fremder Prozess eine Referenz darauf
hält. `destroy()` beendet das nicht sofort; der Erkennerdienst gibt die
Referenzen stapelweise frei. Das erklärt auch, warum die erzwungene
Bereinigung sie nicht holt: eine Wurzel ist eine Wurzel.

## Was die Messung des Messplatzes selbst verursacht hat

Der Sitzungsdauerlauf legt je Sitzung einen Datensatz ab, darin eine Karte
der Dateizeiger nach Art. Zwei Arme, sonst identischer Code:

| | ohne Buchführung | mit Buchführung |
|---|---|---|
| Java-Halde | +0,33 KB/Sitzung | **+1,34** |
| Java 0 → 300 | +172 KB | **+476 KB** |

**1,01 der 1,34 KB je Sitzung waren die Messung.** Der Wert 1,34 liegt nahe
an den 1,66 des ursprünglichen 900er-Laufs — ein großer Teil des dortigen
Java-Wachstums war der Messplatz und nicht die Diktierstrecke.

Der ursprüngliche Messwert bleibt stehen. Seine Deutung ändert sich.

## Einordnung

### SpeechRecognizer: B — BEGRENZTER RÜCKSTAU

Belegt über 900 Sitzungen und sechs Freigabezyklen: Höchstalter nie über
153, nichts älter als 200, Bestandsspitze rund 160, danach Massenfreigabe.
Kein unbegrenztes Wachstum.

Die Ursache liegt **außerhalb unseres Codes**, in der Binder-Schicht des
Systems. Es gibt nichts zu reparieren, was uns gehört.

### Java- und native Halde: eingependelt

Über 900 Sitzungen mit Zwang: Java 0,16 → 0,05 KB je Sitzung, nativ
0,28 → 0,04. Über die Haltepunkte ab Sitzung 150 gerechnet: +0,09 bzw.
+0,11 KB je Sitzung.

### RSS: D — UNGEKLÄRT

Hier passt keine der ersten drei Kategorien, und das wird nicht
zurechtgebogen.

Der Anstieg liegt zum größten Teil im Anlauf: 189 692 KB bei Sitzung 0,
193 112 bei Sitzung 75. Danach driftet er langsam weiter auf 193 908 bei
Sitzung 900 — über die Haltepunkte ab 150 gerechnet 2,48 KB je Sitzung bei
**2,4 Standardfehlern**, also knapp über der Schwelle, und ein einzelner
Ausreißer (196 288 bei Sitzung 750) trägt die Steigung mit. Ohne ihn liegt
die Drift bei rund 1,6 KB je Sitzung.

Was fehlt, um das zu klären: die Erkenner erklären es **nicht** — ihr
Speicher steckt in Java- und nativer Halde, und die pendeln sich ein. RSS
umfasst mehr: Zuordnungen des Betreibers, Code, Fadenstapel, Grafikpuffer.
Welcher Teil davon wächst, ist nicht gemessen.

Größenordnung: rund 1,2 MB über 900 Sitzungen nach dem Anlauf. Das ist
klein. **Klein ist kein Befund.**

## Was das für die App heißt

Dateizeiger stehen bei 127, Fäden bei 18, beide über 900 Sitzungen
unverändert. Die Sitzungsdauer bleibt bei 2620 ms, die Startlatenz bei 9 ms
— nichts wird langsamer.

900 Diktate ohne Prozessneustart sind ein Vielfaches dessen, was im Gebrauch
vorkommt; Android beendet Hintergrundprozesse ohnehin regelmäßig. Für die
Auslieferung ist daraus kein Hindernis abzuleiten.

Das ist eine Einordnung der Größenordnung, keine Entwarnung für den
RSS-Rest. Der bleibt offen.

## Nächster Schritt

Genau einer: den RSS-Anteil aufschlüsseln — `/proc/self/smaps` je
Zuordnungsart an denselben Haltepunkten, damit sichtbar wird, welcher
Bereich wächst. Erst danach lässt sich sagen, ob dort überhaupt etwas ist,
das jemandem gehört.

Kein Eingriff in die Diktierstrecke vorher. Es gibt bisher nichts, worauf
ein Eingriff zielen könnte.
