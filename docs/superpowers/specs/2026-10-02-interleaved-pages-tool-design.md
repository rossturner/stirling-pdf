# Interleaved Pages Tool Design

**Date:** 2026-10-02  
**Author:** Claude Haiku 4.5  
**Status:** Design Phase  
**Category:** Page Formatting / Coloring Book

---

## 1. Overview

Add a new **Interleaved Pages** tool to Stirling PDF that accepts multiple image files and produces a PDF with alternating image pages (odd) and blank pages (even), optimized for duplex printing of coloring books and similar materials.

**Primary Use Case:** Users select multiple images (photos, illustrations, drawings), and the tool produces a print-ready PDF where each image is on an odd-numbered page with a blank A4 page on the back for printing, sketching, or annotation.

---

## 2. Requirements

### Functional Requirements

| ID | Requirement | Details |
|----|-------------|---------|
| FR-1 | Accept image files | Support JPG, PNG, GIF, WebP, and other common image formats |
| FR-2 | Multi-file input | Accept 1 or more images in a single operation |
| FR-3 | User-controlled order | Allow user to reorder images before processing (drag-and-drop or list UI) |
| FR-4 | Image scaling | Scale each image to fit A4 page while maintaining aspect ratio |
| FR-5 | Blank page insertion | Insert blank A4 page after every image page |
| FR-6 | PDF output | Produce single PDF file with alternating image/blank pages |
| FR-7 | Output naming | Auto-generate filename as `interleaved_[timestamp].pdf` |
| FR-8 | Single image support | Even 1 image produces 2-page PDF (image + blank) |

### Non-Functional Requirements

| ID | Requirement | Details |
|----|-------------|---------|
| NFR-1 | Memory efficient | Handle large images without excessive memory overhead; stream where possible |
| NFR-2 | Performance | Complete typical operation (5-10 images) in <5 seconds |
| NFR-3 | Accessibility | Follow WCAG standards for UI components |
| NFR-4 | i18n ready | All UI strings translatable via i18n system |
| NFR-5 | Testable | Support unit and integration testing patterns used in codebase |

---

## 3. Architecture

### 3.1 Backend Architecture

**Component:** `InterleavedPagesController`

**Location:** `/app/core/src/main/java/stirling/software/SPDF/controller/api/misc/InterleavedPagesController.java`

**Endpoint:**
- **Path:** `POST /api/v1/general/interleaved-pages`
- **Content-Type:** `multipart/form-data`
- **Consumes:** Multiple image files
- **Produces:** PDF (application/pdf)

**Request Model:** `InterleavedPagesRequest`
- `fileInput: MultipartFile[]` — array of image files in the desired order (file order is determined by append order in FormData)

**Response:** PDF file via `WebResponseUtils.pdfDocToWebResponse()`

**Processing Flow:**
1. Validate inputs (non-empty, all are images)
2. Create new PDDocument with A4 page size (210mm × 297mm)
3. For each image file in order:
   a. Load image file bytes via `java.nio.file.Files.readAllBytes()`
   b. Create `PDImageXObject` via `PDImageXObject.createFromByteArray(document, imageBytes, imageName)`
   c. Calculate image dimensions and scale factor to fit A4 while preserving aspect ratio
   d. Calculate centered x, y position on A4 page
   e. Create blank PDPage (A4)
   f. Open PDPageContentStream and draw the image: `contentStream.drawImage(pdImage, x, y, scaledWidth, scaledHeight)`
   g. Close content stream
   h. Add blank PDPage (A4) to document
4. Return PDDocument as ResponseEntity<Resource>

**Key Implementation Notes:**
- Do NOT load images as AWT Image objects directly—PDFBox requires `PDImageXObject` for page drawing
- Scale calculations: if image is 1000×800 and A4 is 595×842 points, scale = min(595/1000, 842/800) = 0.595, then widthOnPage = 1000×0.595, heightOnPage = 800×0.595
- Center position: x = (595 - scaledWidth) / 2, y = (842 - scaledHeight) / 2
- Use try-with-resources for PDPageContentStream to ensure cleanup

**Key Libraries:**
- `org.apache.pdfbox:pdfbox` — PDF creation
- `javax.imageio.ImageIO` — Image loading and metadata
- `java.awt.Image` — Image scaling
- `stirling.software.common.util.TempFileManager` — Temp file cleanup

**Error Handling:**
- Invalid image format → 400 Bad Request with message
- Empty file list → 400 Bad Request
- Image load failure → 500 with specific image name in error

### 3.2 Frontend Architecture

**Tool ID:** `interleavedPages`

**Directory Structure:**
```
frontend/editor/src/
├── core/
│   ├── hooks/tools/interleavedPages/
│   │   ├── useInterleavedPagesParameters.ts
│   │   └── useInterleavedPagesOperation.ts
│   ├── components/tools/interleavedPages/
│   │   └── InterleavedPagesSettings.tsx
│   └── data/toolsTaxonomy.ts (update)
├── tools/
│   └── InterleavedPages.tsx
└── data/
    └── useTranslatedToolRegistry.tsx (update)
```

