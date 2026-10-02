# Coloring Book Tool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Coloring Book tool that turns ordered images into an A4 portrait PDF with one blank page after each image.

**Architecture:** Add a dedicated `POST /api/v1/convert/coloring-book` endpoint that uses the existing image decoder and proportional-fit logic. Register a core multi-file frontend tool under Page Formatting. Regenerate the frontend and engine API models from the endpoint's OpenAPI declaration.

**Tech Stack:** Java, Spring Boot, PDFBox, React, TypeScript, Python-generated tool models, Gradle, Task.

**Spec:** `docs/superpowers/specs/2026-10-02-coloring-book-design.md`

## Global Constraints

- Use portrait A4 pages.
- Keep each image's aspect ratio and centre it on the page. Leave white margins where the image does not fill the page.
- Add one blank portrait A4 page after every image page, including after the final image.
- Use the same image formats as the existing image-to-PDF route.
- Keep the existing endpoint and its default behaviour unchanged.
- Do not add page-size, fit, or reorder controls.
- Reject empty or unreadable image input through the existing API error path.
- Do not return a partial PDF.

## Review Focus

- One image: return exactly two A4 pages, with a blank second page. Test in `oneImageProducesImageAndBlankPage`.
- Two images: preserve input order and place a blank page after each. Test in `multipleImagesPreserveOrderAndAlternateBlankPages`.
- Multi-frame TIFF: place a blank page after each decoded frame. Test in `multiFrameTiffAddsBlankAfterEveryFrame`.
- Non-A4 image ratios: keep portrait A4 pages, preserve the image ratio, and leave margins. Test in `nonA4ImageKeepsAspectRatioOnPortraitA4`.
- Empty or unreadable input: raise the standard API error and return no PDF bytes. Test in `emptyOrUnreadableInputFailsWithoutReturningPdf` and `coloringBookControllerPropagatesImageDecodingFailure`.

---

### Task 1: Add the Coloring Book PDF endpoint

**Files:**
- Create: `app/core/src/main/java/stirling/software/SPDF/model/api/converters/ColoringBookRequest.java`
- Modify: `app/core/src/main/java/stirling/software/SPDF/controller/api/converters/ConvertImgPDFController.java`
- Modify: `app/common/src/main/java/stirling/software/common/util/PdfUtils.java`
- Modify: `app/common/src/main/java/stirling/software/SPDF/config/EndpointConfiguration.java`
- Create: `app/common/src/test/java/stirling/software/common/util/PdfUtilsColoringBookTest.java`
- Create: `app/core/src/test/java/stirling/software/SPDF/controller/api/converters/ConvertImgPDFControllerColoringBookTest.java`
- Modify: `app/common/src/test/java/stirling/software/SPDF/config/EndpointConfigurationTest.java`
- Generate: `frontend/editor/src/core/types/toolApiTypes.ts` and `frontend/editor/src/core/types/toolIO.ts`
- Generate: `engine/src/stirling/models/tool_models.py` and `engine/src/stirling/models/tool_io.py`

**Interfaces:**
- Consumes: Existing `PdfUtils` image decoding, `addImageToDocument`, `CustomPDFDocumentFactory`, and the `@ConvertApi` route prefix.
- Produces: `PdfUtils.imageToPdfWithBlankPages(MultipartFile[] files, CustomPDFDocumentFactory factory) throws IOException`, returning PDF bytes.
- Produces: `ColoringBookRequest.fileInput: MultipartFile[]`.
- Produces: `POST /api/v1/convert/coloring-book`, with multipart field `fileInput`, endpoint ID `coloring-book`, and `@ToolIO(accepts = ToolFormat.IMAGE, imageIOInput = true, produces = ToolFormat.PDF, arity = ToolArity.MISO)`.

- [ ] **Step 1: Write the failing PDF utility tests.** Create the four image behaviour tests and the invalid-input test named in Review Focus. Use distinct image colours to assert image order. Assert that every page is portrait A4, even pages have no image content, and the final page is blank.
- [ ] **Step 2: Run the new utility tests. Expect them to fail.**

Run: `./gradlew :common:test --tests stirling.software.common.util.PdfUtilsColoringBookTest`

Expected: The tests fail because `imageToPdfWithBlankPages` does not exist.

- [ ] **Step 3: Implement `PdfUtils.imageToPdfWithBlankPages`.** Share the existing image and TIFF-frame decoding path. Use `maintainAspectRatio`, keep pages portrait, and append one empty A4 page after every decoded image page. Reject a null or empty file array. Do not change the existing `imageToPdf` defaults.
- [ ] **Step 4: Run the utility tests. Expect all five tests to pass.**

Run: `./gradlew :common:test --tests stirling.software.common.util.PdfUtilsColoringBookTest`

Expected: All five tests pass.

- [ ] **Step 5: Write the failing endpoint and endpoint-group tests.** Add `coloringBookControllerReturnsPdfWithGeneratedFilename`. Assert that the controller delegates the ordered `fileInput` array and returns a PDF response. Add `coloringBookControllerPropagatesImageDecodingFailure`. Assert that decoding errors propagate without PDF bytes. Add `coloringBookEndpointIsInConvertAndJavaGroups`. Assert that endpoint ID `coloring-book` belongs to both groups.
- [ ] **Step 6: Add `ColoringBookRequest` and the endpoint.** Add `convertToColoringBookPdf(ColoringBookRequest)` to `ConvertImgPDFController` with `@AutoJobPostMapping(value = "/coloring-book", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, resourceWeight = ResourceWeight.LARGE_WEIGHT)`, `@StandardPdfResponse`, `@Operation`, and the `@ToolIO` contract above. Give `ColoringBookRequest` one required `MultipartFile[] fileInput` field. Use the new PDF utility method and a `_coloring_book.pdf` output suffix. Add the endpoint ID to the Convert and Java groups in `EndpointConfiguration`.
- [ ] **Step 7: Run the endpoint tests and backend quality gate.**

