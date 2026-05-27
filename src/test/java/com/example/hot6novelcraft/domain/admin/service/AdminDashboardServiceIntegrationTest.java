package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.domain.admin.dto.response.AdminDashboardResponse;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.novel.entity.Novel;
import com.example.hot6novelcraft.domain.novel.entity.enums.NovelStatus;
import com.example.hot6novelcraft.domain.novel.repository.NovelRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("AdminDashboardService 통합 테스트")
class AdminDashboardServiceIntegrationTest {

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private MentorRepository mentorRepository;

    @BeforeEach
    void setUp() {
        novelRepository.deleteAll();
        mentorRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        novelRepository.deleteAll();
        mentorRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessCase {

        @Test
        @DisplayName("실제 DB 데이터 기반 전체 통계 조회 성공")
        void getDashboardStats_실제DB_전체조회_성공() {
            userRepository.save(User.register(
                    "dashboard-reader@test.com", "password", "독자1",
                    "01011111111", null, UserRole.READER));
            userRepository.save(User.register(
                    "dashboard-author@test.com", "password", "작가1",
                    "01022222222", null, UserRole.AUTHOR));

            AdminDashboardResponse result =
                    adminDashboardService.getDashboardStatusIntegrated(null, null, null, null);

            assertThat(result.userStatus().totalUsers()).isEqualTo(2L);
            assertThat(result.userStatus().filterUserRole()).isEqualTo(2L);
        }

        @Test
        @DisplayName("role=READER 필터 실제 DB 조회 성공")
        void getDashboardStats_실제DB_독자필터_성공() {
            userRepository.save(User.register(
                    "dashboard-reader@test.com", "password", "독자1",
                    "01011111111", null, UserRole.READER));
            userRepository.save(User.register(
                    "dashboard-author@test.com", "password", "작가1",
                    "01022222222", null, UserRole.AUTHOR));

            AdminDashboardResponse result =
                    adminDashboardService.getDashboardStatusIntegrated(UserRole.READER, null, null, null);

            assertThat(result.userStatus().filterUserRole()).isEqualTo(1L);
        }

        @Test
        @DisplayName("novelStatus=ONGOING 필터 실제 DB 조회 성공")
        void getDashboardStats_실제DB_연재중필터_성공() {
            User author = userRepository.save(User.register(
                    "dashboard-author@test.com", "password", "작가1",
                    "01022222222", null, UserRole.AUTHOR));

            Novel ongoing1 = novelRepository.save(
                    Novel.createNovel(author.getId(), "연재소설1", "설명", "판타지", "태그"));
            Novel ongoing2 = novelRepository.save(
                    Novel.createNovel(author.getId(), "연재소설2", "설명", "로맨스", "태그"));
            Novel pending = novelRepository.save(
                    Novel.createNovel(author.getId(), "보류소설", "설명", "액션", "태그"));

            ongoing1.changeStatus(NovelStatus.ONGOING);
            ongoing2.changeStatus(NovelStatus.ONGOING);
            pending.changeStatus(NovelStatus.PENDING);

            novelRepository.save(ongoing1);
            novelRepository.save(ongoing2);
            novelRepository.save(pending);

            AdminDashboardResponse result =
                    adminDashboardService.getDashboardStatusIntegrated(null, NovelStatus.ONGOING.name(), null, null);

            assertThat(result.novelStatus().novelsByFilter()).isEqualTo(2L);
        }

        @Test
        @DisplayName("isDeleted=true 삭제 소설 필터 실제 DB 조회 성공")
        void getDashboardStats_실제DB_삭제소설필터_성공() {
            User author = userRepository.save(User.register(
                    "dashboard-author@test.com", "password", "작가1",
                    "01022222222", null, UserRole.AUTHOR));

            Novel novel1 = novelRepository.save(
                    Novel.createNovel(author.getId(), "소설1", "설명", "판타지", "태그"));
            Novel novel2 = novelRepository.save(
                    Novel.createNovel(author.getId(), "소설2", "설명", "로맨스", "태그"));

            novel2.delete();
            novelRepository.save(novel2);

            AdminDashboardResponse result =
                    adminDashboardService.getDashboardStatusIntegrated(null, null, null, true);

            assertThat(result.novelStatus().novelsByFilter()).isEqualTo(1L);
        }

        @Nested
        @DisplayName("실패 케이스")
        class FailCase {

            @Test
            @DisplayName("데이터 없을 때 0 반환 - NPE 없음")
            void getDashboardStats_데이터없음_0반환() {
                AdminDashboardResponse result =
                        adminDashboardService.getDashboardStatusIntegrated(null, null, null, null);

                assertThat(result).isNotNull();
                assertThat(result.userStatus().totalUsers()).isZero();
                assertThat(result.novelStatus().totalNovels()).isZero();
                assertThat(result.mentorStatus().totalMentor()).isZero();
            }

            @Test
            @DisplayName("탈퇴 회원은 전체 통계에서 제외")
            void getDashboardStats_탈퇴회원_통계제외() {
                userRepository.save(User.register(
                        "dashboard-active@test.com", "password", "활성유저",
                        "01011111111", null, UserRole.READER));
                User deletedUser = userRepository.save(User.register(
                        "dashboard-deleted@test.com", "password", "탈퇴유저",
                        "01022222222", null, UserRole.READER));
                deletedUser.withdraw();
                userRepository.save(deletedUser);

                AdminDashboardResponse result =
                        adminDashboardService.getDashboardStatusIntegrated(null, null, null, null);

                assertThat(result.userStatus().totalUsers()).isEqualTo(1L);
            }

            @Test
            @DisplayName("관리자 계정은 회원 통계에서 제외")
            void getDashboardStats_관리자_통계제외() {
                userRepository.save(User.register(
                        "dashboard-reader@test.com", "password", "독자1",
                        "01011111111", null, UserRole.READER));
                userRepository.save(User.registerAdmin(
                        "dashboard-admin@test.com", "password",
                        "01033333333", UserRole.PENDING_ADMIN));

                AdminDashboardResponse result =
                        adminDashboardService.getDashboardStatusIntegrated(null, null, null, null);

                assertThat(result.userStatus().totalUsers()).isEqualTo(1L);
            }
        }
    }
}