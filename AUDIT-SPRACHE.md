# Audit der Diktierstrecke

Stand 28.08.2026. Grundlage: der Quellbestand dieses Repositoriums und
Messungen auf einem Samsung SM-A156B (Android 16, API 36).

Dieses Dokument beantwortet die zehn Fragen aus dem Auftrag „Speech Quality
ist Priorität Nummer 1". Es bewertet nichts als gut oder schlecht, was nicht
belegt ist, und es nennt nichts „weltklasse".

---

## Der Befund, der alles andere überlagert

**Nibra nimmt keinen Ton auf. Die App sieht das Audiosignal nie.**

```
$ grep -rn "AudioRecord\|MediaRecorder\|AudioTrack\|NoiseSuppressor\|
           AutomaticGainControl\|AcousticEchoCanceler\|AudioManager\|
           AudioDeviceInfo\|MediaCodec" app/src/main --include=*.kt
(kein Treffer)
```

Die einzige Berührung mit dem Mikrofon ist die Berechtigung
`RECORD_AUDIO` im Manifest. Sie wird gebraucht, weil `SpeechRecognizer`
sie im Namen der aufrufenden App anfordert — nicht, weil Nibra selbst
aufnähme.

Der Tonstrom geht vom Mikrofon **unmittelbar** in den Erkennungsdienst des
Systems. Zurück kommt fertiger Text.

### Was daraus folgt

Der gesamte Abschnitt „Phase A — die Audioaufnahme muss perfekt sein" ist
mit der heutigen Architektur **nicht umsetzbar**. Nicht schwierig,
sondern unmöglich:

| Gefordert | Heute möglich |
|---|---|
| `UNPROCESSED` gegen `VOICE_RECOGNITION` gegen `MIC` messen | **nein** — wir wählen keine Quelle |
| AGC / Noise Suppression / Echo Cancellation kennen und steuern | **nein** — wir sehen sie nicht |
| Verlustfrei bis zur Erkennung, Abtastrate nicht erzwingen | **nein** — wir halten keine Abtastwerte |
| Ringpuffer mit Pre-Roll gegen verschluckte Satzanfänge | **nein** — es gibt nichts zu puffern |
| Eigene VAD, Hangover, Endpointing | **nein** — das macht der Systemdienst |
| Speech Enhancement, RAW gegen ENHANCED messen | **nein** — es gibt kein RAW |
| Audio an eine Cloud-Engine schicken | **nein** — wir haben kein Audio |
| Audio puffern, damit nichts verlorengeht | **nein** |

`RecognitionListener.onBufferReceived` wäre der einzige Weg an Rohdaten.
Er ist in `Spracherkenner.kt:105` als `= Unit` implementiert — und das ist
richtig so: Android ruft ihn seit Jahren praktisch nirgends auf, und die
Gerätefassung des Erkenners liefert dort nie etwas.

**Wer die Ziele dieses Auftrags will, muss den Ton selbst aufnehmen.** Das
ist keine Erweiterung, sondern eine neue Schicht unter allem Bestehenden.

---

## 1. Wie die Diktierstrecke heute genau arbeitet

Zwei Einstiege, ein Weg:

- **App:** `NibraViewModel.starteAufnahme()` → `Erkennerquelle.erkenne()`
- **Blase:** `DiktatBedienungshilfenDienst.aufBlaseGetippt()` → dieselbe Quelle

`Spracherkenner.erkenne()` (`Spracherkenner.kt:70`) öffnet einen
`callbackFlow` und darin:

1. `baueErkenner()` — ab API 33 `createOnDeviceSpeechRecognizer`, sonst
   `createSpeechRecognizer`
2. `startListening(absicht(...))` mit diesen Zusätzen:
   - `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`
   - `EXTRA_LANGUAGE` und `EXTRA_LANGUAGE_PREFERENCE`
   - `EXTRA_PARTIAL_RESULTS = true`
   - `EXTRA_PREFER_OFFLINE = true`
   - ab API 33: `EXTRA_ENABLE_FORMATTING = FORMATTING_OPTIMIZE_QUALITY`,
     `EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION = true`
   - `EXTRA_CALLING_PACKAGE`
