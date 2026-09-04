package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminResponse;
import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j(topic = "ADMIN")
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;

    /**
     * 일반 관리자 승인 대기 목록 조회
     * 관리자 승인/거절 처리
     */

    // 관리자 승인 대기 목록 조회 (SUPER_ADMIN 전용)
    @Transactional(readOnly = true)
    public List<AdminResponse> getPendingAdmins() {
        return userRepository.findAllByRole(UserRole.PENDING_ADMIN).stream()
                .map(AdminResponse::from)
                .toList();
    }

    // 일반 관리자 승인 (SUPER_ADMIN 전용)
    public void approvePendingAdmin(Long userId) {

        // 엔티티 조회 없이 조건부 업데이트 (쿼리 즉시 실행)
        int updateRole = userRepository.updateRoleIfCurrent(userId, UserRole.PENDING_ADMIN, UserRole.ADMIN);

        // 유저가 없거나 이미 다른 관리자가 승인/거절 누른 상태
        if (updateRole == 0) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_NOT_PENDING_ADMIN);
        }
    }

    // 일반 관리자 승인 거절 (SUPER_ADMIN 전용)
    public void rejectPendingAdmin(Long userId) {

        // 엔티티 조회 없이 조건부 업데이트 (쿼리 즉시 실행)
        int updateRole = userRepository.updateRoleIfCurrent(userId, UserRole.PENDING_ADMIN, UserRole.REJECTED_ADMIN);

        // 유저가 없거나 이미 다른 관리자가 승인/거절 누른 상태
        if (updateRole == 0) {
            throw new ServiceErrorException(UserExceptionEnum.ERR_NOT_POSSIBLE_TO_REFUSE);
        }
    }
}
