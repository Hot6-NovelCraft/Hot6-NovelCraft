package com.example.hot6novelcraft.domain.episode.dummy;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dummy")
public class EpisodeDummyController {

    private final EpisodeDummyService episodeDummyService;

    @PostMapping("/episodes")
    public ResponseEntity<String> insertDummy() {
        episodeDummyService.insertDummyData();
        return ResponseEntity.ok("소설 1개 - 에피소드 4000개 생성 완료!");
    }
}
