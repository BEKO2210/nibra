# Sicherheitsgate — Abschluss

Stand 29.08.2026, 04:35. Alle Aussagen sind am Gerät oder am **gebauten
Erzeugnis** geprüft, nicht am Quelltext.

## BEWIESEN

**Die Forschungsausprägung kann nicht mehr ausgeliefert werden.**
`beforeVariants` schaltet die Release-Variante ab, bevor eine ihrer Aufgaben
entsteht. Nachgezählt: `assembleForschungRelease`, `packageForschungRelease`
und `bundleForschungRelease` **existieren nicht** — direkte Aufrufe scheitern
mit „Task not found", nicht mit einer Ausnahme nach getaner Arbeit. Nach
`clean` entsteht kein Erzeugnis. Die alte APK vom 01:48 ist entfernt.

**Der Messplatz ist nicht mehr von aussen auslösbar.**
Aktivität auf `exported="false"`. Der Angriffsweg scheitert am System:

```
java.lang.SecurityException: Permission Denial: starting Intent
{ cmp=…/.ForschungActivity } from null (pid=22794, uid=2000)
not exported from uid 10494
```

Gestartet wird über eine Instrumentierung, die im Prozess der App selbst
läuft. **Die Zugangskontrolle trägt damit Android, nicht ein selbstgebauter
Schlüssel.** Der Schlüsselmechanismus ist wieder entfernt — er war die
schwächere Lösung und existiert im Quelltext nicht mehr (0 Fundstellen).

**Kein Gesprochenes im Systemprotokoll.**
Messung gefahren, danach 7284 Protokollzeilen durchsucht:

| gesucht | Treffer |
|---|---|
| „vorlauftest" | **0** |
| „Zitrone" | **0** |
| „guten morgen" | **0** |
| „dies ist der" | **0** |

Gegenprobe: derselbe Text steht im Bericht sechsmal — die Suche hätte ihn
gefunden. Nibra protokolliert nur noch:
`Bericht geschrieben: vorlaufversuch.txt, 1147 Zeichen`

**Kein Pfad mehr aus fremder Eingabe.**
Die Absicht bestimmt nur eine Kennung; welcher Dateiname dahinter steht,
entscheidet `Messspur` im Programm. Ein Pfad lässt sich nicht mehr
hineinreichen, unabhängig von der Eingabe.

## NEGATIVE TESTS

**Pfadausbruch** — 14 Eingaben, keine verlässt das Verzeichnis:
`../`, `../../../../data/data/…/nibra.db`, `/etc/passwd`,
`/sdcard/Download/fremd.pcm`, leer, nur Leerzeichen, `..`, `.`,
`de/../../x`, `de\..\x`, Name mit Nullbyte, 5000 Zeichen, `\n../x`,
`%2e%2e%2fx`. Alle fallen auf die vorgesehene Aufnahme zurück; kein
Dateiname trägt je einen Pfadanteil.
Gegenprobe: die vier vorgesehenen Kennungen liefern weiterhin ihre Aufnahme.

**Auslösen von aussen** — `am start` mit Messabsicht: SecurityException,
keine Aufnahme, kein Bericht, keine Datei.
Gegenprobe: `am instrument` läuft und erzeugt den Bericht.

**Auslieferungsriegel** — direkte Aufrufe der drei Release-Aufgaben:
alle drei „existiert nicht".
Gegenprobe: `assembleOfflineRelease` baut weiter durch.

279 Modultests grün (105 Auslieferung, 174 Forschung), Lint ohne Befund,
`pruefeNetzfreiheit` bestanden.

## ARTEFAKT-PRÜFUNG

Geprüft wurde die **gebaute** `app-offline-release.apk` (1,9 MB), nicht der
Quelltext.

| | Befund |
|---|---|
| Berechtigungen | **nur `RECORD_AUDIO`** (plus die von AndroidX erzeugte `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) |
| INTERNET | **nicht angefordert** |
| ACCESS_NETWORK_STATE | **nicht angefordert** |
| Komponenten | `MainActivity`, `DiktatBedienungshilfenDienst`, dazu AndroidX-Infrastruktur — **keine Forschungskomponente** |
| `ForschungActivity` | 0 Fundstellen |
| `Messplatzstart`, `Tonstrecke`, `messschluessel` | je 0 Fundstellen |
| Netzbibliotheken (okhttp, retrofit, HttpURLConnection, Socket) | je 0 |

Zwei Fundstellen für „INTERNET" in den Zeichenketten sind das polnische
Wort **„internetowych"** im eigenen Aufklärungstext — ein falscher Alarm der
Suche, kein Netzbezug.

Eine Fundstelle `java.net.URL` steht als **referenzierte** Klasse mit null
eigenen Methoden, stammt also aus einer eingebundenen Bibliothek. Der eigene
Quelltext hat null Fundstellen für `java.net`, und ohne INTERNET-Berechtigung
kann darüber nichts ins Netz.

## OFFEN

**Die APK vom 01:48 gilt als verwundbarer alter Prüfstand.** Es gibt
**keinen Beleg**, dass sie ausgenutzt wurde, und diese Behauptung wird auch
nicht aufgestellt. Sie darf nicht weiterverteilt und nicht für weitere
Messungen verwendet werden; auf den Prüfgeräten läuft nur noch der
berichtigte Stand.

**Semgrep meldete 0 Funde.** Das ist ein zusätzliches Signal, **kein Beleg
für Sicherheit** — die tragenden Belege sind die Negativtests am Gerät und
die Prüfung des gebauten Erzeugnisses oben.

**Die Bedienungshilfen-Nutzung** ist geprüft und deklariert, aber die
Play-Console-Erklärung ist noch nicht ausgefüllt. Das ist eine Aufgabe vor
der Veröffentlichung, kein offener Mangel im Code.

---

**Sicherheit ist hiermit eingefroren.** Weiter mit dem Produkt.
