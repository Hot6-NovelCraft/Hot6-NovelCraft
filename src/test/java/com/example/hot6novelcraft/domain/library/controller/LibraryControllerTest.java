package com.example.hot6novelcraft.domain.library.controller;

import com.example.hot6novelcraft.common.exception.GlobalExceptionHandler;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.LibraryExceptionEnum;
import com.example.hot6novelcraft.domain.library.dto.request.LibraryAddRequest;
import com.example.hot6novelcraft.domain.library.dto.response.LibraryAddResponse;
import com.example.hot6novelcraft.domain.library.dto.response.LibraryListResponse;
import com.example.hot6novelcraft.domain.library.entity.enums.LibraryType;
import com.example.hot6novelcraft.domain.library.service.LibraryService;
import com.example.hot6novelcraft.domain.nationallibrary.service.NationalLibraryService;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 불필요 stubbing 경고 제거
class LibraryControllerTest {

    @InjectMocks private LibraryController libraryController;
    @Mock        private LibraryService    libraryService;
    @Mock        private NationalLibraryService nationalLibraryService;

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
        mockMvc = MockMvcBuilders.standaloneSetup(libraryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new org.springframework.security.web.method.annotation
                                .AuthenticationPrincipalArgumentResolver()
                )
                .build();

        // User Mock 생성 후 UserDetailsImpl에 실제 주입
        var mockUser = mock(com.example.hot6novelcraft.domain.user.entity.User.class);
        given(mockUser.getId()).willReturn(USER_ID);
        given(mockUser.getRole()).willReturn(
                com.example.hot6novelcraft.domain.user.entity.enums.UserRole.READER
        );
        given(mockUser.getPassword()).willReturn("password");
        given(mockUser.getEmail()).willReturn("test@test.com");

        // mock(UserDetailsImpl.class) 대신 실제 객체 생성
        UserDetailsImpl realUserDetails = new UserDetailsImpl(mockUser, null);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        realUserDetails, null, realUserDetails.getAuthorities()
                )
        );
    }

    @Nested
    @DisplayName("POST /api/libraries/{id} - 내서재 담기")
    class AddToLibrary {

        private final LibraryAddRequest request = new LibraryAddRequest(
                45L, "테스트소설", "작가명", "https://cover.png", LibraryType.BOOKMARKED
        );

        @Test
        @DisplayName("정상 담기 - 201 반환")
        void addToLibrary_success() throws Exception {
            LibraryAddResponse response =
                    new LibraryAddResponse(1L, "테스트소설", LocalDateTime.now());
            given(libraryService.addToLibrary(eq(USER_ID), any())).willReturn(response);

            mockMvc.perform(post("/api/libraries/45")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value("201"))
                    .andExpect(jsonPath("$.data.novelTitle").value("테스트소설"));
        }

        @Test
        @DisplayName("중복 담기 - 409 반환")
        void addToLibrary_duplicate_returns409() throws Exception {
            given(libraryService.addToLibrary(eq(USER_ID), any()))
                    .willThrow(new ServiceErrorException(LibraryExceptionEnum.ALREADY_IN_LIBRARY));

            mockMvc.perform(post("/api/libraries/45")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("이미 서재에 담긴 소설입니다"));
        }

        @Test
        @DisplayName("novelId 누락 - 400 반환")
        void addToLibrary_missingNovelId_returns400() throws Exception {
            LibraryAddRequest invalid = new LibraryAddRequest(
                    null, "테스트소설", "작가명", "https://cover.png", LibraryType.BOOKMARKED
            );

            mockMvc.perform(post("/api/libraries/45")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("libraryType 누락 - 400 반환")
        void addToLibrary_missingLibraryType_returns400() throws Exception {
            LibraryAddRequest invalid = new LibraryAddRequest(
                    45L, "테스트소설", "작가명", "https://cover.png", null
            );

            mockMvc.perform(post("/api/libraries/45")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/libraries/me - 내서재 조회")
    class GetMyLibrary {

        @BeforeEach
        void setUpShelf() {
            given(nationalLibraryService.getMyShelf(eq(USER_ID))).willReturn(List.of());
        }

        private final LibraryListResponse item = new LibraryListResponse(
                45L, "테스트소설", "작가명", "https://cover.png",
                "12화: 예외 처리는 어려워", 120L, LocalDateTime.now()
        );

        @Test
        @DisplayName("전체 조회 - 200 반환")
        void getMyLibrary_all_success() throws Exception {
            Page<LibraryListResponse> page =
                    new PageImpl<>(List.of(item), PageRequest.of(0, 12), 1);
            given(libraryService.getMyLibrary(eq(USER_ID), isNull(), eq(0), eq(12), eq("LATEST")))
                    .willReturn(page);

            mockMvc.perform(get("/api/libraries/me")
                            .param("page", "1")
                            .param("size", "12")
                            .param("sort", "LATEST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.novels.content[0].novelId").value(45))     // ← 수정
                    .andExpect(jsonPath("$.data.novels.totalElements").value(1));           // ← 수정
        }

        @Test
        @DisplayName("타입 필터 READING 조회 - 200 반환")
        void getMyLibrary_filterReading_success() throws Exception {
            Page<LibraryListResponse> page =
                    new PageImpl<>(List.of(item), PageRequest.of(0, 12), 1);
            given(libraryService.getMyLibrary(eq(USER_ID), eq(LibraryType.READING), eq(0), eq(12), eq("LATEST")))
                    .willReturn(page);

            mockMvc.perform(get("/api/libraries/me")
                            .param("libraryType", "READING")
                            .param("page", "1")
                            .param("size", "12")
                            .param("sort", "LATEST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.novels.content[0].novelId").value(45));    // ← 수정
        }

        @Test
        @DisplayName("제목순 정렬 조회 - 200 반환")
        void getMyLibrary_sortByTitle_success() throws Exception {
            Page<LibraryListResponse> page =
                    new PageImpl<>(List.of(item), PageRequest.of(0, 12), 1);
            given(libraryService.getMyLibrary(eq(USER_ID), isNull(), eq(0), eq(12), eq("TITLE")))
                    .willReturn(page);

            mockMvc.perform(get("/api/libraries/me")
                            .param("page", "1")
                            .param("size", "12")
                            .param("sort", "TITLE"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("빈 서재 조회 - 200 반환")
        void getMyLibrary_empty_success() throws Exception {
            Page<LibraryListResponse> emptyPage =
                    new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
            given(libraryService.getMyLibrary(eq(USER_ID), isNull(), eq(0), eq(12), eq("LATEST")))
                    .willReturn(emptyPage);

            mockMvc.perform(get("/api/libraries/me")
                            .param("page", "1")
                            .param("size", "12")
                            .param("sort", "LATEST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.novels.totalElements").value(0));           // ← 수정
        }
    }
}