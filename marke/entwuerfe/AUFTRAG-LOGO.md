Baue das App-Zeichen der Android-App "Nibra" als **Android VectorDrawable XML** neu.
Arbeite im Verzeichnis /tmp/claude-1000/-home-belkis/2a6def37-c411-4eda-a7c8-bea26f5cc40c/scratchpad/logo/
und lege dort genau diese Dateien an:

1. `nb_zeichen_vordergrund.xml`  — Adaptive-Icon-Vordergrund
2. `nb_zeichen_hintergrund.xml`  — Adaptive-Icon-Hintergrund
3. `nb_zeichen.xml`              — dasselbe Zeichen als 24dp-Symbol für die Oberfläche
4. `vorschau.svg`                — dieselbe Geometrie als SVG, nur zum Ansehen
5. `NOTIZ.md`                    — womit du die Kurven konstruiert hast (Formeln/Koordinaten)

## Das Motiv (verbindlich, aus dem Lastenheft)

Eine **Federspitze (Schreibfeder-Nib), aus deren Spitze eine Welle austritt** —
gesprochenes Wort wird geschriebenes Wort.

VERBOTEN: Mikrofon, Sprechblase, Equalizer-Balken, Schallwellen-Bögen,
Buchstaben, Emoji, Verlauf/Gradient, Schlagschatten, Rauschen, Textur.

## Harte Vorgaben

- **Spiegelsymmetrie:** Das gesamte Zeichen muss exakt spiegelgleich zur
  senkrechten Mittelachse sein. Jede Kontrollpunkt-Koordinate links hat ihre
  Entsprechung rechts. Das ist die wichtigste Anforderung — die alte Fassung
  war asymmetrisch und wirkte dadurch maschinell erzeugt.
- **Geometrisch konstruiert, nicht freihändig:** Bögen aus Kreisen und
  Ellipsen mit benannten Radien ableiten, kubische Béziers mit begründeten
  Kontrollpunkten. In `NOTIZ.md` die Konstruktion nachvollziehbar aufschreiben.
  Keine „nachgezeichnet bis es passt"-Pfade.
- **Wellenform:** eine ruhige, stetige Sinus-artige Kurve, die an der
  Federspitze beginnt und nach beiden Seiten symmetrisch ausläuft, dünner
  werdend. Genau eine Wellenperiode je Seite, keine wilden Ausschläge.
- **Flächig, keine Verläufe.** Nur Volltonfarben.

## Adaptive-Icon-Geometrie (Android, Pflicht)

- Vordergrund UND Hintergrund: `android:width="108dp" android:height="108dp"`,
  `android:viewportWidth="108" android:viewportHeight="108"`.
- Der Vordergrund ist **transparent** bis auf das Zeichen. Er enthält
  **keinen** Hintergrund und keine Fläche.
- Das Zeichen liegt vollständig in der **Sicherheitszone**: ein Kreis mit
  Durchmesser 66 um den Mittelpunkt (54,54), also alles innerhalb von
  Radius 33. Launcher schneiden alles außerhalb ab. Die alte Fassung reichte
  bis an den Rand und wurde oben abgeschnitten.
- Der Hintergrund ist eine einfarbige, randlose Fläche über die vollen 108.

## Farben (aus marke.json, nicht abweichen)

- Zeichen (Vordergrund): `#F6F3EE`
- Hintergrundfläche:     `#2F6F63`
- Das 24dp-Oberflächensymbol `nb_zeichen.xml`: einfarbig
  `android:fillColor="#FF000000"` und zusätzlich
  `android:tint="?attr/colorControlNormal"` NICHT setzen — die App färbt es
  selbst ein. Viewport 24x24, Strichstärke 1.8 falls du Striche verwendest,
  passend zu den übrigen Symbolen der App.

## Prüfen, bevor du fertig meldest

- `xmllint --noout` über jede XML-Datei (oder gleichwertig) — muss fehlerfrei sein.
- Rechnerisch belegen, dass jeder Punkt des Vordergrunds innerhalb Radius 33
  um (54,54) liegt. Schreib das Ergebnis in `NOTIZ.md`.
- Rechnerisch belegen, dass die Form spiegelsymmetrisch ist. Ergebnis ebenfalls
  in `NOTIZ.md`.

Antworte am Ende knapp auf Deutsch: was du gebaut hast und wie die beiden
Prüfungen ausgegangen sind.
