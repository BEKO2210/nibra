#!/usr/bin/env python3
"""Baut aus einem Handy-Bildschirmfoto ein Play-Store-Bild in Markenoptik.

    python3 scripts/store_bild.py <foto.png> "<Überschrift>" "<Unterzeile>" <ziel.png>

Warum überhaupt ein Skript und nicht Handarbeit im Bildprogramm: die Store-Bilder
werden bei jedem Release neu gebraucht, und von Hand gebaute Bilder driften
auseinander — andere Ränder, andere Schriftgröße, anderer Verlauf. Ein Skript
hält alle Bilder einer Serie deckungsgleich.
"""

import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# --- Marke ----------------------------------------------------------------
# Fest verdrahtet und nicht als Parameter: die Marke ist keine Option, die man
# pro Aufruf anders wählt. Wer sie ändern will, ändert sie hier an einer Stelle.
ACCENT = (47, 111, 99)      # #2F6F63 warmes Teal
DARK = (20, 51, 46)         # #14332E dunkler Akzent
CREAM = (246, 243, 238)     # #F6F3EE Papier

FONT_DIR = "app/src/main/res/font"
FRAUNCES_SEMIBOLD = f"{FONT_DIR}/fraunces_semibold.ttf"
INTER_REGULAR = f"{FONT_DIR}/inter_regular.ttf"

# --- Maße -----------------------------------------------------------------
# 1080x1920 ist das Format, das Play für Handy-Bilder ohne Murren annimmt
# (Kante zwischen 320 und 3840, Seitenverhältnis nah an 16:9).
BREITE, HOEHE = 1080, 1920

RAND_SEITE = 88            # Seitenrand für den Text
RAND_OBEN = 110            # Luft über der Überschrift
RAND_UNTEN = 96            # Luft unter dem Foto
ABSTAND_TEXT_FOTO = 84     # Trennt Text und Foto sichtbar, damit sich beide
                           # nie berühren — Play skaliert die Bilder in der
                           # Vorschau stark herunter, dann verschmilzt Knappes.
ZEILENABSTAND = 14         # zusätzliche Luft zwischen zwei Überschriftzeilen
ABSTAND_UEBERSCHRIFT_UNTERZEILE = 30

UEBERSCHRIFT_MAX = 92      # Startgröße; wird verkleinert, bis zwei Zeilen reichen
UEBERSCHRIFT_MIN = 46      # darunter wird die Überschrift auf dem Handy unlesbar
UNTERZEILE_GROESSE = 40

ECKENRADIUS = 44           # entspricht ungefähr der Rundung echter Handy-Displays
SCHATTEN_UNSCHAERFE = 26
SCHATTEN_VERSATZ = 16


def verlauf(breite, hoehe):
    """Ruhiger diagonaler Verlauf ACCENT -> DARK.

    Diagonal statt rein senkrecht, weil ein senkrechter Verlauf hinter einem
    zentrierten Foto wie ein Fehldruck aussieht: die Kanten des Fotos laufen
    dann exakt parallel zu den Farbbändern.
    """
    bild = Image.new("RGB", (breite, hoehe))
    px = bild.load()
    for y in range(hoehe):
        # Der senkrechte Anteil überwiegt, damit der Kopfbereich hell bleibt
        # und das Foto weiter unten auf dunklerem Grund steht.
        ty = y / (hoehe - 1)
        for x in range(breite):
            t = ty * 0.8 + (x / (breite - 1)) * 0.2
            px[x, y] = (
                int(ACCENT[0] + (DARK[0] - ACCENT[0]) * t),
                int(ACCENT[1] + (DARK[1] - ACCENT[1]) * t),
                int(ACCENT[2] + (DARK[2] - ACCENT[2]) * t),
            )
    return bild


def umbrechen(zeichner, text, schrift, max_breite):
    """Bricht an Wortgrenzen um und gibt die Zeilen zurück.

    Ein Wort, das allein schon zu breit ist, wird nicht zerschnitten — dann
    meldet die aufrufende Stelle über die Zeilenzahl, dass die Schrift kleiner
    muss. Wörter mitten drin zu trennen sähe nach Fehler aus.
    """
    zeilen, aktuell = [], ""
    for wort in text.split():
        probe = f"{aktuell} {wort}".strip()
        if zeichner.textlength(probe, font=schrift) <= max_breite or not aktuell:
            aktuell = probe
        else:
            zeilen.append(aktuell)
            aktuell = wort
    if aktuell:
        zeilen.append(aktuell)
    return zeilen


def ueberschrift_setzen(zeichner, text, max_breite):
    """Sucht die größte Schriftgröße, mit der der Text in zwei Zeilen passt.

    Zwei Zeilen sind die Grenze, weil der Kopfbereich sonst so hoch wird, dass
    das Foto darunter zusammenschrumpft — und das Foto ist das, was den Nutzer
    überzeugt, nicht die Überschrift.
    """
    for groesse in range(UEBERSCHRIFT_MAX, UEBERSCHRIFT_MIN - 1, -2):
        schrift = ImageFont.truetype(FRAUNCES_SEMIBOLD, groesse)
        zeilen = umbrechen(zeichner, text, schrift, max_breite)
        passt_in_breite = all(
            zeichner.textlength(z, font=schrift) <= max_breite for z in zeilen
        )
        if len(zeilen) <= 2 and passt_in_breite:
            return schrift, zeilen
    # Notfall: kleinste Größe nehmen und umbrechen, was geht. Lieber ein
    # gedrängtes Bild als ein Absturz mitten im Release-Bau.
    schrift = ImageFont.truetype(FRAUNCES_SEMIBOLD, UEBERSCHRIFT_MIN)
    return schrift, umbrechen(zeichner, text, schrift, max_breite)


