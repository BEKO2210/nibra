# Auftrag an die Fabrik

Dies ist der Ausgangsstand von `writingmate/aidictation` (MIT-Lizenz),
Android-Teil. Er wird **komplett neu gebaut**, nicht angepasst.

## Was bleibt
- Der Zweck: Diktieren statt Tippen. Sprache aufnehmen, in Text wandeln,
  in jedes Eingabefeld einfügen.
- Die Fähigkeit, über einen Bedienungshilfe-Dienst in beliebige Apps
  einzufügen.
- Sprachaktivitätserkennung (Datei `silero_vad.onnx` liegt bei).

## Was besser werden muss
1. **Optik.** Der Ausgangsstand ist funktional, aber lieblos. Die neue App
   bekommt eine eigene Marke: Palette, Typografie, eigenes Icon, eigene
   Symbole. Keine Emoji. Alles mittig und symmetrisch. Eine Aufnahmefläche,
   die man sofort versteht, mit ruhiger Pegelanzeige.
2. **Funktionen.** Über den Ausgangsstand hinaus:
   - Verlauf der Diktate, durchsuchbar, lokal
   - Textbausteine: eigene Ersetzungen (z. B. „mfg" → volle Grußformel)
   - Diktat-Sprache je Eintrag umschaltbar
   - Ein Bildschirm für Datenschutz: was bleibt auf dem Gerät, was nicht
3. **Sieben Sprachen** für die Oberfläche.
4. **Keine Tracker, keine Klartext-Verbindungen, keine unnötigen
   Berechtigungen.** Was die App an Daten anfasst, steht in `datenfluss.yaml`.

## Rechtliches
Der Ausgangsstand steht unter MIT. Die neue App ist proprietär, muss aber
den MIT-Hinweis der Vorlage mitliefern — in einem Bildschirm
„Verwendete Fremdsoftware" und in der Datei `FREMDSOFTWARE.md`.

---

# Antworten auf die Rückfragen (verbindlich)

1. **Tastatur-Modul `simple-keyboard`: entfällt.** Nicht Teil des Neubaus.
2. **Oberflächensprachen:** de (Quelle), en, fr, es, it, tr, pl.
3. **Kein Konto, keine Bezahlung, kein Supabase, kein Stripe.** Die App
   gehört dem Nutzer, es gibt kein Kontingent und keine Anmeldung. Alle
   zugehörigen Bildschirme, Datenmodelle und Endpunkte entfallen ersatzlos.
4. **Nur Erkennung auf dem Gerät.** Keine Cloud-Transkription, keine
   KI-Nachbearbeitung über fremde Endpunkte, keine API-Schlüssel. Das
   Sprachmodell wird einmalig heruntergeladen; danach arbeitet die App
   netzfrei. `INTERNET` wird ausschließlich für diesen Modell-Download
   verwendet und im Datenschutz-Bildschirm so erklärt.
5. **`datenfluss.yaml`:** Format wie in der Fabrik-Spezifikation — je
   Eintrag `datentyp, quelle, ziel, zweck, speicherung, weitergabe,
   empfaenger, aufbewahrung, loeschung`.
6. **`FREMDSOFTWARE.md`** führt auf: die MIT-Vorlage (aidictation),
   Silero VAD, das Erkennungsmodell, verwendete Schriften — jeweils mit
   Lizenz und Fundstelle.
7. **Audio:** wird nach erfolgreicher Umwandlung verworfen. Eine
   Einstellung „Aufnahmen behalten" ist standardmäßig **aus**.
8. **Sprache je Eintrag:** `RecordingEntity` bekommt ein Feld `sprache`;
   im Verlauf ist sie sichtbar und im Detail umschaltbar (erneute
   Erkennung mit anderer Sprache).
9. **Bedienungshilfen-Dienst bleibt**, mit neu formuliertem, übersetztem
   Offenlegungstext. Er wird nur zum Einfügen von Text verwendet, nie zum
   Mitlesen; Passwortfelder bleiben ausgenommen.
10. **minSdk 26, targetSdk 36** bleiben.
11. **KI-Befehle und Kontextregeln entfallen** (brauchen fremde Modelle).
    Stattdessen die geforderten **Textbausteine**: eigene Ersetzungen,
    lokal, ohne Netz.
12. **Name und Paketname bestimmt die Fabrik** in Station 2 (Marke).
    Der alte Name „AI Dictation" und `com.aidictation.app` werden **nicht**
    übernommen.

**Nachtrag Markenname:** Der erste Fabrik-Durchlauf hatte „Loqui" gewaehlt
— zu deutsch fuer eine international vertriebene App. Verbindlich ist jetzt
**Loqui** (von lateinisch *loqui*, „sprechen"): kurz, in allen Zielsprachen
aussprechbar, kollidiert in keiner der sieben Oberflaechensprachen mit einem
Woerterbucheintrag, und traegt die Bedeutung ohne Mikrofon-Klischee. Paketname
`de.ithandwerkstuttgart.loqui`. Akzentfarbe und Typografie aus `marke.json`
bleiben unveraendert.

**Grundhaltung:** Diese App ist ein lokales Werkzeug. Was auf dem Gerät
entsteht, bleibt auf dem Gerät.

---

# Nachtrag: Anspruch und Technik

## Anspruch
Diese App muss **außergewöhnlich gut** werden — nicht „funktioniert", sondern
so, dass man sie einer fremden Person zeigt und sie sagt: die kaufe ich.
Messlatte:
- Die Aufnahmefläche ist der Kern. Sie muss sofort verständlich sein, ruhig
  reagieren und beim Sprechen eine ehrliche Pegelanzeige zeigen — keine
  hektische Zappel-Animation, sondern eine gleitende Kurve.
- Kein Bildschirm ohne durchdachten Leerzustand.
- Jede Aktion, die länger als 300 ms dauert, hat eine sichtbare Rückmeldung.
- Fehler werden im Klartext erklärt, nie als Code.
- Alles mittig, alles symmetrisch, eine Abstandsskala, eine Formskala.
- Keine Emoji. Alle Symbole eigens erzeugt.

## Spracherkennung — Entscheidung
Auf dem Gerät, ohne fremde Endpunkte:
- **Android-Bordmittel**: `SpeechRecognizer.createOnDeviceSpeechRecognizer`
  ab API 33. Kein Modell-Download, keine Schlüssel, keine Kosten.
- Unter API 33: normaler `SpeechRecognizer` mit
  `EXTRA_PREFER_OFFLINE`; wenn das Gerät es nicht kann, wird das im
  Klartext erklärt statt still zu scheitern.
- **`INTERNET` wird nicht angefordert.** Die App ist netzfrei. Das
  vereinfacht Datenschutz, Datensicherheits-Formular und Freigabe.
- Silero-VAD (`silero_vad.onnx`) bleibt für die Erkennung von Sprechpausen
  und den automatischen Stopp.

## Spätere Bezahlvariante offenhalten
Es gibt **jetzt** kein Konto, keine Zahlung, keine Werbung. Aber der Aufbau
muss eine spätere Entscheidung erlauben, ohne Umbau:
- Alle Funktionen laufen über eine Schicht `Funktionsumfang`
  (z. B. `istFreigeschaltet(Merkmal.UNBEGRENZTER_VERLAUF)`), die heute
  immer `true` liefert. Ein einziger Ort, an dem später eine Prüfung
  eingehängt wird.
- Merkmale, die später kostenpflichtig sein könnten, sind schon jetzt
  benannt: unbegrenzter Verlauf, Textbausteine ohne Zahlbegrenzung,
  Export, mehr als drei Diktatsprachen.
- Keine Bibliothek für Abrechnung einbinden, kein Play-Billing — nur die
  Schnittstelle vorbereiten.

## Funktionsumfang (verbindlich)
1. Diktieren mit einem Griff: große Aufnahmefläche, Pegel, Stopp bei Stille.
2. Einfügen in jede App über den Bedienungshilfen-Dienst.
3. Verlauf: durchsuchbar, nach Datum gruppiert, Eintrag anzeigen, kopieren,
   teilen, löschen; Sprache je Eintrag sichtbar und änderbar.
4. Textbausteine: eigene Ersetzungen, sofort wirksam.
5. Diktatsprache umschaltbar, zuletzt genutzte oben.
6. Datenschutz-Bildschirm: was bleibt auf dem Gerät (alles), was das Netz
   sieht (nichts), wozu der Bedienungshilfen-Dienst dient.
7. Bildschirm „Verwendete Fremdsoftware" mit den Lizenzen.

---

# Nachtrag 2: Marke und Schlankheit

## Marke
- Name: **Loqui** (lateinisch „sprechen"). International, kein Deutsch,
  in allen Zielsprachen aussprechbar, kollidiert mit keinem Wort.
- Paket: `de.ithandwerkstuttgart.loqui`
- Zeichen: Federspitze, aus der eine Welle austritt — gesprochenes Wort
  wird geschriebenes Wort. Kein Mikrofon, keine Sprechblase.

## ONNX und Silero-VAD entfallen
Der Ausgangsstand schleppt `onnxruntime` mit: **113 MB native
Bibliotheken** für vier Architekturen, dazu das 2,3-MB-Modell. Die
Debug-APK wurde damit 133 MB groß — jenseits jeder Vertretbarkeit und
über der Grenze der Fabrik (30 MB).

Gebraucht wird das nicht: `SpeechRecognizer` erkennt Sprechpausen selbst
(`EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` und
`onEndOfSpeech`). Ein zweites Modell für dieselbe Aufgabe ist Ballast.

**Also:** `onnxruntime`-Abhängigkeit raus, `assets/silero_vad.onnx` raus,
`noCompress`-Eintrag raus. Automatischer Stopp bei Stille kommt vom
Bordmittel-Erkenner. Damit entfällt auch dessen Lizenzeintrag in
FREMDSOFTWARE.md.

**Zielgröße: unter 15 MB.**
