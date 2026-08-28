#!/usr/bin/env bash
# Drei Entwuerfe fuer das Nibra-App-Zeichen ueber Higgsfield/Recraft (Vektor).
# Motiv verbindlich aus AUFTRAG.md: Federspitze, aus der eine Welle austritt.
set -u

VERBOTEN="No text, no letters, no numbers, no wordmark. No microphone, no headphones, no speech bubble, no equalizer bars, no sound wave arcs, no circles of dots. No gradient, no shading, no drop shadow, no glow, no texture, no noise, no 3D, no bevel, no photorealism."
BASIS="Flat vector logo mark for a dictation app. Strictly bilaterally symmetric about the vertical centre axis. Geometric construction from circles, ellipses and straight lines with uniform stroke weight. Solid flat colour fills only. Single centred mark with generous empty margin on all sides, the mark occupying about sixty percent of the square. $VERBOTEN"

gen () {
  name="$1"; shift
  prompt="$1"; shift
  echo "=============== $name ==============="
  higgsfield generate create recraft_v4_1 \
    --prompt "$prompt" \
    --model_type vector \
    --resolution 2k \
    --aspect_ratio 1:1 \
    --background_color "#2F6F63" \
    --colors '["#F6F3EE"]' \
    --wait --wait-timeout 12m 2>&1 | tail -8
  echo
}

gen "A-feder-welle" \
  "$BASIS A classic fountain pen nib seen head on, pointing downward: a symmetric shield shape tapering to a sharp point, with a round breather hole on the centre axis and a single straight slit running from the hole down to the tip. Out of the very tip flows one calm continuous wave line that spreads symmetrically to the left and to the right and tapers away thinner towards both outer ends. Ivory mark on deep teal background."

gen "B-feder-negativraum" \
  "$BASIS A symmetric fountain pen nib rendered as negative space: a solid ivory rounded shape from which the nib silhouette is cut out, so the nib reads as the deep teal background showing through. Below the nib tip a single carved wave channel runs symmetrically outward to both sides. Two colours only, ivory and deep teal, hard clean edges."

gen "C-feder-monolinie" \
  "$BASIS A minimal monoline drawing: one continuous ivory line of constant thickness with rounded caps traces the outline of a symmetric pen nib, and the same unbroken line leaves the nib tip and becomes one smooth calm wave running symmetrically outward to both sides. Open line art, the interior stays empty deep teal, no filled areas."
