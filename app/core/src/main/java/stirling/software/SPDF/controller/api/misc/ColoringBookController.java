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
                GeneralUtils.generateFilename(
                        images[0].getOriginalFilename(), "_coloring_book.pdf"));
    }
}
