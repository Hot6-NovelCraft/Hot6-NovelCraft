package com.example.hot6novelcraft.domain.nationallibrary.service;

import com.example.hot6novelcraft.common.dto.PageResponse;
import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.NationalLibraryExceptionEnum;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.BookSaveRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.BookSearchRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.request.UserBookSaveRequest;
import com.example.hot6novelcraft.domain.nationallibrary.dto.response.*;
import com.example.hot6novelcraft.domain.nationallibrary.entity.Book;
import com.example.hot6novelcraft.domain.nationallibrary.entity.UserBook;
import com.example.hot6novelcraft.domain.nationallibrary.infrastructure.NationalLibraryApiClient;
import com.example.hot6novelcraft.domain.nationallibrary.infrastructure.NationalLibraryApiResponse;
import com.example.hot6novelcraft.domain.nationallibrary.repository.BookRepository;
import com.example.hot6novelcraft.domain.nationallibrary.repository.UserBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NationalLibraryService {

    private final NationalLibraryApiClient apiClient;
    private final BookRepository bookRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserBookRepository userBookRepository;

    private static final String SEARCH_CACHE_PREFIX = "book:search:";
    private static final Duration SEARCH_CACHE_TTL  = Duration.ofMinutes(10);

    // 도서 검색 (Redis 캐싱 적용)
    public PageResponse<NationalLibraryBookResponse> searchBooks(BookSearchRequest request) {
        String cacheKey = SEARCH_CACHE_PREFIX + request.query()
                + ":" + request.page() + ":" + request.size();

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof PageResponse<?> pageResponse) {
            log.debug("캐시 히트 - key: {}", cacheKey);
            @SuppressWarnings("unchecked")
            PageResponse<NationalLibraryBookResponse> result =
                    (PageResponse<NationalLibraryBookResponse>) pageResponse;
            return result;
        }

        // ApiClient에서 response 전체를 받아 total 정보 활용
        NationalLibraryApiResponse response =
                apiClient.searchBooks(request.query(), request.page(), request.size());

        List<NationalLibraryBookResponse> content = response.result() == null
                ? List.of()
                : response.result().stream()
                .map(NationalLibraryBookResponse::from)
                .toList();

        long total = response.total();
        int totalPages = (int) Math.ceil((double) total / request.size());
        boolean isLast = (long) request.page() * request.size() >= total;

        PageResponse<NationalLibraryBookResponse> result = new PageResponse<>(
                content,
                request.page() - 1,
                totalPages,
                total,
                request.size(),
                isLast
        );

        redisTemplate.opsForValue().set(cacheKey, result, SEARCH_CACHE_TTL);
        return result;
    }

    // 도서 저장
    @Transactional
    public BookResponse saveBook(BookSaveRequest request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new ServiceErrorException(NationalLibraryExceptionEnum.BOOK_ALREADY_EXISTS);
        }
        Book book = Book.from(request);
        return BookResponse.from(bookRepository.save(book));
    }

    // 도서 단건 조회
    @Transactional(readOnly = true)
    public BookResponse getBook(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() ->
                        new ServiceErrorException(NationalLibraryExceptionEnum.BOOK_NOT_FOUND));
        return BookResponse.from(book);
    }

    // 내 서재에 도서 저장
    @Transactional
    public UserBookResponse saveUserBook(Long userId, UserBookSaveRequest request) {

        Book book = bookRepository.findByIsbn(request.isbn())
                .orElseGet(() -> bookRepository.save(
                        Book.from(new BookSaveRequest(
                                request.isbn(),
                                request.title(),
                                request.author(),
                                request.publisher(),
                                request.publishYear(),
                                request.coverImageUrl()
                        ))
                ));

        if (userBookRepository.existsByUserIdAndBookId(userId, book.getId())) {
            throw new ServiceErrorException(NationalLibraryExceptionEnum.BOOK_ALREADY_IN_SHELF);
        }

        UserBook userBook = userBookRepository.save(UserBook.of(userId, book.getId()));

        return UserBookResponse.of(userBook, book);
    }

    // 내 서재 목록 조회
    @Transactional(readOnly = true)
    public List<MyShelfResponse> getMyShelf(Long userId) {

        List<UserBook> userBooks = userBookRepository.findAllByUserId(userId);

        List<Long> bookIds = userBooks.stream()
                .map(UserBook::getBookId)
                .toList();

        Map<Long, Book> bookMap = bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, book -> book));

        // Book이 삭제된 경우 NPE 방어를 위해 null 체크 추가
        return userBooks.stream()
                .filter(userBook -> Objects.nonNull(bookMap.get(userBook.getBookId())))
                .map(userBook -> MyShelfResponse.of(userBook, bookMap.get(userBook.getBookId())))
                .toList();
    }

    // 내 서재에서 도서 삭제
    @Transactional
    public void removeFromShelf(Long userId, Long userBookId) {
        if (!userBookRepository.existsByIdAndUserId(userBookId, userId)) {
            throw new ServiceErrorException(NationalLibraryExceptionEnum.BOOK_NOT_IN_SHELF);
        }
        userBookRepository.deleteByIdAndUserId(userBookId, userId);
    }
}