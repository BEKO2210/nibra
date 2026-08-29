#!/usr/bin/env python3
"""Wer hält ein Objekt fest? Haltepfade aus einem Abzug der Halde.

Die Diagnose auf dem Gerät sagt, welche ART von Objekt ihre Sitzung
überlebt. Sie sagt nicht, an welcher Kette sie hängt -- an unserem
Zuhörer, an einer Sammlung im Programm, am Erkennerdienst des Geräts.
Genau das beantwortet dieses Werkzeug.

    hprof-conv roh.hprof abzug.hprof
    python3 scripts/haldenpfade.py abzug.hprof android.speech.SpeechRecognizer

**Es wird nichts geraten.** Findet sich kein Pfad zu einer Wurzel, steht
das so im Ergebnis. Ein Objekt ohne Pfad zur Wurzel ist entweder schon
unerreichbar -- dann wäre die Zählung auf dem Gerät falsch gewesen -- oder
der Abzug ist unvollständig. Beides ist ein Befund und keine Fußnote.

Geschrieben, weil auf diesem Rechner kein Auswerter lag. Blogfassungen des
Formats sind an mehreren Stellen falsch; maßgeblich ist die Beschreibung im
OpenJDK-Quelltext (hprof binary format).
"""

import struct
import sys
from collections import defaultdict, deque

