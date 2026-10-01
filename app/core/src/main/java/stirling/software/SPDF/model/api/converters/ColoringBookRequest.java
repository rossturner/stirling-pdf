package stirling.software.SPDF.model.api.converters;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class ColoringBookRequest {

    @Schema(
            description = "The input images to be converted to a coloring book PDF",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile[] fileInput;
}
