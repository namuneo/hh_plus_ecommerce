package sample.hhplus_w2.system.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "헬스체크 API")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "헬스체크", description = "서버 상태 확인")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
