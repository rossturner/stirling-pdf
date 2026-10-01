# Coloring Book Tool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Coloring Book" tool under Page Formatting that turns N images into a 2N-page PDF: image 1, blank, image 2, blank, and so on, for double-sided printing.

**Architecture:** A new `PdfUtils.imagesToColoringBook` reuses the existing image-to-page helpers (A4, aspect-ratio fit, landscape auto-rotate), then inserts a blank page of the same size after every page. `POST /api/v1/misc/coloring-book` (`ColoringBookController`) exposes it. The frontend is a standard multi-file `useBaseTool` / `defineMultiFileTool` tool. The generated tool models (frontend `toolApiTypes.ts`/`toolIO.ts`, engine `tool_models.py`/`tool_io.py`) are regenerated from the backend's OpenAPI spec.

**Tech Stack:** Spring Boot 4 / PDFBox 3 / JUnit 5 + AssertJ + Mockito (backend); React + TypeScript + Mantine + Vitest (frontend); Taskfile.

**Spec:** `docs/superpowers/specs/2026-10-01-coloring-book-tool-design.md`

## Global Constraints

- Endpoint: `POST /api/v1/misc/coloring-book`; endpoint name `coloring-book`; tool id `coloringBook`; URL `/coloring-book`.
- Page size: fixed A4. `fitOption = "maintainAspectRatio"`, `autoRotate = true`, `colorType = "color"`.
- Output: exactly 2N pages for N images (a TIFF frame counts as an image), in the order image, blank, image, blank, …. Each blank is the same size as the image page in front of it.
- An undecodable image fails the whole request. Never skip an image.
- Empty or missing input: 400 via `ExceptionUtils.createIllegalArgumentException`.
- Frontend output filename: `<first image base name>_coloring_book.pdf`.
- Frontend imports always use `@app/*`. Icons via `<Icon name="…" />`. No raw colours.
- Translations: edit `frontend/editor/public/locales/en-US/translation.toml` only, then run `task pre-commit:fix`.
- Comments follow `devGuide/CODE_COMMENTS.md`: only a contract, a why or a hazard; no narration or banners. `task comment-lint` must pass.
- Jackson 3 / Spring Boot 4 stack: copy imports from neighbouring files, not from memory.
- Don't touch `EndpointManagementCard.tsx` or `api-stubs.ts` (deliberately out of scope).

## Review Focus

1. **Mixed workbench (PDFs and images loaded together)**: only the images should be sent, and the PDFs ignored, not cause an error. Pinned in Task 3 by a `toolAcceptsFile` test on the endpoint.
2. **Upper-case or `jpeg` extensions (`PHOTO.JPG`, `scan.JPEG`)**: these should be accepted like lower-case ones. Pinned in Task 3.
3. **Square and very tall images**: these should stay on portrait A4, with a portrait blank behind. Only width > height counts as landscape. Pinned in Task 1.
4. **Mixed orientations in one batch**: each blank should match its own image page, and the order must be kept. Pinned in Task 1 by an orientation-sequence test.
5. **One corrupt file among valid images**: the request should fail outright with no partial PDF. Pinned in Task 1.

---

### Task 1: `PdfUtils.imagesToColoringBook`

**Files:**
- Modify: `app/common/src/main/java/stirling/software/common/util/PdfUtils.java` (`imageToPdf` at ~line 445)
- Create: `app/common/src/test/java/stirling/software/common/util/PdfUtilsColoringBookTest.java`

**Interfaces:**
- Produces: `public byte[] PdfUtils.imagesToColoringBook(MultipartFile[] files, CustomPDFDocumentFactory pdfDocumentFactory) throws IOException`. `PdfUtils` is a Lombok `@UtilityClass`, so call it statically: `PdfUtils.imagesToColoringBook(...)`.

- [ ] **Step 1: Write the failing tests**

Create `PdfUtilsColoringBookTest.java`:

