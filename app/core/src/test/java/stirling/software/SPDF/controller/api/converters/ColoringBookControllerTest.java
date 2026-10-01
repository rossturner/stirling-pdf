package stirling.software.SPDF.controller.api.converters;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.SPDF.config.EndpointConfiguration;
import stirling.software.SPDF.config.swagger.StandardPdfResponse;
import stirling.software.SPDF.model.api.converters.ColoringBookRequest;
import stirling.software.common.annotations.AutoJobPostMapping;
import stirling.software.common.enumeration.ResourceWeight;
import stirling.software.common.model.tool.ToolArity;
import stirling.software.common.model.tool.ToolFormat;
import stirling.software.common.model.tool.ToolIO;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.GeneralUtils;
import stirling.software.common.util.PdfUtils;
import stirling.software.common.util.TempFileManager;
import stirling.software.common.util.WebResponseUtils;

@ExtendWith(MockitoExtension.class)
class ColoringBookControllerTest {

    @Mock private CustomPDFDocumentFactory pdfDocumentFactory;
    @Mock private TempFileManager tempFileManager;
    @Mock private EndpointConfiguration endpointConfiguration;

    @InjectMocks private ConvertImgPDFController controller;

    @Test
    void coloringBookEndpointDeclaresDedicatedRouteAndToolIo() throws Exception {
        Method method =
                ConvertImgPDFController.class.getDeclaredMethod(
                        "convertToColoringBook", ColoringBookRequest.class);

        AutoJobPostMapping mapping = method.getAnnotation(AutoJobPostMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[] {"/coloring-book"}, mapping.value());
        assertArrayEquals(new String[] {MediaType.MULTIPART_FORM_DATA_VALUE}, mapping.consumes());
        assertEquals(ResourceWeight.LARGE_WEIGHT, mapping.resourceWeight());

        assertNotNull(method.getAnnotation(StandardPdfResponse.class));

        ToolIO toolIo = method.getAnnotation(ToolIO.class);
        assertNotNull(toolIo);
        assertArrayEquals(new ToolFormat[] {ToolFormat.IMAGE}, toolIo.accepts());
        assertEquals(true, toolIo.imageIOInput());
        assertEquals(ToolFormat.PDF, toolIo.produces());
        assertEquals(ToolArity.MISO, toolIo.arity());
    }

    @Test
    void convertToColoringBookReturnsPdfFromUtilityWithInputOrderPreserved() throws Exception {
        MockMultipartFile first =
                new MockMultipartFile("fileInput", "cover.png", "image/png", "cover".getBytes());
        MockMultipartFile second =
                new MockMultipartFile("fileInput", "inside.jpg", "image/jpeg", "inside".getBytes());
        MultipartFile[] files = {first, second};

        ColoringBookRequest request = new ColoringBookRequest();
        request.setFileInput(files);

        byte[] pdfBytes = "coloring-book".getBytes();
        ResponseEntity<byte[]> expectedResponse = ResponseEntity.ok(pdfBytes);

        try (MockedStatic<PdfUtils> pdfUtils = Mockito.mockStatic(PdfUtils.class);
                MockedStatic<GeneralUtils> generalUtils = Mockito.mockStatic(GeneralUtils.class);
                MockedStatic<WebResponseUtils> webResponseUtils =
                        Mockito.mockStatic(WebResponseUtils.class)) {

            pdfUtils.when(
                            () ->
                                    PdfUtils.imageToPdfWithBlankPages(
                                            eq(files), eq(pdfDocumentFactory)))
                    .thenReturn(pdfBytes);
            generalUtils
                    .when(() -> GeneralUtils.generateFilename("cover.png", "_converted.pdf"))
                    .thenReturn("cover_converted.pdf");
            webResponseUtils
                    .when(
                            () ->
                                    WebResponseUtils.bytesToWebResponse(
                                            pdfBytes, "cover_converted.pdf"))
                    .thenReturn(expectedResponse);

            ResponseEntity<byte[]> response = controller.convertToColoringBook(request);

            assertSame(expectedResponse, response);
            pdfUtils.verify(
                    () ->
                            PdfUtils.imageToPdfWithBlankPages(
                                    Mockito.argThat(
                                            actual ->
                                                    Arrays.equals(
                                                            actual,
                                                            new MultipartFile[] {first, second})),
                                    eq(pdfDocumentFactory)));
        }
    }

    @Test
    void convertToColoringBookRejectsNullFileInputBeforeConversion() {
        ColoringBookRequest request = new ColoringBookRequest();
        request.setFileInput(null);

        try (MockedStatic<PdfUtils> pdfUtils = Mockito.mockStatic(PdfUtils.class)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> controller.convertToColoringBook(request));
            pdfUtils.verifyNoInteractions();
        }
    }

    @Test
    void convertToColoringBookRejectsEmptyFileInputBeforeConversion() {
        ColoringBookRequest request = new ColoringBookRequest();
        request.setFileInput(new MultipartFile[0]);

        try (MockedStatic<PdfUtils> pdfUtils = Mockito.mockStatic(PdfUtils.class)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> controller.convertToColoringBook(request));
            pdfUtils.verifyNoInteractions();
        }
    }

    @Test
    void convertToColoringBookRejectsEmptyMultipartPartBeforeConversion() {
        ColoringBookRequest request = new ColoringBookRequest();
        request.setFileInput(
                new MultipartFile[] {
                    new MockMultipartFile("fileInput", "empty.png", "image/png", new byte[0])
                });

        try (MockedStatic<PdfUtils> pdfUtils = Mockito.mockStatic(PdfUtils.class)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> controller.convertToColoringBook(request));
            pdfUtils.verifyNoInteractions();
        }
    }
}