**Hooks:**

**`useInterleavedPagesParameters.ts`:**
- Export `InterleavedPagesParameters` interface with fields:
  - `none` (no special parameters needed; reordering happens in UI)
- Default parameters object
- Validation function (always valid since no user-configurable params)

**`useInterleavedPagesOperation.ts`:**
- Export `buildInterleavedPagesFormData(parameters, files)` — appends files to FormData in order (file order determined by array sequence)
  - Pattern: use `objectToFormData()` helper (from merge tool) to build FormData with `{ fileInput: files }`
  - Reordered files are already in correct order in the `files` array by the time this function receives them
- Export `interleavedPagesOperationConfig` — configuration for `useToolOperation`
  - `operationType: 'interleavedPages'`
  - `endpoint: '/api/v1/general/interleaved-pages'`
  - `multiFileEndpoint: true`
  - `filePrefix: 'interleaved_'` (or translate to match output naming)
- Export `useInterleavedPagesOperation()` hook
- Reference: see `useMergeOperation.ts` for exact FormData pattern

**Component:**

**`InterleavedPages.tsx`:**
- Main tool component
- Use `useBaseTool` pattern for state management
- Import `useInterleavedPagesParameters` and `useInterleavedPagesOperation`
- Flow:
  1. File selection step (multi-file upload)
  2. Settings step (reorderable file list + preview)
  3. Execute button
  4. Results display (download, undo, success message)

**`InterleavedPagesSettings.tsx`:**
- Reorderable list of selected images (maintain an ordered array in component state)
- Show small thumbnail of each image
- Drag-to-reorder or up/down buttons for accessibility
- Display file names and estimated output (e.g., "3 images → 6 pages")
- Allow file removal before processing
- When user proceeds: pass reordered files array to `buildInterleavedPagesFormData()` which appends them to FormData in that order
- Backend processes files in the order they arrive in the multipart request

**Registry Entry** (in `useTranslatedToolRegistry.tsx`):
```typescript
interleavedPages: {
  icon: <Icon name="images" size="1.5rem" />,
  name: t("home.interleavedPages.title", "Interleaved Pages"),
  component: InterleavedPages,
  description: t("home.interleavedPages.desc", "Combine images into a PDF with blank pages for duplex printing"),
  categoryId: ToolCategoryId.STANDARD_TOOLS,
  subcategoryId: SubcategoryId.PAGE_FORMATTING,
  maxFiles: -1, // unlimited
  endpoints: ["general/interleaved-pages"],
  operationConfig: interleavedPagesOperationConfig,
  settingsComponent: InterleavedPagesSettings,
  supportsAutomate: true,
},
```

### 3.3 Integration Points

**Tool Taxonomy Update:**
- Already exists: `SubcategoryId.PAGE_FORMATTING`
- Placement: Same category as `BookletImposition`, `PosterPdf`, `MultiPageLayout`

**i18n Integration:**
- New translation keys in `frontend/editor/public/locales/en-US.json`:
  - `home.interleavedPages.title`
  - `home.interleavedPages.desc`
  - `interleavedPages.files.placeholder`
  - `interleavedPages.steps.settings`
  - `interleavedPages.settings.reorder`
  - `interleavedPages.submit`
  - `interleavedPages.results.title`
  - `interleavedPages.error.invalidImage`
  - `interleavedPages.error.emptyFileList`

**Automation Support:**
- Tool supports automation via `operationConfig`
- Automation settings component not needed (no parameters to configure)
- Can be included in workflows and pipelines

---

## 4. Data Flow

### 4.1 User Interaction Flow

```
1. User opens tool
   ↓
2. Selects multiple images (drag-drop or file picker)
   ↓
3. Images displayed in reorderable list
   ↓
4. User optionally reorders images
   ↓
5. User clicks "Process" / "Create PDF"
   ↓
6. Frontend sends FormData with ordered files to /api/v1/misc/interleaved-pages
   ↓
7. Backend processes images, creates PDF, returns file
   ↓
8. Frontend displays download link + preview thumbnail
   ↓
9. User downloads or redoes
```

### 4.2 Backend Processing

```
Input: MultipartFile[] {image1.jpg, image2.png, image3.gif} (in request order)

1. Validate input (non-empty, all files are readable)
2. Create PDDocument with A4 page size (595 points wide × 842 points tall)
3. For each image file in request order:
   a. Read image file bytes
   b. Create PDImageXObject from bytes
   c. Get image dimensions (width, height)
   d. Calculate scale factor: scaleFactor = min(595/imgWidth, 842/imgHeight)
   e. Calculate centered position: x = (595 - scaledWidth)/2, y = (842 - scaledHeight)/2
   f. Create blank PDPage (A4) with white background
   g. Open PDPageContentStream, draw image at scaled size and centered position
   h. Close content stream
   i. Create blank PDPage (A4)
4. Save PDDocument to temp file
5. Return via WebResponseUtils.pdfDocToWebResponse()
6. TempFileManager cleans up temp files automatically
```

---

## 5. UI/UX Design

