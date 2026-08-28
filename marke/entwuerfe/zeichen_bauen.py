#!/usr/bin/env python3
"""Baut aus dem Higgsfield-Entwurf A das fertige App-Zeichen von Nibra.

Der Entwurf traegt das Motiv (Federspitze mit austretender Welle), hat aber
drei technische Maengel: acht Pfade in vier Farben statt zwei, eine nicht
spiegelgleiche Welle, und er fuellt das Quadrat bis an den Rand.

Der Weg hier behebt alle drei, ohne das Motiv anzutasten:

1. gross rendern
2. auf zwei Farben schwellen -- die Schattierungen fallen weg
3. die linke Haelfte nehmen und spiegeln -- danach ist die Symmetrie exakt,
   nicht nur ungefaehr
4. zurueck in Pfade vektorisieren
5. in die Sicherheitszone des Adaptive Icon einpassen (Radius 33 um 54,54
   bei 108 Einheiten Kantenlaenge)
6. als VectorDrawable schreiben
"""

from pathlib import Path

import cairosvg
import numpy as np
import potrace
from PIL import Image

HIER = Path(__file__).parent
QUELLE = HIER / "logo" / "A-feder-welle.svg"
ZIEL = HIER / "zeichen"
ZIEL.mkdir(exist_ok=True)

KANTE = 2048          # Aufloesung fuers Rechnen
VIEWPORT = 108.0      # Adaptive Icon
MITTE = 54.0
SICHER = 33.0         # Radius der Sicherheitszone (66 Durchmesser)
SICHTBAR = 72.0       # Kantenlaenge der ueberhaupt sichtbaren Flaeche

ZEICHENFARBE = "#F6F3EE"
FLAECHENFARBE = "#2F6F63"


def gerendert() -> Image.Image:
    """Den Entwurf als Graustufenbild."""
    roh = cairosvg.svg2png(url=str(QUELLE), output_width=KANTE, output_height=KANTE)
    (ZIEL / "01-gerendert.png").write_bytes(roh)
    return Image.open(ZIEL / "01-gerendert.png").convert("L")


def zweifarbig(bild: Image.Image) -> np.ndarray:
    """Wahr, wo das Zeichen steht.

    Der Entwurf ist helles Elfenbein auf dunklem Teal; die Schwelle in der
    Mitte trennt beides und wirft die Zwischentoene weg.
    """
    feld = np.asarray(bild, dtype=np.uint8)
    return feld > 128


def gespiegelt(maske: np.ndarray) -> np.ndarray:
    """Linke Haelfte behalten, rechte daraus spiegeln.

    Damit ist die Symmetrie nicht mehr Ansichtssache: jeder Bildpunkt rechts
    ist die Entsprechung eines Punktes links.
    """
    hoehe, breite = maske.shape
    halb = breite // 2
    links = maske[:, :halb]
    return np.concatenate([links, np.fliplr(links)], axis=1)


def gekappt(maske: np.ndarray, schwelle: float) -> np.ndarray:
    """Schneidet die Wellenauslaeufer ab, wo sie ohnehin unsichtbar sind.

    Die Welle laeuft nach beiden Seiten in Haarlinien aus, die zuletzt einen
    Bildpunkt dick sind. Sie tragen kein Gewicht, beanspruchen aber ein
    Drittel der Breite -- wer danach skaliert, bekommt eine winzige Feder.
    Der Schnitt liegt bei `schwelle` der groessten Spaltendicke; dort ist der
    Strich so duenn, dass die Kante im fertigen Zeichen nicht auffaellt.

    Geschnitten wird spiegelgleich um die Mittelachse, damit die Symmetrie
    erhalten bleibt.
    """
    spalten = maske.sum(axis=0)
    tragend = np.nonzero(spalten > spalten.max() * schwelle)[0]
    mitte = maske.shape[1] // 2
    weite = int(max(mitte - tragend[0], tragend[-1] - mitte))
    beschnitten = np.zeros_like(maske)
    von, bis = max(0, mitte - weite), min(maske.shape[1], mitte + weite)
    beschnitten[:, von:bis] = maske[:, von:bis]
    return beschnitten