```java
package stirling.software.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.common.service.CustomPDFDocumentFactory;

class PdfUtilsColoringBookTest {

    private CustomPDFDocumentFactory factory;

    @BeforeEach
    void setUp() throws IOException {
        factory = mock(CustomPDFDocumentFactory.class);
        when(factory.createNewDocument()).thenReturn(new PDDocument());
    }

    private static MockMultipartFile png(String name, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, width - 1, height - 1);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return new MockMultipartFile("fileInput", name, "image/png", baos.toByteArray());
    }

    private static MockMultipartFile twoFrameTiff() throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);
            for (Color c : new Color[] {Color.RED, Color.BLUE}) {
                BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(c);
                g.fillRect(0, 0, 16, 16);
                g.dispose();
                writer.writeToSequence(new IIOImage(img, null, null), param);
            }
            writer.endWriteSequence();
        }
        writer.dispose();
        return new MockMultipartFile("fileInput", "scan.tiff", "image/tiff", baos.toByteArray());
    }

    private PDDocument run(MultipartFile... files) throws IOException {
        return Loader.loadPDF(PdfUtils.imagesToColoringBook(files, factory));
    }

    private static boolean isLandscape(PDPage page) {
        return page.getMediaBox().getWidth() > page.getMediaBox().getHeight();
    }

    private static void assertSameSize(PDPage a, PDPage b) {
        assertThat(b.getMediaBox().getWidth()).isEqualTo(a.getMediaBox().getWidth());
        assertThat(b.getMediaBox().getHeight()).isEqualTo(a.getMediaBox().getHeight());
    }

    @Test
    @DisplayName("N images give 2N pages: content on odd pages, blank on even pages")
    void interleavesBlankPages() throws IOException {
        try (PDDocument doc = run(png("a.png", 100, 200), png("b.png", 100, 200), png("c.png", 100, 200))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(6);
            for (int i = 0; i < 6; i += 2) {
                assertThat(doc.getPage(i).hasContents()).isTrue();
                assertThat(doc.getPage(i + 1).hasContents()).isFalse();
                assertSameSize(doc.getPage(i), doc.getPage(i + 1));
            }
        }
    }

    @Test
    @DisplayName("image pages are A4 and the blank back matches each one, in input order")
    void keepsOrderAndPerPageOrientation() throws IOException {
        try (PDDocument doc =
                run(png("p1.png", 100, 200), png("l.png", 200, 100), png("p2.png", 100, 200))) {
            boolean[] expectedLandscape = {false, false, true, true, false, false};
            for (int i = 0; i < 6; i++) {
                assertThat(isLandscape(doc.getPage(i))).as("page %d", i + 1)
                        .isEqualTo(expectedLandscape[i]);
            }
            PDRectangle portrait = doc.getPage(0).getMediaBox();
            assertThat(portrait.getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(portrait.getHeight()).isEqualTo(PDRectangle.A4.getHeight());
        }
    }

    @Test
    @DisplayName("square and very tall images stay on portrait A4")
    void squareAndTallArePortrait() throws IOException {
        try (PDDocument doc = run(png("sq.png", 100, 100), png("tall.png", 10, 1000))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(4);
            for (int i = 0; i < 4; i++) {
                assertThat(isLandscape(doc.getPage(i))).as("page %d", i + 1).isFalse();
            }
        }
    }

    @Test
    @DisplayName("each TIFF frame gets its own blank back")
    void tiffFramesEachGetABlank() throws IOException {
        try (PDDocument doc = run(twoFrameTiff())) {
            assertThat(doc.getNumberOfPages()).isEqualTo(4);
            assertThat(doc.getPage(0).hasContents()).isTrue();
            assertThat(doc.getPage(1).hasContents()).isFalse();
            assertThat(doc.getPage(2).hasContents()).isTrue();
            assertThat(doc.getPage(3).hasContents()).isFalse();
        }
    }

    @Test
    @DisplayName("an undecodable image fails the whole request")
    void corruptImageFails() throws IOException {
        MockMultipartFile corrupt =
                new MockMultipartFile("fileInput", "bad.png", "image/png", "not an image".getBytes());
        MultipartFile[] files = {png("a.png", 100, 200), corrupt, png("c.png", 100, 200)};
        assertThrows(IOException.class, () -> PdfUtils.imagesToColoringBook(files, factory));
    }
}
```