### 5.1 File Selection Step
- Standard multi-file upload (drag-and-drop zone)
- Accept image formats only (.jpg, .png, .gif, .webp, .bmp, .tiff)
- Show file count when files selected
- Placeholder text: "Drag images here or click to select. They'll appear on odd pages with blank pages on the back."

### 5.2 Settings/Reorder Step
- List each image with small thumbnail (100px × 100px)
- Show file name below thumbnail
- Display index (1, 2, 3...)
- Drag handle to reorder or arrow buttons (up/down)
- Remove button (×) to delete from selection
- Summary line: "N images → N×2 pages" (e.g., "3 images → 6 pages")
- Optional: Show estimated file size of output

### 5.3 Results Step
- "Your PDF is ready" confirmation message
- Large download button
- Option to undo (go back to file selection)
- Preview thumbnail of first page
- Success toast notification

---

## 6. Error Handling

| Error Scenario | HTTP Status | User Message | Backend Action |
|---|---|---|---|
| No files uploaded | 400 | "Please select at least one image" | Validate in controller |
| Non-image file uploaded | 400 | "File '{name}' is not a supported image format" | Check MIME type or ImageIO load |
| Corrupted image | 500 | "Failed to process image '{name}'. Is it a valid image file?" | Log exception, return message |
| Unsupported image format | 400 | "Image format not supported. Try JPG, PNG, or GIF" | ImageIO throws exception |
| Disk space exhausted | 500 | "Insufficient space to create PDF. Try with fewer images" | TempFileManager exception |

---

## 7. Testing Strategy

### Backend Tests
- **Unit:** 
  - Image scaling logic (aspect ratio preservation)
  - Page size calculations
  - Order preservation
- **Integration:**
  - Multi-file endpoint with various image formats
  - Output PDF structure (page count = 2×input count)
  - Temp file cleanup
  - Error scenarios (invalid formats, missing files)

### Frontend Tests
- **Component:**
  - File selection accepts images only
  - Reorder UI updates order correctly
  - Drag-and-drop functionality
- **Hook:**
  - FormData built with correct file order
  - Operation executes successfully
  - Error handling displays user messages

### Manual Testing Checklist
- [ ] Upload 1 image → output is 2 pages
- [ ] Upload 5 images → output is 10 pages (verify order preserved)
- [ ] Reorder images before processing → order is reflected in output PDF
- [ ] Large image (10MB+) scales correctly
- [ ] Small image (100×100px) scales to fit A4
- [ ] Non-image file rejected with error message
- [ ] Tool works in automation workflows
- [ ] Translations work (change locale, verify UI text updates)

---

## 8. Success Criteria

✓ Tool is in "Page Formatting" category under "Coloring book"  
✓ Accepts multiple image files (any order)  
✓ Produces PDF with alternating image/blank pages  
✓ Images scaled to A4 while preserving aspect ratio  
✓ Output filename is `interleaved_[timestamp].pdf`  
✓ Supports automation/workflows  
✓ All i18n strings in place  
✓ Tests pass (unit + integration)  
✓ Handles errors gracefully  
✓ Works with large images and large file counts (verified up to 50+ images)

---

## 9. Known Limitations & Future Enhancements

### Known Limitations (v1)
- **A4 only:** Page size is hardcoded to A4. US Letter and other sizes not supported in v1.
- **No EXIF auto-rotation:** Images with embedded EXIF rotation metadata won't auto-rotate. Phone photos may appear sideways if camera wasn't held upright.
- **No progress indication:** Large image sets (20+) provide no progress bar during PDF generation.
- **UI cap at ~20 images:** Reorderable list UI may become sluggish with 50+ images due to thumbnail rendering.

### Future Enhancements (v2+)
- Custom page sizes (Letter, A3, custom dimensions)
- EXIF orientation auto-detection and rotation
- Progress indication during PDF generation (streaming or modal with ETA)
- Batch processing of multiple image sets
- Custom blank page styling (colors, watermarks)
- Collating option (e.g., color on left, blank on right)
- Image quality/compression parameter

---

## 10. Dependencies & Libraries

**Backend:**
- Apache PDFBox (already in dependencies)
- Java AWT/ImageIO (standard library)
- Spring Boot 4.1.1 (already in dependencies)

**Frontend:**
- React (already in dependencies)
- React i18next (already in dependencies)
- Mantine UI (already in dependencies)
- React Beautiful DnD or native HTML5 drag-and-drop for reordering

**No new external dependencies required.**

---

## 11. Implementation Order

1. Create backend controller and request model
2. Write backend logic (image loading, PDF creation, scaling)
3. Create frontend hooks (parameters, operation)
4. Create frontend components (settings, main tool)
5. Register tool in taxonomy and registry
6. Add i18n translations
7. Write tests (backend + frontend)
8. Manual testing and refinement
9. Commit and prepare PR

---

## 12. Estimation

- **Backend:** 2-3 hours (image scaling logic is the main complexity)
- **Frontend:** 3-4 hours (reorderable UI + integration)
- **Testing:** 1-2 hours (unit + integration + manual)
- **Total:** 6-9 hours
