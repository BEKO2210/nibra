# Messsystem-Härtung

Nibra baut nicht nur eine Diktierstrecke, sondern auch das Messmittel, mit
dem über sie entschieden wird. Dieses Messmittel hat in kurzer Zeit elf
Mal etwas Falsches behauptet — jedes Mal glaubwürdig, jedes Mal mit einer
Zahl belegt, die es selbst erzeugt hatte.

Das ist kein Anlass für eine Fehlerliste, sondern für eine Regel:

> **Messcode ist Produktionscode für unsere Entscheidungen.**
> Wenn er lügt, bauen wir das Falsche und merken es nie.

Deshalb steht hier jede der sieben Fehlmessungen mit dem, was aus ihr
folgte: was gemessen wurde, warum die Aussage falsch war, was sie gefunden
hat, wie die Auswertung jetzt abgesichert ist, und welcher Test die
Wiederholung verhindert.

Vier Familien lassen sich erkennen — und drei der sieben Fehler gehören zur
selben:

| Familie | Nummern | Kern |
|---|---|---|
| **Zu enge Quelle** | 1 | Nur an einer Stelle nachgesehen, obwohl es mehrere gibt |
| **Vermischte Bedeutung** | 2 | Dinge zusammengezählt, die verschiedene Sachen bedeuten |
| **Rand für Rate gehalten** | 3, 6, 7 | Ein fester Versatz an den Rändern als laufende Abweichung gelesen |
| **Kriterium ohne Kraft** | 4, 5 | Ein Prüfstein, der in keinem Fall anschlägt, prüft nichts |

---

## 1 — „`EXTRA_AUDIO_SOURCE` wird nicht unterstützt"

**Gemessen:** ob nach dem Einspeisen bekannter Aufnahme Text zurückkommt.
Geprüft wurde allein `onResults`.

