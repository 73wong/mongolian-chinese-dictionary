# Navigation verification — 2026-10-20

The home destinations are Dictionary and Study. Settings opens from the gear in the top right and returns to the originating destination. Search and study cards retain their focused layouts.

Peer navigation reorders existing activities instead of finishing Study and creating it again. Unchanged destinations retain their view trees. Study reloads off the UI thread when the saved study/dictionary file changes; language and day changes refresh its displayed content.

Validation on Pixel 10 Pro emulator (API 36.1):

- Debug app and instrumentation package built successfully.
- `navigationOnly`: ten warm switches reused the same activities and view trees, retained the search query/results, and returned correctly from Settings. Average activity resume latency: 261 ms. This measures emulator warm navigation, not cold startup or real-device frame rate.
- `studyOnly`: study rounds, undo, persistence and existing study checks passed.

Run navigation regression checks with:

```sh
adb shell am instrument -w -e navigationOnly true org.example.mongoldictionary.test/org.example.mongoldictionary.RenderingChecks
```
