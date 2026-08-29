# Live-Pipeline: Gate 0 bis 4

Stand 28.08.2026, Nibra 2.1. Gemessen auf **Samsung SM-S918B** (S23 Ultra,
Android 16) und **Samsung SM-A156B** (A15, Android 16). Alle Zahlen stammen
aus Läufen auf diesen Geräten; nichts ist geschätzt oder aus der Doku
übernommen.

Wo etwas nicht gemessen wurde, steht **NICHT BELEGT**. Das ist kein
Platzhalter, sondern das Ergebnis.

---

## Gate 0 — das Messwerkzeug selbst prüfen

An **einem** Tag haben vier Fehlmessungen etwas Funktionierendes als kaputt
gemeldet:

| # | Behauptung | Wirklichkeit | Ursache |
|---|---|---|---|
| 1 | `EXTRA_AUDIO_SOURCE` nicht unterstützt | funktioniert auf beiden Geräten | nur `onResults` geprüft, Segmente übersehen |
| 2 | 133 % Wortfehlerrate | knapp daneben erkannt | alle Alternativen mit `\|` verkettet und mitgezählt |
| 3 | „ES FEHLT TON" bei −70 ms | kein Ton fehlte | Betrag geprüft statt Vorzeichen |
| 4 | Vorlauf getestet | der Versuch zeigte nichts | Verzögerung zu kurz für einen Verlust |

Später kam eine fünfte dazu, gefunden im Vorlaufversuch selbst (siehe
Gate 1).

Vier Fehler an einem Tag sind kein Zufall, sondern ein Muster: **Messcode
ist Produktionscode für unsere Entscheidungen.** Wenn er lügt, bauen wir
das Falsche und merken es nie.

Daraus folgte: jede Kennzahl, auf der eine Entscheidung ruht, hat einen
handgerechneten Selbsttest. Dafür wurden Entscheidungen aus den Versuchen
herausgezogen, in reine Form ohne Android:

- **`Ergebniswahl`** — Segment, sonst Endergebnis, sonst geretteter
  Zwischenstand. Mehrere Segmente sind nacheinander Gesprochenes und werden
  aneinandergehängt; Alternativen deuten *dieselbe* Stelle und dürfen es
  nie. Dazu eine Deduplizierung, weil Erkenner denselben Abschnitt
  verlängert nachliefern.
- **`Vorlaufpuffer`** — der Ringpuffer, gegen die Folge `1 2 3 4 5` geprüft.
  Ein Vorlauf, der doppelt, vertauscht oder verliert, wäre sonst erst an
  unerklärlich schlechter Erkennung aufgefallen.
- **`Kennzahlen`** — Perzentile nach nächstem Rang. Ohne Messwerte kommt
  `null` heraus, **nicht 0**: eine 0 läse sich als „kein Verzug", also als
  bestmögliches Ergebnis.
- **`Tonstrecke.istLueckenlos`** — das Vorzeichen trägt die Bedeutung.
  Positiv heißt fehlender Ton, negativ heißt Randungenauigkeit.

Ein Golden Test hat sich dabei selbst korrigiert: die erwartete
„Verkettung über 100 %" ergab bei zwei Bezugswörtern exakt 100 %.
Nachgerechnet und die Erwartung berichtigt, statt sie passend zu biegen.

**Stand:** 228 Tests grün (98 Auslieferung, 130 Forschung).

---

## Gate 1 — Vorlauf

**Frage:** Rettet der Vorlaufpuffer den Anfang, den der Erkenner sonst
verpasst?

**Aufbau, bestimmt statt hoffend:**

- eingespeiste Aufnahme statt Mikrofon — sonst wäre bei jedem Durchgang
  anders gesprochen worden, und der Unterschied läge im Sprecher
- gestaffelte Verzögerungen: 0, 500, 1500, 2500 ms
- je Verzögerung ein Durchgang mit und einer ohne Vorlauf
- Prüfung der **Bytefolge**: was ankam, muss ein zusammenhängender
  Abschnitt der Quelle sein

**Ergebnis, auf beiden Geräten gleich:**

| Verzögerung | Vorlauf | Wörter | Folge | Vorlauf-Bytes |
|---|---|---|---|---|
| 0 ms | aus | 7 | ok | 0 |
| 0 ms | an | 7 | ok | 0 |
| 500 ms | aus | 7 | ok | 0 |
| 500 ms | an | 7 | ok | 16 384 |
| 1500 ms | aus | **1** | ok | 0 |
| 1500 ms | an | **7** | ok | 49 152 |
| 2500 ms | aus | **0** | ok | 0 |
| 2500 ms | an | **7** | ok | 79 872 |

**Urteil: der Vorlauf ist belegt.** Ab 1500 ms Verzögerung geht ohne ihn
der Anfang verloren — bei 2500 ms sogar der ganze Satz. Mit Vorlauf kommt
der Text bei jeder Verzögerung vollständig an. Die Bytefolge stimmt in
allen acht Durchgängen: kein Doppel, keine Lücke, keine Vertauschung.

Unter 1500 ms zeigt der Versuch **nichts** — dort kommt auch ohne Vorlauf
alles an. Das ist kein Beleg gegen den Vorlauf, sondern der Grund, warum
der erste Anlauf mit 500 ms nichts gezeigt hat.

### Die fünfte Fehlmessung

Der erste Lauf dieses Versuchs meldete `Kein Durchgang hat den Anfang` —
obwohl die Tabelle darüber den Beweis schon enthielt. Das Urteil hing an
einem Ankerwort („Zitrone"), das der Erkenner in **keinem** Durchgang
lieferte, auch nicht bei 0 ms Verzögerung. Es maß also nicht den Vorlauf,
sondern die Erkennbarkeit eines einzelnen Wortes.

Gemessen wird jetzt die Wortzahl. Die Lehre ist dieselbe wie bei den vier
davor: **ein Kriterium, das in keinem Fall anschlägt, prüft nichts.**
