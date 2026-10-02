package stirling.software.SPDF.controller.api.converters;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.SPDF.config.EndpointConfiguration;
import stirling.software.SPDF.model.api.converters.ColoringBookRequest;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.PdfUtils;
import stirling.software.common.util.TempFileManager;

@ExtendWith(MockitoExtension.class)
class ConvertImgPDFControllerColoringBookTest {

    @Mock private CustomPDFDocumentFactory pdfDocumentFactory;
    @Mock private TempFileManager tempFileManager;
    @Mock private EndpointConfiguration endpointConfiguration;

    @InjectMocks private ConvertImgPDFController controller;

    @Test
    void coloringBookControllerReturnsPdfWithGeneratedFilename() throws Exception {
        byte[] pdfBytes = "%PDF-coloring-book".getBytes();
        MultipartFile[] files = {
            new MockMultipartFile("fileInput", "cover.png", "image/png", new byte[] {1}),
            new MockMultipartFile("fileInput", "page.png", "image/png", new byte[] {2})
        };
        ColoringBookRequest request = new ColoringBookRequest();
        request.setFileInput(files);

        try (MockedStatic<PdfUtils> pdfUtils = Mockito.mockStatic(PdfUtils.class)) {
            pdfUtils.when(
                            () ->
                                    PdfUtils.imageToPdfWithBlankPages(
                                            eq(files), eq(pdfDocumentFactory)))
                    .thenReturn(pdfBytes);

            var response = controller.convertToColoringBookPdf(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertArrayEquals(pdfBytes, response.getBody());
            assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
            assertEquals(
                    "cover_coloring_book.pdf",
                    response.getHeaders().getContentDisposition().getFilename());
            pdfUtils.verify(
                    () -> PdfUtils.imageToPdfWithBlankPages(eq(files), eq(pdfDocumentFactory)));
        }
    }

    @Test
    void coloringBookControllerPropagatesImageDecodingFailure() {
        IOException failure = new IOException("Unable to decode image");
        MultipartFile[] files = {
            new MockMultipartFile("fileInput", "page.png", "image/png", new byte[] {1})
        };
        ColoringBookRequest request = new ColoringBookRequest();
        request.setFileInput(files);

        try (MockedStatic<PdfUtils> pdfUtils = Mockito.mockStatic(PdfUtils.class)) {
            pdfUtils.when(
                            () ->
                                    PdfUtils.imageToPdfWithBlankPages(
                                            eq(files), eq(pdfDocumentFactory)))
                    .thenThrow(failure);

            IOException propagated =
                    assertThrows(
                            IOException.class, () -> controller.convertToColoringBookPdf(request));

            assertSame(failure, propagated);
        }
    }
}
