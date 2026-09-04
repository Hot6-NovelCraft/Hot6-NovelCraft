package com.example.hot6novelcraft.domain.library.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.LibraryExceptionEnum;
import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
import com.example.hot6novelcraft.domain.library.dto.request.LibraryAddRequest;
import com.example.hot6novelcraft.domain.library.dto.response.LibraryAddResponse;
import com.example.hot6novelcraft.domain.library.dto.response.LibraryListResponse;
import com.example.hot6novelcraft.domain.library.entity.Library;
import com.example.hot6novelcraft.domain.library.entity.enums.LibraryType;
import com.example.hot6novelcraft.domain.library.repository.LibraryQueryRepository;
import com.example.hot6novelcraft.domain.library.repository.LibraryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

    @InjectMocks private LibraryService      libraryService;
    @Mock private LibraryRepository          libraryRepository;
    @Mock private LibraryQueryRepository     libraryQueryRepository;
    @Mock private EpisodeRepository          episodeRepository;

    private Library makeLibrary(Long id, Long userId, Long novelId, LibraryType type) {
        Library library = Library.create(userId, novelId, type,
                "테스트소설", "작가명", "https://cover.png");
        try {
            Field idField = Library.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(library, id);

            Field createdAtField = library.getClass().getSuperclass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(library, LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return library;
    }

    @Nested
    @DisplayName("내서재 담기")
    class AddToLibrary {

        private final LibraryAddRequest request = new LibraryAddRequest(
                45L, "테스트소설", "작가명", "https://cover.png", LibraryType.BOOKMARKED
        );

        @Test
        @DisplayName("정상적으로 서재에 담긴다")
        void addToLibrary_success() {
            Library saved = makeLibrary(1L, 1L, 45L, LibraryType.BOOKMARKED);
            given(libraryRepository.existsByUserIdAndNovelId(1L, 45L)).willReturn(false);
            given(libraryRepository.save(any(Library.class))).willReturn(saved);

            LibraryAddResponse response = libraryService.addToLibrary(1L, request);

            assertThat(response.libraryId()).isEqualTo(1L);
            assertThat(response.novelTitle()).isEqualTo("테스트소설");
            then(libraryRepository).should().save(any(Library.class));
        }

        @Test
        @DisplayName("이미 서재에 담긴 소설이면 ALREADY_IN_LIBRARY 예외가 발생한다")
        void addToLibrary_duplicate_throwsException() {
            given(libraryRepository.existsByUserIdAndNovelId(1L, 45L)).willReturn(true);

            assertThatThrownBy(() -> libraryService.addToLibrary(1L, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(LibraryExceptionEnum.ALREADY_IN_LIBRARY.getMessage());

            then(libraryRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("내서재 목록 조회")
    class GetMyLibrary {

        @Test
        @DisplayName("전체 목록을 페이징하여 조회한다")
        void getMyLibrary_all_success() {
            Library lib1 = makeLibrary(1L, 1L, 45L, LibraryType.BOOKMARKED);
            Library lib2 = makeLibrary(2L, 1L, 46L, LibraryType.READING);
            Page<Library> page = new PageImpl<>(List.of(lib1, lib2), PageRequest.of(0, 12), 2);

            given(libraryQueryRepository.findByUserIdWithSort(eq(1L), isNull(), eq("LATEST"), any()))
                    .willReturn(page);
            given(episodeRepository.countByNovelIds(List.of(45L, 46L)))
                    .willReturn(Map.of(45L, 10L, 46L, 20L));

            Page<LibraryListResponse> result =
                    libraryService.getMyLibrary(1L, null, 0, 12, "LATEST");

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).totalEpisodes()).isEqualTo(10L);
        }

        @Test
        @DisplayName("타입 필터로 READING만 조회한다")
        void getMyLibrary_filterByType_success() {
            Library lib = makeLibrary(1L, 1L, 45L, LibraryType.READING);
            Page<Library> page = new PageImpl<>(List.of(lib), PageRequest.of(0, 12), 1);

            given(libraryQueryRepository.findByUserIdWithSort(eq(1L), eq(LibraryType.READING), eq("LATEST"), any()))
                    .willReturn(page);
            given(episodeRepository.countByNovelIds(List.of(45L)))
                    .willReturn(Map.of(45L, 50L));

            Page<LibraryListResponse> result =
                    libraryService.getMyLibrary(1L, LibraryType.READING, 0, 12, "LATEST");

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).totalEpisodes()).isEqualTo(50L);
        }

        @Test
        @DisplayName("서재에 아무것도 없으면 빈 페이지를 반환한다")
        void getMyLibrary_empty_returnsEmptyPage() {
            Page<Library> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
            given(libraryQueryRepository.findByUserIdWithSort(any(), any(), any(), any()))
                    .willReturn(emptyPage);

            Page<LibraryListResponse> result =
                    libraryService.getMyLibrary(1L, null, 0, 12, "LATEST");

            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("제목순 정렬로 조회한다")
        void getMyLibrary_sortByTitle_success() {
            Page<Library> page = new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
            given(libraryQueryRepository.findByUserIdWithSort(eq(1L), isNull(), eq("TITLE"), any()))
                    .willReturn(page);

            Page<LibraryListResponse> result =
                    libraryService.getMyLibrary(1L, null, 0, 12, "TITLE");

            then(libraryQueryRepository).should()
                    .findByUserIdWithSort(eq(1L), isNull(), eq("TITLE"), any());
        }
    }
}