Sharing of the blank page's mediabox object can't be observed through the returned bytes, so no test covers it. Step 3 builds a fresh `PDRectangle` instead.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :common:test --tests "stirling.software.common.util.PdfUtilsColoringBookTest"`
Expected: compilation FAILS with `cannot find symbol … imagesToColoringBook`.

- [ ] **Step 3: Implement**

In `PdfUtils.java`, replace the body of `imageToPdf` and add the new method and two private helpers next to it. Add `import org.apache.pdfbox.pdmodel.PDPageTree;`. `ArrayList` and `List` are already imported.

```java
    public byte[] imageToPdf(
            MultipartFile[] files,
            String fitOption,
            boolean autoRotate,
            String colorType,
            CustomPDFDocumentFactory pdfDocumentFactory)
            throws IOException {
        try (PDDocument doc = pdfDocumentFactory.createNewDocument()) {
            appendImages(doc, files, fitOption, autoRotate, colorType);
            return saveToBytes(doc);
        }
    }

    /**
     * Lays each image on its own A4 page (aspect ratio kept, landscape images on landscape A4) and
     * follows every image page with a blank page of the same size, so a duplex print leaves the
     * back of each image empty. A multi-frame TIFF contributes one image page per frame.
     *
     * <p>Throws if any image cannot be decoded: skipping one would move every later image onto the
     * back of a sheet.
     */
    public byte[] imagesToColoringBook(
            MultipartFile[] files, CustomPDFDocumentFactory pdfDocumentFactory)
            throws IOException {
        try (PDDocument doc = pdfDocumentFactory.createNewDocument()) {
            appendImages(doc, files, "maintainAspectRatio", true, "color");
            PDPageTree pages = doc.getPages();
            List<PDPage> imagePages = new ArrayList<>();
            pages.forEach(imagePages::add);
            for (PDPage imagePage : imagePages) {
                PDRectangle size = imagePage.getMediaBox();
                pages.insertAfter(
                        new PDPage(new PDRectangle(size.getWidth(), size.getHeight())), imagePage);
            }
            return saveToBytes(doc);
        }
    }

    private void appendImages(
            PDDocument doc,
            MultipartFile[] files,
            String fitOption,
            boolean autoRotate,
            String colorType)
            throws IOException {
        for (MultipartFile file : files) {
            if (isTiff(file)) {
                appendTiffFrames(doc, file, fitOption, autoRotate, colorType);
            } else {
                appendSingleImage(doc, file, fitOption, autoRotate, colorType);
            }
        }
    }

    private byte[] saveToBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        doc.save(byteArrayOutputStream);
        log.debug("PDF successfully saved to byte array");
        return byteArrayOutputStream.toByteArray();
    }
```

If `PDPageTree.insertAfter` doesn't compile against the PDFBox version in use, stop and report it. Don't swap in a different approach silently.

- [ ] **Step 4: Run the new tests and the existing imageToPdf tests**

Run: `./gradlew :common:test --tests "stirling.software.common.util.PdfUtilsColoringBookTest" --tests "stirling.software.common.util.PdfUtils*"`
Expected: all PASS. The existing `imageToPdf` TIFF tests in `PdfUtilsMoreTest` show the refactor didn't change behaviour.

- [ ] **Step 5: Format and commit**

```bash
task backend:format
git add app/common/src/main/java/stirling/software/common/util/PdfUtils.java app/common/src/test/java/stirling/software/common/util/PdfUtilsColoringBookTest.java
git commit -m "feat(coloring-book): build image pages interleaved with blank backs"
```

---

### Task 2: Endpoint, registration and generated models

**Files:**
- Create: `app/core/src/main/java/stirling/software/SPDF/model/api/misc/ColoringBookRequest.java`
- Create: `app/core/src/main/java/stirling/software/SPDF/controller/api/misc/ColoringBookController.java`
- Create: `app/core/src/test/java/stirling/software/SPDF/controller/api/misc/ColoringBookControllerTest.java`
- Modify: `app/common/src/main/java/stirling/software/SPDF/config/EndpointConfiguration.java` (line ~357 `PageOps`, line ~521 `Java`)
- Regenerated (commit as-is): `frontend/editor/src/core/types/toolApiTypes.ts`, `frontend/editor/src/core/types/toolIO.ts`, `engine/src/stirling/models/tool_models.py`, `engine/src/stirling/models/tool_io.py`

**Interfaces:**
- Consumes: `PdfUtils.imagesToColoringBook(MultipartFile[], CustomPDFDocumentFactory)` from Task 1.
- Produces: `POST /api/v1/misc/coloring-book` (multipart field `fileInput`, repeated), returning `application/pdf`. A `TOOL_IO["/api/v1/misc/coloring-book"]` entry with `inputExtensions`. `"/api/v1/misc/coloring-book"` becomes a valid `ToolEndpoint`.

- [ ] **Step 1: Write the failing controller test**

```java
package stirling.software.SPDF.controller.api.misc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.SPDF.model.api.misc.ColoringBookRequest;
import stirling.software.common.service.CustomPDFDocumentFactory;

@ExtendWith(MockitoExtension.class)
class ColoringBookControllerTest {

    @Mock private CustomPDFDocumentFactory pdfDocumentFactory;

    @InjectMocks private ColoringBookController controller;

