# Coloring Book tool: design

## Goal

A new **Coloring Book** tool under Standard Tools → Page Formatting. It takes a
set of images and produces one PDF for double-sided printing. Each image is a
full page on an odd-numbered page, and each even-numbered page is blank, so the
back of every coloring page is empty.

## Requirements

- Input: one or more image files, in workbench file order (the user reorders
  them in the file editor). The tool runs on every loaded file the endpoint
  accepts, not only the selected ones, including when opened from the viewer.
- Output: a single PDF with 2N pages for N images:
  image 1, blank, image 2, blank, …, image N, blank.
- Images are placed as-is. The tool does not convert photos to outlines.
- Page size is fixed A4. The image is scaled to fit with its aspect ratio kept
  and centred. A landscape image gets a landscape A4 page (existing auto-rotate
  behaviour).
- Each blank page has the same mediabox as the image page in front of it, so
  duplex printing lines up.
- A multi-frame TIFF counts as one image per frame, and each frame gets its own
  blank back. An animated GIF counts as one image (its first frame, since
  `ImageIO.read` reads only that).
- The tool has no user-facing settings.

Out of scope: outline or edge conversion, page size choice, colour modes, margins.
The hand-kept endpoint lists in `EndpointManagementCard.tsx` and the test
`api-stubs.ts` are left unchanged, matching the Auto Rotate and Create Portfolio
changes.

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
`app/core/.../controller/api/misc/ColoringBookController.java`, annotated
`@MiscApi` for the `/api/v1/misc` prefix.

- `@AutoJobPostMapping(consumes = MULTIPART_FORM_DATA_VALUE, value = "/coloring-book", resourceWeight = LARGE_WEIGHT)`
- `@StandardPdfResponse`
- `@ToolIO(accepts = ToolFormat.IMAGE, imageIOInput = true, produces = ToolFormat.PDF, arity = ToolArity.MISO)`
- `@Operation` summary and description.

**Request model**: `ColoringBookRequest` holds one field, `MultipartFile[] fileInput`.

**Endpoint registration**: in `EndpointConfiguration`, add `coloring-book` to the
"PageOps" group and the "Java" group, next to `booklet-imposition`, so it can
be disabled like any other endpoint.

**Logic**: the controller stays thin. A new public `PdfUtils.imagesToColoringBook`
method sits next to `imageToPdf`. It builds the image pages with the same
private helpers that `imageToPdf` uses, then interleaves the blanks in a
separate pass:

1. Create the document through `pdfDocumentFactory.createNewDocument()`.
2. Append every image in request order through the existing private
   `appendSingleImage` / `appendTiffFrames` helpers. Use
   `fitOption = "maintainAspectRatio"`, `autoRotate = true` and
   `colorType = "color"`. The loop that `imageToPdf` runs over its input is
   extracted into a private method that both public methods call, so it isn't
   duplicated.
3. Take a snapshot of the image pages into a list with
   `doc.getPages().forEach(...)` (`PDPageTree` is not a `Collection`, so
   `List.copyOf` does not apply). For each
   page, insert a blank after it with
   `doc.getPages().insertAfter(new PDPage(new PDRectangle(w, h)), page)`, where
   `w` and `h` are that page's mediabox width and height. A fresh `PDRectangle`
   is used rather than sharing the image page's mediabox object. Because the
   blanks are interleaved after every page, including each TIFF frame, the 2N
   invariant holds by construction.
4. Return the bytes. The response `Content-Disposition` filename is
   `GeneralUtils.generateFilename(firstName, "_coloring_book.pdf")`, for direct
   API callers. The frontend names its own output (see Frontend).

**Errors**:
- Empty or missing `fileInput`: the controller checks explicitly and throws
  `ExceptionUtils.createIllegalArgumentException`, which
  `GlobalExceptionHandler` maps to 400. There is no shared validation that
  already does this; `/img/pdf` would throw an NPE.
- An image that can't be decoded: the whole request fails with the standard error
  response. A skipped image would put every later image on the wrong side of
  the sheet.

**Generated models**: run `task tool-models`, which regenerates both the
frontend (`core/types/toolApiTypes.ts`, `core/types/toolIO.ts`) and the engine
(`engine/src/stirling/models/tool_models.py`, `tool_io.py`). Without the
frontend `toolIO` entry, `toolAcceptsFile` would let PDFs through to the
endpoint, and `ENDPOINT satisfies ToolEndpoint` would not type-check.

