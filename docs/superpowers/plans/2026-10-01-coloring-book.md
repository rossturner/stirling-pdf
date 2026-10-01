# Coloring Book Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Page Formatting tool that turns ordered images into one portrait-A4 PDF with a blank page after every image page.

**Architecture:** Add a distinct `/api/v1/convert/coloring-book` endpoint and a PDF utility that reuses image conversion before interleaving blank pages. Register a typed multi-file frontend tool using the existing FileContext workflow and standard operation results.

**Tech Stack:** Spring Boot 4, Java 25, PDFBox, React, TypeScript, i18next/TOML, generated OpenAPI tool models, Gradle, Task.

**Spec:** `docs/superpowers/specs/2026-10-01-coloring-book-design.md`

## Global Constraints

- Process images in the order shown in the selected-file list.
- Render each image centered on a portrait A4 page, preserving its aspect ratio. Any unused area remains white.
- Follow each image page with an empty portrait A4 page, including after the final image.
- Return one PDF. For single-page image files, N inputs produce exactly 2N pages.
- Keep the layout fixed; do not add page-size, orientation, color-conversion, or blank-page options.
- Use a dedicated request model with only a required `MultipartFile[] fileInput`.
- Do not change the general image-to-PDF endpoint or its existing defaults.

## Review Focus

- Empty multipart input must fail before PDF conversion; malformed image input must fail without a partial PDF. Test in Tasks 1–2.
- Mixed raster inputs must preserve upload order and the image/blank sequence. Test in Task 1.
- Each multi-frame TIFF frame must be followed immediately by a blank. Test in Task 1.
- Portrait and landscape image content must retain aspect ratio on portrait A4, and blank pages must remain empty A4 pages. Test in Task 1.
- The single-segment Convert route must resolve to an independently disableable `coloring-book` endpoint key. Test in Task 2.

---

## File Map

| File | Responsibility |
|---|---|
| `app/common/src/main/java/stirling/software/common/util/PdfUtils.java` | Convert the files with fixed Coloring Book layout and interleave blank pages. |
| `app/common/src/test/java/stirling/software/common/util/PdfUtilsTest.java` | Verify page order across mixed image formats, dimensions, image fit, and TIFF frame behavior. |
| `app/core/src/main/java/stirling/software/SPDF/model/api/converters/ColoringBookRequest.java` | Required multipart image input, with no layout parameters. |
| `app/core/src/main/java/stirling/software/SPDF/controller/api/converters/ConvertImgPDFController.java` | Dedicated multipart endpoint and PDF response. |
| `app/common/src/main/java/stirling/software/SPDF/config/EndpointConfiguration.java` | Register the distinct key in Convert and Java groups. |
| `app/core/src/test/java/stirling/software/SPDF/controller/api/converters/ColoringBookControllerTest.java` | Endpoint parameter, empty-input, and response behavior. |
| `app/common/src/test/java/stirling/software/SPDF/config/EndpointConfigurationTest.java` | Route-to-key mapping. |
| `app/common/src/test/java/stirling/software/SPDF/config/EndpointConfigurationGapTest.java` | Endpoint-group disable behavior. |
| `frontend/editor/src/core/types/toolApiTypes.ts`, `toolIO.ts`, `engine/src/stirling/models/tool_models.py`, `tool_io.py` | Generated API and tool-I/O contracts; update with `task tool-models`. |
| `frontend/editor/src/core/types/toolId.ts` | Add the `coloringBook` tool identity. |
| `app/proprietary/src/main/java/stirling/software/proprietary/service/ToolKeyRegistry.java` | Mirror the frontend tool ID for backend usage tracking. |
| `app/proprietary/src/test/java/stirling/software/proprietary/service/ToolKeyRegistryTest.java` | Verify the new core ID remains in parity with the frontend registry. |
| `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookParameters.ts` | Parameterless configuration and endpoint availability key. |
| `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.ts` | Typed multi-file request and operation configuration. |
| `frontend/editor/src/core/tools/ColoringBook.tsx` | Standard file, execution, and result workflow. |
| `frontend/editor/src/core/data/useTranslatedToolRegistry.tsx` | Page Formatting registry entry. |
| `frontend/editor/src/core/hooks/tools/shared/migratedToolMappers.test.ts` | Include the operation in the mapper contract sweep. |
| `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.test.ts` | Verify multipart files retain caller order. |
| `frontend/editor/src/core/data/coloringBookToolRegistration.test.tsx` | Verify placement, formats, and endpoint registration. |
| `frontend/editor/public/locales/en-US/translation.toml` | English-US title, description, tags, and fixed-layout guidance. |
| `frontend/editor/public/og-metadata.json`, `og-metadata.saas.json` | Generated social-preview manifests kept in sync with the new regular tool. |

