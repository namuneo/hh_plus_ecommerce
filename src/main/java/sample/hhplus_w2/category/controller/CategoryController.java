package sample.hhplus_w2.category.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Category", description = "카테고리 API")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Operation(summary = "카테고리 목록 조회", description = "전체 카테고리 목록을 조회합니다")
    @ApiResponse(responseCode = "200", description = "카테고리 목록 조회 성공")
    @GetMapping
    public ResponseEntity<String> getCategories() {
        return ResponseEntity.ok("카테고리 목록 조회");
    }
}