3. Ereignisse: `onReadyForSpeech` → `Hoert`, `onRmsChanged` → `Pegel`,
   `onEndOfSpeech` → `Stille`, `onPartialResults` → `Teiltext`,
   `onResults` → `Ergebnis`, dann **`close()`**
4. Nachbearbeitung im ViewModel: `setzeSatzzeichen()` (gesprochene
   Satzzeichen in sieben Sprachen), dann `wendeBausteineAn()`
5. Ablage in Room

Sprachwahl: `sprachkandidaten()` probiert der Reihe nach `de-DE`,
`de-DE` normalisiert, `de`, dann leer.

---

## 2. Wo heute Abtastwerte und Wörter verlorengehen

### a) Die Lücke zwischen zwei Segmenten — der schwerste Fall

Beim Dauerdiktat schließt der Fluss nach jedem Ergebnis (`close()` in
`onResults`). Die Schleife in `NibraViewModel` ruft dann `erkenne()`
erneut auf. Das bedeutet je Satz:

```
onResults → close() → awaitClose → cancel() → destroy()
          → neuer callbackFlow → baueErkenner() → Dienst binden
          → startListening()
```

**Zwischen `destroy()` und dem nächsten `onReadyForSpeech` hört niemand
zu.** Auf einem Mittelklassegerät sind das erfahrungsgemäß mehrere hundert
Millisekunden. Wer ohne Pause weiterspricht, verliert den Anfang des
nächsten Satzes — und niemand merkt es, weil kein Ton existiert, mit dem
man es nachweisen könnte.

Das ist die wahrscheinlichste Quelle für „verschluckte Satzanfänge", und
sie ist mit der heutigen Architektur **nicht behebbar**, nur mildernd zu
umgehen.

### b) Der Anfang des ersten Satzes

Zwischen `startListening()` und dem tatsächlichen Beginn der Aufnahme im
Systemdienst liegt eine unbekannte Zeit. Ein Pre-Roll-Puffer, der das
auffangen würde, ist nicht möglich (siehe oben).

### c) Das Satzende

`onResults` liefert **nur das erste Element** der n-besten Liste
(`ersterText()`, `Spracherkenner.kt:285`). Verworfen werden dabei:

- die Alternativen der n-besten Liste
- `SpeechRecognizer.CONFIDENCE_SCORES` — wir wissen nie, wie sicher der
  Erkenner war
- Wortzeitstempel (liefert diese Schnittstelle ohnehin nicht)

Ohne Konfidenz ist die vom Auftrag geforderte „Second Opinion bei geringer
Confidence" heute nicht auslösbar.

### d) `NICHTS_VERSTANDEN` im Dauerdiktat

`ERROR_NO_MATCH` und `ERROR_SPEECH_TIMEOUT` werden beide auf
`Fehlerart.NICHTS_VERSTANDEN` abgebildet und im Dauerdiktat als
Sprechpause gewertet. Ein echtes „nichts verstanden" ist damit von einer
Pause nicht zu unterscheiden — gesprochener Text kann still verschwinden.

---

## 3. Welche Audioverarbeitung Android und der Hersteller heute machen

**Unbekannt, und von unserer Seite nicht feststellbar.**

Der Systemdienst wählt die Audioquelle, die Abtastrate und jede
Vorverarbeitung selbst. Ob AGC, Rauschunterdrückung, Echounterdrückung
oder ein Hersteller-DSP aktiv ist, sehen wir nicht und können es nicht
abschalten. Auf Samsung-Geräten ist Vorverarbeitung im Sprachpfad üblich.

Das ist nicht ein fehlendes Detail, sondern eine Lücke im Kern des
Auftrags: „Wenn Verarbeitung vorhanden ist, müssen wir wissen, dass sie
vorhanden ist." Heute wissen wir es nicht.

