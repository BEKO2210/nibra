#!/usr/bin/env bash
set -euo pipefail

ROUNDS="${1:-3}"
SILENCE_SEC="${SILENCE_SEC:-8}"
SPEECH_SEC="${SPEECH_SEC:-8}"
NOISE_SEC="${NOISE_SEC:-8}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required but not found."
  exit 1
fi

ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB+=(-s "$ANDROID_SERIAL")
fi

echo "Clearing logcat..."
"${ADB[@]}" logcat -c

echo
echo "Protocol:"
echo "1) Open any text field on phone."
echo "2) Tap overlay mic to start recording."
echo "3) Follow prompts below."
echo "4) Tap mic to stop at the end of each round."
echo

for ((i=1; i<=ROUNDS; i++)); do
  echo "[Round $i/$ROUNDS] Keep SILENT for ${SILENCE_SEC}s..."
  sleep "$SILENCE_SEC"
  echo "[Round $i/$ROUNDS] Speak in normal voice for ${SPEECH_SEC}s..."
  sleep "$SPEECH_SEC"
  echo "[Round $i/$ROUNDS] Make background NOISE for ${NOISE_SEC}s..."
  sleep "$NOISE_SEC"
  echo "[Round $i/$ROUNDS] Stop recording now."
  sleep 3
done

echo
echo "Collecting VAD logs..."
TMP_LOG="$(mktemp /tmp/vad_loop_XXXX.log)"
"${ADB[@]}" logcat -d -s AudioRecorder:D AudioRecorder:I > "$TMP_LOG"

if command -v rg >/dev/null 2>&1; then
  SUMMARY_LINES="$(rg 'VAD_SUMMARY' "$TMP_LOG" || true)"
else
  SUMMARY_LINES="$(grep 'VAD_SUMMARY' "$TMP_LOG" || true)"
fi

if [[ -z "$SUMMARY_LINES" ]]; then
  echo "No VAD_SUMMARY lines found. Make sure you started/stopped recording during rounds."
  echo "Raw log saved at: $TMP_LOG"
  exit 1
fi

echo "Per-run summaries:"
echo "$SUMMARY_LINES"
echo

echo "$SUMMARY_LINES" | awk '
{
  for (i = 1; i <= NF; i++) {
    split($i, kv, "=")
    k = kv[1]
    v = kv[2] + 0
    if (k == "speechRatio") speechRatio += v
    if (k == "avgNoiseAmp") avgNoise += v
    if (k == "avgSpeechAmp") avgSpeech += v
    if (k == "noiseFloor") noiseFloor += v
    if (k == "start") startThr += v
    if (k == "stop") stopThr += v
  }
  runs += 1
}
END {
  if (runs == 0) {
    print "No parseable summaries."
    exit 1
  }
  meanSpeechRatio = speechRatio / runs
  meanNoise = avgNoise / runs
  meanSpeech = avgSpeech / runs
  meanNoiseFloor = noiseFloor / runs
  meanStart = startThr / runs
  meanStop = stopThr / runs
  snr = meanSpeech / (meanNoise > 1 ? meanNoise : 1)

  printf("Aggregate over %d runs\n", runs)
  printf("speechRatio mean: %.2f (target ~0.30-0.80 while speaking test)\n", meanSpeechRatio)
  printf("avgNoiseAmp mean: %.0f\n", meanNoise)
  printf("avgSpeechAmp mean: %.0f\n", meanSpeech)
  printf("speech/noise amplitude ratio: %.2f (higher is better)\n", snr)
  printf("noiseFloor mean: %.0f\n", meanNoiseFloor)
  printf("stop/start thresholds mean: %.0f / %.0f\n", meanStop, meanStart)
}'

echo
echo "Raw log saved at: $TMP_LOG"
