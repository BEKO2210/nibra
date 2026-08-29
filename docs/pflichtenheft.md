# Pflichtenheft: Nibra

## Zweck
Lokales Diktier-Werkzeug fuer Android (Paket de.ithandwerkstuttgart.nibra): Sprache aufnehmen, auf dem Gerät in Text wandeln und per Bedienungshilfen-Dienst an der Cursorposition in jede beliebige App einfügen. Erkennung ausschließlich gerätelokal (SpeechRecognizer.createOnDeviceSpeechRecognizer ab API 33, darunter SpeechRecognizer mit EXTRA_PREFER_OFFLINE), Silero-VAD (silero_vad.onnx) fuer Sprechpausen und automatischen Stopp. Kein INTERNET-Recht, kein Konto, keine Bezahlung, keine Tracker; Diktate, Textbausteine und Einstellungen bleiben lokal. Zusätzlich durchsuchbarer Verlauf, eigene Textersetzungen und umschaltbare Diktatsprache je Eintrag.

## Bildschirme
- Einrichtung (EinrichtungBildschirm): Mikrofonrecht anfordern, Bedienungshilfen-Dienst aktivieren, Offenlegungstext
- Aufnahme (AufnahmeBildschirm): große mittige Aufnahmeflaeche, gleitende Pegelkurve, Dauer, Stille-Erkennung, Zustände Bereit/Laeuft/Wandelt/Fehler
- Verlauf (VerlaufBildschirm): Diktate durchsuchbar, gruppiert nach Heute/Gestern/Diese Woche/Älter, Sprache je Eintrag sichtbar
- Diktat-Detail (DiktatDetailBildschirm): Text anzeigen, kopieren, teilen, löschen, einfügen, Sprache umschalten
- Diktatsprache (DiktatspracheBildschirm): Sprache wählen, zuletzt genutzte oben, Verfügbarkeit auf dem Gerät
- Textbausteine (TextbausteineBildschirm): eigene Ersetzungen Kürzel -> Ersatztext anlegen, bearbeiten, löschen
- Einstellungen (EinstellungenBildschirm): Stopp bei Stille, Aufnahmen behalten (Standard aus), Oberflächensprache, Diktatsprache, Status von Mikrofon und Bedienungshilfen-Dienst
- Datenschutz (DatenschutzBildschirm): was auf dem Gerät bleibt (alles), was das Netz sieht (nichts), wozu der Bedienungshilfen-Dienst dient
- Verwendete Fremdsoftware (FremdsoftwareBildschirm): Lizenzhinweise, u. a. MIT-Vorlage aidictation, Silero VAD, Schriften
- Schwebende Aufnahmeblase des Bedienungshilfen-Dienstes über fremden Apps (nur über fokussiertem, editierbarem Feld, nie über Passwortfeldern)

## Datenmodell
- Diktat: id, text, zeitpunktMillis, uhrzeit, datum, sprachCode, sprachName, dauerSekunden
- VerlaufGruppe: schluessel (Gruppenschluessel HEUTE|GESTERN|DIESE_WOCHE|AELTER), eigenesDatum, diktate
- Diktatsprache: code, name, eigenName, aufGeraetVerfuegbar, zuletztGenutzt
- Textbaustein: id, kuerzel, ersatz
- Einstellungen: stoppBeiStille, aufnahmenBehalten, dienstzustand, mikrofonzustand, oberflaechenspracheName, diktatspracheName
- Aufnahmezustand (sealed): Bereit | Laeuft(pegel, dauerSekunden, verlauf, stilleErkannt) | Wandelt | Fehler(art)
- Fehlerart: KEIN_MIKROFON_RECHT, ERKENNUNG_NICHT_VERFUEGBAR, SPRACHE_NICHT_AUF_GERAET, NICHTS_VERSTANDEN, UNBEKANNT
- Dienstzustand: EINGERICHTET | NICHT_EINGERICHTET; Mikrofonzustand: ERTEILT | NICHT_ERTEILT
- Merkmal (Funktionsumfang-Schicht): UNBEGRENZTER_VERLAUF, TEXTBAUSTEINE_UNBEGRENZT, EXPORT, MEHR_ALS_DREI_SPRACHEN
- Audioaufnahme als temporäre Daten: nach erfolgreicher Umwandlung verworfen, außer Einstellung 'Aufnahmen behalten' ist an

## Berechtigungen
- Mikrofon (Aufnahme der Sprache fuer die Erkennung auf dem Gerät)
- Bedienungshilfen-Dienst (nur zum Einfügen des erkannten Textes in fremde Apps; kein Mitlesen, Passwortfelder ausgenommen) - vom Nutzer in den Systemeinstellungen zu aktivieren, im Manifest als BIND_ACCESSIBILITY_SERVICE deklariert
- Kein Internet-Recht: die App fordert INTERNET bewusst nicht an