---

## 4. Wie gut die Segmentierung ist

Es gibt **keine eigene Segmentierung**. Sie liegt vollständig beim
Systemdienst.

Und sie ist überwiegend **nicht einmal eingestellt**: die
Stille-Parameter werden nur im Zweig `if (!stoppBeiStille)` gesetzt
(`Spracherkenner.kt:225`). Die Voreinstellung der App ist
`stoppBeiStille = true` — in diesem Fall geht **kein einziger**
Endpointing-Parameter an den Erkenner. Es gilt, was der Hersteller
voreingestellt hat, üblicherweise ein bis zwei Sekunden Stille.

Damit unterscheidet die App heute nicht zwischen „ich bin fertig" und
„ich denke kurz nach". Der Auftrag verlangt genau das.

Ergänzend: Android ignoriert diese Zusätze auf vielen Geräten ohnehin.
Selbst der gesetzte Wert ist eine Bitte, keine Zusage.

---

## 5. Welches ASR heute läuft und wo seine Grenzen liegen

`android.speech.SpeechRecognizer`, ab API 33 in der Gerätefassung.

**Was dafür spricht:** kein Netz, kein Konto, keine Kosten, keine
Schlüssel, kein Datenschutzproblem — und es ist der Grund, warum die App
heute ohne `INTERNET`-Berechtigung auskommt.

**Harte Grenzen:**

| | |
|---|---|
| Rohaudio | nicht erhältlich |
| Wortzeitstempel | nicht vorhanden |
| Konfidenz | im Bundle vorgesehen, in der Praxis meist leer; wir lesen sie ohnehin nicht |
| Keyterms / Kontext / Anpassung | nicht vorhanden |
| Sprachwechsel im Satz | nicht vorgesehen |
| Diarisierung | nicht vorhanden |
| Endpointing steuerbar | nur als Bitte, oft ignoriert |
| Modellqualität | vom Hersteller und vom Gerät abhängig, nicht von uns |
| Reproduzierbarkeit | keine — dasselbe Audio kann auf zwei Geräten anders erkannt werden |

Der letzte Punkt ist für den geforderten Benchmark entscheidend: Wir
können mit dieser Schnittstelle **keine reproduzierbare Messung** machen,
weil wir weder das Eingangssignal kontrollieren noch das Modell kennen.

---

## 6. Die drei stärksten Kandidaten für unseren Fall

Bewertet nach unserem Fall — deutschsprachiges Diktat, mobil, kurze bis
mittlere Segmente, Namen und Zahlen wichtig — nicht nach Bestenlisten.

Alle drei sind **Kandidaten für die Messung**, keine Empfehlung.

1. **Deepgram Nova-3** — auf Streaming ausgelegt, Keyterm-Prompting
   vorhanden, günstig je Minute. Zu prüfen: Deutsch-Qualität, Verhalten
   bei Zahlen und Eigennamen.
2. **AssemblyAI Universal-3 Pro Streaming** — Streaming mit
   Wortzeitstempeln und Konfidenz, Wortverstärkung vorhanden. Zu prüfen:
   Latenz vom Mobilnetz aus, Deutsch.
3. **ElevenLabs Scribe v2 Realtime** — jung, auf niedrige Latenz
   ausgelegt. Zu prüfen: Halluzinationsneigung, Deutsch, Preis.

**Warum nicht die naheliegenden:**

- *OpenAI GPT-Transcribe*: Whisper-Abkömmlinge neigen bei Stille und
  schlechtem Signal zu **erfundenem Text**. Der Auftrag nennt
  Halluzination ausdrücklich ein Todeskriterium. Muss gemessen werden,
  bevor es in die engere Wahl kommt — nicht ausgeschlossen, aber unter
  besonderem Verdacht.
- *Google Chirp 3*: technisch stark, aber wir wären beim selben Anbieter
  wie die heutige Lösung, mit Abrechnung und Kontobindung obendrauf.

