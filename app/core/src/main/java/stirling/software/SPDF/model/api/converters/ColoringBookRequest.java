package stirling.software.SPDF.model.api.converters;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

@Data
public class ColoringBookRequest {

    @Schema(
            description = "The input images for the coloring book",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile[] fileInput;
}
