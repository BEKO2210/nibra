# Nibra: Zustand der neuen Aufnahme- und Erkennungsstrecke

Stand 29.08.2026, 01:40. **Keine Promotion-Bewertung** — dieser Bericht
beantwortet nur: ist die Architektur technisch belastbar genug, um als
Produktionskandidat weiterzugehen?

**Geprüfte Geräte:** Samsung A15 (Android 16), Google Pixel 9 (Android 17).
Frühere Läufe zusätzlich auf Samsung S23 Ultra (Android 16) und Tab S9 Ultra;
beide stehen nicht mehr zur Verfügung.

Jede Aussage gilt **für die geprüften Geräte**, nicht für Android allgemein.

---

## BEWIESEN

**Transport ohne Datenverlust — 15 Minuten.**
`AudioRecord → Vorlauf → Warteschlange → Rohr → SpeechRecognizer`.
Über 60, 300 und 900 Sekunden, S23 und A15:
Producer und Consumer stimmen auf das Byte überein (28 794 880 Bytes bei
900 s, beidseitig identisch), 0 verworfene Blöcke, 0 Lesefehler,
Warteschlange nie über 1 von 64, Rechenzeit 2,6–5,0 % eines Kerns.
Formulierung ausdrücklich: **15 Minuten belegt**, nicht „unbegrenzt".

**Keine wandernde Ratenabweichung.**
Über 90 Zeitfenster im 15-Minuten-Lauf: erste gegen zweite Hälfte
unterscheiden sich um 13 ppm (S23) und 64 ppm (A15). Die Schwankung der
Einzelfenster (±2000 ppm) ist Blockquantisierung — jede Stichprobe fällt
auf eine Blockgrenze, ein Block ist 64 ms.
**Ursache der verbleibenden Abweichung ist nicht bestimmt** und wird nicht
behauptet.

**Der Vorlaufpuffer rettet den Wortanfang.**
Drei Geräte, zwei Hersteller, zwei Android-Versionen:

| Verzögerung | ohne Vorlauf | mit Vorlauf |
|---|---|---|
| 1500 ms (S23/A15) | 1 Wort | 7 Wörter |
| 2500 ms (S23/A15) | 0 Wörter | 7 Wörter |
| 1500 ms (Pixel) | 3 Wörter | 11 Wörter |
| 2500 ms (Pixel) | 0 Wörter | 11 Wörter |

Bytefolge in allen Durchgängen korrekt: kein Doppel, keine Lücke, keine
Vertauschung.

**Sitzungsdauer.** Eine Sitzung trägt 15 Minuten (S23, A15) — beim letzten
Segment lief sie noch, als die Einspeisung endete. 219 Segmente, größte
Lücke 4,28 s = genau die Länge der eingespeisten Aufnahme.

**Erholung nach Störung — 15 Fälle, A15 und Pixel 9.**
Nach **jedem** Fall liefert ein frisches Diktat wieder Text, ohne
App-Neustart. Null Abstürze, null Rückrufe nach dem Ende.
Darunter: ordentliches Rohrende, cancel, destroy, sterbender Schreiber,
geschlossene Leseseite, doppeltes startListening, Aufnahme stoppt mit
offenem Rohr, Neuaufbau der Oberfläche, Hintergrund und zurück,
Start→sofort Stop, cancel→sofort Neustart, Rohrende während der Erkenner
rechnet, destroy aus dem Rückruf heraus, Aufnahme nicht anlegbar.
Bildschirm aus/an: auf S23 und A15 belegt (Segmente kamen während der
dunklen Phase), auf dem Pixel **nicht gemessen**.

**300 aufeinanderfolgende Sitzungen, A15 und Pixel 9.**
300 von 300 mit Text. Dateizeiger ohne Wachstum (185 bzw. 159, Mitte wie
Ende), Fäden auf Plateau, keine Verlangsamung: Startlatenz, Sitzungsdauer
und Aufräumdauer über 300 Sitzungen unverändert. Ohne Neustart, ohne
Hintergrund-Trick, ohne erzwungenes Aufräumen.

**Verzug, 20 von 20 Läufen je Gerät.**

