# Coloring Book Tool Design

## Goal

Add a **Coloring Book** tool to the **Page Formatting** category. It accepts one or more images and creates one PDF for duplex printing, with every image page followed by a blank page so the printed back of each coloring page remains empty.

## User-facing behavior

- Accept the image formats already supported by the image-to-PDF converter.
- Process images in the order shown in the selected-file list.
- Render each image centered on a portrait A4 page, preserving its aspect ratio. Any unused area remains white.
- Follow each image page with an empty portrait A4 page, including after the final image.
- Return one PDF. For single-page image files, N inputs produce exactly 2N pages.
- Keep the layout fixed; do not add page-size, orientation, color-conversion, or blank-page options.

The existing image conversion utility expands each frame in a multi-frame TIFF to its own PDF page. Treat each such frame as an image page and follow it with a blank page.

## Architecture

### Frontend

Register a dedicated tool named **Coloring Book** in the standard tool registry under `Page Formatting`. Use the standard multi-file tool flow and `FileContext` selection rather than adding a separate upload or file-processing path. Restrict the tool’s supported formats to the image formats accepted by the existing image converter and allow multiple files.

The operation sends the selected images, in selected-file order, to the dedicated endpoint. Use the standard operation result view, preview, download, and error handling. Add English (US) labels and descriptions for the tool and any user-facing instructions.

### Backend

Add a multipart endpoint at `POST /api/v1/convert/img/pdf/coloring-book`. Its request contains the image files and no layout options. Declare its image-to-PDF input/output contract in the endpoint metadata so the API and tool registries can identify it as a multi-input, single-PDF operation.

Use the existing image-to-PDF utility with aspect-preserving placement, portrait A4 pages, and the existing color-preserving behavior. After conversion, insert an empty page with the same page dimensions after every generated image page. Preserve generated page order. Return a single PDF through the standard PDF response and job handling.

Do not change the general image-to-PDF endpoint or its existing defaults.

### Errors

An empty file list, unsupported or unreadable image, or conversion failure must use the standard endpoint/job error response. Do not return a partial or success-shaped PDF when any image fails.

## Verification

Backend tests must verify page count and order for multiple images, portrait A4 image and blank pages, an empty page after the final image, the image-page sequence, and conversion failure for invalid image data. A multi-frame TIFF test must verify that each frame gets a following blank page.

Frontend tests must verify the tool’s Page Formatting registration, image-only/multi-file eligibility, endpoint and file-order form data, and standard operation result wiring. Run the relevant backend and frontend checks. Regenerate API-derived tool models where required by the endpoint contract and run the engine check if generated engine models change.

## Out of scope

- Landscape PDF pages or automatic image rotation.
- Alternate page sizes, image stretching/cropping, or color conversion controls.
- One output PDF per image.
- Optional blank pages or blanks placed before the image pages.
