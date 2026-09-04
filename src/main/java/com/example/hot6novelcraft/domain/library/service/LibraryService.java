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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final LibraryRepository libraryRepository;
    private final LibraryQueryRepository libraryQueryRepository;
    private final EpisodeRepository episodeRepository;

    @Transactional
    public LibraryAddResponse addToLibrary(Long userId, LibraryAddRequest request) {

        if (libraryRepository.existsByUserIdAndNovelId(userId, request.novelId())) {
            throw new ServiceErrorException(LibraryExceptionEnum.ALREADY_IN_LIBRARY);
        }

        Library library = libraryRepository.save(
                Library.create(
                        userId,
                        request.novelId(),
                        request.libraryType(),
                        request.novelTitle(),
                        request.authorNickname(),
                        request.coverImageUrl()
                )
        );

        return LibraryAddResponse.from(library);
    }

    @Transactional(readOnly = true)
    public Page<LibraryListResponse> getMyLibrary(Long userId, LibraryType libraryType,
                                                  int page, int size, String sort) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<Library> libraryPage =
                libraryQueryRepository.findByUserIdWithSort(userId, libraryType, sort, pageable);

        // N+1 해결 - novelId 목록 일괄 집계
        List<Long> novelIds = libraryPage.getContent().stream()
                .map(Library::getNovelId)
                .toList();

        Map<Long, Long> episodeCountMap = episodeRepository
                .countByNovelIds(novelIds);

        return libraryPage.map(lib ->
                LibraryListResponse.from(lib,
                        episodeCountMap.getOrDefault(lib.getNovelId(), 0L))
        );
    }
}