    private static MockMultipartFile png(String name) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 40, BufferedImage.TYPE_INT_RGB), "png", baos);
        return new MockMultipartFile("fileInput", name, "image/png", baos.toByteArray());
    }

    private static ColoringBookRequest request(MultipartFile[] files) {
        ColoringBookRequest request = new ColoringBookRequest();
        request.setFileInput(files);
        return request;
    }

    @Test
    void returnsInterleavedPdfNamedAfterFirstImage() throws IOException {
        when(pdfDocumentFactory.createNewDocument()).thenReturn(new PDDocument());

        ResponseEntity<byte[]> response =
                controller.createColoringBook(
                        request(new MultipartFile[] {png("cat.png"), png("dog.png")}));

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("cat_coloring_book.pdf");
        try (PDDocument doc = Loader.loadPDF(response.getBody())) {
            assertThat(doc.getNumberOfPages()).isEqualTo(4);
        }
    }

    @Test
    void rejectsMissingInput() {
        assertThrows(
                IllegalArgumentException.class, () -> controller.createColoringBook(request(null)));
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.createColoringBook(request(new MultipartFile[0])));
    }
}
```

- [ ] **Step 2: Run it and check it fails**

Run: `./gradlew :stirling-pdf:test --tests "stirling.software.SPDF.controller.api.misc.ColoringBookControllerTest"`
Expected: compilation FAILS (`ColoringBookController` / `ColoringBookRequest` not found).

- [ ] **Step 3: Implement the request model and controller**

`ColoringBookRequest.java`:

```java
package stirling.software.SPDF.model.api.misc;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

@Data
public class ColoringBookRequest {

    @Schema(
            description = "The images to lay out, one per page, in the order given.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile[] fileInput;
}
```

`ColoringBookController.java`:

```java
package stirling.software.SPDF.controller.api.misc;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.config.swagger.StandardPdfResponse;
import stirling.software.SPDF.model.api.misc.ColoringBookRequest;
import stirling.software.common.annotations.AutoJobPostMapping;
import stirling.software.common.annotations.api.MiscApi;
import stirling.software.common.enumeration.ResourceWeight;
import stirling.software.common.model.tool.ToolArity;
import stirling.software.common.model.tool.ToolFormat;
import stirling.software.common.model.tool.ToolIO;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.ExceptionUtils;
import stirling.software.common.util.GeneralUtils;
import stirling.software.common.util.PdfUtils;
import stirling.software.common.util.WebResponseUtils;

@MiscApi
@RequiredArgsConstructor
public class ColoringBookController {

    private final CustomPDFDocumentFactory pdfDocumentFactory;

    @AutoJobPostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            value = "/coloring-book",
            resourceWeight = ResourceWeight.LARGE_WEIGHT)
    @StandardPdfResponse
    @ToolIO(
            accepts = ToolFormat.IMAGE,
            imageIOInput = true,
            produces = ToolFormat.PDF,
            arity = ToolArity.MISO)
    @Operation(
            summary = "Create a coloring book from images",
            description =
                    "Places each image on its own A4 page, in order, and follows every image"
                            + " page with a blank page so the back of each image is empty when"
                            + " printed double-sided.")
    public ResponseEntity<byte[]> createColoringBook(@ModelAttribute ColoringBookRequest request)
            throws IOException {
        MultipartFile[] images = request.getFileInput();
        if (images == null || images.length == 0) {
            throw ExceptionUtils.createIllegalArgumentException(
                    "error.coloringBookImagesRequired", "At least one image is required");
        }
        byte[] pdf = PdfUtils.imagesToColoringBook(images, pdfDocumentFactory);
        return WebResponseUtils.bytesToWebResponse(
                pdf,
                GeneralUtils.generateFilename(images[0].getOriginalFilename(), "_coloring_book.pdf"));
    }
}
```

`EndpointConfiguration.java`: add one line after each `booklet-imposition` line:

```java
        addEndpointToGroup("PageOps", "coloring-book");
```
```java
        addEndpointToGroup("Java", "coloring-book");
