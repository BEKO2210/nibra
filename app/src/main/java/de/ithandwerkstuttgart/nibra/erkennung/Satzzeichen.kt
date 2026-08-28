package de.ithandwerkstuttgart.nibra.erkennung

import java.util.Locale

/**
 * Gesprochene Satzzeichen. Android setzt ab API 33 selbst Punkt und Komma,
 * aber nicht in jeder Sprache und nicht immer dort, wo der Sprecher sie
 * haben will. Wer "Punkt" sagt, bekommt hier einen Punkt -- unabhaengig vom
 * Erkenner und ohne Netz.
 */
private val BEFEHLE: Map<String, List<Pair<String, String>>> = mapOf(
    "de" to listOf(
        "neuer absatz" to "\n\n",
        "neue zeile" to "\n",
        "zeilenumbruch" to "\n",
        "fragezeichen" to "?",
        "ausrufezeichen" to "!",
        "doppelpunkt" to ":",
        "strichpunkt" to ";",
        "semikolon" to ";",
        "gedankenstrich" to " —",
        "bindestrich" to "-",
        "komma" to ",",
        "punkt" to "."
    ),
    "en" to listOf(
        "new paragraph" to "\n\n",
        "new line" to "\n",
        "question mark" to "?",
        "exclamation mark" to "!",
        "exclamation point" to "!",
        "colon" to ":",
        "semicolon" to ";",
        "dash" to " —",
        "hyphen" to "-",
        "comma" to ",",
        "full stop" to ".",
        "period" to "."
    ),
    "fr" to listOf(
        "nouveau paragraphe" to "\n\n",
        "nouvelle ligne" to "\n",
        "point d'interrogation" to " ?",
        "point d'exclamation" to " !",
        "deux points" to " :",
        "point virgule" to " ;",
        "tiret" to "-",
        "virgule" to ",",
        "point" to "."
    ),
    "es" to listOf(
        "nuevo párrafo" to "\n\n",
        "nueva línea" to "\n",
        "signo de interrogación" to "?",
        "signo de exclamación" to "!",
        "dos puntos" to ":",
        "punto y coma" to ";",
        "guion" to "-",
        "coma" to ",",
        "punto" to "."
    ),
    "it" to listOf(
        "nuovo paragrafo" to "\n\n",
        "nuova riga" to "\n",
        "punto interrogativo" to "?",
        "punto esclamativo" to "!",
        "due punti" to ":",
        "punto e virgola" to ";",
        "trattino" to "-",
        "virgola" to ",",
        "punto" to "."
    ),
    "tr" to listOf(
        "yeni paragraf" to "\n\n",
        "yeni satır" to "\n",
        "soru işareti" to "?",
        "ünlem işareti" to "!",
        "iki nokta" to ":",
        "noktalı virgül" to ";",
        "tire" to "-",
        "virgül" to ",",
        "nokta" to "."
    ),
    "pl" to listOf(
        "nowy akapit" to "\n\n",
        "nowa linia" to "\n",
        "znak zapytania" to "?",
        "wykrzyknik" to "!",
        "dwukropek" to ":",
        "średnik" to ";",
        "myślnik" to "-",
        "przecinek" to ",",
        "kropka" to "."
    )
)

/** Vor diesen Zeichen steht im Deutschen und Englischen kein Leerzeichen. */
private val ANGEHAENGT = setOf('.', ',', ';', ':', '!', '?')

/**
 * Wandelt gesprochene Satzzeichen um, raeumt die Abstaende auf und setzt
 * Satzanfaenge gross.
 *
 * @param sprachCode z. B. "de-DE"; ausgewertet wird nur der Sprachteil.
 */
fun setzeSatzzeichen(text: String, sprachCode: String): String {
    if (text.isBlank()) return text
    val sprache = sprachCode.substringBefore('-').lowercase(Locale.ROOT)
    val locale = Locale.forLanguageTag(sprachCode.ifBlank { sprache })
    var ergebnis = text

    BEFEHLE[sprache].orEmpty().forEach { (wort, zeichen) ->
        val muster = Regex(
            "(?<![\\p{L}\\p{N}])" + Regex.escape(wort) + "(?![\\p{L}\\p{N}])",
            setOf(RegexOption.IGNORE_CASE)
        )
        ergebnis = muster.replace(ergebnis) { zeichen }
    }

    ergebnis = raeumeAbstaendeAuf(ergebnis)
    return grossNachSatzende(ergebnis, locale)
}

/** Kein Leerzeichen vor einem angehaengten Satzzeichen, keine doppelten. */
private fun raeumeAbstaendeAuf(text: String): String {
    var ergebnis = text
    ANGEHAENGT.forEach { zeichen ->
        ergebnis = ergebnis.replace(Regex("[ \\t]+\\" + zeichen), zeichen.toString())
    }
    ergebnis = ergebnis.replace(Regex("[ \\t]{2,}"), " ")
    ergebnis = ergebnis.replace(Regex("[ \\t]+\n"), "\n")
    ergebnis = ergebnis.replace(Regex("\n[ \\t]+"), "\n")
    // Nach einem Satzzeichen folgt ein Abstand, wenn direkt ein Wort anschliesst.
    ergebnis = ergebnis.replace(Regex("([.,;:!?])(?=[\\p{L}\\p{N}])"), "$1 ")
    return ergebnis.trim()
}

/** Erster Buchstabe und jeder Satzanfang gross. */
private fun grossNachSatzende(text: String, locale: Locale): String {
    val gebaut = StringBuilder(text.length)
    var satzanfang = true
    text.forEach { zeichen ->
        when {
            satzanfang && zeichen.isLetter() -> {
                gebaut.append(zeichen.toString().uppercase(locale))
                satzanfang = false
            }

            zeichen == '.' || zeichen == '!' || zeichen == '?' || zeichen == '\n' -> {
                gebaut.append(zeichen)
                satzanfang = true
            }

            else -> {
                if (!zeichen.isWhitespace()) satzanfang = false
                gebaut.append(zeichen)
            }
        }
    }
    return gebaut.toString()
}
