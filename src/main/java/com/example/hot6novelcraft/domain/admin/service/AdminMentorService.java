package com.example.hot6novelcraft.domain.admin.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.MentorExceptionEnum;
import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import com.example.hot6novelcraft.domain.admin.dto.response.AdminPendingMentorResponse;
import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.mentor.repository.MentorRepository;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import com.example.hot6novelcraft.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j(topic = "AdminMentorService")
@Service
@RequiredArgsConstructor
@Transactional
public class AdminMentorService {

    private final MentorRepository mentorRepository;
    private final UserRepository userRepository;

    // 숙련 (PROFICIENT) 등급 심사 대기 목록 조회
    @Transactional(readOnly = true)
    public List<AdminPendingMentorResponse> getPendingProficientMentors() {
        List<Mentor> pendingMentors = mentorRepository.findAllByStatusAndCareerLevel(
                MentorStatus.PENDING
                , CareerLevel.PROFICIENT
        );

        return pendingMentors.stream()
                .map(mentor -> {
                    User user = userRepository.findById(mentor.getUserId())
                            .orElseThrow(() -> new ServiceErrorException(UserExceptionEnum.ERR_NOT_FOUND_USER));

                    return AdminPendingMentorResponse.of(mentor, user);
                })
                .toList();
    }

    // 멘토 승인 처리
    public void approveMentor(Long mentorId) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));
        mentor.approve();
    }

    // 멘토 거절 처리
    public void rejectMentor(Long mentorId, String rejectReason) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ServiceErrorException(MentorExceptionEnum.MENTOR_NOT_FOUND));
        mentor.reject(rejectReason);
    }
}