# Grössen der Feldarten, wie im Format festgelegt.
GROESSEN = {2: None, 4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
OBJEKT = 2

WURZELARTEN = {
    0xFF: "unbekannt",
    0x01: "JNI global",
    0x02: "JNI lokal",
    0x03: "Java-Rahmen",
    0x04: "nativer Stapel",
    0x05: "Klasse (sticky)",
    0x06: "Faden-Block",
    0x07: "benutzter Monitor",
    0x08: "Faden-Objekt",
    0x89: "intern",
    0x8A: "Endlager",
    0x8B: "Debugger",
    0x8D: "VM intern",
    0x8E: "JNI Monitor",
}


class Abzug:
    def __init__(self, pfad):
        with open(pfad, "rb") as f:
            self.d = f.read()
        self.p = 0
        ende = self.d.index(b"\0", 0)
        self.kopf = self.d[:ende].decode("ascii", "replace")
        self.p = ende + 1
        self.idgroesse = self._u4()
        self._u4(); self._u4()          # Zeitstempel, zwei Worte

        self.texte = {}                 # String-Kennung -> Text
        self.klassenname = {}           # Klassen-Objektkennung -> Name
        self.klasse_von = {}            # Objekt -> Klassen-Objektkennung
        self.oberklasse = {}
        self.felder = {}                # Klasse -> [(Feldname, Art)]
        self.statische = {}             # Klasse -> [(Feldname, Wert)]
        self.verweise = defaultdict(list)   # Objekt -> [(Ziel, Beschriftung)]
        self.wurzeln = {}               # Objekt -> Wurzelart
        self.array_von = {}             # Array -> Elementklasse
        self.groesse = {}

    # -- Lesehilfen ---------------------------------------------------
    def _u1(self):
        v = self.d[self.p]; self.p += 1; return v

    def _u2(self):
        v = struct.unpack_from(">H", self.d, self.p)[0]; self.p += 2; return v

    def _u4(self):
        v = struct.unpack_from(">I", self.d, self.p)[0]; self.p += 4; return v

    def _id(self):
        if self.idgroesse == 4:
            return self._u4()
        v = struct.unpack_from(">Q", self.d, self.p)[0]; self.p += 8; return v

    def _wert(self, art):
        if art == OBJEKT:
            return self._id()
        n = GROESSEN[art]
        self.p += n
        return None

    # -- Hauptschleife ------------------------------------------------
    def lies(self):
        n = len(self.d)
        while self.p < n:
            tag = self._u1()
            self._u4()                  # Zeit seit dem Anfang
            laenge = self._u4()
            ende = self.p + laenge
            if tag == 0x01:             # STRING IN UTF8
                kennung = self._id()
                self.texte[kennung] = self.d[self.p:ende].decode("utf-8", "replace")
            elif tag == 0x02:           # LOAD CLASS
                self._u4()
                objekt = self._id()
                self._u4()
                self.klassenname[objekt] = self.texte.get(self._id(), "?")
            elif tag in (0x0C, 0x1C):   # HEAP DUMP (SEGMENT)
                self._haldenteil(ende)
            self.p = ende
        # Klassennamen nachtragen, die erst im Haldenteil auftauchten
        for k in list(self.klassenname):
            self.klassenname[k] = self.klassenname[k].replace("/", ".")

    def _haldenteil(self, ende):
        while self.p < ende:
            art = self._u1()
            if art in WURZELARTEN:
                objekt = self._id()
                # Zusatzworte je nach Wurzelart überspringen
                zusatz = {0x01: 1, 0x02: 2, 0x03: 2, 0x04: 1, 0x06: 1,
                          0x08: 2, 0x8E: 2}.get(art, 0)
                if art == 0x01:
                    self._id()
                else:
                    for _ in range(zusatz):
                        self._u4()
                self.wurzeln.setdefault(objekt, WURZELARTEN[art])
            elif art == 0x20:
                self._klassenabzug()
            elif art == 0x21:
                self._objektabzug()
            elif art == 0x22:
                self._objektfeldabzug()
            elif art == 0x23:
                self._rohfeldabzug()
            else:
                raise ValueError(f"unbekannte Marke 0x{art:02X} bei {self.p}")

    def _klassenabzug(self):
        klasse = self._id()
        self._u4()
        ober = self._id()
        lader = self._id()
        self._id(); self._id(); self._id(); self._id()
        self.groesse[klasse] = self._u4()
        self.oberklasse[klasse] = ober
        if ober:
            self.verweise[klasse].append((ober, "<Oberklasse>"))
        if lader:
            self.verweise[klasse].append((lader, "<Klassenlader>"))

        for _ in range(self._u2()):     # Konstantenvorrat
            self._u2()
            self._wert(self._u1())

        statisch = []
        for _ in range(self._u2()):
            name = self.texte.get(self._id(), "?")
            wert = self._wert(self._u1())
            if wert:
                statisch.append((name, wert))
                # Statische Felder sind der klassische Halter: was hier
                # hängt, lebt so lange wie die Klasse.
                self.verweise[klasse].append((wert, f"static {name}"))
        self.statische[klasse] = statisch

        felder = []
        for _ in range(self._u2()):
            name = self.texte.get(self._id(), "?")
            felder.append((name, self._u1()))
        self.felder[klasse] = felder

    def _feldliste(self, klasse):
        """Felder der Klasse und aller Oberklassen, in Lesereihenfolge."""
        liste = []
        k = klasse
        while k:
            liste.extend(self.felder.get(k, []))
            k = self.oberklasse.get(k)
        return liste

    def _objektabzug(self):
        objekt = self._id()
        self._u4()
        klasse = self._id()
        laenge = self._u4()
        ende = self.p + laenge
        self.klasse_von[objekt] = klasse
        for name, art in self._feldliste(klasse):
            if self.p >= ende:
                break
            wert = self._wert(art)
            if art == OBJEKT and wert:
                self.verweise[objekt].append((wert, name))
        self.p = ende

    def _objektfeldabzug(self):
        feld = self._id()
        self._u4()
        anzahl = self._u4()
        self.array_von[feld] = self._id()
        for i in range(anzahl):
            ziel = self._id()
            if ziel:
                self.verweise[feld].append((ziel, f"[{i}]"))

    def _rohfeldabzug(self):
        feld = self._id()
        self._u4()
        anzahl = self._u4()
        art = self._u1()
        self.p += anzahl * GROESSEN[art]
        self.groesse[feld] = anzahl * GROESSEN[art]

    # -- Auswertung ---------------------------------------------------
    def name_von(self, objekt):
        if objekt in self.klassenname:
            return f"class {self.klassenname[objekt]}"
        klasse = self.klasse_von.get(objekt)
        if klasse is not None:
            return self.klassenname.get(klasse, f"?{klasse:x}")
        elem = self.array_von.get(objekt)
        if elem is not None:
            return f"{self.klassenname.get(elem, '?')}[]"
        return f"?{objekt:x}"

    def haltepfade(self, klassenname, hoechstens=5):
        """Kürzeste Ketten von einer Wurzel zu Objekten dieser Klasse.

        Rückwärts gesucht: von den Zielen zu einer Wurzel. Vorwärts von
        allen Wurzeln aus wäre der halbe Abzug abzulaufen, für nichts.
        """
        ziele = [o for o, k in self.klasse_von.items()
                 if self.klassenname.get(k, "") == klassenname]
        if not ziele:
            return [], 0

        rueck = defaultdict(list)
        for quelle, kanten in self.verweise.items():
            for ziel, beschriftung in kanten:
                rueck[ziel].append((quelle, beschriftung))

        pfade = []
        for ziel in ziele[:hoechstens]:
            gesehen = {ziel}
            schlange = deque([(ziel, [])])
            gefunden = None
            while schlange and not gefunden:
                knoten, weg = schlange.popleft()
                if knoten in self.wurzeln and weg:
                    gefunden = (knoten, weg)
                    break
                if len(weg) > 25:
                    continue
                for halter, beschriftung in rueck.get(knoten, []):
                    if halter in gesehen:
                        continue
                    gesehen.add(halter)
                    schlange.append((halter, weg + [(knoten, beschriftung, halter)]))
            pfade.append((ziel, gefunden))
        return pfade, len(ziele)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    abzug = Abzug(sys.argv[1])
    abzug.lies()
    gesucht = sys.argv[2]

    print(f"Abzug: {abzug.kopf}, Kennungsgrösse {abzug.idgroesse}")
    print(f"Klassen {len(abzug.klassenname)}, Objekte {len(abzug.klasse_von)}, "
          f"Wurzeln {len(abzug.wurzeln)}")
    print()

    if gesucht == "--verteilung":
        zaehl = defaultdict(int)
        for objekt, klasse in abzug.klasse_von.items():
            zaehl[abzug.klassenname.get(klasse, "?")] += 1
        for name, n in sorted(zaehl.items(), key=lambda x: -x[1])[:40]:
            print(f"  {n:8d}  {name}")
        return 0

    pfade, gesamt = abzug.haltepfade(gesucht)
    if gesamt == 0:
        print(f"Keine Instanz von {gesucht} im Abzug.")
        print("Das ist ein Befund: entweder lebt keine mehr, oder der Abzug")
        print("wurde an anderer Stelle genommen als die Zählung.")
        return 0

    print(f"{gesamt} Instanzen von {gesucht}, davon {len(pfade)} verfolgt.")
    print()
    for objekt, gefunden in pfade:
        print(f"Instanz 0x{objekt:x}")
        if not gefunden:
            print("  KEIN PFAD ZU EINER WURZEL.")
            print("  Das Objekt ist unerreichbar -- es wartet nur auf die")
            print("  nächste Bereinigung und wird nicht festgehalten.")
            print()
            continue
        wurzel, weg = gefunden
        print(f"  Wurzel: {abzug.wurzeln[wurzel]} -- {abzug.name_von(wurzel)}")
        for knoten, beschriftung, halter in reversed(weg):
            print(f"    {abzug.name_von(halter)}  .{beschriftung}  ->  "
                  f"{abzug.name_von(knoten)}")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