**Memory and size**: only `image/jpeg` uploads go through `JPEGFactory`. Other
formats are embedded losslessly, so a set of large PNGs makes a large PDF. That
is accepted, since it matches `/img/pdf`, and `LARGE_WEIGHT` covers the job
scheduling.

## Frontend

**Registration**
- `core/types/toolId.ts`: add `"coloringBook"`.
- `core/data/useTranslatedToolRegistry.tsx`: add an entry in the Page Formatting
  block after `bookletImposition`:
  - `icon: <Icon name="palette" size="1.5rem" />`
  - `name: t("home.coloringBook.title", "Coloring Book")`
  - `description: t("home.coloringBook.desc", …)`
  - `component: lazy(() => import("@app/tools/ColoringBook"))`
  - `categoryId: STANDARD_TOOLS`
  - `subcategoryId: PAGE_FORMATTING`
  - `maxFiles: -1`
  - `endpoints: ["coloring-book"]`
  - `supportedFormats`: the generated `toolIO` `inputExtensions` for
    `/api/v1/misc/coloring-book`, read from the generated spec rather than
    copied by hand. `supportedFormats` only gates the upload and library
    pickers; `toolIO` decides which files the tool actually runs on, so taking
    both from the same source stops the two lists from disagreeing.
  - `operationConfig: asRegistryConfig(coloringBookOperationConfig)`
  - `automationSettings: null`
  - `synonyms: getSynonyms(t, "coloringBook")`
- `core/utils/urlMapping.ts`: map `/coloring-book` to `coloringBook`.
- `public/og-metadata.json` and `public/og-metadata.saas.json`: add the tool
  entry and the route.

**Hooks** (`core/hooks/tools/coloringBook/`)
- `useColoringBookParameters.ts`: an empty parameter set with
  `endpointName: "coloring-book"`, and validation that always passes.
- `useColoringBookOperation.ts`: follows the CreatePortfolio pattern.
  - `const ENDPOINT = "/api/v1/misc/coloring-book" satisfies ToolEndpoint`
  - `coloringBookOperationConfig = defineMultiFileTool({ ... })`
  - `buildColoringBookFormData` appends every file as `fileInput`, in order.
  - A `responseHandler` names the output `<first image base name>_coloring_book.pdf`.
    Without it, the multi-file path names the result
    `${filePrefix}${firstInput.name}`, which gives a PDF called `…cat.png`.

  The selected images are consumed and replaced by one PDF, with undo through
  `useToolOperation`.

**Component**: `core/tools/ColoringBook.tsx`, using `useBaseTool(..., { ignoreViewerScope: true })`
+ `createToolFlow` with one information step ("Double-sided printing") that carries
the tooltip. `ignoreViewerScope` stops the viewer from narrowing the input to the
one image on display, so the execute button sets `disableScopeHints` to drop the
single-file scope hints (all loaded images are processed). It has the file list, a
"Create coloring book" execute button and the review panel. A
`useColoringBookTips.ts` tooltip explains the alternating image and blank pages
and that the output is meant for double-sided printing.

**Translations** (`public/locales/en-US/translation.toml` only, then
`task pre-commit:fix`):
- `home.coloringBook.{title, desc, tags}` (`tags` feeds `getSynonyms`)
- `coloringBook.{submit, info.{title, text}, results.title, error.failed, tooltip.*}`

## Testing

**Backend** (`task backend:check`)
- 3 images give 6 pages, and pages 2, 4 and 6 have no content.
- A landscape image gives a landscape image page and a landscape blank back.
- A 2-frame TIFF gives 4 pages.
- An empty input is rejected with 400.
- Blanks have a fresh mediabox object, not one shared with the image page.

**Frontend** (`task frontend:check`)
- `buildColoringBookFormData` appends every file as `fileInput` and keeps their order.
- The response handler names the output `<first base name>_coloring_book.pdf`.

**Engine** (`task engine:check`) after regenerating the tool models.

**End-to-end**
- Run `task dev`, log in as `admin` / `password`, open Page Formatting →
  Coloring Book, and add 3 images, one of them landscape.
- Run the tool and confirm the output has 6 pages in the order image, blank,
  image, blank, image, blank, with a landscape blank behind the landscape image.
  Use Playwright and keep a screenshot.
- Undo restores the N input images.
