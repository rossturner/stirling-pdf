## Task 2 Report: InterleavedPagesController

**Status:** DONE_WITH_CONCERNS

**Commits:** 0dd3dfcfc

**Test Summary:** `./gradlew :stirling-pdf:compileJava` succeeds with no errors. No endpoint testing was done. `task backend:build` runs the test suites and fails in unrelated existing tests (JPDFium, PdfMarkdownConverter, EnterpriseProcessing).

**Concerns:**
- The brief's `GeneralUtils.generateFilename(null, "")` does not exist, so the filename is a fixed `interleaved.pdf`.
- ScaledDimensions is a record. The redundant IllegalArgumentException catch-rethrow and the narrating comments from the brief were dropped, per project rules.
- Float offsets are passed to drawImage without the int casts in the brief, which avoids truncation.

## Fix Round 1

**Fixes applied:**
- Added timestamp to filename using LocalDateTime pattern (`interleaved_yyyyMMdd_HHmmss.pdf`)
- Wrapped PDImageXObject.createFromByteArray in try-catch to throw IllegalArgumentException for invalid images (-> 400); real I/O errors from getBytes() still propagate as IOException (-> 500)

**Compilation:** `./gradlew :stirling-pdf:compileJava` succeeds
