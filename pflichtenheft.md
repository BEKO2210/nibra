# Pflichtenheft: Nibra

## Zweck
Lokales Diktier-Werkzeug fuer Android (Paket de.ithandwerkstuttgart.nibra): Sprache aufnehmen, auf dem Geraet in Text wandeln und per Bedienungshilfen-Dienst an der Cursorposition in jede beliebige App einfuegen. Erkennung ausschliesslich geraetelokal (SpeechRecognizer.createOnDeviceSpeechRecognizer ab API 33, darunter SpeechRecognizer mit EXTRA_PREFER_OFFLINE), Silero-VAD (silero_vad.onnx) fuer Sprechpausen und automatischen Stopp. Kein INTERNET-Recht, kein Konto, keine Bezahlung, keine Tracker; Diktate, Textbausteine und Einstellungen bleiben lokal. Zusaetzlich durchsuchbarer Verlauf, eigene Textersetzungen und umschaltbare Diktatsprache je Eintrag.

## Bildschirme
- Einrichtung (EinrichtungBildschirm): Mikrofonrecht anfordern, Bedienungshilfen-Dienst aktivieren, Offenlegungstext
- Aufnahme (AufnahmeBildschirm): grosse mittige Aufnahmeflaeche, gleitende Pegelkurve, Dauer, Stille-Erkennung, Zustaende Bereit/Laeuft/Wandelt/Fehler
- Verlauf (VerlaufBildschirm): Diktate durchsuchbar, gruppiert nach Heute/Gestern/Diese Woche/Aelter, Sprache je Eintrag sichtbar
- Diktat-Detail (DiktatDetailBildschirm): Text anzeigen, kopieren, teilen, loeschen, einfuegen, Sprache umschalten
- Diktatsprache (DiktatspracheBildschirm): Sprache waehlen, zuletzt genutzte oben, Verfuegbarkeit auf dem Geraet
- Textbausteine (TextbausteineBildschirm): eigene Ersetzungen Kuerzel -> Ersatztext anlegen, bearbeiten, loeschen
- Einstellungen (EinstellungenBildschirm): Stopp bei Stille, Aufnahmen behalten (Standard aus), Oberflaechensprache, Diktatsprache, Status von Mikrofon und Bedienungshilfen-Dienst
- Datenschutz (DatenschutzBildschirm): was auf dem Geraet bleibt (alles), was das Netz sieht (nichts), wozu der Bedienungshilfen-Dienst dient
- Verwendete Fremdsoftware (FremdsoftwareBildschirm): Lizenzhinweise, u. a. MIT-Vorlage aidictation, Silero VAD, Schriften
- Schwebende Aufnahmeblase des Bedienungshilfen-Dienstes ueber fremden Apps (nur ueber fokussiertem, editierbarem Feld, nie ueber Passwortfeldern)

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
- Audioaufnahme als temporaere Daten: nach erfolgreicher Umwandlung verworfen, ausser Einstellung 'Aufnahmen behalten' ist an

## Berechtigungen
- Mikrofon (Aufnahme der Sprache fuer die Erkennung auf dem Geraet)
- Bedienungshilfen-Dienst (nur zum Einfuegen des erkannten Textes in fremde Apps; kein Mitlesen, Passwortfelder ausgenommen) - vom Nutzer in den Systemeinstellungen zu aktivieren, im Manifest als BIND_ACCESSIBILITY_SERVICE deklariert
- Kein Internet-Recht: die App fordert INTERNET bewusst nicht an