```

- [ ] **Step 4: Run the controller test and the whole backend gate**

Run: `./gradlew :stirling-pdf:test --tests "stirling.software.SPDF.controller.api.misc.ColoringBookControllerTest"`
Expected: PASS.

Run: `task backend:format && task backend:check`
Expected: PASS. Watch for `ToolIODeclarationCoverageTest`, which requires the `@ToolIO` declaration added above.

- [ ] **Step 5: Regenerate the tool models**

Run: `task tool-models`
Expected: `toolApiTypes.ts` and `toolIO.ts` gain `/api/v1/misc/coloring-book`, and the engine `tool_models.py` / `tool_io.py` gain the coloring-book entries. Confirm with:

```bash
grep -n "coloring-book" frontend/editor/src/core/types/toolIO.ts frontend/editor/src/core/types/toolApiTypes.ts engine/src/stirling/models/tool_models.py engine/src/stirling/models/tool_io.py
```

The `toolIO.ts` entry must have `"accepts": ["IMAGE"]`, `"arity": "MISO"` and an `inputExtensions` list matching `/api/v1/convert/img/pdf`'s.

Run: `task engine:check`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/core/src/main/java/stirling/software/SPDF/model/api/misc/ColoringBookRequest.java \
  app/core/src/main/java/stirling/software/SPDF/controller/api/misc/ColoringBookController.java \
  app/core/src/test/java/stirling/software/SPDF/controller/api/misc/ColoringBookControllerTest.java \
  app/common/src/main/java/stirling/software/SPDF/config/EndpointConfiguration.java \
  frontend/editor/src/core/types/toolApiTypes.ts frontend/editor/src/core/types/toolIO.ts \
  engine/src/stirling/models/tool_models.py engine/src/stirling/models/tool_io.py
git commit -m "feat(coloring-book): add /api/v1/misc/coloring-book endpoint"
```

---

### Task 3: Frontend parameters and operation hooks

**Files:**
- Create: `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookParameters.ts`
- Create: `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.ts`
- Create: `frontend/editor/src/core/hooks/tools/coloringBook/useColoringBookOperation.test.ts`

**Interfaces:**
- Consumes: the `ToolEndpoint` `"/api/v1/misc/coloring-book"` and its `TOOL_IO` entry from Task 2.
- Produces:
  - `ColoringBookParameters` (type alias of `BaseParameters`), `defaultParameters`, `useColoringBookParameters(): BaseParametersHook<ColoringBookParameters>`
  - `COLORING_BOOK_ENDPOINT` (`"/api/v1/misc/coloring-book"`)
  - `buildColoringBookFormData(params: ColoringBookParameters, files: File[]): FormData`
  - `coloringBookFileName(firstImageName: string): string`
  - `coloringBookResponseHandler: ResponseHandler`
  - `coloringBookOperationConfig` (from `defineMultiFileTool`)
  - `useColoringBookOperation()`

- [ ] **Step 1: Write the failing tests**

`useColoringBookOperation.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import {
  buildColoringBookFormData,
  coloringBookFileName,
  coloringBookResponseHandler,
  COLORING_BOOK_ENDPOINT,
} from "@app/hooks/tools/coloringBook/useColoringBookOperation";
import { defaultParameters } from "@app/hooks/tools/coloringBook/useColoringBookParameters";
import { toolAcceptsFile } from "@app/utils/toolIOCompat";

const image = (name: string) => new File(["x"], name, { type: "image/png" });

describe("buildColoringBookFormData", () => {
  it("sends every image as fileInput in workbench order", () => {
    const formData = buildColoringBookFormData(defaultParameters, [
      image("b.png"),
      image("a.png"),
      image("c.png"),
    ]);
    const names = formData
      .getAll("fileInput")
      .map((entry) => (entry as File).name);
    expect(names).toEqual(["b.png", "a.png", "c.png"]);
  });
});

describe("coloringBookFileName", () => {
  it("replaces the image extension with _coloring_book.pdf", () => {
    expect(coloringBookFileName("cat.png")).toBe("cat_coloring_book.pdf");
  });

  it("keeps dots inside the base name", () => {
    expect(coloringBookFileName("my.cat.v2.jpeg")).toBe(
      "my.cat.v2_coloring_book.pdf",
    );
  });

  it("handles a name with no extension", () => {
    expect(coloringBookFileName("scan")).toBe("scan_coloring_book.pdf");
  });
});

describe("coloringBookResponseHandler", () => {
  it("returns one PDF named after the first image", async () => {
    const blob = new Blob(["%PDF"], { type: "application/pdf" });
    const files = await coloringBookResponseHandler(blob, [
      image("cat.png"),
      image("dog.png"),
    ]);
    expect(files).toHaveLength(1);
    expect(files[0].name).toBe("cat_coloring_book.pdf");
    expect(files[0].type).toBe("application/pdf");
  });
});

describe("coloring-book input filtering", () => {
  it("accepts images, including upper-case and jpeg extensions", () => {
    for (const name of ["a.png", "PHOTO.JPG", "scan.JPEG", "frames.tiff"]) {
      expect(
        toolAcceptsFile(COLORING_BOOK_ENDPOINT, { name, type: "" }),
        name,
      ).toBe(true);
    }
  });

  it("ignores PDFs loaded alongside the images", () => {
    expect(
      toolAcceptsFile(COLORING_BOOK_ENDPOINT, {
        name: "doc.pdf",
        type: "application/pdf",
      }),
    ).toBe(false);
  });
});
```