Run: `./gradlew :stirling-pdf:test --tests stirling.software.SPDF.controller.api.converters.ConvertImgPDFControllerColoringBookTest`

Run: `./gradlew :common:test --tests stirling.software.SPDF.config.EndpointConfigurationTest`

Run: `task backend:check`

Expected: Both targeted test commands and the backend quality gate pass.

- [ ] **Step 8: Regenerate the frontend and engine API models.**

Run: `task frontend:tool-models`

Run: `task engine:tool-models`

Run: `task engine:check`

Expected: The generated models include `/api/v1/convert/coloring-book` and the engine quality gate passes.

- [ ] **Step 9: Commit the endpoint and generated API models.**

```bash
git add app/core/src/main/java/stirling/software/SPDF/model/api/converters/ColoringBookRequest.java app/core/src/main/java/stirling/software/SPDF/controller/api/converters/ConvertImgPDFController.java app/common/src/main/java/stirling/software/common/util/PdfUtils.java app/common/src/main/java/stirling/software/SPDF/config/EndpointConfiguration.java app/common/src/test/java/stirling/software/common/util/PdfUtilsColoringBookTest.java app/core/src/test/java/stirling/software/SPDF/controller/api/converters/ConvertImgPDFControllerColoringBookTest.java app/common/src/test/java/stirling/software/SPDF/config/EndpointConfigurationTest.java frontend/editor/src/core/types/toolApiTypes.ts frontend/editor/src/core/types/toolIO.ts engine/src/stirling/models/tool_models.py engine/src/stirling/models/tool_io.py
git commit -m "feat: add coloring book PDF endpoint"
```

### Task 2: Add the Page Formatting tool

**Files:**
- Create: `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookParameters.ts`
- Create: `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.ts`
- Create: `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.test.ts`
- Create: `frontend/editor/src/core/tools/ColoringBook.tsx`
- Modify: `frontend/editor/src/core/data/useTranslatedToolRegistry.tsx`
- Create: `frontend/editor/src/core/data/useTranslatedToolCatalog.test.tsx`
- Modify: `frontend/editor/src/core/types/toolId.ts`
- Modify: `frontend/editor/src/core/utils/urlMapping.ts`
- Create: `frontend/editor/src/core/utils/urlMapping.test.ts`
- Modify: `frontend/editor/public/locales/en-US/translation.toml`
- Consume: Generated API types from Task 1.

**Interfaces:**
- Consumes: `POST /api/v1/convert/coloring-book`, request field `fileInput`, and endpoint ID `coloring-book`.
- Produces: Tool ID `coloringBook`, URL `/coloring-book`, and a multi-file operation that submits `File[]` in order.
- Produces: A registry entry with category `ToolCategoryId.STANDARD_TOOLS` and subcategory `SubcategoryId.PAGE_FORMATTING`.

- [ ] **Step 1: Write the failing operation and registry tests.** Assert that `buildFormData` appends each selected file to `fileInput` in order and targets `/api/v1/convert/coloring-book`. Assert that `useTranslatedToolCatalog_registersColoringBookUnderPageFormatting` places `coloringBook` under Page Formatting. Assert that `URL_TO_TOOL_MAP` maps `/coloring-book` to `coloringBook`.
- [ ] **Step 2: Run the targeted frontend tests. Expect them to fail.**

Run: `cd frontend && npx vitest run --root editor src/core/hooks/tools/coloringBook/useColoringBookOperation.test.ts src/core/data/useTranslatedToolCatalog.test.tsx src/core/utils/urlMapping.test.ts`

Expected: The tests fail because the tool hooks, registry entry, and URL mapping do not exist.

- [ ] **Step 3: Implement the parameter and operation hooks.** Use an empty parameter set, endpoint ID `coloring-book`, endpoint path `/api/v1/convert/coloring-book`, and `defineMultiFileTool`. Append the selected files as repeated `fileInput` fields.
- [ ] **Step 4: Implement and register `ColoringBook`.** Use `useBaseTool`, `createToolFlow`, and the standard result view. Require at least one file. Add `coloringBook` to `CORE_REGULAR_TOOL_IDS`. Map `/coloring-book` to `coloringBook` in `URL_TO_TOOL_MAP`. Set its registry category to Page Formatting. Reuse the existing `book-open` icon through `@app/ui/Icon`.
- [ ] **Step 5: Add English (US) translations.** Add the home title and description, file prompt, submit label, result title, filename prefix, and error text under the `coloringBook` keys. Run the required translation fix task.

Run: `task pre-commit:fix`

- [ ] **Step 6: Run the targeted frontend tests and quality gate.**

Run: `cd frontend && npx vitest run --root editor src/core/hooks/tools/coloringBook/useColoringBookOperation.test.ts src/core/data/useTranslatedToolCatalog.test.tsx src/core/utils/urlMapping.test.ts`

Run: `task frontend:check`

Run: `task tool-models:check`

Expected: The targeted tests, frontend quality gate, and generated-model check pass.

- [ ] **Step 7: Commit the frontend tool.**

```bash
git add frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookParameters.ts frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.ts frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.test.ts frontend/editor/src/core/tools/ColoringBook.tsx frontend/editor/src/core/data/useTranslatedToolRegistry.tsx frontend/editor/src/core/data/useTranslatedToolCatalog.test.tsx frontend/editor/src/core/types/toolId.ts frontend/editor/src/core/utils/urlMapping.ts frontend/editor/src/core/utils/urlMapping.test.ts frontend/editor/public/locales/en-US/translation.toml
git commit -m "feat: add coloring book tool"
```