| | A15 P50 | Pixel P50 |
|---|---|---|
| t0→t1 Aufnahme bereit | 288 ms | 192 ms |
| t2→t4 Erkenner bereit | 17 ms | 19 ms |
| t2→t5 erster Text sichtbar | 1116 ms | 654 ms |
| t2→t6 erstes Segment | 3432 ms | 6486 ms |
| t7→t8 Sprachende bis Text | 73 ms | 457 ms |
| t7→t9 bis alles frei | 76 ms | 459 ms |

t9 misst echtes Aufräumen: die Freigabe wird abgewartet, nicht nur
angestoßen.

**Datenschutz.** Die Auslieferungsausprägung kennt weder INTERNET noch
ACCESS_NETWORK_STATE; ein Gradle-Tor prüft das bei jedem Bau und schlägt
auch fehl, wenn es kein Manifest findet. Ton wird im Arbeitsspeicher
verarbeitet und verworfen; nichts wird archiviert. Das Diagnoseprotokoll
schreibt nur Rückrufnamen, Zeiten, Fehlercodes und Zahlen — nie Inhalt.
Eine Bauart-Regel hält das fest.

---

## WIDERLEGT

**„Der Erkenner unterstützt `EXTRA_AUDIO_SOURCE` nicht."** Er tut es, auf
allen geprüften Geräten. Der erste Befund entstand, weil nur `onResults`
geprüft wurde — der Text kommt bei gesetzter Segmentsitzung über
`onSegmentResults`.

**„Die Dateizeiger wachsen je Diktat."** Sie wachsen beim einmaligen Aufbau
der Dienstverbindung und erreichen nach etwa 40 Sitzungen ein Plateau. Über
300 Sitzungen kein weiteres Wachstum.

**„Die Freigabe hängt am Wechsel in den Hintergrund."** Nein — über 300
Sitzungen ohne einen einzigen Hintergrundwechsel blieben die Zeiger
konstant.

**„Die Ratenabweichung wandert."** Diese Meldung kam aus einem
Sechzig-Sekunden-Lauf mit fünf Fenstern; der Unterschied lag bei 1,2
Standardfehlern, also im Rauschen.

---

## OFFEN

**Java-Halde: langsamer, gleichgerichteter Anstieg auf beiden Geräten.**
Boden je 50 Sitzungen:
A15 4607 → 5191 KB, Pixel 4473 → 4881 KB über 300 Sitzungen.
Rund 1,9 bzw. 1,4 KB je Sitzung. RSS wächst nicht mit, die native Halde
pendelt sich ein. **Ursache unbekannt.** Zu klären mit einem längeren Lauf
(1000 Sitzungen) und einem Haldenabbild, statt es jetzt Leck oder
Nicht-Leck zu nennen.

**Sitzungsgrenze über 15 Minuten hinaus.** Nicht geprüft. Eine Grenze
oberhalb der längsten geprüften Dauer ist nicht auszuschließen. Ein
Sitzungswechsel ohne hörbare Lücke ist damit weiterhin ungeplant.

**Bildschirm aus/an auf dem Pixel.** Nicht gemessen.

**Berechtigungsverlust während eines laufenden Diktats.** Der Messplatz
verweigert ohne Mikrofonrecht sauber den Start; wie sich die App verhält,
wenn das Recht **mitten im Diktat** entzogen wird, ist nicht geprüft.

**Erkennungsqualität.** Diese Nacht hat den Transport gemessen, nicht die
Güte. Wortfehlerraten liegen nur aus Einzelläufen vor.

**Transport auf A15 und Pixel.** Läuft zum Zeitpunkt dieses Berichts.

---

## GEÄNDERT

**Vorläufiger und bestätigter Text sind getrennt.** Vorher stand beides in
einer Farbe — eine stille Unwahrheit, weil der laufende Satz ständig
umgeschrieben wird und der fertige nie wieder. Der vorläufige Teil wird
**nicht abgedunkelt** (das läse sich als unwichtig), sondern trägt eine
Unterstreichung. Zwei getrennte Textknoten, damit TalkBack bei jeder
Zwischenmeldung nicht den ganzen Satz neu vorliest; kein `liveRegion`.

**Kein Diktat ohne Sprachpaket.** Fehlt es, beginnt Nibra gar nicht erst,
sondern meldet es und stößt das Laden an. Vorher lief das Diktat an, nahm
sechs Sekunden auf und merkte es erst hinterher — auf dem Pixel gar nicht,
weil der Dienst dort schlicht schweigt.

