# Englische Bezugsaufnahmen (FLEURS en_us)

**Das Audio liegt nicht im Repo.** Nur `verzeichnis.json` ist verfolgt.

Das ist dieselbe Regel wie auf der deutschen Seite: fremde Aufnahmen unter
CC-BY-4.0 dürften zwar weitergegeben werden, aber es gibt keinen Grund,
sie über dieses Repo zu verbreiten -- und 180 MB Fremddaten in der
Versionsgeschichte helfen niemandem.

## Der Nachweis hängt nicht am Audio

Ein Lauf ist ohne die Dateien vollständig nachvollziehbar:

| woher | was |
|---|---|
| `verzeichnis.json` | für alle 200 Clips: Quellkennung, Bezugstext, Dauer, Rate |
| `../en200/lauf.json` | Datensatzfassung, Saat, Commit, je Clip SHA-256 **vor und nach** der Wandlung |

Wer den Lauf wiederholt, holt sich die Clips über die Quellkennung aus
`google/fleurs`, wandelt sie nach denselben Regeln und vergleicht die
Prüfsummen. Weichen sie ab, wurde etwas anderes gemessen -- und man merkt
es, statt es zu übersehen.

Genau dafür wurden die Prüfsummen aufgenommen. Ohne sie wäre das Audio im
Repo notwendig gewesen.

## Auf dieser Maschine

Die Dateien liegen weiterhin unter `roh/`, `pcm/` und `pilot/`. Sie sind
nur nicht verfolgt. Wer sie löscht, verliert nichts, was nicht wieder zu
beschaffen wäre.