**Diese drei brauchen alle `INTERNET`.** Siehe den Konflikt weiter unten.

---

## 7. Der stärkste Offline-Kandidat

**sherpa-onnx** (k2-fsa) ist die einzige ernsthafte Grundlage: läuft
netzfrei, hat eine gepflegte Android-Anbindung, unterstützt Zipformer,
NeMo-Transducer, Whisper und Parakeet, und inzwischen auch QNN, also die
NPU von Qualcomm.

**Aber der ehrliche Vorbehalt für uns:** Die gut gepflegten
**streamenden** Modelle sind überwiegend Englisch und Chinesisch. Ein
streamendes deutsches Modell in belastbarer Qualität ist in den
öffentlichen Modelllisten nicht erkennbar. Für Deutsch bleiben damit
zunächst **nicht-streamende** Modelle (Whisper, NeMo Canary) — die
liefern erst nach dem Satz Text, nicht währenddessen.

Dazu kommt: Der SM-A156B hat einen Exynos 1330, **keine** Qualcomm-NPU.
Die QNN-Beschleunigung nützt uns auf dem Testgerät nichts.

Zu messen wären App-Größe, Arbeitsspeicher, Startzeit, Akku und
Gerätetemperatur — der Auftrag nennt sie zu Recht.

---

## 8. Wie der Benchmark zu bauen ist

Er hängt an einer Voraussetzung: **Wir brauchen erst eigenes Audio.**
Ohne eine eigene Aufnahmeschicht gibt es keine Datei, die man zwei Engines
vorlegen könnte, und keine Reproduzierbarkeit.

Ist die Voraussetzung erfüllt, in dieser Reihenfolge:

1. **Aufnahmewerkzeug in der App** (Entwicklerzugang): nimmt mit
   `AudioRecord` auf, schreibt WAV verlustfrei, notiert Quelle, Abtastrate,
   Gerät, Mikrofon und ob eine Vorverarbeitung gemeldet wird.
2. **Korpus** nach der Liste im Auftrag: Raum, Abstand, Lautstärke,
   Tempo, Störgeräusch, Sprecher. Je Aufnahme ein von Hand geschriebenes
   Referenztranskript. Realistisch 60–120 Aufnahmen für eine erste
   Aussage.
3. **Auswertung auf dem Rechner**, nicht auf dem Telefon: ein Programm,
   das den Korpus gegen jede Engine schickt und WER, CER, Auslassungen,
   Einfügungen, Halluzinationen, Satzanfangs- und Satzendeverlust,
   Eigennamen-, Zahlen- und Entitätenfehler misst.
4. **Halluzination getrennt zählen**: eingefügte Wörter ohne Entsprechung
   im Referenztext, gewichtet — der Auftrag nennt es ein Todeskriterium.
5. **Latenz nur auf dem Gerät messen**, nicht am Rechner: erstes Teilwort,
   Teilergebnis-Latenz P50/P95, Endergebnis-Latenz, Endpoint-Latenz.
6. **Kosten je Minute** aus der tatsächlichen Abrechnung, nicht aus der
   Preisliste.

---

## 9. Was sofort sinnvoll ist — ohne Architekturwechsel

Diese vier Punkte lohnen sich, gleich wie die Engine-Entscheidung ausfällt:

1. **Konfidenz und n-beste Liste lesen statt verwerfen.**
   `onResults` nimmt heute nur `firstOrNull()`. Wo das Gerät
   `CONFIDENCE_SCORES` liefert, ist das die einzige Möglichkeit,
   „unsicher" überhaupt zu erkennen. Kleiner Eingriff, sofortiger Nutzen.
2. **Endpointing auch im Regelfall setzen.** Heute geht bei
   `stoppBeiStille = true` **kein** Parameter an den Erkenner. Ein
   bewusster Wert — etwa 1200 ms „möglicherweise fertig", 2000 ms
   „fertig" — ist besser als die Voreinstellung des Herstellers, auch
   wenn sie nur eine Bitte ist.
