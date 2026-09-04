package com.example.hot6novelcraft.domain.admin.dto.response;

import com.example.hot6novelcraft.domain.mentor.entity.Mentor;
import com.example.hot6novelcraft.domain.mentor.entity.enums.MentorStatus;
import com.example.hot6novelcraft.domain.user.entity.User;
import com.example.hot6novelcraft.domain.user.entity.enums.CareerLevel;

public record AdminPendingMentorResponse(
        Long mentorId
        , Long userId
        , String email
        , String nickname
        , CareerLevel careerLevel
        , MentorStatus status
        , String awardsCareer
) {
    public static AdminPendingMentorResponse of(Mentor mentor, User user) {
        return new AdminPendingMentorResponse(
                mentor.getId()
                , user.getId()
                , user.getEmail()
                , user.getNickname()
                , mentor.getCareerLevel()
                , mentor.getStatus()
                , mentor.getAwardsCareer()
        );
    }
}
