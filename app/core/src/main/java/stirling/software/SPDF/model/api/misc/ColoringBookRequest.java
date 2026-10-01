package stirling.software.SPDF.model.api.misc;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

@Data
public class ColoringBookRequest {

    @Schema(
            description = "The images to lay out, one per page, in the order given.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile[] fileInput;
}
