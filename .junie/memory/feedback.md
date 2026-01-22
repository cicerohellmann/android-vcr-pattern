[2026-01-22 15:59] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "Tape parsing failure",
    "EXPECTATION": "Tape should load successfully; the first NDJSON event must be well-formed and compatible with the loader.",
    "NEW INSTRUCTION": "WHEN serializing events THEN match TapeLoader's expected JSON schema and discriminator."
}

[2026-01-22 18:02] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Error visibility",
    "EXPECTATION": "Errors should be visible: printed, logged to Logcat, and captured in another shareable way.",
    "NEW INSTRUCTION": "WHEN an error occurs during tape load or replay THEN print stacktrace, logcat error, and persist message to shareable file"
}

[2026-01-22 18:05] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "Schema/sequence errors",
    "EXPECTATION": "Tape must parse cleanly: first event well-formed and all seq values strictly increase.",
    "NEW INSTRUCTION": "WHEN writing NDJSON events THEN include seq and enforce strictly increasing sequence"
}

[2026-01-22 18:29] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Seq monotonicity/lifecycle",
    "EXPECTATION": "Tape should parse cleanly with strictly increasing seq; no concurrent writers and pending writes must be flushed before reading or swapping recorders.",
    "NEW INSTRUCTION": "WHEN creating/replacing a SessionRecorder THEN shutdown old recorder, await flush, ensure single writer"
}

[2026-01-22 20:16] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Seq monotonicity/lifecycle",
    "EXPECTATION": "Tape should parse cleanly with strictly increasing seq; no concurrent writers and pending writes must be flushed before reading or swapping recorders.",
    "NEW INSTRUCTION": "WHEN creating or replacing a SessionRecorder THEN shutdown old recorder, await flush, ensure single writer"
}

[2026-01-22 20:23] - Updated by Junie
{
    "TYPE": "preference",
    "CATEGORY": "Pace and decisiveness",
    "EXPECTATION": "Quick determination of task readiness and a fast path to commit or finish.",
    "NEW INSTRUCTION": "WHEN asked about readiness or to commit THEN answer yes/no and list minimal next steps"
}

[2026-01-22 20:23] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "Pace/decisiveness",
    "EXPECTATION": "Provide a quick yes/no on whether Step 06 can start, with minimal blockers or next steps.",
    "NEW INSTRUCTION": "WHEN asked if next step can start THEN reply yes/no and list 1-3 blockers or actions"
}

[2026-01-22 20:25] - Updated by Junie
{
    "TYPE": "preference",
    "CATEGORY": "Pace and decisiveness",
    "EXPECTATION": "Quick determination of task readiness and a fast path to commit or finish.",
    "NEW INSTRUCTION": "WHEN asked about readiness or to commit THEN answer yes/no and list minimal next steps"
}

[2026-01-22 20:37] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "Player UI persistence",
    "EXPECTATION": "Player controls must remain visible and accessible during playback, with an obvious way to stop/close.",
    "NEW INSTRUCTION": "WHEN playback starts or in REPLAY mode THEN keep player controls visible on top and add Stop/Close"
}

[2026-01-22 20:40] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Player UI persistence/placement",
    "EXPECTATION": "Player controls must remain always visible and accessible; a floating controller or a bottom-docked bar that resizes content is acceptable.",
    "NEW INSTRUCTION": "WHEN in REPLAY mode THEN display persistent bottom-docked player and inset content"
}

[2026-01-22 20:42] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Player UI persistence/placement",
    "EXPECTATION": "Keep the player always visible without replacing AlertDialog; avoid a permanent bottom bar; ensure dialogs cannot grow over the player's area; solution must work app-agnostically.",
    "NEW INSTRUCTION": "WHEN in REPLAY mode THEN mount player in window decor and reserve bottom inset"
}