3. **Die Lücke zwischen den Segmenten verkleinern.** Den Erkenner beim
   Dauerdiktat nicht zerstören und neu bauen, sondern dieselbe Instanz
   erneut starten. Spart Bindung und Aufbau.
4. **`NICHTS_VERSTANDEN` aufteilen.** `ERROR_NO_MATCH` und
   `ERROR_SPEECH_TIMEOUT` unterscheiden — nur das zweite ist eine Pause.

---

## 10. Was ohne nachweisbaren Nutzen bliebe

- **Speech Enhancement (GTCRN, DPDFNet) einbauen, bevor eigenes Audio
  existiert.** Ohne RAW gibt es nichts zu verbessern und nichts zu
  vergleichen. Und selbst dann gilt der Vorbehalt aus dem Auftrag: ein für
  Menschen sauberes Signal kann für ASR schlechter sein.
- **Diarisierung.** Nibra ist eine Diktier-App für eine Person. Ein
  Sprecherwechsel kommt nicht vor.
- **Zwei teure Engines dauerhaft parallel.** Der Auftrag schließt es
  selbst aus; es gehört nur als gezielte Zweitmeinung hinein.
- **NPU-Beschleunigung als frühes Ziel.** Auf dem Testgerät gibt es keine
  passende NPU. Erst messen, ob die CPU reicht.
- **Ein eigener ASR-Trainingsweg.** Weit außerhalb dessen, was diese App
  rechtfertigt.

---

## Der Konflikt, den nur der Auftraggeber lösen kann

`AUFTRAG.md` ist an drei Stellen eindeutig:

> Antwort 4: „Nur Erkennung auf dem Gerät. Keine Cloud-Transkription."

> Nachtrag „Spracherkennung": „`INTERNET` wird nicht angefordert. Die App
> ist netzfrei."

> `README.md`: „Keine `INTERNET`-Berechtigung."

Der Datenschutz-Bildschirm sagt es dem Nutzer zu, in sieben Sprachen. Das
ist heute das stärkste Alleinstellungsmerkmal der App.

**Alle drei Cloud-Kandidaten aus Abschnitt 6 brechen diese Zusage.**
Sie brauchen `INTERNET`, sie senden Sprache an fremde Rechner, und die
Datensicherheitsangaben bei Google Play müssten von „keine Daten erhoben"
auf „Audio wird übertragen" geändert werden.

Das ist keine technische Frage. Es sind drei mögliche Wege:

| Weg | Bedeutung |
|---|---|
| **A — netzfrei bleiben** | Kein Cloud-Kandidat. Der ganze Auftrag beschränkt sich auf eigene Aufnahme plus On-Device-Engine. Die Zusage bleibt. Die erreichbare Erkennungsqualität ist begrenzt |
| **B — Cloud wahlweise** | Netzfrei bleibt Voreinstellung, Cloud ist ein ausdrücklich einzuschaltender Schalter mit klarer Aufklärung. Zusage wird zur Voreinstellung statt zur Eigenschaft |
| **C — Cloud voreingestellt** | Beste erreichbare Qualität, aber das Alleinstellungsmerkmal ist weg und die App steht neben allen anderen |

Bevor diese Frage nicht beantwortet ist, wäre jede Arbeit an einer
Engine-Schicht möglicherweise für die Tonne.

---

## Was zuerst gebaut werden muss

Unabhängig von der Entscheidung oben führt jeder Weg über denselben
ersten Schritt:

**Eine eigene Aufnahmeschicht mit `AudioRecord`.** Ohne sie ist kein
Pre-Roll möglich, keine eigene VAD, kein Benchmark, keine
Reproduzierbarkeit, kein Schutz vor Audioverlust — und keine einzige der
Cloud-Engines überhaupt anschließbar.

Sie ist auch für Weg A nötig: Selbst wenn die On-Device-Erkennung bleibt,
brauchte man eigenes Audio, um überhaupt messen zu können, was heute
verlorengeht.
