package com.example.hot6novelcraft.domain.nationallibrary.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NationalLibraryExceptionEnum;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.BookSaveRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.BookSearchRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.UserBookSaveRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.BookResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.MyShelfResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.NationalLibraryApiItem;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.NationalLibraryBookResponse;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.UserBookResponse;
import com.example.hot6novelcraft.domain.nationallibrary.entity.Book;
import com.example.hot6novelcraft.domain.nationallibrary.entity.UserBook;
import com.example.hot6novelcraft.domain.nationallibrary.infrastructure.NationalLibraryApiClient;
import com.example.hot6novelcraft.domain.nationallibrary.infrastructure.NationalLibraryApiResponse;
import com.example.hot6novelcraft.domain.nationallibrary.repository.BookRepository;
import com.example.hot6novelcraft.domain.nationallibrary.repository.UserBookRepository;
import com.example.hot6novelcraft.common.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NationalLibraryServiceTest {

    @InjectMocks private NationalLibraryService    nationalLibraryService;
    @Mock        private NationalLibraryApiClient  apiClient;
    @Mock        private BookRepository            bookRepository;
    @Mock        private UserBookRepository        userBookRepository;
    @Mock        private RedisTemplate<String, Object> redisTemplate;
    @Mock        private ValueOperations<String, Object> valueOperations;

    private Book makeBook(Long id, String isbn, String title, String author,
                          String publisher, String publishYear) {
        Book book = Book.from(new BookSaveRequest(isbn, title, author, publisher, publishYear, ""));
        try {
            Field idField = Book.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(book, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return book;
    }

    private UserBook makeUserBook(Long id, Long userId, Long bookId) {
        UserBook userBook = UserBook.of(userId, bookId);
        try {
            Field idField = UserBook.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userBook, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return userBook;
    }

    @Nested
    @DisplayName("도서 검색")
    class SearchBooks {

        private final BookSearchRequest request = new BookSearchRequest("스프링", 1, 10);

        @Test
        @DisplayName("캐시 미스 시 외부 API를 호출하고 결과를 반환한다")
        void searchBooks_cacheMiss_callsApiAndReturns() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);

            NationalLibraryApiItem item = new NationalLibraryApiItem(
                    "9791193235447", "스프링", "온다 리쿠",
                    "클레이하우스", "2025", "https://www.nl.go.kr/...", ""
            );
            // List.of(item) → NationalLibraryApiResponse로 변경
            given(apiClient.searchBooks("스프링", 1, 10))
                    .willReturn(new NationalLibraryApiResponse(1, List.of(item)));

            PageResponse<NationalLibraryBookResponse> result =
                    nationalLibraryService.searchBooks(request);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).isbn()).isEqualTo("9791193235447");
            assertThat(result.content().get(0).title()).isEqualTo("스프링");
            assertThat(result.totalElements()).isEqualTo(1L);
            then(apiClient).should().searchBooks("스프링", 1, 10);
            then(valueOperations).should().set(anyString(), any(), any());
        }

        @Test
        @DisplayName("캐시 히트 시 외부 API를 호출하지 않는다")
        void searchBooks_cacheHit_doesNotCallApi() {
            PageResponse<NationalLibraryBookResponse> cached = new PageResponse<>(
                    List.of(), 0, 1, 1L, 10, true
            );
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(cached);

            PageResponse<NationalLibraryBookResponse> result =
                    nationalLibraryService.searchBooks(request);

            assertThat(result).isEqualTo(cached);
            then(apiClient).should(never()).searchBooks(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("외부 API 결과가 비어있으면 빈 페이지를 반환한다")
        void searchBooks_emptyResult_returnsEmptyPage() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);
            // List.of() → NationalLibraryApiResponse(0, List.of())로 변경
            given(apiClient.searchBooks("스프링", 1, 10))
                    .willReturn(new NationalLibraryApiResponse(0, List.of()));

            PageResponse<NationalLibraryBookResponse> result =
                    nationalLibraryService.searchBooks(request);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("도서 저장")
    class SaveBook {

        private final BookSaveRequest request = new BookSaveRequest(
                "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025", ""
        );

        @Test
        @DisplayName("정상적으로 도서를 저장한다")
        void saveBook_success() {
            Book saved = makeBook(1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025");
            given(bookRepository.existsByIsbn("9791193235447")).willReturn(false);
            given(bookRepository.save(any(Book.class))).willReturn(saved);

            BookResponse response = nationalLibraryService.saveBook(request);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.isbn()).isEqualTo("9791193235447");
            assertThat(response.title()).isEqualTo("스프링");
            then(bookRepository).should().save(any(Book.class));
        }

        @Test
        @DisplayName("이미 존재하는 ISBN이면 BOOK_ALREADY_EXISTS 예외가 발생한다")
        void saveBook_duplicateIsbn_throwsException() {
            given(bookRepository.existsByIsbn("9791193235447")).willReturn(true);

            assertThatThrownBy(() -> nationalLibraryService.saveBook(request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(NationalLibraryExceptionEnum.BOOK_ALREADY_EXISTS.getMessage());

            then(bookRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("도서 단건 조회")
    class GetBook {

        @Test
        @DisplayName("정상적으로 도서를 조회한다")
        void getBook_success() {
            Book book = makeBook(1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025");
            given(bookRepository.findById(1L)).willReturn(Optional.of(book));

            BookResponse response = nationalLibraryService.getBook(1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.title()).isEqualTo("스프링");
        }

        @Test
        @DisplayName("존재하지 않는 도서면 BOOK_NOT_FOUND 예외가 발생한다")
        void getBook_notFound_throwsException() {
            given(bookRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> nationalLibraryService.getBook(999L))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(NationalLibraryExceptionEnum.BOOK_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("내 서재 도서 저장")
    class SaveUserBook {

        private final UserBookSaveRequest request = new UserBookSaveRequest(
                "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025", ""
        );

        @Test
        @DisplayName("books 테이블에 없으면 저장 후 내 서재에 추가한다")
        void saveUserBook_newBook_savesAndAddsToShelf() {
            Book book = makeBook(1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025");
            UserBook userBook = makeUserBook(1L, 1L, 1L);

            given(bookRepository.findByIsbn("9791193235447")).willReturn(Optional.empty());
            given(bookRepository.save(any(Book.class))).willReturn(book);
            given(userBookRepository.existsByUserIdAndBookId(1L, 1L)).willReturn(false);
            given(userBookRepository.save(any(UserBook.class))).willReturn(userBook);

            UserBookResponse response = nationalLibraryService.saveUserBook(1L, request);

            assertThat(response.userBookId()).isEqualTo(1L);
            assertThat(response.isbn()).isEqualTo("9791193235447");
            then(bookRepository).should().save(any(Book.class));
            then(userBookRepository).should().save(any(UserBook.class));
        }

        @Test
        @DisplayName("books 테이블에 있으면 기존 도서를 재사용해 내 서재에 추가한다")
        void saveUserBook_existingBook_reusesBookAndAddsToShelf() {
            Book book = makeBook(1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025");
            UserBook userBook = makeUserBook(1L, 1L, 1L);

            given(bookRepository.findByIsbn("9791193235447")).willReturn(Optional.of(book));
            given(userBookRepository.existsByUserIdAndBookId(1L, 1L)).willReturn(false);
            given(userBookRepository.save(any(UserBook.class))).willReturn(userBook);

            UserBookResponse response = nationalLibraryService.saveUserBook(1L, request);

            assertThat(response.userBookId()).isEqualTo(1L);
            then(bookRepository).should(never()).save(any());
            then(userBookRepository).should().save(any(UserBook.class));
        }

        @Test
        @DisplayName("이미 내 서재에 있으면 BOOK_ALREADY_IN_SHELF 예외가 발생한다")
        void saveUserBook_alreadyInShelf_throwsException() {
            Book book = makeBook(1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025");

            given(bookRepository.findByIsbn("9791193235447")).willReturn(Optional.of(book));
            given(userBookRepository.existsByUserIdAndBookId(1L, 1L)).willReturn(true);

            assertThatThrownBy(() -> nationalLibraryService.saveUserBook(1L, request))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(NationalLibraryExceptionEnum.BOOK_ALREADY_IN_SHELF.getMessage());

            then(userBookRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("내 서재 목록 조회")
    class GetMyShelf {

        @Test
        @DisplayName("정상적으로 내 서재 목록을 반환한다")
        void getMyShelf_success() {
            Book book = makeBook(1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025");
            UserBook userBook = makeUserBook(1L, 1L, 1L);

            given(userBookRepository.findAllByUserId(1L)).willReturn(List.of(userBook));
            given(bookRepository.findAllById(List.of(1L))).willReturn(List.of(book));

            List<MyShelfResponse> result = nationalLibraryService.getMyShelf(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).title()).isEqualTo("스프링");
            assertThat(result.get(0).author()).isEqualTo("온다 리쿠");
        }

        @Test
        @DisplayName("내 서재가 비어있으면 빈 리스트를 반환한다")
        void getMyShelf_empty_returnsEmptyList() {
            given(userBookRepository.findAllByUserId(1L)).willReturn(List.of());
            given(bookRepository.findAllById(List.of())).willReturn(List.of());

            List<MyShelfResponse> result = nationalLibraryService.getMyShelf(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("여러 도서가 있으면 전체 목록을 반환한다")
        void getMyShelf_multipleBooks_returnsAll() {
            Book book1 = makeBook(1L, "9791193235447", "스프링", "온다 리쿠", "클레이하우스", "2025");
            Book book2 = makeBook(2L, "9788970188195", "파이썬", "오연재", "기한재", "2026");
            UserBook userBook1 = makeUserBook(1L, 1L, 1L);
            UserBook userBook2 = makeUserBook(2L, 1L, 2L);

            given(userBookRepository.findAllByUserId(1L)).willReturn(List.of(userBook1, userBook2));
            given(bookRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(book1, book2));

            List<MyShelfResponse> result = nationalLibraryService.getMyShelf(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).title()).isEqualTo("스프링");
            assertThat(result.get(1).title()).isEqualTo("파이썬");
        }
    }
}