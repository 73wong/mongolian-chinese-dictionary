# Indexed runtime dictionary

The Gradle `buildDictionaryDatabase` task runs `tools/build_dictionary_db.py` before packaging. Restore the original release data first, as documented in the README. The generated SQLite database and SHA-256 version file are build artifacts, not source files to upload manually.

The builder applies the same supplemental entries, headword/pronunciation corrections, reviewed reference definitions, definition corrections, and confirmed aliases as the legacy Java loader. Original fields and stable entry IDs are retained in each JSON payload. Search normalization uses NFC; transliteration equivalences are limited to u/o, U/O, d/t, and g/k. Non-transliteration fields remain literal. Cyrillic alternatives are searched separately.

On first use, the bundled database is copied to private storage through a temporary file and verified against its packaged SHA-256. Later process starts reopen it. Rows are fetched on demand with a bounded 256-row cache. Exact and prefix lookups have B-tree indexes; contains and suffix operations scan normalized SQLite columns, preserving short queries and literal percent/underscore characters. This is not an FTS tokenizer migration. Candidate results are verified with the original Java SearchRules.

Imported dictionary JSON remains the authoritative editable data. A versioned sidecar index is generated once after an import or when the source timestamp, size, or bundled corrections change. Failed index construction does not overwrite source data. Import commits invalidate the current in-process index. CSV import and export retain their formats. Favorites and study records retain their existing storage and IDs.

Study startup loads IDs and row positions, not every definition. Card content is fetched when needed. Operations that enumerate all vocabulary for manual/random selection run off the UI thread. Large result sets and manual selection still materialize matching content; paging those screens remains a potential further optimization.

## Validation

Run the instrumentation runner with `-e indexedOnly true` to compare every field in all compiled entries with the legacy loader, compare search results, validate the lazy study map, and test imported-index invalidation and CSV updates. Run `-e searchUiOnly true`, `-e navigationOnly true`, and `-e studyOnly true` for UI and learning regression coverage.

Measure first database installation separately from reopening an installed database. Emulator debug timings are diagnostic comparisons, not release-build or physical-device performance guarantees. The compiled database adds storage; source JSON is still packaged for migration/validation compatibility.
