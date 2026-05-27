package com.example.hot6novelcraft.domain.user.repository;

import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByNickname(String nickname);

    Boolean existsByEmail(String email);
    Boolean existsByNickname(String nickname);

    Optional<User> findByIdAndIsDeletedFalse(Long id);
    List<User> findAllByRole(UserRole role);

     // 닉네임 중복 확인 (본인 제외)
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    // 탈퇴 스케쥴러
    List<User> findByIsDeletedTrueAndDeletedAtBeforeAndAnonymizedAtIsNull(LocalDateTime date);

    Page<User> findAllByRole(UserRole role, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update User u
            set u.role = :nextRole
            where u.id = :userId and u.role = :currentRole
            """)
    int updateRoleIfCurrent(
            @Param("userId") Long userId,
            @Param("currentRole") UserRole currentRole,
            @Param("nextRole") UserRole nextRole
    );
}
