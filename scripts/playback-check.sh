#!/usr/bin/env bash
set +e
mkdir -p playback-evidence
adb shell svc wifi disable
adb shell svc data disable
(cd android && ./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.contentagent.vibestream.PlaybackSmokeTest)
result=$?
adb logcat -d > playback-evidence/player-log.txt
grep -E 'PlaybackBenchmark|PlaybackException|ExoPlaybackException|AndroidRuntime|TestRunner|Unable to|Error|error|denied' playback-evidence/player-log.txt | tail -140 || true
adb pull /sdcard/Android/data/com.contentagent.vibestream/files/playback-smoke.png playback-evidence/ || true
adb pull /data/local/tmp/compilation-chapters.png playback-evidence/ || true
adb pull /data/local/tmp/recognition-consent.png playback-evidence/ || true
exit $result
