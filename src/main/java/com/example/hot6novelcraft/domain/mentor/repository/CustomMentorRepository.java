package com.example.hot6novelcraft.domain.mentor.repository;

import com.example.hot6novelcraft.domain.mentor.dto.response.MenteeInfoResponse;

import java.util.List;

public interface CustomMentorRepository {
    List<MenteeInfoResponse> findMenteesWithDetails(Long mentorId);
}