## Task 1: Build and Test the PDF Sequence

**Files:**
- Modify: `app/common/src/main/java/stirling/software/common/util/PdfUtils.java`
- Test: `app/common/src/test/java/stirling/software/common/util/PdfUtilsTest.java`

**Interfaces:**
- Produces: `PdfUtils.imageToPdfWithBlankPages(MultipartFile[] files, CustomPDFDocumentFactory factory) throws IOException`, returning the completed PDF bytes.

- [ ] **Step 1: Write failing PDF sequence tests**

Add tests that call `imageToPdfWithBlankPages` with generated valid images and assert:
- A PNG followed by a JPEG produces four portrait-A4 pages; rendered image pages preserve the first/second image order and each intervening/final page is white.
- Very wide and very tall image content fits within the page without distortion.
- A two-frame TIFF produces `frame 1, blank, frame 2, blank`.
- An unreadable image throws rather than returning PDF bytes.

- [ ] **Step 2: Run the focused tests and confirm they fail**

Run: `./gradlew :common:test --tests stirling.software.common.util.PdfUtilsTest`

Expected: compilation or test failure because `PdfUtils.imageToPdfWithBlankPages` is not implemented.

- [ ] **Step 3: Implement `PdfUtils.imageToPdfWithBlankPages`**

Call the existing `imageToPdf` with `fitOption = "maintainAspectRatio"`, `autoRotate = false`, and `colorType = "color"`. Load its result, insert an empty `PDPage(PDRectangle.A4)` immediately after each page that existed before insertion, then serialize the complete document. A multi-frame TIFF frame is one generated image page and gets its own following blank.

- [ ] **Step 4: Run the focused tests and confirm they pass**

Run: `./gradlew :common:test --tests stirling.software.common.util.PdfUtilsTest`

Expected: all sequence, A4, fitting, TIFF, and invalid-image assertions pass.

- [ ] **Step 5: Commit**

```bash
git add app/common/src/main/java/stirling/software/common/util/PdfUtils.java app/common/src/test/java/stirling/software/common/util/PdfUtilsTest.java
git commit -m "feat: interleave blank pages in coloring book PDFs"
```

## Task 2: Add the Dedicated Backend Endpoint and Endpoint Key

**Files:**
- Create: `app/core/src/main/java/stirling/software/SPDF/model/api/converters/ColoringBookRequest.java`
- Modify: `app/core/src/main/java/stirling/software/SPDF/controller/api/converters/ConvertImgPDFController.java`
- Modify: `app/common/src/main/java/stirling/software/SPDF/config/EndpointConfiguration.java`
- Test: `app/core/src/test/java/stirling/software/SPDF/controller/api/converters/ColoringBookControllerTest.java`
- Test: `app/common/src/test/java/stirling/software/SPDF/config/EndpointConfigurationTest.java`
- Test: `app/common/src/test/java/stirling/software/SPDF/config/EndpointConfigurationGapTest.java`

**Interfaces:**
- Consumes: `PdfUtils.imageToPdfWithBlankPages(MultipartFile[], CustomPDFDocumentFactory)` from Task 1.
- Produces: `POST /api/v1/convert/coloring-book`, with endpoint key `coloring-book`.

- [ ] **Step 1: Write failing endpoint and endpoint-key tests**

Verify that the controller passes the request’s image array, in order, to the PDF utility and returns its single PDF response. Verify that null or empty `fileInput` fails before conversion. Verify `EndpointConfiguration.endpointKeyForUri("/api/v1/convert/coloring-book")` returns `"coloring-book"` and that removing either the `Convert` or `Java` group disables that key.

- [ ] **Step 2: Run the focused tests and confirm they fail**

Run:
```bash
./gradlew :stirling-pdf:test --tests stirling.software.SPDF.controller.api.converters.ColoringBookControllerTest
./gradlew :common:test --tests stirling.software.SPDF.config.EndpointConfigurationTest --tests stirling.software.SPDF.config.EndpointConfigurationGapTest
```

Expected: new endpoint/model and mapping assertions fail before implementation.

- [ ] **Step 3: Add `ColoringBookRequest` and the endpoint**

