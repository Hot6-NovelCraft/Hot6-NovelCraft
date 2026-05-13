//package com.example.hot6novelcraft.common.config;
//
//import com.example.hot6novelcraft.domain.episode.entity.Episode;
//import com.example.hot6novelcraft.domain.episode.repository.EpisodeRepository;
//import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
//import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
//import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
//import com.example.hot6novelcraft.domain.novel.entity.Novel;
//import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
//import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
//import com.example.hot6novelcraft.domain.user.entity.AuthorProfile;
//import com.example.hot6novelcraft.domain.user.entity.User;
//import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
//import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
//import com.example.hot6novelcraft.domain.user.repository.AuthorProfileRepository;
//import com.example.hot6novelcraft.domain.user.repository.UserRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.context.annotation.Profile;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//// @Profile({"local", "dev", "test"})
//public class DataInitializer implements ApplicationRunner {
//
//    private final UserRepository userRepository;
//    private final PasswordEncoder passwordEncoder;
//    private final AuthorProfileRepository authorProfileRepository;
//    private final NovelRepository novelRepository;
//    private final EpisodeRepository episodeRepository;
//    private final MentorRepository mentorRepository;
//
//    private final RedisTemplate<String, Object> redisTemplate;
//    private final JdbcTemplate jdbcTemplate;
//
//    private static final String REALTIME_RANKING_KEY = "ranking:novel:realtime";
//    private static final String WEEKLY_RANKING_KEY = "ranking:novel:weekly";
//
//    @Value("${app.init.bulk-data:false}")
//    private boolean enableBulkInsert;
//
//    @Override
//    @Transactional
//    public void run(ApplicationArguments args) {
//
//        // 슈퍼 어드민 계정 생성
//        createSuperAdmin();
//
//        // 기존 데이터가 있으면 스킵
//        // bulkInsertData() 사용 시, 3 -> 1000 으로 변경
//        if (enableBulkInsert) {
//            if (userRepository.count() < 100000) { // 대략적인 카운트로 방어 로직
//                log.info("[DataInitializer] 성능 테스트용 대용량 더미데이터(10만) 삽입을 시작합니다.");
//                bulkInsertData();
//            } else {
//                log.info("[DataInitializer] 대용량 데이터가 이미 존재하여 스킵합니다.");
//            }
//            return; // 대용량 데이터를 넣었으면 아래 소량 데이터 로직은 스킵
//        }
//
//        log.info("[DataInitializer] 더미데이터 및 Redis 랭킹 세팅 시작");
//
//        // 기존 Redis 랭킹 데이터 초기화
//        redisTemplate.delete(REALTIME_RANKING_KEY);
//        redisTemplate.delete(WEEKLY_RANKING_KEY);
//
//        // ========================
//        // 1-1. 유저 및 작가 프로필 생성
//        // ========================
//        User user1 = userRepository.save(User.register("백산@test.com", passwordEncoder.encode("test1234!"), "백산", "010-1111-1111", null, UserRole.AUTHOR));
//        User user2 = userRepository.save(User.register("백산아@test.com", passwordEncoder.encode("test1234!"), "백산아", "010-2222-2222", null, UserRole.AUTHOR));
//        User user3 = userRepository.save(User.register("바다@test.com", passwordEncoder.encode("test1234!"), "바다", "010-3333-3333", null, UserRole.AUTHOR));
//
//        authorProfileRepository.save(AuthorProfile.register(user1.getId(), "판타지 전문", CareerLevel.INTRODUCTION, "FANTASY", null, null, null, true));
//        authorProfileRepository.save(AuthorProfile.register(user2.getId(), "로맨스판타지 전문", CareerLevel.INTERMEDIATE, "ROMANCE_FANTASY", null, null, null, false));
//        authorProfileRepository.save(AuthorProfile.register(user3.getId(), "현대 전문", CareerLevel.PROFICIENT, "MODERN", null, null, null, true));
//
//        // ========================
//        // 1-2. 유저 및 독자 프로필 생성
//        // ========================
//        User reader1 = userRepository.save(User.register("reader_today1@test.com", passwordEncoder.encode("test1234!"), "오늘가입독자1", "010-9000-1001", null, UserRole.READER));
//        User reader2 = userRepository.save(User.register("reader_today2@test.com", passwordEncoder.encode("test1234!"), "오늘가입독자2", "010-9000-1002", null, UserRole.READER));
//        User reader3 = userRepository.save(User.register("reader_past@test.com", passwordEncoder.encode("test1234!"), "예전가입독자", "010-9000-1003", null, UserRole.READER));
//        User reader4 = userRepository.save(User.register("reader_del@test.com", passwordEncoder.encode("test1234!"), "탈퇴한독자", "010-9000-1004", null, UserRole.READER));
//
//
//        // ========================
//        // 2. 멘토 승인/거절 테스트용 데이터 (주석 해제 후 교체)
//        // ========================
//
//        User mentorCandidate1 = userRepository.save(User.register("mentor_ok@test.com", passwordEncoder.encode("test1234!"), "승인테스트유저", "010-1000-1000", null, UserRole.AUTHOR));
//        authorProfileRepository.save(AuthorProfile.register(mentorCandidate1.getId(), "멘토가 되고 싶습니다.", CareerLevel.PROFICIENT, "FANTASY", null, null, null, true));
//
//        User mentorCandidate2 = userRepository.save(User.register("mentor_reject@test.com", passwordEncoder.encode("test1234!"), "거절테스트유저", "010-2000-2000", null, UserRole.AUTHOR));
//        authorProfileRepository.save(AuthorProfile.register(mentorCandidate2.getId(), "수상 내역 첨부합니다.", CareerLevel.PROFICIENT, "ROMANCE", null, null, null, true));
//
//        User mentorCandidate3 = userRepository.save(User.register("mentor_fail@test.com", passwordEncoder.encode("test1234!"), "자격미달유저", "010-3000-3000", null, UserRole.AUTHOR));
//        authorProfileRepository.save(AuthorProfile.register(mentorCandidate3.getId(), "멘토 시켜주세요", CareerLevel.INTERMEDIATE, "HORROR", null, null, null, false));
//
//        // 빌더 대신 안전한 Mentor.create() 메서드 사용!
//        //Case 1: 승인 테스트 (숙련자, 파일 O)
//        mentorRepository.save(Mentor.create(
//                mentorCandidate1.getId(), CareerLevel.PROFICIENT, null, null, null, null,
//                "공모전_대상_증명서.pdf", 5, true, null, MentorStatus.PENDING
//        ));
//
//        // Case 2: 거절 테스트 (숙련자, 파일 O)
//        mentorRepository.save(Mentor.create(
//                mentorCandidate2.getId(), CareerLevel.PROFICIENT, null, null, null, null,
//                "타플랫폼_연재_경력증명서.png", 3, false, null, MentorStatus.PENDING
//        ));
//
//        // Case 3: 조건 미달 테스트 (중급자, 파일 X)
//        mentorRepository.save(Mentor.create(
//                mentorCandidate3.getId(), CareerLevel.INTERMEDIATE, null, null, null, null,
//                null, 1, true, null, MentorStatus.PENDING
//        ));
//
//        // ========================
//        // 3. 소설 생성 및 랭킹 세팅 (날짜 조작 SQL을 위해 순서 유지 필수)
//        // ========================
//        // 정상 노출 데이터 (1달 이내)
//        saveNovelAndRanking(user1.getId(), "먼치킨 백산의 귀환", "압도적 1위 테스트", "FANTASY", "MUNCHKIN,ISEKAI", 10000, 50000); // ID: 1
//        saveNovelAndRanking(user2.getId(), "재벌집 로맨스", "로맨스 강자 테스트", "ROMANCE", "ROMANCE,CONTRACT", 8000, 45000); // ID: 2
//        saveNovelAndRanking(user3.getId(), "비밀스러운 계약", "잔잔한 로맨스", "ROMANCE", "ROMANCE,HEALING", 1500, 8000); // ID: 3
//
//        // 기간 초과 데이터 (1달 초과 - 리스트에 안 나와야 함)
//        saveNovelAndRanking(user1.getId(), "바다가 보이는 카페", "갑자기 어제부터 뜬 소설", "HEALING", "HEALING,ROMANCE", 7500, 10000); // ID: 4
//        saveNovelAndRanking(user2.getId(), "심해의 군주", "바다물 판타지", "FANTASY", "DUNGEON,MUNCHKIN", 5000, 30000); // ID: 5
//
//        // 상태 불만족 데이터 (보류 및 삭제)
//        saveNovelAndRanking(user3.getId(), "보류중인 소설", "관리자 확인 필요", "HORROR", "ADULT", 7000, 43000); // ID: 6
//        saveNovelAndRanking(user1.getId(), "삭제될 운명의 소설", "삭제 테스트", "FANTASY", "TEST", 0, 0); // ID: 7
//
//        saveNovelAndRanking(user3.getId(), "성인 인증 테스트 소설", "성인 인증 테스트", "HORROR", "ADULT", 7000, 43000); // ID: 8
//
//        // ========================
//        // 4. 신작 조회 테스트용 날짜 강제 조작 (JPA Auditing 무시)
//        // ========================
//        log.info("[DataInitializer] 테스트용 소설 생성일자 조작 시작...");
//
//        // 정상 노출 (ID 1, 2, 3)
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 1 DAY), status = 'ONGOING', is_deleted = false WHERE id = 1");
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 15 DAY), status = 'ONGOING', is_deleted = false WHERE id = 2");
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 29 DAY), status = 'ONGOING', is_deleted = false WHERE id = 3");
//
//        // 기간 초과 (ID 4, 5)
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 2 MONTH), status = 'ONGOING', is_deleted = false WHERE id = 4");
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 6 MONTH), status = 'ONGOING', is_deleted = false WHERE id = 5");
//
//        // 상태 불만족 (ID 6 - 보류, ID 7 - 삭제)
//        jdbcTemplate.execute("UPDATE novels SET created_at = NOW(), status = 'PENDING', is_deleted = false WHERE id = 6");
//        jdbcTemplate.execute("UPDATE novels SET created_at = NOW(), status = 'ONGOING', is_deleted = true WHERE id = 7");
//
//        // ========================
//        // 4. JDBC를 이용한 생성일자 및 탈퇴 상태 강제 조작 (통계 테스트용)
//        // ========================
//        log.info("[DataInitializer] 테스트용 데이터 상태/일자 강제 조작 시작...");
//
//        // 유저(독자) 데이터 조작 - id를 하드코딩하면 위험하므로 email로 조작
//        jdbcTemplate.execute("UPDATE users SET created_at = DATE_SUB(NOW(), INTERVAL 10 DAY) WHERE email = 'reader_past@test.com'");
//        jdbcTemplate.execute("UPDATE users SET is_deleted = true WHERE email = 'reader_del@test.com'");
//
//        // 소설 데이터 조작
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 1 DAY), status = 'ONGOING', is_deleted = false WHERE id = 1");
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 15 DAY), status = 'ONGOING', is_deleted = false WHERE id = 2");
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 29 DAY), status = 'ONGOING', is_deleted = false WHERE id = 3");
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 2 MONTH), status = 'ONGOING', is_deleted = false WHERE id = 4");
//        jdbcTemplate.execute("UPDATE novels SET created_at = DATE_SUB(NOW(), INTERVAL 6 MONTH), status = 'ONGOING', is_deleted = false WHERE id = 5");
//        jdbcTemplate.execute("UPDATE novels SET created_at = NOW(), status = 'PENDING', is_deleted = false WHERE id = 6");
//        jdbcTemplate.execute("UPDATE novels SET created_at = NOW(), status = 'ONGOING', is_deleted = true WHERE id = 7");
//
//        log.info("[DataInitializer] 더미데이터 및 Redis 랭킹 세팅 완료");
//    }
//
//        private void createSuperAdmin () {
//            if (!userRepository.existsByEmail("super@admin.com")) {
//                User superAdmin = User.builder()
//                        .email("super@admin.com")
//                        .password(passwordEncoder.encode("super1234567!"))
//                        .nickname("최고 관리자")
//                        .phoneNo("01010002000")
//                        .birthday(java.time.LocalDate.of(2000, 1, 1))
//                        .role(UserRole.SUPER_ADMIN)
//                        .build();
//
//                userRepository.save(superAdmin);
//                log.info("==== [system] 슈퍼 어드민 테스트 계정 생성 완료 ====");
//            }
//
//
//        // 2. 일반 관리자 (ADMIN) - 추가!
//        if(!userRepository.existsByEmail("normal@admin.com")) {
//            User normalAdmin = User.builder()
//                    .email("normal@admin.com")
//                    .password(passwordEncoder.encode("admin1234!"))
//                    .nickname("일반 관리자")
//                    .phoneNo("01020003000")
//                    .birthday(java.time.LocalDate.of(1995, 5, 5))
//                    .role(UserRole.ADMIN)
//                    .build();
//            userRepository.save(normalAdmin);
//        }
//        }
//
//    private Long saveNovelAndRanking(Long authorId, String title, String description, String genre, String tags,
//                                     double realtimeScore, double weeklyScore) {
//        // 1. 소설 저장
//        Novel novel = Novel.createNovel(authorId, title, description, genre, tags);
//        Novel saved = novelRepository.save(novel);
//        saved.changeStatus(NovelStatus.ONGOING);
//        novelRepository.save(saved);
//
//        // 2. 에피소드 저장
//        Episode episode = Episode.createEpisode(
//                saved.getId(), 1, title + " - 1화", "이것은 테스트를 위한 " + title + "의 1화 본문입니다.", true, 0
//        );
//        episode.publish();
//        episodeRepository.save(episode);
//
//        // 3. 랭킹 스코어 반영
//        redisTemplate.opsForZSet().incrementScore(REALTIME_RANKING_KEY, String.valueOf(saved.getId()), realtimeScore);
//        redisTemplate.opsForZSet().incrementScore(WEEKLY_RANKING_KEY, String.valueOf(saved.getId()), weeklyScore);
//
//        return saved.getId();
//    }
//
//        private void bulkInsertData () {
//            log.info("[DataInitializer] 벌크 데이터 삽입 시작 (10만 건)");
//
//            // 1. 공통 비밀번호 (한 번만 인코딩해서 재사용)
//            String encodedPassword = passwordEncoder.encode("test1234!");
//
//            // 2. 유저 10만 명 삽입
//            String userSql = "INSERT INTO users (email, password, nickname, phone_no, role, is_deleted, created_at, is_adult_verified, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
//            int totalUsers = 100000;
//            int batchSize = 1000; // 1,000건씩 묶어서 처리
//
//            for (int i = 0; i < totalUsers; i += batchSize) {
//                int currentBatchSize = Math.min(batchSize, totalUsers - i);
//                final int startIdx = i;
//
//                jdbcTemplate.batchUpdate(userSql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
//                    @Override
//                    public void setValues(java.sql.PreparedStatement ps, int j) throws java.sql.SQLException {
//                        int idx = startIdx + j;
//                        ps.setString(1, "bulk_user" + idx + "@test.com");
//                        ps.setString(2, encodedPassword);
//                        ps.setString(3, "더미유저" + idx);
//                        ps.setString(4, "010-0000-" + String.format("%04d", idx % 10000));
//                        ps.setString(5, (idx % 10 == 0) ? "AUTHOR" : "READER"); // 10%는 작가로 설정
//                        ps.setBoolean(6, false);
//                        // 통계 테스트를 위해 생성 날짜를 최근 30일로 분산
//                        ps.setTimestamp(7, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(idx % 30)));
//                        ps.setBoolean(8, false);
//                        ps.setTimestamp(9, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(idx % 30)));
//                    }
//
//                    @Override
//                    public int getBatchSize() {
//                        return currentBatchSize;
//                    }
//                });
//            }
//            log.info("[DataInitializer] 유저 10만 건 삽입 완료");
//
//            // 3. 소설 5만 건 삽입 (작가가 작성한 것으로 가정)
//            String novelSql = "INSERT INTO novels (author_id, title, description, genre, tags, status, is_deleted, created_at, bookmark_count, updated_at, view_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
//            int totalNovels = 50000;
//
//            for (int i = 0; i < totalNovels; i += batchSize) {
//                int currentBatchSize = Math.min(batchSize, totalNovels - i);
//                final int startIdx = i;
//
//                jdbcTemplate.batchUpdate(novelSql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
//                    @Override
//                    public void setValues(java.sql.PreparedStatement ps, int j) throws java.sql.SQLException {
//                        int idx = startIdx + j;
//                        ps.setLong(1, (long) (1 + (idx % 100))); // 앞부분에 생성된 유저 ID와 연결
//                        ps.setString(2, "대용량 소설 테스트 " + idx);
//                        ps.setString(3, "설명입니다 " + idx);
//                        ps.setString(4, (idx % 2 == 0) ? "FANTASY" : "ROMANCE");
//                        ps.setString(5, "TAG1,TAG2");
//                        ps.setString(6, "ONGOING");
//                        ps.setBoolean(7, false);
//                        ps.setTimestamp(8, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(idx % 60)));
//                        ps.setInt(9, 0);
//                        ps.setTimestamp(10, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(idx % 60)));
//                        ps.setLong(11, 0L);
//                    }
//
//                    @Override
//                    public int getBatchSize() {
//                        return currentBatchSize;
//                    }
//                });
//            }
//            log.info("[DataInitializer] 소설 5만 건 삽입 완료");
//        }
//    }