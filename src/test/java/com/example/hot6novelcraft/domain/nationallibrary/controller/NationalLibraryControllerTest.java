package com.example.hot6novelcraft.domain.nationallibrary.controller;

import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NationalLibraryExceptionEnum;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.BookSaveRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.BookSearchRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.UserBookSaveRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.BookResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.NationalLibraryBookResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.UserBookResponse;
import com.example.hot6novelcraft.domain.nationallibrary.service.NationalLibraryService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import com.example.hot6novelcraft.common.dto.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NationalLibraryControllerTest {

    @InjectMocks private NationalLibraryController nationalLibraryController;
    @Mock        private NationalLibraryService    nationalLibraryService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Long USER_ID = 1L;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(nationalLibraryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation
                                .AuthenticationPrincipalArgumentResolver()
                )
                .build();

        var mockUser = mock(com.example.hot6novelcraft.domain.user.entity.User.class);
        given(mockUser.getId()).willReturn(USER_ID);
        given(mockUser.getRole()).willReturn(
                com.example.hot6novelcraft.domain.user.entity.enums.UserRole.READER
        );
        given(mockUser.getPassword()).willReturn("password");
        given(mockUser.getEmail()).willReturn("test@test.com");

        UserDetailsImpl realUserDetails = new UserDetailsImpl(mockUser, null);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        realUserDetails, null, realUserDetails.getAuthorities()
                )
        );
    }

    @Nested
    @DisplayName("GET /api/v1/national-library/books/search - 도서 검색")
    class SearchBooks {

        @Test
        @DisplayName("정상 검색 - 200 반환")
        void searchBooks_success() throws Exception {
            NationalLibraryBookResponse book = new NationalLibraryBookResponse(
                    "9791193235447", "스프링", "온다 리쿠",
                    "클레이하우스", "2025", "https://www.nl.go.kr/..."
            );
            PageResponse<NationalLibraryBookResponse> pageResponse = new PageResponse<>(
                    List.of(book), 0, 1, 1L, 10, true
            );
            given(nationalLibraryService.searchBooks(any(BookSearchRequest.class)))
                    .willReturn(pageResponse);

            mockMvc.perform(get("/api/v1/national-library/books/search")
                            .param("query", "스프링")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value("200"))
                    .andExpect(jsonPath("$.data.content[0].isbn").value("9791193235447"))
                    .andExpect(jsonPath("$.data.content[0].title").value("스프링"));
        }

        @Test
        @DisplayName("검색어 누락 - 400 반환")
        void searchBooks_missingQuery_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/national-library/books/search")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("외부 API 오류 - 502 반환")
        void searchBooks_externalApiError_returns502() throws Exception {
            given(nationalLibraryService.searchBooks(any(BookSearchRequest.class)))
                    .willThrow(new ServiceErrorException(NationalLibraryExceptionEnum.EXTERNAL_API_ERROR));

            mockMvc.perform(get("/api/v1/national-library/books/search")
                            .param("query", "스프링")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("국립중앙도서관 API 호출 중 오류가 발생했습니다"));
        }

        @Test
        @DisplayName("빈 검색 결과 - 200 반환")
        void searchBooks_emptyResult_returns200() throws Exception {
            PageResponse<NationalLibraryBookResponse> emptyPage = new PageResponse<>(
                    List.of(), 0, 0, 0L, 10, true
            );
            given(nationalLibraryService.searchBooks(any(BookSearchRequest.class)))
                    .willReturn(emptyPage);

            mockMvc.perform(get("/api/v1/national-library/books/search")
                            .param("query", "없는책제목xyz")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/national-library/books - 도서 저장")
    class SaveBook {

        private final BookSaveRequest request = new BookSaveRequest(
                "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025", ""
        );

        @Test
        @DisplayName("정상 저장 - 201 반환")
        void saveBook_success() throws Exception {
            BookResponse response = new BookResponse(
                    1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025"
            );
            given(nationalLibraryService.saveBook(any(BookSaveRequest.class)))
                    .willReturn(response);

            mockMvc.perform(post("/api/v1/national-library/books")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value("201"))
                    .andExpect(jsonPath("$.data.isbn").value("9791193235447"))
                    .andExpect(jsonPath("$.data.title").value("스프링"));
        }

        @Test
        @DisplayName("ISBN 누락 - 400 반환")
        void saveBook_missingIsbn_returns400() throws Exception {
            BookSaveRequest invalid = new BookSaveRequest(
                    "", "스프링", "온다 리쿠", "클레이하우스", "2025", ""
            );

            mockMvc.perform(post("/api/v1/national-library/books")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("제목 누락 - 400 반환")
        void saveBook_missingTitle_returns400() throws Exception {
            BookSaveRequest invalid = new BookSaveRequest(
                    "9791193235447", "", "온다 리쿠", "클레이하우스", "2025", ""
            );

            mockMvc.perform(post("/api/v1/national-library/books")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("중복 도서 저장 - 409 반환")
        void saveBook_duplicate_returns409() throws Exception {
            given(nationalLibraryService.saveBook(any(BookSaveRequest.class)))
                    .willThrow(new ServiceErrorException(NationalLibraryExceptionEnum.BOOK_ALREADY_EXISTS));

            mockMvc.perform(post("/api/v1/national-library/books")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("이미 저장된 도서입니다"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/national-library/books/{bookId} - 도서 단건 조회")
    class GetBook {

        @Test
        @DisplayName("정상 조회 - 200 반환")
        void getBook_success() throws Exception {
            BookResponse response = new BookResponse(
                    1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025"
            );
            given(nationalLibraryService.getBook(1L)).willReturn(response);

            mockMvc.perform(get("/api/v1/national-library/books/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("스프링"));
        }

        @Test
        @DisplayName("존재하지 않는 도서 - 404 반환")
        void getBook_notFound_returns404() throws Exception {
            given(nationalLibraryService.getBook(999L))
                    .willThrow(new ServiceErrorException(NationalLibraryExceptionEnum.BOOK_NOT_FOUND));

            mockMvc.perform(get("/api/v1/national-library/books/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("해당 도서를 찾을 수 없습니다"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/national-library/books/shelf - 내 서재 도서 저장")
    class SaveUserBook {

        private final UserBookSaveRequest request = new UserBookSaveRequest(
                "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025", ""
        );

        @Test
        @DisplayName("정상 저장 - 201 반환")
        void saveUserBook_success() throws Exception {
            UserBookResponse response = new UserBookResponse(
                    1L, 1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025"
            );
            given(nationalLibraryService.saveUserBook(eq(USER_ID), any(UserBookSaveRequest.class)))
                    .willReturn(response);

            mockMvc.perform(post("/api/v1/national-library/books/shelf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value("201"))
                    .andExpect(jsonPath("$.data.isbn").value("9791193235447"))
                    .andExpect(jsonPath("$.data.title").value("스프링"));
        }

        @Test
        @DisplayName("이미 내 서재에 있는 도서 - 409 반환")
        void saveUserBook_alreadyInShelf_returns409() throws Exception {
            given(nationalLibraryService.saveUserBook(eq(USER_ID), any(UserBookSaveRequest.class)))
                    .willThrow(new ServiceErrorException(NationalLibraryExceptionEnum.BOOK_ALREADY_IN_SHELF));

            mockMvc.perform(post("/api/v1/national-library/books/shelf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("이미 내 서재에 저장된 도서입니다"));
        }

        @Test
        @DisplayName("ISBN 누락 - 400 반환")
        void saveUserBook_missingIsbn_returns400() throws Exception {
            UserBookSaveRequest invalid = new UserBookSaveRequest(
                    "", "스프링", "온다 리쿠", "클레이하우스", "2025", ""
            );

            mockMvc.perform(post("/api/v1/national-library/books/shelf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("제목 누락 - 400 반환")
        void saveUserBook_missingTitle_returns400() throws Exception {
            UserBookSaveRequest invalid = new UserBookSaveRequest(
                    "9791193235447", "", "온다 리쿠", "클레이하우스", "2025", ""
            );

            mockMvc.perform(post("/api/v1/national-library/books/shelf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }
}