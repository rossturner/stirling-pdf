package stirling.software.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.common.service.CustomPDFDocumentFactory;

class PdfUtilsColoringBookTest {

    @Test
    void singleImageProducesImagePageAndBlankBack() throws IOException {
        byte[] pdfBytes = convert(imageFile("red.png", Color.RED, 24, 24));

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertEquals(2, document.getNumberOfPages());
            assertEveryPageIsPortraitA4(document);
            assertPageColour(document, 0, Color.RED);
            assertBlankPage(document, 1);
        }
    }

    @Test
    void multipleImagesPreserveOrderAndHaveBlankBacks() throws IOException {
        byte[] pdfBytes =
                convert(
                        imageFile("red.png", Color.RED, 24, 24),
                        imageFile("green.png", Color.GREEN, 24, 24),
                        imageFile("blue.png", Color.BLUE, 24, 24));

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertEquals(6, document.getNumberOfPages());
            assertEveryPageIsPortraitA4(document);
            assertPageColour(document, 0, Color.RED);
            assertBlankPage(document, 1);
            assertPageColour(document, 2, Color.GREEN);
            assertBlankPage(document, 3);
            assertPageColour(document, 4, Color.BLUE);
            assertBlankPage(document, 5);
        }
    }

    @Test
    void multiFrameTiffProducesImagePageAndBlankBackPerFrame() throws IOException {
        MockMultipartFile tiff =
                new MockMultipartFile("fileInput", "pages.tiff", "image/tiff", multiFrameTiff());
        byte[] pdfBytes = convert(tiff);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertEquals(4, document.getNumberOfPages());
            assertEveryPageIsPortraitA4(document);
            assertPageColour(document, 0, Color.RED);
            assertBlankPage(document, 1);
            assertPageColour(document, 2, Color.BLUE);
            assertBlankPage(document, 3);
        }
    }

    @Test
    void maintainAspectRatioKeepsImageProportionsOnPortraitA4() throws IOException {
        byte[] pdfBytes = convert(imageFile("wide.png", Color.MAGENTA, 80, 40));

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertEveryPageIsPortraitA4(document);
            BufferedImage rendered = new PDFRenderer(document).renderImage(0, 0.25f);
            int minX = rendered.getWidth();
            int maxX = -1;
            int minY = rendered.getHeight();
            int maxY = -1;
            for (int y = 0; y < rendered.getHeight(); y++) {
                for (int x = 0; x < rendered.getWidth(); x++) {
                    if ((rendered.getRGB(x, y) & 0x00ffffff)
                            == (Color.MAGENTA.getRGB() & 0x00ffffff)) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }

            double renderedAspectRatio = (double) (maxX - minX + 1) / (maxY - minY + 1);
            assertTrue(renderedAspectRatio > 1.9 && renderedAspectRatio < 2.1);
            assertBlankPage(document, 1);
        }
    }

    @Test
    void nullOrEmptyFilesAreRejected() throws IOException {
        CustomPDFDocumentFactory factory = newFactory();

        assertThrows(
                IllegalArgumentException.class,
                () -> PdfUtils.imageToPdfWithBlankPages(null, factory));
        assertThrows(
                IllegalArgumentException.class,
                () -> PdfUtils.imageToPdfWithBlankPages(new MultipartFile[0], factory));
    }

    @Test
    void malformedImageFailsWithFileReadException() {
        MockMultipartFile malformedImage =
                new MockMultipartFile("fileInput", "page.png", "image/png", new byte[] {1});

        assertThrows(ExceptionUtils.FileReadException.class, () -> convert(malformedImage));
    }

    private static byte[] convert(MockMultipartFile... files) throws IOException {
        return PdfUtils.imageToPdfWithBlankPages(files, newFactory());
    }

    private static CustomPDFDocumentFactory newFactory() throws IOException {
        CustomPDFDocumentFactory factory = mock(CustomPDFDocumentFactory.class);
        when(factory.createNewDocument()).thenReturn(new PDDocument());
        return factory;
    }

    private static MockMultipartFile imageFile(String filename, Color color, int width, int height)
            throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile(
                "fileInput", filename, MediaType.IMAGE_PNG_VALUE, output.toByteArray());
    }

    private static byte[] multiFrameTiff() throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);
            for (Color color : new Color[] {Color.RED, Color.BLUE}) {
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setColor(color);
                graphics.fillRect(0, 0, 16, 16);
                graphics.dispose();
                writer.writeToSequence(new IIOImage(image, null, null), parameters);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static void assertEveryPageIsPortraitA4(PDDocument document) {
        for (PDPage page : document.getPages()) {
            PDRectangle size = page.getMediaBox();
            assertEquals(PDRectangle.A4.getWidth(), size.getWidth(), 0.01f);
            assertEquals(PDRectangle.A4.getHeight(), size.getHeight(), 0.01f);
            assertTrue(size.getWidth() < size.getHeight());
        }
    }

    private static void assertPageColour(PDDocument document, int pageIndex, Color expected)
            throws IOException {
        BufferedImage rendered = new PDFRenderer(document).renderImage(pageIndex, 0.25f);
        int centerPixel = rendered.getRGB(rendered.getWidth() / 2, rendered.getHeight() / 2);
        assertEquals(expected.getRGB() & 0x00ffffff, centerPixel & 0x00ffffff);
        assertTrue(PdfUtils.hasImagesOnPage(document.getPage(pageIndex)));
    }

    private static void assertBlankPage(PDDocument document, int pageIndex) throws IOException {
        PDPage page = document.getPage(pageIndex);
        assertTrue(
                page.getResources() == null
                        || PdfUtils.getAllImages(page.getResources()).isEmpty());
        BufferedImage rendered = new PDFRenderer(document).renderImage(pageIndex, 0.25f);
        for (int y = 0; y < rendered.getHeight(); y++) {
            for (int x = 0; x < rendered.getWidth(); x++) {
                assertEquals(Color.WHITE.getRGB() & 0x00ffffff, rendered.getRGB(x, y) & 0x00ffffff);
            }
        }
    }
}