def foto_platte(foto, max_breite, max_hoehe):
    """Skaliert das Foto proportional und gibt es mit runden Ecken zurück.

    Bewusst nur skaliert, nie beschnitten: ein beschnittenes Bildschirmfoto
    zeigt eine Oberfläche, die es so in der App nicht gibt — im Store ist das
    eine Falschangabe, kein Gestaltungsmittel.
    """
    faktor = min(max_breite / foto.width, max_hoehe / foto.height)
    breite = max(1, int(foto.width * faktor))
    hoehe = max(1, int(foto.height * faktor))
    klein = foto.convert("RGBA").resize((breite, hoehe), Image.LANCZOS)

    maske = Image.new("L", (breite, hoehe), 0)
    ImageDraw.Draw(maske).rounded_rectangle(
        (0, 0, breite - 1, hoehe - 1), radius=ECKENRADIUS, fill=255
    )
    platte = Image.new("RGBA", (breite, hoehe), (0, 0, 0, 0))
    platte.paste(klein, (0, 0), maske)
    return platte, maske


def schatten_legen(grund, maske, x, y):
    """Weicher Schatten unter dem Foto, damit es über dem Grund zu schweben scheint.

    Kein aufgemalter Gerätrahmen: gezeichnete Knöpfe und Kameraloch passen nie
    zu dem Gerät, das der Betrachter in der Hand hält, und wirken billig. Der
    Schatten allein reicht, um die Fläche als Gerät lesbar zu machen.
    """
    rand = SCHATTEN_UNSCHAERFE * 3
    flaeche = Image.new("L", (maske.width + 2 * rand, maske.height + 2 * rand), 0)
    flaeche.paste(maske, (rand, rand))
    flaeche = flaeche.filter(ImageFilter.GaussianBlur(SCHATTEN_UNSCHAERFE))
    # Schwarz mit gedämpfter Deckkraft: ein voller Schwarzschatten würde auf
    # dem dunklen Verlauf als grauer Fleck auffallen.
    flaeche = flaeche.point(lambda v: int(v * 0.45))
    tinte = Image.new("RGB", flaeche.size, (8, 24, 21))
    grund.paste(tinte, (x - rand, y - rand + SCHATTEN_VERSATZ), flaeche)


def bauen(foto_pfad, ueberschrift, unterzeile, ziel_pfad):
    grund = verlauf(BREITE, HOEHE)
    zeichner = ImageDraw.Draw(grund)
    textbreite = BREITE - 2 * RAND_SEITE

    schrift_titel, zeilen = ueberschrift_setzen(zeichner, ueberschrift, textbreite)
    schrift_unter = ImageFont.truetype(INTER_REGULAR, UNTERZEILE_GROESSE)

    # Zeilenhöhe aus den Schriftmetriken, nicht aus der Höhe des konkreten
    # Textes: sonst rutschen Zeilen ohne Unterlänge enger zusammen als andere.
    aufstieg, abstieg = schrift_titel.getmetrics()
    zeilenhoehe = aufstieg + abstieg + ZEILENABSTAND

    y = RAND_OBEN
    for zeile in zeilen:
        # Zentriert, weil der Blick im Store zuerst mittig auf das Foto fällt
        # und ein linksbündiger Kopf darüber aus der Achse kippt.
        breite_zeile = zeichner.textlength(zeile, font=schrift_titel)
        zeichner.text(
            ((BREITE - breite_zeile) / 2, y), zeile, font=schrift_titel, fill=CREAM
        )
        y += zeilenhoehe

    y += ABSTAND_UEBERSCHRIFT_UNTERZEILE
    breite_unter = zeichner.textlength(unterzeile, font=schrift_unter)
    zeichner.text(
        ((BREITE - breite_unter) / 2, y), unterzeile, font=schrift_unter, fill=CREAM
    )
    a_unter, b_unter = schrift_unter.getmetrics()
    text_unterkante = y + a_unter + b_unter

    foto = Image.open(foto_pfad)
    oben = text_unterkante + ABSTAND_TEXT_FOTO
    platte, maske = foto_platte(
        foto, BREITE - 2 * RAND_SEITE * 2, HOEHE - RAND_UNTEN - oben
    )
    x = (BREITE - platte.width) // 2
    # Im verbleibenden Feld senkrecht mittig, damit ein kurzes Querformat-Foto
    # nicht unter der Überschrift klebt.
    y_foto = oben + (HOEHE - RAND_UNTEN - oben - platte.height) // 2

    schatten_legen(grund, maske, x, y_foto)
    grund.paste(platte, (x, y_foto), platte)
    grund.save(ziel_pfad)

    return {
        "ziel": ziel_pfad,
        "groesse": grund.size,
        "ueberschrift_groesse": schrift_titel.size,
        "zeilen": zeilen,
        "text_unterkante": int(text_unterkante),
        "foto": (x, y_foto, platte.width, platte.height),
        "abstand_text_foto": int(y_foto - text_unterkante),
        "rand_unten": HOEHE - (y_foto + platte.height),
    }


def main(argv):
    if len(argv) != 5:
        print(
            'Aufruf: python3 scripts/store_bild.py <foto.png> "<Überschrift>" '
            '"<Unterzeile>" <ziel.png>',
            file=sys.stderr,
        )
        return 2
    ergebnis = bauen(argv[1], argv[2], argv[3], argv[4])
    for schluessel, wert in ergebnis.items():
        print(f"{schluessel}: {wert}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
