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
        try (PDDocument doc =
                run(png("a.png", 100, 200), png("b.png", 100, 200), png("c.png", 100, 200))) {
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
                assertThat(isLandscape(doc.getPage(i)))
                        .as("page %d", i + 1)
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
                new MockMultipartFile(
                        "fileInput", "bad.png", "image/png", "not an image".getBytes());
        MultipartFile[] files = {png("a.png", 100, 200), corrupt, png("c.png", 100, 200)};
        assertThrows(IOException.class, () -> PdfUtils.imagesToColoringBook(files, factory));
    }
}