## Ziele
- Diktieren mit einem Griff: große Aufnahmeflaeche, ehrliche gleitende Pegelanzeige, automatischer Stopp bei Stille
- Einfügen des Textes in jede App über den Bedienungshilfen-Dienst, ohne Mitlesen und ohne Passwortfelder
- Alles bleibt auf dem Gerät: keine Cloud-Transkription, keine fremden Endpunkte, keine Tracker, kein INTERNET-Recht
- Durchsuchbarer, nach Datum gruppierter Verlauf mit Kopieren, Teilen, Löschen und je Eintrag umschaltbarer Sprache
- Textbausteine: eigene Ersetzungen, lokal und sofort wirksam
- Eigene Marke statt lieblose Optik: eigene Palette (Teal/Mint), Typografie (Fraunces/Inter), eigenes Icon und eigene Symbole, keine Emoji, alles mittig und symmetrisch, eine Abstands- und Formskala
- Qualitätslatte: kein Bildschirm ohne durchdachten Leerzustand, sichtbare Rückmeldung fuer alles über 300 ms, Fehler im Klartext statt als Code
- Sieben Oberflächensprachen
- Spätere Bezahlvariante ohne Umbau möglich: einzige Schicht Funktionsumfang.istFreigeschaltet(Merkmal), heute immer true, keine Abrechnungsbibliothek
- Rechtssicherheit: proprietäre App, aber MIT-Hinweis der Vorlage in Bildschirm 'Verwendete Fremdsoftware' und in FREMDSOFTWARE.md; Datenfluss dokumentiert in datenfluss.yaml
- minSdk 26, targetSdk 36

## Sprachen
- de
- en
- fr
- es
- it
- tr
- pl

## Rückfragen
- Persistenz ist noch nicht verdrahtet: Room und DataStore sind als Abhängigkeiten vorhanden, aber es existieren keine Entities/DAOs; der Zustand liegt in MainActivity in `remember`. Welche Tabellen/Schluessel sollen Diktate, Textbausteine und Einstellungen konkret speichern?
- Die Oberflächen-Strings liegen nur in res/values (deutsch); Übersetzungsordner values-en/fr/es/it/tr/pl fehlen. Sollen sie als Ressourcenordner angelegt werden, und wird die Oberflächensprache per LocaleManager/App-Sprachen (per-app language) oder eigenem Umschalter im Einstellungen-Bildschirm gewaehlt?
- Die tatsächliche Spracherkennung ist noch nicht implementiert (nur als Entscheidung in AUFTRAG.md festgehalten); ebenso die Anbindung von silero_vad.onnx an die ONNX-Runtime. Wer liefert die Schwellwerte fuer Pegelglättung und Stille-Erkennung (Dauer bis Auto-Stopp)?
- datenfluss.yaml und FREMDSOFTWARE.md sind gefordert, liegen aber noch nicht im Repo. Welche Fundstellen/Lizenztexte sollen fuer das Erkennungsmodell und die Schriften Fraunces/Inter eingetragen werden?
- Der Bedienungshilfen-Dienst zeichnet eine schwebende Blase per WindowManager. Ob dafür ausschließlich TYPE_ACCESSIBILITY_OVERLAY genutzt wird (dann kein SYSTEM_ALERT_WINDOW nötig) ist aus dem Code nicht abschliessend belegbar - Überlagerungsrecht bestätigen oder ausschliessen.
- Welche Liste von Diktatsprachen wird angeboten und wie wird 'aufGeraetVerfuegbar' ermittelt (Abfrage der On-Device-Erkenner-Sprachen)? Verhalten unter API 33 ohne Offline-Fähigkeit ist als Klartext-Fehler beschrieben, der genaue Text fehlt.
- Textbausteine: greift die Ersetzung automatisch nach jeder Erkennung oder nur beim Einfügen? Gross-/Kleinschreibung und Wortgrenzen-Regeln sind nicht festgelegt.
- Export ist als später kostenpflichtiges Merkmal benannt, aber kein Export-Bildschirm/-Format vorhanden. Soll Export in Version 1.0 enthalten sein und in welchem Format?
- Aufbewahrung: bei aktivierter Einstellung 'Aufnahmen behalten' - wo werden Audiodateien abgelegt, gibt es eine automatische Löschfrist?
- Play-Store-Einträge unter app/src/main/play (Titel, Beschreibungen, Screenshots, Datenschutzerklärung, Kontakt) stammen noch aus der Vorlage bzw. sind nur en-US. Sollen sie fuer Nibra und alle sieben Sprachen neu erstellt werden?