If `toolAcceptsFile`'s `Pick<StirlingFileStub, "name" | "type" | "processedFile">` needs `processedFile`, add `processedFile: undefined` to the literals.

- [ ] **Step 2: Run them and check they fail**

Run: `cd frontend && npx vitest run editor/src/core/hooks/tools/coloringBook`
Expected: FAIL, because the module `@app/hooks/tools/coloringBook/useColoringBookOperation` can't be resolved.

- [ ] **Step 3: Implement**

`useColoringBookParameters.ts`:

```ts
import { BaseParameters } from "@app/types/parameters";
import {
  useBaseParameters,
  BaseParametersHook,
} from "@app/hooks/tools/shared/useBaseParameters";

export type ColoringBookParameters = BaseParameters;

export const defaultParameters: ColoringBookParameters = {};

export const useColoringBookParameters =
  (): BaseParametersHook<ColoringBookParameters> =>
    useBaseParameters({
      defaultParameters,
      endpointName: "coloring-book",
    });
```

`useColoringBookOperation.ts`:

```ts
import { useTranslation } from "react-i18next";
import {
  useToolOperation,
  defineMultiFileTool,
} from "@app/hooks/tools/shared/useToolOperation";
import { type ToolEndpoint } from "@app/hooks/tools/shared/toolApiMapping";
import { createStandardErrorHandler } from "@app/utils/toolErrorHandler";
import type { ResponseHandler } from "@app/utils/toolResponseProcessor";
import {
  ColoringBookParameters,
  defaultParameters,
} from "@app/hooks/tools/coloringBook/useColoringBookParameters";

export const COLORING_BOOK_ENDPOINT =
  "/api/v1/misc/coloring-book" satisfies ToolEndpoint;

export const buildColoringBookFormData = (
  _parameters: ColoringBookParameters,
  files: File[],
): FormData => {
  const formData = new FormData();
  files.forEach((file) => formData.append("fileInput", file));
  return formData;
};

export const coloringBookFileName = (firstImageName: string): string => {
  const dot = firstImageName.lastIndexOf(".");
  const baseName = dot > 0 ? firstImageName.slice(0, dot) : firstImageName;
  return `${baseName}_coloring_book.pdf`;
};

// Without this the multi-file path names the PDF after the first input as-is (`…cat.png`).
export const coloringBookResponseHandler: ResponseHandler = (
  blob,
  originalFiles,
) => [
  new File([blob], coloringBookFileName(originalFiles[0]?.name ?? "images"), {
    type: "application/pdf",
  }),
];

export const coloringBookOperationConfig = defineMultiFileTool({
  buildFormData: buildColoringBookFormData,
  operationType: "coloringBook",
  endpoint: COLORING_BOOK_ENDPOINT,
  filePrefix: "coloring_book_",
  responseHandler: coloringBookResponseHandler,
  defaultParameters,
});

export const useColoringBookOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<ColoringBookParameters>({
    ...coloringBookOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t(
        "coloringBook.error.failed",
        "An error occurred while creating the coloring book.",
      ),
    ),
  });
};
```

If `defineMultiFileTool`'s type requires `toApiParams`/`fromApiParams` for this endpoint, add `toApiParams: () => ({ fileInput: [] })` and `fromApiParams: () => ({})`. These follow the CreatePortfolio pattern, where the files are not scalar parameters. Match whatever shape the generated `ToolApiParams["/api/v1/misc/coloring-book"]` requires.

- [ ] **Step 4: Run the tests and check they pass**

