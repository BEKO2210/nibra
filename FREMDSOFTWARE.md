# Verwendete Fremdsoftware

Nibra wurde neu entwickelt. Der funktionale Ansatz der nachfolgend genannten Vorlage wurde übernommen; ihr Code wurde nicht fortgeschrieben. Die jeweiligen Urheberrechte und Lizenzbedingungen der verwendeten Fremdsoftware bleiben unberührt.

## aidictation

- Projekt: `writingmate/aidictation`
- Fundstelle: https://github.com/writingmate/aidictation
- Verwendung: Vorlage und konzeptioneller Ausgangspunkt für Diktieren und das Einfügen über einen Android-Bedienungshilfen-Dienst
- Lizenz: MIT License
- Copyright: Copyright (c) 2026 AI Dictation contributors

Vollständiger Lizenztext:

```text
MIT License

Copyright (c) 2026 AI Dictation contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```


## Inter

- Projekt: Inter
- Fundstelle: https://github.com/rsms/inter
- Verwendung: Textschrift der Oberfläche (`res/font/inter_regular.ttf`,
  `inter_medium.ttf`, `inter_semibold.ttf`)
- Lizenz: SIL Open Font License, Version 1.1
- Copyright: Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter)
- Änderung: Aus der variablen Originaldatei `Inter[opsz,wght].ttf` wurden die
  Schnitte 400, 500 und 600 bei optischer Größe 14 fest eingestellt und auf die
  Zeichen der sieben Oberflächensprachen beschnitten. Der Name „Inter“ ist kein
  reservierter Name im Sinne der Lizenz.

## Fraunces

- Projekt: Fraunces
- Fundstelle: https://github.com/undercasetype/Fraunces
- Verwendung: Titelschrift der Oberfläche (`res/font/fraunces_semibold.ttf`)
- Lizenz: SIL Open Font License, Version 1.1
- Copyright: Copyright 2018 The Fraunces Project Authors (https://github.com/undercasetype/Fraunces)
- Änderung: Aus der variablen Originaldatei
  `Fraunces[SOFT,WONK,opsz,wght].ttf` wurde der Schnitt 600 bei SOFT 30,
  WONK 0 und optischer Größe 28 fest eingestellt und auf die Zeichen der
  sieben Oberflächensprachen beschnitten. Der Name „Fraunces“ ist kein
  reservierter Name im Sinne der Lizenz.

Beide Schriften stehen unter demselben Lizenztext:

```text
This license is copied below, and is also available with a FAQ at:
https://scripts.sil.org/OFL


-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded, 
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```

## AndroidX und Jetpack Compose

Die folgenden in Nibra verwendeten Bibliotheken des Android Open Source Project stehen unter der Apache License 2.0:

- AndroidX Core KTX 1.15.0
- AndroidX Lifecycle Runtime KTX und Lifecycle ViewModel Compose 2.8.7
- AndroidX Activity Compose 1.9.3
- Jetpack Compose BOM 2024.11.00 mit Compose UI, UI Graphics, UI Tooling Preview und Material 3
- AndroidX Navigation Compose 2.8.4
- AndroidX Hilt Navigation Compose 1.2.0
- AndroidX Room Runtime, Room KTX und Room Compiler 2.6.1
- AndroidX DataStore Preferences 1.1.1

Fundstellen: https://developer.android.com/jetpack/androidx und https://developer.android.com/develop/ui/compose

Lizenz: Apache License, Version 2.0. Der Lizenztext ist unter https://www.apache.org/licenses/LICENSE-2.0 abrufbar. Nach Maßgabe dieser Lizenz darf die Software insbesondere verwendet, vervielfältigt, verändert und verbreitet werden. Die Software wird ohne Gewährleistung bereitgestellt; vorhandene Urheberrechts-, Patent-, Marken- und Herkunftshinweise sind beizubehalten.

Android und AndroidX sind Marken beziehungsweise Projekte ihrer jeweiligen Rechteinhaber. Die Nennung bedeutet keine Unterstützung oder Zertifizierung von Nibra durch diese Rechteinhaber.