**Riegel gegen die Auslieferung der Forschungsausprägung.** Sie trägt
INTERNET, rohe Tonaufnahme und Messberichte. Ein Gradle-Riegel bricht jede
Aufgabe ab, die Forschung und Auslieferung verbindet.

**Ungenutztes Recht abgegeben.** `flagRetrieveInteractiveWindows` entfernt —
der Dienst ruft `getWindows()` nirgends auf.

**Offenlegung vervollständigt.** In Web- und Sonderfeldern fügt der Dienst
über die Zwischenablage ein und überschreibt deren Inhalt. Der
Aufklärungstext sagte das nicht. Jetzt in sieben Sprachen.

**`isAccessibilityTool="false"`** steht ausdrücklich statt zu fehlen.

**Messsystem gehärtet.** Neun Fehlmessungen gefunden, jede mit Kontrollfall
abgesichert — siehe `MESSSYSTEM.md`.

---

## NÄCHSTER SCHRITT

1. Transportläufe auf A15 und Pixel abschließen.
2. Biasing-A/B mit Eigennamen (Aufbau steht, Prüfsatz aufgenommen).
3. Erst danach Promotion-Bewertung.

**Nicht promoten**, solange die Erkennungsqualität nicht gemessen ist. Der
Transport ist belegt; ob der neue Weg **besser erkennt** als der heutige,
ist es nicht.

---

## Nachtrag 29.08.2026, 05:10 — lautlos gemessen

### BEWIESEN: die Segmentsitzung erkennt besser

Zwölf Prüfsätze, zwei Sprecher, identischer eingespeister Ton, A15.
Verglichen wurde **nur die Einstellung** des Erkenners: `EXTRA_SEGMENTED_SESSION`
aus gegen an. Kein Lautsprecher, kein Mikrofon, kein Raum -- und damit kein
Zufall, der sich in die Zahlen mischt.

| | ohne Segment | mit Segment | |
|---|---|---|---|
| rohe Wortfehlerrate | 34,2 % | **23,8 %** | −10,5 |
| bereinigte Wortfehlerrate | 29,3 % | **18,8 %** | −10,5 |
| Zeichenfehlerrate | 26,7 % | **15,4 %** | −11,3 |
| Auslassungen | 20,9 % | **9,4 %** | −11,4 |
| Einfügungen | 1,2 % | 1,2 % | ±0 |
| **Verlust am Satzanfang** | **1,67 Wörter** | **0,00** | −1,67 |
| Verlust am Satzende | 0,00 | 0,00 | ±0 |
| Trefferquote normale Wörter | 76 % | **87 %** | +11 |
| Trefferquote Eigennamen | 50 % | **75 %** | +25 |
| Trefferquote Fachbegriffe | 88 % | 88 % | ±0 |
| Trefferquote Zahlen | 75 % | 75 % | ±0 |
| erfundene Wörter je Lauf | 0,17 | 0,17 | ±0 |

Ausbeute 12 von 12 auf beiden Seiten.

**Der stärkste Einzelbefund:** ohne Segmentsitzung fehlen im Schnitt 1,67
Wörter am Satzanfang, mit Segmentsitzung keines. Das ist derselbe Verlust,
gegen den der Vorlaufpuffer gebaut ist -- hier zeigt er sich als
Erkennungsqualität statt als Bytezahl.

**Die Einfügungsrate bleibt gleich.** Der neue Weg erfindet nicht mehr dazu,
er lässt weniger weg. Das ist wichtig: eine Verbesserung, die durch mehr
Halluzinationen erkauft wäre, wäre keine.

**Was das nicht ist.** Gemessen wurde die Einstellung bei eingespeistem Ton.
Über den Mikrofonweg -- Raum, Entfernung, echte Stimme -- sagt diese Messung
**nichts**.

### OFFEN — reale Stimme später

Der End-to-End-Vergleich **alte Pipeline gegen neue Pipeline über das echte
Mikrofon** steht aus. Er verlangt hörbaren Ton und ist deshalb ausdrücklich
zurückgestellt.

Bis er gefahren ist, gilt: es ist **nicht belegt**, dass die neue Pipeline im
echten Mikrofonbetrieb besser erkennt. Der Aufbau dafür steht und ist gegen
versehentliches Starten verriegelt.

### GEÄNDERT — der Versuch darf nicht mehr laut werden

