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
