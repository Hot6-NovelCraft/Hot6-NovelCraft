package com.example.hot6novelcraft.domain.episode.dummy;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EpisodeDummyService {

    private final JdbcTemplate jdbcTemplate;

    private static final int NOVEL_COUNT = 1;
    private static final int EPISODE_PER_NOVEL = 4000;

    public void insertDummyData() {
        insertNovels();
        insertEpisodes();
    }

    private void insertNovels() {
        String sql = """
                INSERT INTO novels (author_id, title, description, genre, tags, status, view_count, bookmark_count, is_deleted, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;

        String[] genres = {
                "FANTASY", "ROMANCE_FANTASY", "MODERN", "SF", "HORROR",
                "CHIVALROUS", "FANTASY", "ROMANCE_FANTASY", "MODERN", "SF"
        };

        String[] tags = {
                "ISEKAI,ROMANCE", "VILLAINESS,COMEDY", "ACTION,THRILLER", "SCIFI,ROMANCE", "THRILLER,MYSTERY",
                "REGRESSION,ACTION", "MAGIC,DAILY_LIFE", "ROMANCE,SUPERNATURAL", "RICH,COMEDY", "MAGIC,ACTION"
        };

        String[] titles = {
                "빙의물의 법칙", "검은 태양의 기사", "재벌집 막내아들", "천재 마법사의 일상", "빌런이 되기로 했다",
                "우주 끝에서", "저승사자와 계약했다", "공포의 저택", "무림 최강자의 귀환", "소녀와 드래곤",
                "현대판 흑막", "달콤한 복수", "빛의 마법사", "AI와 사랑에 빠졌다", "나는 악당의 스승이다",
                "용사의 귀환", "마왕의 딸", "시간을 달리는 소녀", "별빛 아래서", "운명의 붉은 실"
        };

        String[] descriptions = {
                "수백번 빙의를 반복하는 주인공의 이야기", "어둠의 기사가 된 전생 기억을 가진 주인공",
                "재벌가에 빙의한 이야기", "최강 마법사의 느긋한 일상물", "악역으로 살아가는 이야기",
                "우주를 배경으로 한 SF 로맨스", "저승사자와 계약을 맺은 소녀", "버려진 저택에 갇힌 사람들",
                "현대로 귀환한 무림 고수", "드래곤을 키우는 소녀의 이야기",
                "현대 배경 흑막 주인공", "복수를 위한 달콤한 계략", "빛을 다루는 마법사의 여정",
                "AI와 인간의 로맨스", "악당을 키우게 된 주인공",
                "전설의 용사가 귀환하는 이야기", "마왕의 딸로 태어난 주인공", "시간을 달리는 능력자의 이야기",
                "별빛 아래 펼쳐지는 로맨스", "붉은 실로 연결된 두 사람의 운명"
        };

        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = 0; i < NOVEL_COUNT; i++) {
            batchArgs.add(new Object[]{
                    1L,
                    titles[i],
                    descriptions[i],
                    genres[i % genres.length],
                    tags[i % tags.length],
                    "ONGOING",
                    0,
                    0,
                    false
            });
        }

        jdbcTemplate.batchUpdate(sql, batchArgs);
        System.out.println("소설 더미데이터 " + NOVEL_COUNT + "개 생성 완료!");
    }

    private void insertEpisodes() {
        String sql = """
                INSERT INTO episodes (novel_id, episode_number, title, content, is_free, point_price, status, like_count, is_deleted, published_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;

        String[] titles = {
                "시작", "만남", "갈등", "전개", "위기",
                "반전", "절정", "해결", "여운", "출발",
                "운명", "선택", "비밀", "진실", "각성",
                "동맹", "배신", "복수", "화해", "결말"
        };

        List<Object[]> batchArgs = new ArrayList<>();

        for (int novelId = 1; novelId <= NOVEL_COUNT; novelId++) {
            for (int ep = 1; ep <= EPISODE_PER_NOVEL; ep++) {
                boolean isFree = ep <= 2;

                batchArgs.add(new Object[]{
                        novelId,
                        ep,
                        ep + "화 - " + titles[(ep - 1) % titles.length],
                        generateContent(novelId, ep),
                        isFree,
                        isFree ? 0 : 200,
                        "PUBLISHED",
                        0,
                        false
                });

                if (batchArgs.size() == 1000) {
                    jdbcTemplate.batchUpdate(sql, batchArgs);
                    batchArgs.clear();
                }
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }

        System.out.println("에피소드 더미데이터 " + (NOVEL_COUNT * EPISODE_PER_NOVEL) + "개 생성 완료!");
    }

    private String generateContent(int novelId, int ep) {
        String base = String.format(
                "%d번 소설 %d화입니다. 주인공은 오늘도 새로운 모험을 시작합니다. " +
                        "어둠 속에서 빛나는 검을 들고 앞으로 나아가는 그의 눈빛은 굳건했다. " +
                        "바람이 불어왔고, 나뭇잎이 흩날렸다. 멀리서 적의 함성이 들려왔다. " +
                        "그는 두려움 없이 발걸음을 내딛었다. 이것이 진정한 영웅의 시작이었다. " +
                        "검은 하늘 아래 별빛이 쏟아졌고, 그의 마음속엔 오직 하나의 목표만이 남아있었다. " +
                        "적들은 사방에서 몰려들었지만, 그는 흔들리지 않았다. ",
                novelId, ep
        );

        StringBuilder sb = new StringBuilder();
        while (sb.length() < 4000) {
            sb.append(base);
        }
        return sb.substring(0, 4000);
    }
}