def pfade(maske: np.ndarray):
    """Die Maske als Pfade. potrace erwartet Wahr dort, wo Farbe ist.

    Zwei Fallen von potracer: der Typ muss `bool` bleiben (mit `uint8` oder
    `uint32` kommt nur eine Kurve um das ganze Bild heraus), und die Maske
    muss umgedreht werden -- potracer verfolgt die *falschen* Punkte, so
    dass sonst der Hintergrund als Flaeche und das Zeichen als Loch
    herauskommt.
    """
    return potrace.Bitmap(~maske.astype(bool)).trace(
        turdsize=8,             # Krümel unter 8 Punkten wegwerfen
        alphamax=1.0,           # Ecken bleiben Ecken
        opticurve=1,
        opttolerance=0.2,
    )


KERNSCHWELLE = 0.04
KAPPSCHWELLE = 0.12


def kern_groesse(maske: np.ndarray) -> tuple[float, float]:
    """Breite und Hoehe des tragenden Teils, in Bildpunkten.

    Spalten und Zeilen, die weniger als KERNSCHWELLE der groessten Dicke
    haben, gelten als Auslauf und zaehlen nicht mit.
    """
    spalten = maske.sum(axis=0)
    zeilen = maske.sum(axis=1)
    x = np.nonzero(spalten > spalten.max() * KERNSCHWELLE)[0]
    y = np.nonzero(zeilen > zeilen.max() * KERNSCHWELLE)[0]
    return float(x[-1] - x[0]), float(y[-1] - y[0])


def weitester_punkt_roh(maske: np.ndarray) -> float:
    """Groesster Abstand eines Zeichenpunktes vom Mittelpunkt des Rechtecks."""
    y, x = np.nonzero(maske)
    mitte_x = (x.min() + x.max()) / 2
    mitte_y = (y.min() + y.max()) / 2
    return float(np.hypot(x - mitte_x, y - mitte_y).max())


def grenzen(kurven) -> tuple[float, float, float, float]:
    xs, ys = [], []
    for kurve in kurven:
        xs.append(kurve.start_point.x)
        ys.append(kurve.start_point.y)
        for stueck in kurve:
            for punkt in ((stueck.c1, stueck.c2, stueck.end_point)
                          if stueck.is_corner is False
                          else (stueck.c, stueck.end_point)):
                xs.append(punkt.x)
                ys.append(punkt.y)
    return min(xs), min(ys), max(xs), max(ys)


def als_pfaddaten(kurven, skala: float, versatz_x: float, versatz_y: float,
                  links: float, oben: float) -> str:
    """Die Kurven als `pathData`, bereits in Viewport-Einheiten."""

    def x(wert: float) -> float:
        return (wert - links) * skala + versatz_x

    def y(wert: float) -> float:
        return (wert - oben) * skala + versatz_y

    teile = []
    for kurve in kurven:
        p = kurve.start_point
        teile.append(f"M{x(p.x):.3f},{y(p.y):.3f}")
        for stueck in kurve:
            if stueck.is_corner:
                teile.append(f"L{x(stueck.c.x):.3f},{y(stueck.c.y):.3f}")
                teile.append(f"L{x(stueck.end_point.x):.3f},{y(stueck.end_point.y):.3f}")
            else:
                teile.append(
                    f"C{x(stueck.c1.x):.3f},{y(stueck.c1.y):.3f} "
                    f"{x(stueck.c2.x):.3f},{y(stueck.c2.y):.3f} "
                    f"{x(stueck.end_point.x):.3f},{y(stueck.end_point.y):.3f}"
                )
        teile.append("Z")
    return " ".join(teile)