`Vergleichsversuch` stellte die Medienlautstärke selbst auf vier Fünftel des
Höchstwertes und hat damit ein stumm gestelltes Gerät zum Sprechen gebracht.
Eine Messung darf eine Einstellung des Nutzers nicht überschreiben.

Jetzt wird die Lautstärke nur noch **gelesen**; unter 40 Prozent bricht der
Versuch mit einer Erklärung ab, statt sie hochzudrehen. Und hörbares
Abspielen verlangt eine ausdrückliche Freigabe (`--ez tonErlaubt true`).

Zwei Bauart-Regeln halten das fest, beide mit Gegenprobe: keine Quelldatei
der Forschungsausprägung darf `setStreamVolume` aufrufen, und die
Freigabeprüfung muss **vor** dem Abspielen stehen.

### GEÄNDERT — rohe und bereinigte Fehlerrate sind jetzt getrennt

`Wortvergleich.zerlege` wandelt Ziffern in Zahlwörter, bevor verglichen wird.
Jede Wortfehlerrate, die in diesem Bericht vor diesem Nachtrag steht, war
damit bereits die **bereinigte** -- eine rohe gab es nie. Das war keine
falsche Zahl, aber eine falsch benannte.

Beide werden jetzt getrennt ausgewiesen. Der Unterschied zwischen ihnen ist
genau das, was nur Schreibweise war: bei der Segmentmessung 4,9 Punkte
(34,2 gegen 29,3) beziehungsweise 5,0 Punkte (23,8 gegen 18,8).

### WIDERLEGT: die Vorgabeliste bringt auf diesem Gerät nichts

`EXTRA_BIASING_STRINGS` mit „Belkis, Aslani, Nibra, Weinreich, d und b
audiotechnik", 12 Prüfsätze, 3 Paare je Satz, 36 gegen 36 Läufe, A15,
Erkenner auf dem Gerät mit `EXTRA_PREFER_OFFLINE`.

| | ohne Vorgabe | mit Vorgabe |
|---|---|---|
| rohe Wortfehlerrate | 23,8 % | 23,8 % |
| bereinigte Wortfehlerrate | 18,8 % | 18,8 % |
| Zeichenfehlerrate | 15,4 % | 15,4 % |
| Auslassungen / Einfügungen | 9,4 % / 1,2 % | 9,4 % / 1,2 % |
| Eigennamen | 75 % | 75 % |
| normale Wörter | 87 % | 87 % |
| erfundene Wörter | 0,17 | 0,17 |

**Kein Unterschied in irgendeiner Kennzahl.** Die erkannten Texte sind
Zeichen für Zeichen gleich, samt der Fehler:

```
buchstaben-m  OHNE  … und die Firma heißt in ebra
buchstaben-m  MIT   … und die Firma heißt in ebra
```

„Nibra" steht in der Vorgabeliste und wird trotzdem als „in ebra" erkannt --
in beiden Fassungen identisch. Genau der Fall, für den die Liste gedacht ist.

**Warum das kein Zufall sein kann.** Der Erkenner arbeitet bei
eingespeistem Ton vollständig bestimmt: 26 Gruppen aus je drei
Wiederholungen, alle Zeichen für Zeichen gleich, keine einzige Abweichung.
Bei bestimmtem Verhalten heißt gleiche Ausgabe bei verschiedener Vorgabe:
die Vorgabe hatte **keine** Wirkung. Kein einziger der 12 Sätze unterscheidet
sich.

**Folge für das Produkt.** Ein persönliches Wörterbuch über diese
Schnittstelle wäre Arbeit ohne Wirkung -- und ein Versprechen an den Nutzer,
das die Technik nicht hält. Es wird **nicht** gebaut, solange kein Weg
gefunden ist, der messbar etwas ändert.

**Grenzen dieser Aussage.** Gemessen auf **einem** Gerät, mit dem Erkenner
auf dem Gerät und `EXTRA_PREFER_OFFLINE`. Ob die Liste mit dem Erkenner über
das Netz wirkt, ist nicht geprüft -- für Nibra aber ohne Belang, weil die
Auslieferung ohne Netz arbeitet.

**Nebenbefund, der spätere Messungen billiger macht:** bei eingespeistem Ton
ist der Erkenner bestimmt. Wiederholungen dienen dort nur der Absicherung
gegen Aufbaufehler, nicht gegen Streuung -- die gibt es nicht.
