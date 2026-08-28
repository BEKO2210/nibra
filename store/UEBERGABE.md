# Übergabe an Google Play

Diese Anleitung gilt für Nibra 1.0 mit `versionCode 1`.

## Technische Eckdaten

- App-Name: Nibra
- Paketname: `de.ithandwerkstuttgart.nibra`
- Mindest-API: 26
- Ziel-API: 36
- Upload-Signatur SHA-256: `16:9B:99:08:12:AE:A2:63:10:85:CB:97:CD:8C:C4:B3:CF:33:77:99:1A:27:6B:81:65:BC:9B:24:77:7F:BE:11`
- Anbieter: IT-Handwerk Stuttgart
- Kontakt: belkis.aslani@gmail.com
- Berechtigungen: `android.permission.RECORD_AUDIO` und Bedienungshilfen-Dienst mit `android.permission.BIND_ACCESSIBILITY_SERVICE`
- Keine Berechtigung `android.permission.INTERNET`

## 1. App in der Play Console anlegen

1. Die Google Play Console öffnen und „Alle Apps“ wählen.
2. „App erstellen“ anklicken.
3. Als App-Name „Nibra“ eintragen.
4. Als Standardsprache Deutsch auswählen.
5. „App“ und die zutreffende kostenlose oder kostenpflichtige Bereitstellung auswählen.
6. Die Erklärungen bestätigen und „App erstellen“ anklicken.
7. Kontrollieren, dass beim ersten hochgeladenen Bundle der Paketname `de.ithandwerkstuttgart.nibra` angezeigt wird. Der Paketname kann nach der Anlage nicht geändert werden.

## 2. App-Bundle hochladen

1. Im linken Menü „Testen und veröffentlichen“ öffnen.
2. Zunächst „Interner Test“ und anschließend „Neue Version erstellen“ wählen.
3. Falls angeboten, „Google Play App Signing“ aktivieren. Der Upload-Schlüssel bleibt dabei der Schlüssel von IT-Handwerk Stuttgart; Google verwaltet den getrennten App-Signaturschlüssel.
4. Das signierte Android App Bundle mit der Endung `.aab` aus dem Ordner `abgabe/` in den Bereich „App-Bundles“ ziehen.
5. Prüfen, dass Version „1.0“, Versionscode „1“, Ziel-API „36“ und Paket `de.ithandwerkstuttgart.nibra` erkannt werden.
6. Versionshinweise eintragen, speichern und die Version zunächst für den internen Test freigeben.

Der Upload-Keystore liegt nie im Repository. Im Tresor liegen:

- `NIBRA_UPLOAD_KEYSTORE_B64`: Base64-kodierte Keystore-Datei
- `NIBRA_KEYSTORE_PROPERTIES_B64`: Base64-kodierte `keystore.properties` mit Dateipfad, Alias und Kennwörtern

Vor einem Upload ist der SHA-256-Fingerabdruck des verwendeten Upload-Zertifikats mit `16:9B:99:08:12:AE:A2:63:10:85:CB:97:CD:8C:C4:B3:CF:33:77:99:1A:27:6B:81:65:BC:9B:24:77:7F:BE:11` abzugleichen. Keystore, Eigenschaften, Alias und Kennwörter dürfen weder in das Repository noch in Store-Texte oder Support-Unterlagen übernommen werden.

## 3. Store-Eintrag ausfüllen

1. Im linken Menü „Nutzer gewinnen“, „Präsenz im Play Store“ und „Store-Haupteintrag“ öffnen.
2. Für Deutsch die Dateien aus `store/de/` übernehmen:
   - App-Name aus `store/de/titel.txt`
   - Kurzbeschreibung aus `store/de/kurz.txt`
   - Vollständige Beschreibung aus `store/de/lang.txt`
3. Über „Übersetzungen verwalten“ die weiteren Sprachen hinzufügen und jeweils die gleichnamigen Dateien verwenden:
   - Englisch aus `store/en/`
   - Französisch aus `store/fr/`
   - Spanisch aus `store/es/`
   - Italienisch aus `store/it/`
   - Türkisch aus `store/tr/`
   - Polnisch aus `store/pl/`
4. Unter „App-Symbol“ `store/grafik/icon-1024.png` hochladen.
5. Unter „Feature-Grafik“ `store/grafik/feature.png` hochladen.
6. Falls die Play Console Telefon-Screenshots verlangt, freigegebene aktuelle Screenshots ergänzen. Der Ordner `store/grafik/` enthält derzeit das App-Symbol und die Feature-Grafik.
7. Als Kontakt-E-Mail belkis.aslani@gmail.com eintragen und den Store-Eintrag speichern.

## 4. App-Inhalte und Datenschutz

1. Im linken Menü „Richtlinien und Programme“ und „App-Inhalte“ öffnen.
2. Bei „Datenschutzrichtlinie“ die öffentlich erreichbare HTTPS-Adresse der gehosteten Datei `store/datenschutz.md` eintragen. Die Datei enthält Deutsch und Englisch.
3. „Datensicherheit“ öffnen und die Antworten anhand von `store/datensicherheit.json` eintragen: keine erhobenen Daten und keine geteilten Daten. Audio, Diktattexte, Textbausteine und Einstellungen werden nur lokal verarbeitet und verlassen das Gerät nicht.
4. Im Bereich für sensible Berechtigungen die Mikrofonberechtigung mit der lokalen Aufnahme und Umwandlung von Diktaten begründen.
5. Die Erklärung zur Nutzung der Bedienungshilfen-API ausfüllen: Der Dienst wird ausschließlich auf ausdrückliche Aktivierung dazu verwendet, vom Nutzer erzeugten Diktattext in das ausgewählte Eingabefeld einzufügen. Er liest Inhalte nicht mit, wertet sie nicht aus und verwendet keine Passwortfelder.
6. Falls die Play Console eine Video-Demonstration oder eine zusätzliche Offenlegung für den Bedienungshilfen-Dienst verlangt, eine aktuelle Aufnahme bereitstellen, die Aktivierung, sichtbare Offenlegung und Einfügevorgang zeigt.
7. Bei Werbung „Nein“ auswählen. Nibra enthält keine Werbung, Konten, Tracker oder Analysedienste.
8. Den Abschnitt „Zielgruppe und Inhalte“ wahrheitsgemäß ausfüllen und speichern.

## 5. Prüfung und Veröffentlichung

1. Unter „Veröffentlichungsübersicht“ alle Warnungen und unvollständigen Pflichtangaben prüfen.
2. Den internen Test installieren und Mikrofonfreigabe, lokale Spracherkennung, Verlauf, Löschen und den Bedienungshilfen-Dienst auf einem Gerät prüfen.
3. In der Play Console unter „App-Integrität“ das Upload-Zertifikat kontrollieren. Der dort angezeigte Upload-Fingerabdruck muss dem oben genannten SHA-256-Wert entsprechen. Ein von Google verwaltetes App-Signaturzertifikat darf einen anderen Fingerabdruck besitzen.
4. Nach erfolgreichem Test eine Produktionsversion anlegen oder die getestete Version in die Produktion hochstufen.
5. Die Veröffentlichungsübersicht erneut prüfen und die Version zur Überprüfung einreichen.

## Noch von einem Menschen zu erledigen

- Die Fragen der Inhaltseinstufung vollständig und wahrheitsgemäß beantworten.
- Preis sowie verfügbare Länder und Regionen festlegen.
- `store/datenschutz.md` unter einer dauerhaft öffentlich erreichbaren HTTPS-Adresse hosten und diese Datenschutz-URL in der Play Console eintragen.
