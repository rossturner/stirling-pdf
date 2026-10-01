# Coloring Book tool: design

## Goal

A new **Coloring Book** tool under Standard Tools → Page Formatting. It takes a
set of images and produces one PDF for double-sided printing. Each image is a
full page on an odd-numbered page, and each even-numbered page is blank, so the
back of every coloring page is empty.

## Requirements

- Input: one or more image files, in workbench selection order.
- Output: a single PDF with 2N pages for N images:
  image 1, blank, image 2, blank, …, image N, blank.
- Images are placed as-is. The tool does not convert photos to outlines.
- Page size is fixed A4. The image is scaled to fit with its aspect ratio kept
  and centred. A landscape image gets a landscape A4 page (existing auto-rotate
  behaviour).
- Each blank page has the same mediabox as the image page in front of it, so
  duplex printing lines up.
- A multi-frame TIFF counts as one image per frame, and each frame gets its own
  blank back.
- The tool has no user-facing settings.

Out of scope: outline or edge conversion, page size choice, colour modes, margins.

## Approach

A new backend endpoint and a standard multi-file frontend tool. This matches
the other Page Formatting tools, and makes the tool available to the API,
automations, pipelines and the AI engine's `tool_models`.

Rejected alternatives:
- Client-side only (PDFium `customProcessor`): the tool would be invisible to the
  API, automations and the engine, and would diverge from its sibling tools.
- Chaining existing endpoints: there is no endpoint that interleaves blank pages,
  so this still needs backend work, plus two round trips.

## Backend

**Endpoint**: `POST /api/v1/misc/coloring-book`, in a new
`app/core/.../controller/api/misc/ColoringBookController.java`.

- `@AutoJobPostMapping(consumes = MULTIPART_FORM_DATA_VALUE, value = "/coloring-book", resourceWeight = LARGE_WEIGHT)`
- `@StandardPdfResponse`
- `@ToolIO(accepts = ToolFormat.IMAGE, imageIOInput = true, produces = ToolFormat.PDF, arity = ToolArity.MISO)`
- `@Operation` summary and description.

**Request model**: `ColoringBookRequest` holds one field, `MultipartFile[] fileInput`.

**Endpoint registration**: in `EndpointConfiguration`, add `coloring-book` to the
"PageOps" group and the "Java" group, next to `booklet-imposition`, so it can be disabled like any other endpoint.

**Logic**: the controller stays thin. A new `PdfUtils` method next to
`imageToPdf` builds the document:

1. Create the document through `pdfDocumentFactory`.
2. For each image, in request order, append its page or pages the way
   `imageToPdf` does (`appendSingleImage` / `appendTiffFrames`), with
   `fitOption = "maintainAspectRatio"`, `autoRotate = true` and
   `colorType = "color"`.
3. After each image page, append `new PDPage(imagePage.getMediaBox())`.
4. Return the bytes. The response filename is
   `GeneralUtils.generateFilename(firstName, "_coloring_book.pdf")`.

**Errors**:
- Empty or missing `fileInput`: 400 through the existing validation path.
- An image that can't be decoded: the whole request fails with the standard error
  response. A skipped image would put every later image on the wrong side of
  the sheet.

**Engine**: regenerate `engine/src/stirling/models/tool_models.py` with
`task engine:tool-models`.

## Frontend

**Registration**
- `core/types/toolId.ts`: add `"coloringBook"`.
- `core/data/useTranslatedToolRegistry.tsx`: add an entry in the Page Formatting
  block after `bookletImposition`:
  - `icon: <Icon name="palette" size="1.5rem" />`
  - `component: lazy(() => import("@app/tools/ColoringBook"))`
  - `categoryId: STANDARD_TOOLS`
  - `subcategoryId: PAGE_FORMATTING`
  - `maxFiles: -1`
  - `endpoints: ["coloring-book"]`
  - `supportedFormats: ["png", "jpg", "jpeg", "gif", "bmp", "tiff", "tif", "webp"]`.
    SVG is excluded because ImageIO can't decode it.
  - `operationConfig: asRegistryConfig(coloringBookOperationConfig)`
  - `automationSettings: null`
  - `synonyms: getSynonyms(t, "coloringBook")`
- `core/utils/urlMapping.ts`: map `/coloring-book` to `coloringBook`.
- `public/og-metadata.json` and `public/og-metadata.saas.json`: add the tool
  entry and the route.

**Hooks** (`core/hooks/tools/coloringBook/`)
- `useColoringBookParameters.ts`: an empty parameter set with
  `endpointName: "coloring-book"`, and validation that always passes.
- `useColoringBookOperation.ts`: exports `buildColoringBookFormData` and
  `coloringBookOperationConfig`:
  - `toolType: ToolType.multiFile`
  - `endpoint: "/api/v1/misc/coloring-book"`
  - `buildFormData`: appends every file as `fileInput`, in order.
  - `filePrefix` from translation.

  The selected images are consumed and replaced by one PDF, with undo through
  `useToolOperation`.

**Component**: `core/tools/ColoringBook.tsx`, using `useBaseTool` +
`createToolFlow` with no settings steps. It has the file list, a
"Create coloring book" execute button and the review panel. A
`useColoringBookTips.ts` tooltip explains the alternating image and blank pages
and that the output is meant for double-sided printing.

**Translations** (`public/locales/en-US/translation.toml` only, then
`task pre-commit:fix`):
- `home.coloringBook.{title, desc}`
- `coloringBook.{submit, filenamePrefix, results.title, error.failed, tooltip.*}`

## Testing

**Backend** (`task backend:check`)
- 3 images give 6 pages, and pages 2, 4 and 6 have no content.
- A landscape image gives a landscape image page and a landscape blank back.
- A 2-frame TIFF gives 4 pages.
- An empty input is rejected.

**Frontend** (`task frontend:check`)
- `buildColoringBookFormData` appends every file as `fileInput` and keeps their order.

**Engine** (`task engine:check`) after regenerating the tool models.

**End-to-end**
- Run `task dev`, log in as `admin` / `password`, open Page Formatting →
  Coloring Book, and add 3 images, one of them landscape.
- Run the tool and confirm the output has 6 pages in the order image, blank,
  image, blank, image, blank, with a landscape blank behind the landscape image.
  Use Playwright and keep a screenshot.
