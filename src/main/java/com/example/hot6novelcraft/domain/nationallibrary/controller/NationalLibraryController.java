package com.example.hot6novelcraft.domain.nationallibrary.controller;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.BookSaveRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.BookSearchRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.UserBookSaveRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.BookResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.MyShelfResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.NationalLibraryBookResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.UserBookResponse;
import com.example.hot6novelcraft.domain.nationallibrary.service.NationalLibraryService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/national-library")
public class NationalLibraryController {

    private final NationalLibraryService nationalLibraryService;

    @GetMapping("/books/search")
    public ResponseEntity<BaseResponse<PageResponse<NationalLibraryBookResponse>>> searchBooks(
            @Valid BookSearchRequest request) {

        PageResponse<NationalLibraryBookResponse> data =
                nationalLibraryService.searchBooks(request);

        return ResponseEntity.ok(
                BaseResponse.success("200", "도서 검색에 성공했습니다", data)
        );
    }

    @PostMapping("/books")
    public ResponseEntity<BaseResponse<BookResponse>> saveBook(
            @Valid @RequestBody BookSaveRequest request) {

        BookResponse data = nationalLibraryService.saveBook(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                BaseResponse.success("201", "도서가 저장되었습니다", data)
        );
    }

    @GetMapping("/books/{bookId}")
    public ResponseEntity<BaseResponse<BookResponse>> getBook(
            @PathVariable Long bookId) {

        BookResponse data = nationalLibraryService.getBook(bookId);

        return ResponseEntity.ok(
                BaseResponse.success("200", "도서 조회에 성공했습니다", data)
        );
    }

    @GetMapping("/books/shelf")
    public ResponseEntity<BaseResponse<List<MyShelfResponse>>> getMyShelf(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUser().getId();
        List<MyShelfResponse> data = nationalLibraryService.getMyShelf(userId);

        return ResponseEntity.ok(
                BaseResponse.success("200", "내 서재 조회에 성공했습니다", data)
        );
    }

    @PostMapping("/books/shelf")
    public ResponseEntity<BaseResponse<UserBookResponse>> saveUserBook(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UserBookSaveRequest request) {

        Long userId = userDetails.getUser().getId();
        UserBookResponse data = nationalLibraryService.saveUserBook(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                BaseResponse.success("201", "내 서재에 도서가 저장되었습니다", data)
        );
    }

    @DeleteMapping("/books/shelf/{userBookId}")
    public ResponseEntity<BaseResponse<Void>> removeFromShelf(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long userBookId) {

        Long userId = userDetails.getUser().getId();
        nationalLibraryService.removeFromShelf(userId, userBookId);

        return ResponseEntity.ok(
                BaseResponse.success("200", "도서가 서재에서 삭제되었습니다", null)
        );
    }
}