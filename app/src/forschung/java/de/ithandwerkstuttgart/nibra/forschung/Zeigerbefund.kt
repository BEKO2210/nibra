package de.ithandwerkstuttgart.nibra.forschung

import java.io.File

/**
 * Was der Prozess offen hält -- nicht nur wie viel.
 *
 * Eine wachsende Zahl offener Dateizeiger sagt für sich genommen nichts.
 * Erst wenn man weiß, **was** offen bleibt, lässt sich entscheiden, ob ein
 * Vorrat angelegt wird oder etwas liegen bleibt: ein Rohr, das keiner mehr
 * liest, ist ein Fehler; eine Steckdose zum Erkennerdienst, die
 * wiederverwendet wird, ist gewollt.
 *
 * Gelesen werden nur **Ziele** von Verweisen -- Art und Pfad, kein Inhalt.
 * Tondaten kommen hier nie vor und dürfen es nie.
 */
object Zeigerbefund {

    /**
     * Ordnet ein Verweisziel einer Art zu.
     *
     * Die Reihenfolge der Prüfungen ist wesentlich: `anon_inode:[eventfd]`
     * beginnt wie jedes andere `anon_inode`, und ein Pfad unter `/dev/`
     * kann alles Mögliche sein. Vom Genauen zum Allgemeinen, sonst
     * verschwinden die aussagekräftigen Arten im Sammelbecken.
     */
    fun einteilen(ziel: String): String = when {
        ziel.startsWith("pipe:") -> "Rohr"
        ziel.startsWith("socket:") -> "Steckdose"
        ziel.contains("[eventfd]") -> "Ereigniszähler"
        ziel.contains("dmabuf") -> "Grafikpuffer"
        ziel.contains("[timerfd]") -> "Wecker"
        ziel.contains("[signalfd]") -> "Signal"
        ziel.contains("binder") -> "Binder"
        ziel.contains("ashmem") || ziel.contains("memfd") -> "geteilter Speicher"
        ziel.endsWith(".apk") || ziel.contains("/apk/") -> "Programmpaket"
        ziel.endsWith(".ttf") || ziel.endsWith(".otf") -> "Schrift"
        ziel.endsWith(".so") -> "Programmbibliothek"
        ziel.startsWith("/dev/") -> "Gerät ${ziel.removePrefix("/dev/").substringBefore('/')}"
        ziel.startsWith("/data/") -> "Datei in /data"
        ziel.startsWith("/system/") -> "Datei in /system"
        ziel.startsWith("anon_inode:") -> "namenlos ${ziel.removePrefix("anon_inode:")}"
        ziel.isBlank() -> "nicht lesbar"
        else -> "sonstiges"
    }

    /**
     * Zählt die offenen Zeiger nach Art.
     *
     * Verweise, die zwischen Auflisten und Lesen verschwinden, werden
     * übergangen -- das ist der Normalfall in einem laufenden Prozess und
     * kein Fehler. Ist das Verzeichnis gar nicht lesbar, kommt eine **leere
     * Karte** zurück; wer sie mit „nichts offen" verwechselt, sieht am
     * Gesamtzähler, dass etwas nicht stimmt.
     */
    fun nachArt(): Map<String, Int> {
        val ordner = File("/proc/self/fd")
        val eintraege = ordner.listFiles() ?: return emptyMap()
        val zaehlung = mutableMapOf<String, Int>()
        eintraege.forEach { eintrag ->
            // **readlink zuerst, canonicalPath nur als Rückfall.**
            // canonicalPath löst den Verweis auf und liefert für ein Rohr
            // wieder einen Pfad unter /proc -- niemals „pipe:[123]". In der
            // ersten Fassung landeten dadurch 93 von 151 Zeigern im
            // Sammelbecken „sonstiges", und die eine Frage, für die dieser
            // Befund gebaut ist -- welche Art wächst? -- war nicht zu
            // beantworten.
            val ziel = runCatching {
                android.system.Os.readlink(eintrag.absolutePath)
            }.getOrElse {
                runCatching { eintrag.canonicalPath }.getOrNull()
            } ?: return@forEach
            val art = einteilen(ziel)
            zaehlung[art] = (zaehlung[art] ?: 0) + 1
        }
        return zaehlung
    }

    /**
     * Der Unterschied zweier Zählungen, nur die Arten, die sich geändert
     * haben. Arten, die verschwunden sind, erscheinen mit negativem Wert --
     * sonst sähe ein Tausch von zwanzig Rohren gegen zwanzig Steckdosen wie
     * gar keine Änderung aus.
     */
    /**
     * Beispielziele für eine Art -- höchstens [BEISPIELE] Stück.
     *
     * Damit „sonstiges: 42" nicht als Rätsel im Bericht steht. Gezeigt
     * werden nur Ziele, keine Inhalte; Pfade unter dem Datenverzeichnis
     * werden auf ihren letzten Teil gekürzt, damit nichts Persönliches
     * mitwandert.
     */
    fun beispieleFuer(art: String): List<String> {
        val eintraege = File("/proc/self/fd").listFiles() ?: return emptyList()
        return eintraege.mapNotNull { eintrag ->
            val ziel = runCatching {
                android.system.Os.readlink(eintrag.absolutePath)
            }.getOrNull() ?: return@mapNotNull null
            if (einteilen(ziel) != art) null else gekuerzt(ziel)
        }.distinct().take(BEISPIELE)
    }

    /** Nur so viel vom Pfad, wie zum Einordnen nötig ist. */
    private fun gekuerzt(ziel: String): String = when {
        ziel.startsWith("/data/") -> "/data/.../${ziel.substringAfterLast('/')}"
        else -> ziel
    }

    const val BEISPIELE = 5

    fun unterschied(vorher: Map<String, Int>, nachher: Map<String, Int>): Map<String, Int> =
        (vorher.keys + nachher.keys).mapNotNull { art ->
            val d = (nachher[art] ?: 0) - (vorher[art] ?: 0)
            if (d == 0) null else art to d
        }.sortedByDescending { it.second }.toMap()
}
