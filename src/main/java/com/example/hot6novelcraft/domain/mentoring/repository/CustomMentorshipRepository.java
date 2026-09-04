package com.example.hot6novelcraft.domain.mentoring.repository;

import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorWithNickname;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentoringReceivedResponse;
import com.example.hot6novelcraft.domain.mentoring.dto.response.MentorshipHistoryResponse;
import com.example.hot6novelcraft.domain.mentoring.entity.enums.MentorshipStatus;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomMentorshipRepository {

    // V1: 멘토 목록 조회
    Page<MentorWithNickname> findMentorList(String genre, CareerLevel careerLevel, Long excludeUserId, Pageable pageable);

    // V2: 멘토링 이력 조회 - N+1 개선 (mentorRepository + userRepository 반복 쿼리 → QueryDSL JOIN 단일 쿼리)
    List<MentorshipHistoryResponse> findMyHistoryWithMentorNickname(Long menteeId, MentorshipStatus status);

    // V2: 멘토링 접수 목록 조회 - soft-delete 적용 + N+1 개선 (userRepository + novelRepository 반복 쿼리 → QueryDSL JOIN 단일 쿼리)
    Page<MentoringReceivedResponse> findReceivedMentoringsWithDetails(Long mentorId, Pageable pageable);
}