Define `ColoringBookRequest` with only required `MultipartFile[] fileInput`. Add a `@ConvertApi` method mapped at `"/coloring-book"` using multipart `@AutoJobPostMapping` with `ResourceWeight.LARGE_WEIGHT`, `@StandardPdfResponse`, and `@ToolIO(accepts = ToolFormat.IMAGE, imageIOInput = true, produces = ToolFormat.PDF, arity = ToolArity.MISO)`. Reject missing/empty input through the repository’s standard invalid-request path; otherwise call the Task 1 utility and return its bytes through `WebResponseUtils`.

- [ ] **Step 4: Register `coloring-book` in endpoint groups**

Add the endpoint key to both `Convert` and `Java` in `EndpointConfiguration`. Keep the route single-segment so `endpointKeyForUri` returns the distinct key rather than `img-to-pdf`.

- [ ] **Step 5: Run the focused backend tests**

Run the commands from Step 2 and:
```bash
./gradlew :stirling-pdf:test --tests stirling.software.SPDF.config.ToolIODeclarationCoverageTest
```

Expected: controller behavior, route mapping, group removal, and endpoint I/O declaration tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/core/src/main/java/stirling/software/SPDF/model/api/converters/ColoringBookRequest.java app/core/src/main/java/stirling/software/SPDF/controller/api/converters/ConvertImgPDFController.java app/common/src/main/java/stirling/software/SPDF/config/EndpointConfiguration.java app/core/src/test/java/stirling/software/SPDF/controller/api/converters/ColoringBookControllerTest.java app/common/src/test/java/stirling/software/SPDF/config/EndpointConfigurationTest.java app/common/src/test/java/stirling/software/SPDF/config/EndpointConfigurationGapTest.java
git commit -m "feat: add Coloring Book PDF endpoint"
```

## Task 3: Generate the Endpoint Contracts

**Files:**
- Generate: `frontend/editor/src/core/types/toolApiTypes.ts`
- Generate: `frontend/editor/src/core/types/toolIO.ts`
- Generate: `engine/src/stirling/models/tool_models.py`
- Generate: `engine/src/stirling/models/tool_io.py`

**Interfaces:**
- Consumes: the OpenAPI endpoint and `@ToolIO` declaration from Task 2.
- Produces: the generated `ToolEndpoint`, `ToolApiParams`, file-field, and tool-I/O entries used by the frontend operation and engine.

- [ ] **Step 1: Generate the frontend and engine models**

Run: `task tool-models`

Expected: generated contracts include `/api/v1/convert/coloring-book`, its `fileInput` upload field, image input, PDF output, and `MISO` arity.

- [ ] **Step 2: Verify generated models are current**

Run: `task tool-models:check`

Expected: both generated model sets are current and the command exits successfully.

- [ ] **Step 3: Commit**

```bash
git add frontend/editor/src/core/types/toolApiTypes.ts frontend/editor/src/core/types/toolIO.ts engine/src/stirling/models/tool_models.py engine/src/stirling/models/tool_io.py
git commit -m "chore: generate Coloring Book endpoint models"
```

## Task 4: Register the Frontend Tool

**Files:**
- Modify: `frontend/editor/src/core/types/toolId.ts`
- Modify: `app/proprietary/src/main/java/stirling/software/proprietary/service/ToolKeyRegistry.java`
- Test: `app/proprietary/src/test/java/stirling/software/proprietary/service/ToolKeyRegistryTest.java`
- Create: `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookParameters.ts`
- Create: `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.ts`
- Create: `frontend/editor/src/core/tools/ColoringBook.tsx`
- Modify: `frontend/editor/src/core/data/useTranslatedToolRegistry.tsx`
- Modify: `frontend/editor/src/core/hooks/tools/shared/migratedToolMappers.test.ts`
- Create: `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.test.ts`
- Create: `frontend/editor/src/core/data/coloringBookToolRegistration.test.tsx`
- Modify: `frontend/editor/public/locales/en-US/translation.toml`
- Generate: `frontend/editor/public/og-metadata.json`
- Generate: `frontend/editor/public/og-metadata.saas.json`

**Interfaces:**
- Consumes: generated `/api/v1/convert/coloring-book` contracts from Task 3.
- Produces: regular tool ID `coloringBook`, endpoint availability key `coloring-book`, and Page Formatting registry entry.

- [ ] **Step 1: Write failing operation and registration tests**

Assert that `buildColoringBookFormData` appends every file under `fileInput` in the input array’s order. Assert that the translated registry contains `coloringBook` in `SubcategoryId.PAGE_FORMATTING`, uses endpoint key `"coloring-book"`, exposes the operation config, sets `maxFiles` to `-1`, and sets supported formats to the generated endpoint’s nonempty image `inputExtensions`. Run the existing `ToolKeyRegistryTest` before changing the backend mirror and confirm it fails because `coloringBook` is absent.

- [ ] **Step 2: Run the focused frontend tests and confirm they fail**

Run:
```bash
cd frontend && npx vitest run --root editor src/core/hooks/tools/coloringBook/useColoringBookOperation.test.ts src/core/data/coloringBookToolRegistration.test.tsx
```

Expected: tests fail because the Coloring Book operation and registry entry are not present.

- [ ] **Step 3: Add the tool identity, parameters, and multi-file operation**

Add `"coloringBook"` to `CORE_REGULAR_TOOL_IDS` and `ToolKeyRegistry.DEFAULT_KEYS`. Define empty default parameters with endpoint name `"coloring-book"`. Export `buildColoringBookFormData(_parameters: ColoringBookParameters, files: File[]): FormData` using `fileOnlyMapping` and `objectToFormData(..., { fileInput: files })`. Bind `defineMultiFileTool` to `"/api/v1/convert/coloring-book" satisfies ToolEndpoint`; set `operationType: "coloringBook"` and `filePrefix: "coloring-book_"`.

- [ ] **Step 4: Add the tool component and registry entry**

Build `ColoringBook.tsx` with `useBaseTool` and `createToolFlow` so file selection, execution, preview, download, error handling, and undo follow existing patterns. Register it under `SubcategoryId.PAGE_FORMATTING`, set `maxFiles: -1`, set `endpoints: ["coloring-book"]`, and bind `operationConfig` to `coloringBookOperationConfig`. Use the generated endpoint `inputExtensions` from `TOOL_IO` for image-only selection; fail explicitly if the generated I/O entry or its extensions are missing. Reuse the already-registered `book-open` icon.

- [ ] **Step 5: Add English-US copy and mapper coverage**

Add `[home.coloringBook]` title, description, and search tags to `translation.toml`, plus concise fixed-layout guidance under a `[coloringBook]` section. Add `coloringBookOperationConfig` to `migratedToolMappers.test.ts`. Run `task pre-commit:fix` after changing the translation file, then regenerate the social-preview manifests with `node editor/scripts/generate-og-metadata.mjs`.

- [ ] **Step 6: Run focused frontend tests**

Run the command from Step 2 and:
```bash
task frontend:test:editor
```

Also run:
```bash
./gradlew :proprietary:test --tests stirling.software.proprietary.service.ToolKeyRegistryTest
./gradlew :common:test --tests stirling.software.common.util.PdfUtilsTest
task frontend:og:check
```

Expected: focused form-data order and registry assertions pass. The full editor suite may report the two known baseline failures in `src/proprietary/routes/AuthCallback.test.tsx`; reproduce those against the pre-Task-4 revision and record them as baseline blockers, not Coloring Book failures. No new test failures may be introduced.

- [ ] **Step 7: Commit**

```bash
git add frontend/editor/src/core/types/toolId.ts app/proprietary/src/main/java/stirling/software/proprietary/service/ToolKeyRegistry.java app/proprietary/src/test/java/stirling/software/proprietary/service/ToolKeyRegistryTest.java frontend/editor/src/core/hooks/tools/coloringBook frontend/editor/src/core/tools/ColoringBook.tsx frontend/editor/src/core/data/useTranslatedToolRegistry.tsx frontend/editor/src/core/hooks/tools/shared/migratedToolMappers.test.ts frontend/editor/src/core/data/coloringBookToolRegistration.test.tsx frontend/editor/public/locales/en-US/translation.toml frontend/editor/public/og-metadata.json frontend/editor/public/og-metadata.saas.json
git commit -m "feat: add Coloring Book frontend tool"
```

## Task 5: Run the Required Quality Gates

**Files:** No new source files; resolve only failures introduced by this feature.

**Interfaces:** Consumes the complete backend, generated contracts, and frontend implementation from Tasks 1–4.

- [ ] **Step 1: Run all required checks**

Run:
```bash
task tool-models:check
task frontend:og:check
task backend:check
task frontend:check
task engine:check
```

Expected: `task tool-models:check` and `task engine:check` pass. Run the backend and frontend checks; if they fail on the already observed host issues (JPDFium's GLIBC requirement, unrelated YAML formatting, or the two AuthCallback tests), record the exact output and verify it is unchanged from the relevant baseline. Do not change unrelated files to hide those failures. All feature-scoped tests must pass.

- [ ] **Step 2: Commit any check-driven corrections**

Commit only changes required to make the feature pass these checks, with a message that describes the correction.
