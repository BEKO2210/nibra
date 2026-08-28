# Lauf 2.5 — Beleg am Gerät

Gerät: Samsung SM-A156B, Android 16 (API 36), 1080×2340 bei Dichte 450
(384 dp breit). Release-Build, signiert mit dem Upload-Schlüssel.
Bedienungshilfen-Dienst eingeschaltet und gebunden:

```
Bound services:{Service[label=Loqui, id=de.ithandwerkstuttgart.loqui/…]}
Enabled services:{{de.ithandwerkstuttgart.loqui/…DiktatBedienungshilfenDienst}}
accessibility_enabled = 1
```

Testseite über `adb reverse tcp:8099` in Chrome geöffnet, drei Felder:
normaler Text, Passwort, PIN. Die Knoten meldeten:

```
Feld: ""  password=false  [64,567][1015,716]     <- normal
Feld: ""  password=true   [64,921][1015,1070]    <- Passwort
Feld: ""  password=true   [64,1275][1015,1424]   <- PIN
```

## 01-normales-feld-blase-steht.png

Normales Textfeld hat den Fokus. Die Blase steht unten rechts.

## 02-passwortfeld-keine-blase.png

Passwortfeld hat den Fokus (Cursor sichtbar, Rand hell). Die Blase ist weg.

Damit stimmt die Zusage aus dem Einrichtungstext: „Der Dienst liest keine
Inhalte mit, sendet nichts und übergeht Passwortfelder."
