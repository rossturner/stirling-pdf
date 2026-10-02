package stirling.software.SPDF.controller.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.SPDF.model.api.general.InterleavedPagesRequest;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.TempFile;
import stirling.software.common.util.TempFileManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterleavedPagesControllerTest {

    @Mock private CustomPDFDocumentFactory pdfDocumentFactory;
    @Mock private TempFileManager tempFileManager;
    @InjectMocks private InterleavedPagesController controller;

    @BeforeEach
    void setUp() throws Exception {
        when(pdfDocumentFactory.createNewDocument()).thenAnswer(inv -> new PDDocument());
        when(tempFileManager.createManagedTempFile(anyString()))
                .thenAnswer(
                        inv -> {
                            File f = Files.createTempFile("interleaved-test", ".pdf").toFile();
                            f.deleteOnExit();
                            TempFile tf = mock(TempFile.class);
                            when(tf.getFile()).thenReturn(f);
                            when(tf.getPath()).thenReturn(f.toPath());
                            when(tf.getAbsolutePath()).thenReturn(f.getAbsolutePath());
                            return tf;
                        });
    }

    private static byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static MultipartFile png(String name, int width, int height) throws IOException {
        return new MockMultipartFile("fileInput", name, "image/png", pngBytes(width, height));
    }

    private ResponseEntity<Resource> run(MultipartFile... files) throws IOException {
        InterleavedPagesRequest request = new InterleavedPagesRequest();
        request.setFileInput(files);
        return controller.createInterleavedPDF(request);
    }

    private static PDDocument parse(ResponseEntity<Resource> response) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = response.getBody().getInputStream()) {
            in.transferTo(out);
        }
        return Loader.loadPDF(out.toByteArray());
    }

    private static PDImageXObject firstImage(PDPage page) throws IOException {
        COSName name = page.getResources().getXObjectNames().iterator().next();
        return (PDImageXObject) page.getResources().getXObject(name);
    }

    private static boolean isBlank(PDPage page) {
        return page.getResources() == null
                || !page.getResources().getXObjectNames().iterator().hasNext();
    }

    @Test
    void singleImageProducesImagePlusBlankPage() throws Exception {
        try (PDDocument doc = parse(run(png("a.png", 1000, 800)))) {
            assertEquals(2, doc.getNumberOfPages());
            assertTrue(doc.getPage(0).getResources().getXObjectNames().iterator().hasNext());
            assertTrue(isBlank(doc.getPage(1)));
        }
    }

    @Test
    void multipleImagesProduceTwiceAsManyPages() throws Exception {
        try (PDDocument doc =
                parse(
                        run(
                                png("a.png", 1000, 800),
                                png("b.png", 600, 900),
                                png("c.png", 1000, 800)))) {
            assertEquals(6, doc.getNumberOfPages());
        }
    }

    @Test
    void pagesAreA4() throws Exception {
        try (PDDocument doc = parse(run(png("a.png", 1000, 800)))) {
            for (PDPage page : doc.getPages()) {
                assertEquals(595f, page.getMediaBox().getWidth(), 1f);
                assertEquals(842f, page.getMediaBox().getHeight(), 1f);
            }
        }
    }

    @Test
    void imageOrderIsPreserved() throws Exception {
        try (PDDocument doc = parse(run(png("a.png", 100, 50), png("b.png", 50, 100)))) {
            assertEquals(4, doc.getNumberOfPages());
            PDImageXObject first = firstImage(doc.getPage(0));
            PDImageXObject second = firstImage(doc.getPage(2));
            assertEquals(100, first.getWidth());
            assertEquals(50, first.getHeight());
            assertEquals(50, second.getWidth());
            assertEquals(100, second.getHeight());
        }
    }

    @Test
    void emptyFileListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> run());
    }

    @Test
    void nullFileListIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.createInterleavedPDF(new InterleavedPagesRequest()));
    }

    @Test
    void emptyFileIsRejected() {
        MultipartFile empty = new MockMultipartFile("fileInput", "e.png", "image/png", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> run(empty));
    }

    @Test
    void nonImageFileIsRejected() {
        MultipartFile text =
                new MockMultipartFile(
                        "fileInput",
                        "test.txt",
                        "text/plain",
                        "not an image".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> run(text));
    }
}
