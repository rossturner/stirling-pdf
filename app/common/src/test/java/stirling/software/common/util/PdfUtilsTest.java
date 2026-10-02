package stirling.software.common.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.service.PdfMetadataService;

class PdfUtilsTest {

    private static CustomPDFDocumentFactory pdfDocumentFactory() {
        return new CustomPDFDocumentFactory(mock(PdfMetadataService.class));
    }

    private static MockMultipartFile image(
            String filename, String format, Color color, int width, int height) throws IOException {
        return new MockMultipartFile(
                "file",
                filename,
                MediaType.parseMediaType("image/" + format).toString(),
                imageBytes(format, color, width, height));
    }

    private static byte[] imageBytes(String format, Color color, int width, int height)
            throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, baos)) {
            throw new IOException("No image writer available for format: " + format);
        }
        return baos.toByteArray();
    }

    private static byte[] multiFrameTiff() throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);
            for (Color color : new Color[] {Color.RED, Color.BLUE}) {
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setColor(color);
                graphics.fillRect(0, 0, 16, 16);
                graphics.dispose();
                writer.writeToSequence(new IIOImage(image, null, null), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private static BufferedImage renderPage(byte[] pdf, int pageIndex) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFRenderer(document).renderImageWithDPI(pageIndex, 72, ImageType.RGB);
        }
    }

    private static Rectangle nonWhiteBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!isWhite(image.getRGB(x, y))) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static boolean isWhite(int rgb) {
        Color color = new Color(rgb, true);
        return color.getRed() > 245 && color.getGreen() > 245 && color.getBlue() > 245;
    }

    private static void assertA4Portrait(PDPage page) {
        assertEquals(PDRectangle.A4.getWidth(), page.getMediaBox().getWidth(), 0.01f);
        assertEquals(PDRectangle.A4.getHeight(), page.getMediaBox().getHeight(), 0.01f);
    }

    private static void assertCenterColor(BufferedImage image, Color expected) {
        Color actual = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2), true);
        assertEquals(expected.getRed(), actual.getRed(), 5);
        assertEquals(expected.getGreen(), actual.getGreen(), 5);
        assertEquals(expected.getBlue(), actual.getBlue(), 5);
    }

    private static void assertAllWhite(BufferedImage image) {
        assertNull(nonWhiteBounds(image));
    }

    @ParameterizedTest
    @CsvSource({"A0", "A1", "A2", "A3", "A4", "A5", "A6", "LETTER", "LEGAL"})
    void textToPageSize_validSizes_returnsCorrectRectangle(String size) {
        PDRectangle result = PdfUtils.textToPageSize(size);
        assertNotNull(result);
        assertTrue(result.getWidth() > 0);
        assertTrue(result.getHeight() > 0);
    }

    @Test
    void textToPageSize_lowercaseA4_returnsA4() {
        PDRectangle result = PdfUtils.textToPageSize("a4");
        assertEquals(PDRectangle.A4.getWidth(), result.getWidth(), 0.01f);
        assertEquals(PDRectangle.A4.getHeight(), result.getHeight(), 0.01f);
    }

    @Test
    void textToPageSize_invalidSize_throwsException() {
        assertThrows(Exception.class, () -> PdfUtils.textToPageSize("INVALID"));
    }

    @Test
    void getAllImages_emptyResources_returnsEmptyList() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            doc.addPage(page);
            List<RenderedImage> images = PdfUtils.getAllImages(page.getResources());
            assertTrue(images.isEmpty());
        }
    }

    @Test
    void getAllImages_withImage_returnsImage() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            BufferedImage bufferedImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bufferedImage);

            PDResources resources = new PDResources();
            resources.add(pdImage);
            page.setResources(resources);

            List<RenderedImage> images = PdfUtils.getAllImages(page.getResources());
            assertEquals(1, images.size());
        }
    }

    @Test
    void hasImagesOnPage_noImages_returnsFalse() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            doc.addPage(page);
            assertFalse(PdfUtils.hasImagesOnPage(page));
        }
    }

    @Test
    void hasTextOnPage_noText_returnsFalse() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            assertFalse(PdfUtils.hasTextOnPage(page, "hello"));
        }
    }

    @Test
    void pageCount_greaterComparator_correct() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            assertTrue(PdfUtils.pageCount(doc, 2, "greater"));
        }
    }

    @Test
    void pageCount_equalComparator_correct() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            assertTrue(PdfUtils.pageCount(doc, 2, "equal"));
        }
    }

    @Test
    void pageCount_lessComparator_correct() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            assertTrue(PdfUtils.pageCount(doc, 5, "less"));
        }
    }

    @Test
    void pageCount_invalidComparator_throwsException() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            assertThrows(Exception.class, () -> PdfUtils.pageCount(doc, 1, "invalid"));
        }
    }

    @Test
    void pageSize_matchingSize_returnsTrue() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            String sizeStr = PDRectangle.A4.getWidth() + "x" + PDRectangle.A4.getHeight();
            assertTrue(PdfUtils.pageSize(doc, sizeStr));
        }
    }

    @Test
    void pageSize_nonMatchingSize_returnsFalse() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            assertFalse(PdfUtils.pageSize(doc, "100x100"));
        }
    }

    // --- hasImages ---

    @Test
    void hasImages_noImages_returnsFalse() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            doc.addPage(page);
            assertFalse(PdfUtils.hasImages(doc, "all"));
        }
    }

    @Test
    void hasImages_withImage_returnsTrue() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            BufferedImage bufferedImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bufferedImage);
            PDResources resources = new PDResources();
            resources.add(pdImage);
            page.setResources(resources);

            assertTrue(PdfUtils.hasImages(doc, "all"));
        }
    }

    // --- hasText ---

    @Test
    void hasText_noText_returnsFalse() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            assertFalse(PdfUtils.hasText(doc, "all", "hello"));
        }
    }

    // --- textToPageSize additional ---

    @Test
    void textToPageSize_letter_returnsLetter() {
        PDRectangle result = PdfUtils.textToPageSize("letter");
        assertEquals(PDRectangle.LETTER.getWidth(), result.getWidth(), 0.01f);
        assertEquals(PDRectangle.LETTER.getHeight(), result.getHeight(), 0.01f);
    }

    @Test
    void textToPageSize_legal_returnsLegal() {
        PDRectangle result = PdfUtils.textToPageSize("legal");
        assertEquals(PDRectangle.LEGAL.getWidth(), result.getWidth(), 0.01f);
        assertEquals(PDRectangle.LEGAL.getHeight(), result.getHeight(), 0.01f);
    }

    // --- pageCount additional ---

    @Test
    void pageCount_greaterComparator_false() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            assertFalse(PdfUtils.pageCount(doc, 5, "greater"));
        }
    }

    @Test
    void pageCount_equalComparator_false() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            assertFalse(PdfUtils.pageCount(doc, 3, "equal"));
        }
    }

    @Test
    void pageCount_lessComparator_false() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            assertFalse(PdfUtils.pageCount(doc, 2, "less"));
        }
    }

    // --- hasImagesOnPage with image ---

    @Test
    void hasImagesOnPage_withImage_returnsTrue() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            BufferedImage bufferedImage = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bufferedImage);
            PDResources resources = new PDResources();
            resources.add(pdImage);
            page.setResources(resources);

            assertTrue(PdfUtils.hasImagesOnPage(page));
        }
    }

    @Test
    void imageToPdfWithBlankPages_interleavesPngAndJpegImagesWithBlankPages() throws IOException {
        MockMultipartFile first = image("first.png", "png", Color.RED, 40, 40);
        MockMultipartFile second = image("second.jpg", "jpeg", Color.BLUE, 40, 40);

        byte[] pdfOut =
                PdfUtils.imageToPdfWithBlankPages(
                        new MultipartFile[] {first, second}, pdfDocumentFactory());

        try (PDDocument doc = Loader.loadPDF(pdfOut)) {
            assertEquals(4, doc.getNumberOfPages());
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                assertA4Portrait(doc.getPage(i));
            }

            BufferedImage page0 = renderPage(pdfOut, 0);
            BufferedImage page1 = renderPage(pdfOut, 1);
            BufferedImage page2 = renderPage(pdfOut, 2);
            BufferedImage page3 = renderPage(pdfOut, 3);

            assertCenterColor(page0, Color.RED);
            assertAllWhite(page1);
            assertCenterColor(page2, Color.BLUE);
            assertAllWhite(page3);
        }
    }

    @ParameterizedTest
    @CsvSource({"wide.png,640,160,RED", "tall.png,160,640,BLUE"})
    void imageToPdfWithBlankPages_preservesAspectRatio(
            String filename, int width, int height, String colorName) throws IOException {
        Color color = "RED".equals(colorName) ? Color.RED : Color.BLUE;
        MockMultipartFile image = image(filename, "png", color, width, height);

        byte[] pdfOut =
                PdfUtils.imageToPdfWithBlankPages(
                        new MultipartFile[] {image}, pdfDocumentFactory());

        try (PDDocument doc = Loader.loadPDF(pdfOut)) {
            assertEquals(2, doc.getNumberOfPages());
            assertA4Portrait(doc.getPage(0));
            assertA4Portrait(doc.getPage(1));
        }

        BufferedImage rendered = renderPage(pdfOut, 0);
        Rectangle bounds = nonWhiteBounds(rendered);
        assertNotNull(bounds);

        double sourceRatio = (double) width / height;
        double renderedRatio = (double) bounds.width / bounds.height;
        assertEquals(sourceRatio, renderedRatio, 0.1);
        assertAllWhite(renderPage(pdfOut, 1));
    }

    @Test
    void imageToPdfWithBlankPages_preservesTiffFrameOrder() throws IOException {
        MockMultipartFile tiff =
                new MockMultipartFile("file", "scan.tiff", "image/tiff", multiFrameTiff());

        byte[] pdfOut =
                PdfUtils.imageToPdfWithBlankPages(new MultipartFile[] {tiff}, pdfDocumentFactory());

        try (PDDocument doc = Loader.loadPDF(pdfOut)) {
            assertEquals(4, doc.getNumberOfPages());
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                assertA4Portrait(doc.getPage(i));
            }
        }

        assertCenterColor(renderPage(pdfOut, 0), Color.RED);
        assertAllWhite(renderPage(pdfOut, 1));
        assertCenterColor(renderPage(pdfOut, 2), Color.BLUE);
        assertAllWhite(renderPage(pdfOut, 3));
    }

    @Test
    void imageToPdfWithBlankPages_unreadableImageThrows() {
        MockMultipartFile unreadable =
                new MockMultipartFile(
                        "file", "broken.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3});

        assertThrows(
                IOException.class,
                () ->
                        PdfUtils.imageToPdfWithBlankPages(
                                new MultipartFile[] {unreadable}, pdfDocumentFactory()));
    }
}
