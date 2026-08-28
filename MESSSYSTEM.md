# Messsystem-Härtung

Nibra baut nicht nur eine Diktierstrecke, sondern auch das Messmittel, mit
dem über sie entschieden wird. Dieses Messmittel hat in kurzer Zeit sieben
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
