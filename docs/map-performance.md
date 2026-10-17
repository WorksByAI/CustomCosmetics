# Map Editor performance check

Tested with the Minecraft 26.2 native client on 2026-10-17, using `-XX:ActiveProcessorCount=2`.

The fixture stores 1,048,576 edits across loaded sections. It measures client frames and Map Editor ticks while restoring that data, editing one point, undoing, saving/loading/renaming a preset, and filling/undoing a 64,000-position region.

Latest isolated run:

| Measurement | Time |
| --- | ---: |
| Single edit | 4 ms |
| Single edit undo | 1 ms |
| Longest Map Editor tick during restoration | 5 ms |
| Longest client frame during restoration | 145 ms |
| Longest client frame including preset and fill operations | 220 ms |

The fixture mostly uses air with stored non-air origin flags to stress storage and map preparation without submitting a million standalone ghost models. These numbers do not measure a fully populated Hypixel arena, all other installed mods, or server performance. A previous combined run following multiple world loads reached 973 ms and failed the 500 ms frame threshold, while Map Editor ticks remained below 2 ms; isolated runs are therefore reported separately rather than treating that combined run as a pass. The integrated server also reported catch-up delays during preparation, so this check is not a server tick-rate guarantee.

The latest isolated native test passes the 500 ms maximum client-frame and 250 ms Map Editor tick/edit thresholds. The focused map, update preference, configuration and packaging suite has 141 passing tests.

Local evidence: `build/audit/map-smooth-region-final.log` and `build/audit/map-smooth-delivery.log` (generated files, not distributed).

Run the native scenario with `./gradlew.bat runClientGameTest -Pmap_edit_perf_only=true -x test --offline`. Use a fresh run directory for a comparable isolated measurement.
