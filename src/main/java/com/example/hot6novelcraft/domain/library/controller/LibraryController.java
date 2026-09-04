package com.example.hot6novelcraft.domain.library.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.library.dto.request.LibraryAddRequest;
import com.example.hot6novelcraft.domain.library.dto.response.LibraryAddResponse;
import com.example.hot6novelcraft.domain.library.dto.response.LibraryListResponse;
import com.example.hot6novelcraft.domain.library.dto.response.MyLibraryResponse;
import com.example.hot6novelcraft.domain.library.entity.enums.LibraryType;
import com.example.hot6novelcraft.domain.library.service.LibraryService;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.MyShelfResponse;
import com.example.hot6novelcraft.domain.nationallibrary.service.NationalLibraryService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/libraries")
@RequiredArgsConstructor
@Validated
public class LibraryController {

    private final LibraryService libraryService;
    private final NationalLibraryService nationalLibraryService;  // ← 추가

    @PostMapping("/{id}")
    public ResponseEntity<BaseResponse<LibraryAddResponse>> addToLibrary(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody LibraryAddRequest request
    ) {
        Long userId = userDetails.getUser().getId();
        LibraryAddResponse response = libraryService.addToLibrary(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.success("201", "소설을 서재에 성공적으로 담았습니다", response));
    }

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<MyLibraryResponse>> getMyLibrary(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) LibraryType libraryType,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "페이지는 1 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "사이즈는 1 이상이어야 합니다")
            @Max(value = 100, message = "사이즈는 100 이하여야 합니다") int size,
            @RequestParam(defaultValue = "LATEST") String sort
    ) {
        Long userId = userDetails.getUser().getId();

        Page<LibraryListResponse> novels =
                libraryService.getMyLibrary(userId, libraryType, page - 1, size, sort);

        List<MyShelfResponse> nationalLibraryBooks =
                nationalLibraryService.getMyShelf(userId);

        return ResponseEntity.ok(
                BaseResponse.success("200", "내 서재 목록 조회가 완료되었습니다",
                        new MyLibraryResponse(PageResponse.register(novels), nationalLibraryBooks))
        );
    }
}