def main() -> None:
    maske = gekappt(gespiegelt(zweifarbig(gerendert())), KAPPSCHWELLE)
    Image.fromarray((maske * 255).astype(np.uint8)).save(ZIEL / "02-gespiegelt.png")

    ergebnis = pfade(maske)
    kurven = list(ergebnis)
    links, oben, rechts, unten = grenzen(kurven)
    breite, hoehe = rechts - links, unten - oben

    # Einpassen nach dem *tragenden* Teil, nicht nach der aeussersten Spitze.
    #
    # Die Welle laeuft nach beiden Seiten in Haarlinien aus, die zuletzt einen
    # Bildpunkt dick sind. Wuerde man danach skalieren, bliebe das Zeichen
    # winzig und die Feder unlesbar. Massgeblich ist darum der Kern: der
    # Bereich, in dem die Welle noch Gewicht traegt (KERNSCHWELLE der groessten
    # Spaltendicke). Er muss vollstaendig in die Sicherheitszone; die
    # Haarlinien duerfen darueber hinausreichen, solange sie im Viewport
    # bleiben. Genau so werden App-Zeichen ueblicherweise gesetzt -- die
    # Launcher-Maske nimmt dort nichts Tragendes weg.
    kern_breite, kern_hoehe = kern_groesse(maske)
    nach_kern = 2 * SICHER / ((kern_breite ** 2 + kern_hoehe ** 2) ** 0.5)

    # Zweite Bedingung: nichts darf ueberhaupt abgeschnitten werden -- auch
    # nicht von einer runden Maske. Die schaerfste gebraeuchliche Maske ist
    # der Kreis mit Durchmesser SICHTBAR. Gemessen wird darum der weiteste
    # Punkt des Zeichens vom Mittelpunkt seines Rechtecks aus, nicht die
    # Rechteckdiagonale: sonst waere die Grenze zu streng.
    nach_sichtbar = (SICHTBAR / 2) / weitester_punkt_roh(maske)

    skala = min(nach_kern, nach_sichtbar)
    versatz_x = MITTE - breite * skala / 2
    versatz_y = MITTE - hoehe * skala / 2

    daten = als_pfaddaten(kurven, skala, versatz_x, versatz_y, links, oben)

    # Nachweis: liegt wirklich alles im Kreis?
    import re
    punkte = [tuple(map(float, paar.split(",")))
              for paar in re.findall(r"(-?\d+\.\d+,-?\d+\.\d+)", daten)]
    weiteste = max(((px - MITTE) ** 2 + (py - MITTE) ** 2) ** 0.5 for px, py in punkte)

    vordergrund = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Nibra-Zeichen: Federspitze mit austretender Welle.
     Entwurf aus Higgsfield/Recraft, danach auf zwei Farben gebracht, an der
     Mittelachse gespiegelt und in die Sicherheitszone des Adaptive Icon
     eingepasst. Weitester Punkt: {weiteste:.2f} von {SICHER:.0f} erlaubt. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{ZEICHENFARBE}"
        android:fillType="evenOdd"
        android:pathData="{daten}" />
</vector>
'''
    (ZIEL / "nb_zeichen_vordergrund.xml").write_text(vordergrund, encoding="utf-8")

    hintergrund = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{FLAECHENFARBE}"
        android:pathData="M0,0 H108 V108 H0 Z" />
</vector>
'''
    (ZIEL / "nb_zeichen_hintergrund.xml").write_text(hintergrund, encoding="utf-8")

    # Dieselbe Geometrie als SVG, nur zum Ansehen.
    (ZIEL / "vorschau.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">'
        f'<rect width="108" height="108" rx="24" fill="{FLAECHENFARBE}"/>'
        f'<path fill="{ZEICHENFARBE}" fill-rule="evenodd" d="{daten}"/></svg>',
        encoding="utf-8")

    # Dasselbe Zeichen als 24dp-Symbol fuer die Oberflaeche. Einfarbig
    # schwarz, damit die App es ueber `tint` in jeder Lage einfaerben kann.
    skala24 = 24.0 / max(breite, hoehe)
    daten24 = als_pfaddaten(
        kurven, skala24,
        12.0 - breite * skala24 / 2, 12.0 - hoehe * skala24 / 2,
        links, oben)
    (ZIEL / "nb_zeichen.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Nibra-Zeichen als Symbol. Einfarbig, damit die App es einfaerben kann. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:fillType="evenOdd"
        android:pathData="{daten24}" />
</vector>
''', encoding="utf-8")

    # Nachweis der Spiegelsymmetrie: das fertige Zeichen rendern und die
    # linke Haelfte mit der gespiegelten rechten vergleichen.
    cairosvg.svg2png(url=str(ZIEL / "vorschau.svg"),
                     write_to=str(ZIEL / "03-pruefung.png"),
                     output_width=1024, output_height=1024)
    pruef = np.asarray(Image.open(ZIEL / "03-pruefung.png").convert("L"), dtype=np.int16)
    halb = pruef.shape[1] // 2
    abweichung = np.abs(pruef[:, :halb] - np.fliplr(pruef[:, halb:]))
    print(f"Symmetrie:         groesste Abweichung {abweichung.max()} von 255, "
          f"Mittel {abweichung.mean():.4f}")

    print(f"Pfadkurven:        {len(kurven)}")
    print(f"Rohgroesse:        {breite:.0f} x {hoehe:.0f} Punkte")
    print(f"Skala:             {skala:.5f}")
    print(f"Weitester Punkt:   {weiteste:.2f}  (erlaubt {SICHER:.0f})")
    print(f"pathData-Laenge:   {len(daten)} Zeichen")


if __name__ == "__main__":
    main()