**Warum falsch:** bei gesetzter Segmentsitzung liefert der Erkenner den Text
über `onSegmentResults`; `onResults` bleibt leer. Die Stelle, an der
nachgesehen wurde, war schlicht die falsche. Der Weg funktionierte die ganze
Zeit — die daraus gezogene Folgerung („wir brauchen einen ganz anderen
Ansatz") hätte eine Architektur umgeworfen.

**Gefunden durch:** die ausdrückliche Anweisung, die Schlussfolgerung
zurückzunehmen und den Weg erst zu prüfen, statt ihn zu verwerfen.

**Jetzt abgesichert:** `Ergebniswahl` entscheidet an **einer** Stelle in
fester Rangfolge — Segment, sonst Endergebnis, sonst geretteter
Zwischenstand. Dazu `wurdeUnterstuetzt`, das ein leeres `onResults`
ausdrücklich **nicht** als fehlende Unterstützung wertet.

**Regressionstest:** `segmente gehen vor endergebnis und zwischenstand`,
`ohne segmente zaehlt das endergebnis`, `bei leerem endergebnis rettet der
letzte zwischenstand`, `leeres endergebnis widerlegt die unterstuetzung
nicht`.

---

## 2 — „133 % Wortfehlerrate"

**Gemessen:** die Wortfehlerrate gegen einen Bezugstext.

**Warum falsch:** alle Lesarten des Erkenners wurden mit `|` aneinander­gehängt
und gemeinsam gewertet. Alternativen deuten aber **dieselbe** Stelle, nicht
nacheinander Gesprochenes. Jede zusätzliche Lesart zählte damit als lauter
zusätzliche falsche Wörter. Ein fast richtig erkannter Satz sah aus wie ein
Totalausfall.

**Gefunden durch:** die Unmöglichkeit des Werts selbst — über 100 % Fehler
bei sichtbar brauchbarem Text.

**Jetzt abgesichert:** je Segment wird nur die beste Lesart genommen.
Mehrere **Segmente** werden aneinandergehängt, mehrere **Alternativen** nie.

**Regressionstest:** `verkettete alternativen treiben die fehlerrate ueber
hundert prozent` — der Test hält den falschen Fall fest, damit die
Unterscheidung nicht wieder verschwindet. Er hat sich beim Schreiben selbst
korrigiert: die erwartete Zahl war bei zwei Bezugswörtern exakt 100 %, nicht
mehr. Nachgerechnet und die Erwartung berichtigt, statt sie passend zu
biegen.

---

## 3 — „ES FEHLT TON" bei −70 ms

**Gemessen:** ob Ton verloren ging, aus dem Unterschied zwischen verstrichener
Zeit und der Zeit, die in den Abtastwerten steckt.

**Warum falsch:** geprüft wurde der **Betrag** der Abweichung statt ihres
**Vorzeichens**. Positiv heißt: es kam weniger Ton an als Zeit verging — das
wäre Verlust. Negativ heißt: es kam mehr Ton an als Zeit verging — das ist
Ungenauigkeit an den Rändern und kann gar kein Verlust sein. Gemeldet wurde
Verlust bei **null** verworfenen Blöcken.

**Gefunden durch:** der Widerspruch in derselben Zeile — „ES FEHLT TON"
neben „verworfene Blöcke 0".

**Jetzt abgesichert:** `Tonstrecke.istLueckenlos` ist eine reine Funktion und
prüft ausdrücklich nur die positive Richtung.

**Regressionstest:** `nur ein positiver rueckstand ist verlust`.

---

## 4 — „Der Vorlauf ist getestet"

**Gemessen:** ein Durchgang mit und einer ohne Vorlaufpuffer, bei 500 ms
verspätetem Start der Erkennung.

**Warum falsch:** bei 500 ms Verzögerung geht auch **ohne** Vorlauf nichts
verloren. Beide Durchgänge lieferten denselben vollständigen Text. Daraus
folgt nichts — weder für noch gegen den Vorlauf. Berichtet wurde es
trotzdem als Prüfung.

**Gefunden durch:** die Nachfrage, was der Versuch eigentlich gezeigt hat.

**Jetzt abgesichert:** gestaffelte Verzögerungen von 0 bis 2500 ms. Zeigt
sich kein Unterschied, sagt das Urteil ausdrücklich **„nicht belegt, nur
unschädlich"** statt Erfolg.

**Regressionstest:** kein Modultest, sondern der Aufbau des Versuchs
(`Vorlaufversuch`) und sein Urteilstext, der den Fall „kein Unterschied"
eigens benennt.

---

## 5 — „Kein Durchgang hat den Anfang"

**Gemessen:** ob der Vorlauf den Wortanfang rettet, geprüft am Ankerwort
„Zitrone" am Anfang der Aufnahme.

**Warum falsch:** der Erkenner lieferte „Zitrone" in **keinem** Durchgang —
auch nicht bei 0 ms Verzögerung, wo nichts verloren gehen konnte. Das
Kriterium maß also nicht den Vorlauf, sondern die Erkennbarkeit eines
einzelnen Wortes. Es schlug in jedem Fall gleich aus und war damit blind für
den Unterschied, der in der Tabelle darüber bereits schwarz auf weiß stand:
ohne Vorlauf 1 Wort, mit Vorlauf 7.

**Gefunden durch:** der Widerspruch zwischen Urteil und eigener Tabelle.

**Jetzt abgesichert:** gemessen wird die Wortzahl, nicht ein einzelnes Wort.

**Regel daraus:** *Ein Kriterium, das in keinem Fall anschlägt, prüft
nichts.* Ein Prüfstein braucht mindestens einen Fall, in dem er anspricht,
und einen, in dem er schweigt.

---

## 6 — „15 261 ppm Drift"

**Gemessen:** die Abweichung der Abtastrate, aus gelesenen Abtastwerten
gegen verstrichene Zeit.

**Warum falsch:** die Uhr lief erst **nach** der ersten Lesung an — deren
Abtastwerte wurden aber schon gezählt. Der Zeitachse fehlte damit rund ein
Block, etwa 76 ms. Über fünf Sekunden gemessen sieht so ein fester Versatz
aus wie anderthalb Prozent Taktfehler; über eine Viertelstunde verschwindet
er im Rauschen.

Die naheliegende Reparatur — die Uhr an den Start der Aufnahme ziehen —
kehrte nur das Vorzeichen um: dann fällt die Anlaufzeit des Mikrofons in die
Messung, in der noch gar nichts kommt (−9 363 ppm und −26 569 ppm).
**Beide Ränder taugen nicht zum Messen einer Rate.**

**Gefunden durch:** den 5-Sekunden-Kontrollfall am Anfang des
Transportlaufs. Er hat abgebrochen, bevor 21 Minuten vermessen wurden.
Bemerkenswert: gerade weil er **kurz** ist, reagiert er auf einen festen
Randversatz am empfindlichsten — der lange Lauf hätte ihn verdünnt und
unauffällig durchgehen lassen.

**Jetzt abgesichert:** die Rate wird nur im eingeschwungenen Teil gemessen,
ab zwei Sekunden nach dem Start. Der Randversatz wird eigens ausgewiesen und
ausdrücklich **nicht** als Rate bezeichnet.

**Regressionstest:** `die uhr startet vor der ersten lesung` und `takt und
verlust lassen die raender aus`, beide mit Gegenprobe an der alten Fassung.

---

## 7 — „ES FEHLT TON, 149 ms" auf dem A15

**Gemessen:** Tonverlust, wie in Nummer 3 — aber über den **ganzen** Lauf
einschließlich der Ränder.

**Warum falsch:** derselbe Randversatz wie in Nummer 6, hier in der
Verlustrechnung. Gemeldet wurden 149 ms fehlender Ton bei null verworfenen
Blöcken und einer Ratenabweichung von −165 ppm im eingeschwungenen Teil —
das sind drei Millisekunden über achtzehn Sekunden.

**Gefunden durch:** derselbe Widerspruch wie bei Nummer 3, entdeckt beim
Prüfen der Korrektur zu Nummer 6.

**Jetzt abgesichert:** der Verlust wird vom Einschwungpunkt aus gerechnet.
Verloren ist Ton nur da, wo die Strecke ihn wegwerfen musste — und das steht
in `verworfeneBloecke`.

**Regressionstest:** `takt und verlust lassen die raender aus`.

---

---

## 10 — „Der Erkenner versteht den Prüfsatz kaum" (112 % Wortfehlerrate)

**Gemessen:** ALT gegen NEU am echten Mikrofon, zwölf Prüfsätze, 72 Diktate,
zwanzig Minuten Vorlesen.

**Warum falsch:** Der Bildschirm zeigte **nie** die zwölf Prüfsätze. Er las
seinen Text aus `Sprachlauf.bezugstextFuer(...)` -- dem fest eingebauten Satz
des alten Sprachlaufs --, während die Auswertung gegen die Liste im Versuch
rechnete. Zwei Kopien desselben Korpus, die auseinanderliefen.

Belkis hat zweiundsiebzig Mal korrekt vorgelesen, was dastand. Gemessen wurde
es gegen etwas anderes. Daher 112 % Fehlerrate und 11 % Trefferquote bei
gewöhnlichen Wörtern -- Zahlen, die kein Erkenner der Welt erzeugt.

**Gefunden durch:** die Unmöglichkeit der Zahlen selbst, und dann durch einen
Blick auf die einzelnen Diktate: alle 72 enthielten denselben Satz, egal
welcher Prüfsatz gerade dran war.

**Warum das die lehrreichste der Fehlmessungen ist.** Die neun davor lagen in
einer Rechnung -- ein Vorzeichen, ein Zeitraum, eine Schwelle. Diese lag in
der **Architektur der Messung**: zwei Stellen, die dasselbe meinten, ohne dass
irgendetwas sie aneinander band. Kein Einzeltest hätte sie gefunden, weil
jede Seite für sich richtig war.

**Jetzt abgesichert:** `Testfall(id, text, kategorie)` mit SHA-256 ist die
**einzige** Quelle. Die Anzeige liest `stand.testfall.text`, die Auswertung
bewertet dasselbe Objekt.

Dazu ein Abgleich zur Laufzeit **vor jedem Diktat**: der Versuch fragt die
Oberfläche, was sie wirklich zeigt -- zurückgelesen aus ihrem eigenen
Zustand, nicht aus der eigenen Vorlage. Bei abweichender Kennung oder
abweichendem Fingerabdruck bricht der Lauf ab, bevor Aufnahme oder Erkenner
starten. Der Bericht führt je Diktat Kennung, Abdruck, Bezugstext und
erkannten Text mit.

**Regressionstests:** `derselbe testfall geht durch`, `eine andere kennung
schlaegt an`, `gleiche kennung mit anderem text schlaegt an` -- der
heimtückische Fall, in dem die Kennung passt und der Inhalt nicht --,
`keine anzeige schlaegt an`, `verschiedene texte haben verschiedene
abdruecke`, `die kennungen im korpus sind eindeutig`.

**Regel daraus:** *Was an zwei Stellen dasselbe sein muss, darf nicht an zwei
Stellen stehen.* Eine Kopie ist nicht bequem, sondern gefährlich: sie läuft
auseinander, ohne dass ein Test anschlägt, und zeigt sich erst in Zahlen, die
niemand erklären kann.

---

## 11 — „Die Java-Halde pendelt sich ein" (sie leckte)

**Was gemeldet wurde.** Der Lauf über 900 Sitzungen am 29.08. urteilte für
die Java-Halde: *Boden steigt mit schrumpfenden Zuwächsen (4788 -> 5335 ->
5601, Zuwächse 547, 266) -- pendelt sich ein.* Also: kein Leck, Haken dran.

**Was tatsächlich war.** Dieselben Rohdaten über dreißig Fenster statt drei:
1,66 KB je Sitzung bei siebenfachem Standardfehler, und die zweite Hälfte
(2,18) so steil wie die erste (2,38). Der Boden stieg über den ganzen Lauf
von 4788 auf 6671 KB.

**Warum die Regel das nicht sah.** Sie schloss aus drei Böden, indem sie den
letzten Zuwachs mit dem ersten verglich: ist der zweite höchstens halb so
groß, gilt das als Einpendeln. Auf einem Sägezahn hängt der Boden eines
Fensters aber davon ab, wo die Bereinigung gerade stand. Fällt der erste
Zuwachs zufällig größer aus, sieht gleichmäßiges Wachstum aus wie
Abflachen — und die Regel entscheidet sich für die bequemere Lesart.

Drei Stützpunkte reichen für keine Aussage über einen Verlauf. Das war
dieselbe Erkenntnis wie bei Fehler 6 und 7, nur an anderer Stelle: eine
Kennzahl aus zu wenigen Punkten ist keine Kennzahl.

**Wie es abgesichert wurde.** Der Beurteiler rechnet jetzt über eine
Ausgleichsgerade durch mindestens acht Böden und verlangt zwei
Standardfehler — dieselbe Schwelle wie beim Ratenverlauf. Der Aufrufer
bildet zwanzig Fenster statt drei.

Zwei Fehler steckten dabei in der neuen Fassung selbst und fielen erst durch
die Prüfungen auf:

1. Streuen die Reste **nicht**, war der Standardfehler null und die
   Sicherheit wurde als 0 gemeldet. Eine schnurgerade steigende Reihe — der
   eindeutigste denkbare Leckbefund — kam damit als „ruhig" heraus.
2. Für „pendelt sich ein" wurde zusätzlich verlangt, dass die zweite Hälfte
   für sich genommen nicht mehr belegt ist. Eine glatte Wurzelkurve flacht
   deutlich ab, ist aber sauber gemessen und damit hoch belegt. Nach dieser
   Regel wäre nie ein Einpendeln erkannt worden.

**Gegenprobe.** Die dreißig tatsächlich gemessenen Böden liegen im Prüfsatz
und müssen als Leck herauskommen; daneben steht die alte Rechnung mit drei
Böden, die zeigt, dass sie „pendelt sich ein" gesagt hätte. Dazu eine
Wurzelkurve, die als Einpendeln gelten muss, und Rauschen ohne Anstieg, das
ruhig bleiben muss.

**Was offen bleibt.** Der Speicher wächst weiterhin, rund 3,4 KB je Sitzung
nach RSS. Die Ursache ist nicht untersucht. Der Fehler war die falsche
Entwarnung, nicht das Wachstum.

## Was daraus als Verfahren bleibt

1. **Jede entscheidungsrelevante Auswertung bekommt zuerst einen kleinen
   Kontrollfall mit bekanntem Ausgang.** Stimmt er nicht, wird gar nicht erst
   gemessen. Nummer 6 hat das an einem einzigen Tag bezahlt gemacht.
2. **Kurze Kontrollfälle sind empfindlicher als lange Läufe** — jedenfalls
   für feste Versätze. Beide werden gebraucht.
3. **Ein Kriterium braucht eine Gegenprobe.** Zu jeder Regel gehört ein Fall,
   in dem sie durchfällt; sonst ist nicht zu erkennen, ob sie überhaupt etwas
   prüft.
4. **Fehlt eine Messung, heißt das `null` und nie `0`.** Null Verzug, null
   Rechenzeit und null Fehlerrate sind hervorragende Ergebnisse und das
   genaue Gegenteil von „nicht gemessen".
5. **Beim Vorzeichen und beim Zeitraum genau hinsehen.** Drei der sieben
   Fehler stammen daher: ein Wert war nicht zu groß, sondern in die falsche
   Richtung oder über den falschen Abschnitt gerechnet.
6. **Gemessen wird die Größe, nicht die Ursache.** Was die Ratenabweichung
   verursacht — Audiotakt, Treiber, Umtastung, Rechenzeiteinteilung, unsere
   eigene Uhr — ist nicht bestimmt und wird nicht behauptet.