Run: `cd frontend && npx vitest run editor/src/core/hooks/tools/coloringBook`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/editor/src/core/hooks/tools/coloringBook
git commit -m "feat(coloring-book): add frontend operation and parameter hooks"
```

---

### Task 4: Tool UI, registration, translations and OG metadata

**Files:**
- Create: `frontend/editor/src/core/tools/ColoringBook.tsx`
- Create: `frontend/editor/src/core/components/tooltips/useColoringBookTips.ts`
- Modify: `frontend/editor/src/core/types/toolId.ts` (add `"coloringBook"` next to `"createPortfolio"`, ~line 58)
- Modify: `frontend/editor/src/core/data/useTranslatedToolRegistry.tsx` (import ~line 32; entry after `bookletImposition`, ~line 690)
- Modify: `frontend/editor/src/core/utils/urlMapping.ts` (next to `"/create-portfolio"`, ~line 90)
- Modify: `frontend/editor/public/locales/en-US/translation.toml`
- Regenerated: `frontend/editor/public/og-metadata.json`, `frontend/editor/public/og-metadata.saas.json`, `frontend/editor/src/core/data/ogImageMap.json`, `frontend/editor/src/core/data/urlSeoOverrides.json` (whichever change)

**Interfaces:**
- Consumes: `useColoringBookParameters`, `useColoringBookOperation`, `coloringBookOperationConfig` and `COLORING_BOOK_ENDPOINT` from Task 3; `toolIOFor` from `@app/types/toolIO`.
- Produces: tool id `coloringBook`, route `/coloring-book`.

- [ ] **Step 1: Tooltip hook**

`useColoringBookTips.ts`:

```ts
import { useTranslation } from "react-i18next";
import { TooltipContent } from "@app/types/tips";

export const useColoringBookTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t("coloringBook.tooltip.header.title", "About coloring books"),
    },
    tips: [
      {
        title: t("coloringBook.tooltip.description.title", "What it does"),
        description: t(
          "coloringBook.tooltip.description.text",
          "Puts each picture on its own page with a blank page after it, so when you print double-sided the back of every picture is empty and colours can't show through.",
        ),
        bullets: [
          t(
            "coloringBook.tooltip.description.bullet1",
            "Pictures go in the same order as your files. Reorder them in the file editor.",
          ),
          t(
            "coloringBook.tooltip.description.bullet2",
            "Every page is A4. Wide pictures get a landscape page.",
          ),
        ],
      },
    ],
  };
};
```

- [ ] **Step 2: Tool component**

`ColoringBook.tsx`. There are no settings, so a single information step carries the tooltip and the duplex note:

```tsx
import { useTranslation } from "react-i18next";
import { Text } from "@mantine/core";
import { createToolFlow } from "@app/components/tools/shared/createToolFlow";
import { useColoringBookParameters } from "@app/hooks/tools/coloringBook/useColoringBookParameters";
import { useColoringBookOperation } from "@app/hooks/tools/coloringBook/useColoringBookOperation";
import { useBaseTool } from "@app/hooks/tools/shared/useBaseTool";
import { useColoringBookTips } from "@app/components/tooltips/useColoringBookTips";
import { BaseToolProps, ToolComponent } from "@app/types/tool";

