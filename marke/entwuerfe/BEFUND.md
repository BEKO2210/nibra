# Logo-Entwürfe, 28.08.2026

Erzeugt mit Higgsfield / Recraft V4.1 (`model_type=vector`), 10 Credits je
Entwurf. Der Auftrag steht in `AUFTRAG-LOGO.md`. Alle drei kamen als echtes
SVG zurück, nicht als Rasterbild.

| Entwurf | Urteil |
|---|---|
| **A — Feder + Welle** | **Gewählt.** Liest sich sofort als Federspitze mit austretender Welle, flächig, zwei Farben, kein Verlauf. Trifft AUFTRAG.md. |
| B — Negativraum | Verworfen. Liest sich als Tulpe oder Schote, nicht als Feder. |
| C — Monolinie | Verworfen. Wirkt wie Krone oder Tintenfisch; die Wellenhaken sind unruhig. |

## Was an A noch fehlt

1. **Spiegelsymmetrie exakt herstellen.** Die Welle läuft links und rechts
   nicht gleich aus. AUFTRAG.md verlangt strenge Symmetrie.
2. **Sicherheitszone.** Die Welle reicht bis an den linken und rechten Rand.
   Ein Adaptive Icon zeigt nur den Kreis mit Durchmesser 66 von 108 um die
   Mitte — so wird die Welle abgeschnitten. Das Zeichen muss hineinskaliert
   werden.
3. **Nach VectorDrawable wandeln**, getrennt in Vordergrund (transparent, nur
   das Zeichen) und Hintergrund (einfarbig `#2F6F63`).

## Warum das alte Zeichen ersetzt wird

`marke/icon_1024.png` und die `mipmap-*/ic_launcher*.png`:

- Rasterbild statt Vektor, nicht umfärbbar, nicht skalierbar
- Radialer Verlauf mit Rauschen im Hintergrund — sieht maschinell erzeugt aus
- Welle links und rechts nicht spiegelgleich
- Der „Vordergrund" enthält den Hintergrund mit; `<background>` greift nie und
  die Launcher-Maske schneidet die Federspitze oben an
- `ic_launcher.png`, `ic_launcher_round.png` und `ic_launcher_foreground.png`
  sind je Dichte dieselbe Datei
- `drawable/ic_launcher_foreground.png` zeigt etwas ganz anderes: fünf
  Equalizer-Balken mit orangen Konturen. Orange kommt in der Palette nicht vor,
  und Balken sind das Klischee, das AUFTRAG.md ausdrücklich ausschließt.
  Unbenutzter Altbestand.