## Ziele
- Diktieren mit einem Griff: grosse Aufnahmeflaeche, ehrliche gleitende Pegelanzeige, automatischer Stopp bei Stille
- Einfuegen des Textes in jede App ueber den Bedienungshilfen-Dienst, ohne Mitlesen und ohne Passwortfelder
- Alles bleibt auf dem Geraet: keine Cloud-Transkription, keine fremden Endpunkte, keine Tracker, kein INTERNET-Recht
- Durchsuchbarer, nach Datum gruppierter Verlauf mit Kopieren, Teilen, Loeschen und je Eintrag umschaltbarer Sprache
- Textbausteine: eigene Ersetzungen, lokal und sofort wirksam
- Eigene Marke statt lieblose Optik: eigene Palette (Teal/Mint), Typografie (Fraunces/Inter), eigenes Icon und eigene Symbole, keine Emoji, alles mittig und symmetrisch, eine Abstands- und Formskala
- Qualitaetslatte: kein Bildschirm ohne durchdachten Leerzustand, sichtbare Rueckmeldung fuer alles ueber 300 ms, Fehler im Klartext statt als Code
- Sieben Oberflaechensprachen
- Spaetere Bezahlvariante ohne Umbau moeglich: einzige Schicht Funktionsumfang.istFreigeschaltet(Merkmal), heute immer true, keine Abrechnungsbibliothek
- Rechtssicherheit: proprietaere App, aber MIT-Hinweis der Vorlage in Bildschirm 'Verwendete Fremdsoftware' und in FREMDSOFTWARE.md; Datenfluss dokumentiert in datenfluss.yaml
- minSdk 26, targetSdk 36

## Sprachen
- de
- en
- fr
- es
- it
- tr
- pl

## Rueckfragen
- Persistenz ist noch nicht verdrahtet: Room und DataStore sind als Abhaengigkeiten vorhanden, aber es existieren keine Entities/DAOs; der Zustand liegt in MainActivity in `remember`. Welche Tabellen/Schluessel sollen Diktate, Textbausteine und Einstellungen konkret speichern?
- Die Oberflaechen-Strings liegen nur in res/values (deutsch); Uebersetzungsordner values-en/fr/es/it/tr/pl fehlen. Sollen sie als Ressourcenordner angelegt werden, und wird die Oberflaechensprache per LocaleManager/App-Sprachen (per-app language) oder eigenem Umschalter im Einstellungen-Bildschirm gewaehlt?
- Die tatsaechliche Spracherkennung ist noch nicht implementiert (nur als Entscheidung in AUFTRAG.md festgehalten); ebenso die Anbindung von silero_vad.onnx an die ONNX-Runtime. Wer liefert die Schwellwerte fuer Pegelglaettung und Stille-Erkennung (Dauer bis Auto-Stopp)?
- datenfluss.yaml und FREMDSOFTWARE.md sind gefordert, liegen aber noch nicht im Repo. Welche Fundstellen/Lizenztexte sollen fuer das Erkennungsmodell und die Schriften Fraunces/Inter eingetragen werden?
- Der Bedienungshilfen-Dienst zeichnet eine schwebende Blase per WindowManager. Ob dafuer ausschliesslich TYPE_ACCESSIBILITY_OVERLAY genutzt wird (dann kein SYSTEM_ALERT_WINDOW noetig) ist aus dem Code nicht abschliessend belegbar - Ueberlagerungsrecht bestaetigen oder ausschliessen.
- Welche Liste von Diktatsprachen wird angeboten und wie wird 'aufGeraetVerfuegbar' ermittelt (Abfrage der On-Device-Erkenner-Sprachen)? Verhalten unter API 33 ohne Offline-Faehigkeit ist als Klartext-Fehler beschrieben, der genaue Text fehlt.
- Textbausteine: greift die Ersetzung automatisch nach jeder Erkennung oder nur beim Einfuegen? Gross-/Kleinschreibung und Wortgrenzen-Regeln sind nicht festgelegt.
- Export ist als spaeter kostenpflichtiges Merkmal benannt, aber kein Export-Bildschirm/-Format vorhanden. Soll Export in Version 1.0 enthalten sein und in welchem Format?
- Aufbewahrung: bei aktivierter Einstellung 'Aufnahmen behalten' - wo werden Audiodateien abgelegt, gibt es eine automatische Loeschfrist?
- Play-Store-Eintraege unter app/src/main/play (Titel, Beschreibungen, Screenshots, Datenschutzerklaerung, Kontakt) stammen noch aus der Vorlage bzw. sind nur en-US. Sollen sie fuer Nibra und alle sieben Sprachen neu erstellt werden?