const ColoringBook = (props: BaseToolProps) => {
  const { t } = useTranslation();
  const tips = useColoringBookTips();

  // The viewer would otherwise narrow the input to the one image on screen.
  const base = useBaseTool(
    "coloringBook",
    useColoringBookParameters,
    useColoringBookOperation,
    props,
    { ignoreViewerScope: true },
  );

  return createToolFlow({
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
    },
    steps: [
      {
        title: t("coloringBook.info.title", "Double-sided printing"),
        isCollapsed: base.hasResults,
        tooltip: tips,
        content: (
          <Text size="sm" c="dimmed">
            {t(
              "coloringBook.info.text",
              "Each picture gets its own page followed by a blank page, ready to print double-sided.",
            )}
          </Text>
        ),
      },
    ],
    executeButton: {
      text: t("coloringBook.submit", "Create coloring book"),
      isVisible: !base.hasResults,
      loadingText: t("loading"),
      onClick: base.handleExecute,
      endpointEnabled: base.endpointEnabled,
      paramsValid: base.params.validateParameters(),
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t("coloringBook.results.title", "Coloring book"),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

ColoringBook.tool = () => useColoringBookOperation;

export default ColoringBook as ToolComponent;
```

- [ ] **Step 3: Register the tool**

`toolId.ts`: add `"coloringBook",` next to `"createPortfolio",`.

`urlMapping.ts`: add `"/coloring-book": "coloringBook",` next to `"/create-portfolio": "createPortfolio",`.

`useTranslatedToolRegistry.tsx`: add these imports next to the `createPortfolioOperationConfig` import:

```tsx
import {
  coloringBookOperationConfig,
  COLORING_BOOK_ENDPOINT,
} from "@app/hooks/tools/coloringBook/useColoringBookOperation";
import { toolIOFor } from "@app/types/toolIO";
```

Then add this entry directly after the `bookletImposition` entry:

```tsx
      coloringBook: {
        icon: <Icon name="palette" size="1.5rem" />,
        name: t("home.coloringBook.title", "Coloring Book"),
        component: lazy(() => import("@app/tools/ColoringBook")),
        description: t(
          "home.coloringBook.desc",
          "Turn images into a printable coloring book with a blank back behind every page",
        ),
        categoryId: ToolCategoryId.STANDARD_TOOLS,
        subcategoryId: SubcategoryId.PAGE_FORMATTING,
        maxFiles: -1,
        endpoints: ["coloring-book"],
        supportedFormats: toolIOFor(COLORING_BOOK_ENDPOINT)?.inputExtensions,
        operationConfig: asRegistryConfig(coloringBookOperationConfig),
        automationSettings: null,
        synonyms: getSynonyms(t, "coloringBook"),
      },
```

- [ ] **Step 4: Translations**

In `frontend/editor/public/locales/en-US/translation.toml`, add the tables in alphabetical position. `[coloringBook…]` goes before `[createPortfolio]` (~line 4205), and `[home.coloringBook]` goes before `[home.createPortfolio]` (~line 5627). Keep any existing neighbour that sorts between them in order.

```toml
[coloringBook]
submit = "Create coloring book"

[coloringBook.error]
failed = "An error occurred while creating the coloring book."

[coloringBook.info]
text = "Each picture gets its own page followed by a blank page, ready to print double-sided."
title = "Double-sided printing"

[coloringBook.results]
title = "Coloring book"

[coloringBook.tooltip.description]
bullet1 = "Pictures go in the same order as your files. Reorder them in the file editor."
bullet2 = "Every page is A4. Wide pictures get a landscape page."
text = "Puts each picture on its own page with a blank page after it, so when you print double-sided the back of every picture is empty and colours can't show through."
title = "What it does"

[coloringBook.tooltip.header]
title = "About coloring books"
```

```toml
[home.coloringBook]
desc = "Turn images into a printable coloring book with a blank back behind every page"
tags = "coloring book,colouring book,coloring pages,colouring pages,kids,print,double-sided,duplex,blank pages,images to pdf"
title = "Coloring Book"
```

Run: `task pre-commit:fix`

- [ ] **Step 5: Regenerate OG metadata**

Run: `node frontend/editor/scripts/generate-og-metadata.mjs`
Expected: `og-metadata.json` and `og-metadata.saas.json` gain `coloringBook` and `/coloring-book` entries. Check with `git diff --stat frontend/editor`.

- [ ] **Step 6: Run the frontend gate**

Run: `task frontend:check`
Expected: PASS (typecheck, lint including icons/colours/comments, format, tests).

Run: `task comment-lint`
Expected: no findings.

- [ ] **Step 7: Commit**

```bash
git add frontend/editor
git commit -m "feat(coloring-book): add Coloring Book tool under Page Formatting"
```

---

### Task 5: End-to-end verification in the running app

**Files:** none (verification only; screenshots go to the session scratchpad).

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Start the app**

Run `task dev` in the background and wait until the backend answers on `http://localhost:8080` and the frontend on `http://localhost:5173`.

- [ ] **Step 2: Make test images**

In the scratchpad, write three PNGs with Python/PIL or ImageMagick: `1-portrait.png` (600×900), `2-landscape.png` (900×600) and `3-portrait.png` (600×900). Each has a distinct outline drawing or label, so the order can be seen.

- [ ] **Step 3: Drive the UI with Playwright**

1. Open `http://localhost:5173`, log in as `admin` / `password`.
2. Open the tool picker → Page Formatting → **Coloring Book**. Confirm the palette icon, the name and the description.
3. Upload the three images in order.
4. Click **Create coloring book**.
5. Confirm the result is one file named `1-portrait_coloring_book.pdf`.
6. Take a screenshot of the results panel.

- [ ] **Step 4: Check the PDF**

Download the output, or call the endpoint directly:

```bash
curl -s -u admin:password -o out.pdf \
  -F fileInput=@1-portrait.png -F fileInput=@2-landscape.png -F fileInput=@3-portrait.png \
  http://localhost:8080/api/v1/misc/coloring-book
```

If basic auth isn't accepted, use the downloaded file from the UI instead. Check it with `qpdf --show-npages out.pdf` (expect `6`) and with PDFBox/pdfinfo per-page sizes. Pages 1–2 and 5–6 should be 595×842, and pages 3–4 should be 842×595. Render page 2 to confirm it is blank.

- [ ] **Step 5: Check undo**

In the UI, click Undo on the result and confirm the three images come back in the workbench.

- [ ] **Step 6: Report**

Report the page count, the per-page sizes, the screenshot path, and any failures with their output. Nothing to commit.
