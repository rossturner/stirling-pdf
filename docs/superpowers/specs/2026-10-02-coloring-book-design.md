# Coloring Book Tool Design

Date: 2026-10-02

## Goal

Add a **Coloring Book** tool under **Page Formatting**. The tool accepts image files and returns one PDF for double-sided printing. It does not convert images to line art.

## Approved behaviour

- Preserve the submitted image order.
- Use portrait A4 pages.
- Keep each image's aspect ratio and centre it on the page. Leave white margins where the image does not fill the page.
- Add one blank portrait A4 page after every image page, including after the final image.
- Return one PDF. For `N` image pages, the PDF has `2N` pages.
- Use the same image formats as the existing image-to-PDF route.
- Use the existing PDF result flow. Do not add page-size, fit, or reorder controls.

Each image page has an odd page number. Its blank reverse page has the next even page number. If the existing decoder expands a multi-frame image into several pages, add a blank page after each decoded image page.

## Architecture

Add a dedicated multipart endpoint beside the existing image-to-PDF endpoint. Keep the existing endpoint and its default behaviour unchanged.

Reuse the current image decoding and proportional-fit logic. Add a focused PDF generation path that creates an A4 portrait image page and then a blank A4 portrait page for each decoded image page. The endpoint returns the resulting PDF.

Add a frontend tool that submits the selected images in order. Register it in the core tool registry under `ToolCategoryId.STANDARD_TOOLS` and `SubcategoryId.PAGE_FORMATTING`. Use the standard multi-file operation and result flow. Add English (US) translations.

## Errors

Reject empty or unreadable image input through the existing API error path. Report endpoint and PDF-generation failures through the standard tool error flow. Do not return a partial PDF.

## Verification

Backend tests cover page count, A4 portrait dimensions, image order, blank even pages, proportional image placement, and errors for empty or unreadable input. Frontend tests cover the new tool operation, selected file order, and registry entry.

Run `task backend:check` and `task frontend:check`. After translation changes, run `task pre-commit:fix`.

## Out of scope

- Converting images into coloring-book line art.
- Custom paper sizes or page orientation.
- Image reordering controls.
- Changes to the existing image-to-PDF tool.
