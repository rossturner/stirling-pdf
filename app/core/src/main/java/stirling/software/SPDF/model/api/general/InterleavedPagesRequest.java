package stirling.software.SPDF.model.api.general;

import org.springframework.web.multipart.MultipartFile;

import lombok.Data;

@Data
public class InterleavedPagesRequest {
    private MultipartFile[] fileInput;
}
