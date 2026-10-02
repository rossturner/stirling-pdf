package stirling.software.SPDF.controller.api;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.model.api.general.InterleavedPagesRequest;
import stirling.software.common.annotations.AutoJobPostMapping;
import stirling.software.common.enumeration.ResourceWeight;
import stirling.software.common.model.tool.ToolFormat;
import stirling.software.common.model.tool.ToolIO;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.TempFileManager;
import stirling.software.common.util.WebResponseUtils;

@RestController
@RequestMapping("/api/v1/general")
@Tag(name = "General", description = "General APIs")
@RequiredArgsConstructor
@Slf4j
public class InterleavedPagesController {

    private static final float A4_WIDTH = 595f;
    private static final float A4_HEIGHT = 842f;

    private final CustomPDFDocumentFactory pdfDocumentFactory;
    private final TempFileManager tempFileManager;

    @AutoJobPostMapping(
            value = "/interleaved-pages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            resourceWeight = ResourceWeight.MEDIUM_WEIGHT)
    @ToolIO(produces = ToolFormat.PDF)
    @Operation(
            summary = "Create a PDF with alternating image and blank pages",
            description =
                    "Accepts multiple images and produces a PDF with images on odd pages"
                            + " and blank pages on even pages for duplex printing of coloring books"
                            + " and similar materials. Images are scaled to fit A4 while preserving"
                            + " aspect ratio.")
    public ResponseEntity<Resource> createInterleavedPDF(
            @ModelAttribute InterleavedPagesRequest request) throws IOException {

        MultipartFile[] files = request.getFileInput();

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Please select at least one image");
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("One or more files are empty");
            }
        }

        try (PDDocument document = pdfDocumentFactory.createNewDocument()) {
            for (MultipartFile imageFile : files) {
                try {
                    PDImageXObject pdImage =
                            PDImageXObject.createFromByteArray(document, imageFile.getBytes(), "");

                    ScaledDimensions scaled =
                            calculateImageScaling(pdImage.getWidth(), pdImage.getHeight());

                    addImagePageAndBlankPage(
                            document,
                            pdImage,
                            scaled.scaledWidth(),
                            scaled.scaledHeight(),
                            scaled.xOffset(),
                            scaled.yOffset());
                } catch (IOException e) {
                    log.error("Failed to process image: {}", imageFile.getOriginalFilename(), e);
                    throw new IOException(
                            String.format(
                                    "Failed to process image '%s'. Is it a valid image file?",
                                    imageFile.getOriginalFilename()),
                            e);
                }
            }

            return WebResponseUtils.pdfDocToWebResponse(
                    document, "interleaved.pdf", tempFileManager);
        }
    }

    private record ScaledDimensions(
            float scaledWidth, float scaledHeight, float xOffset, float yOffset) {}

    private ScaledDimensions calculateImageScaling(int imageWidth, int imageHeight) {
        float scaleFactor = Math.min(A4_WIDTH / imageWidth, A4_HEIGHT / imageHeight);

        float scaledWidth = imageWidth * scaleFactor;
        float scaledHeight = imageHeight * scaleFactor;

        float xOffset = (A4_WIDTH - scaledWidth) / 2;
        float yOffset = (A4_HEIGHT - scaledHeight) / 2;

        return new ScaledDimensions(scaledWidth, scaledHeight, xOffset, yOffset);
    }

    private void addImagePageAndBlankPage(
            PDDocument document,
            PDImageXObject image,
            float scaledWidth,
            float scaledHeight,
            float xOffset,
            float yOffset)
            throws IOException {

        PDPage imagePage = new PDPage(PDRectangle.A4);
        document.addPage(imagePage);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, imagePage)) {
            contentStream.drawImage(image, xOffset, yOffset, scaledWidth, scaledHeight);
        }

        document.addPage(new PDPage(PDRectangle.A4));
    }
}
