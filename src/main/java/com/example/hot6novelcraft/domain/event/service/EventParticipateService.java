package com.example.hot6novelcraft.domain.event.service;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.EventExceptionEnum;
import com.example.hot6novelcraft.domain.event.dto.response.EventParticipateResponse;
import com.example.hot6novelcraft.domain.event.entity.Event;
import com.example.hot6novelcraft.domain.event.entity.EventParticipant;
import com.example.hot6novelcraft.domain.event.repository.EventParticipantRepository;
import com.example.hot6novelcraft.domain.event.repository.EventRepository;
import com.example.hot6novelcraft.domain.point.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventParticipateService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final PointService pointService;

    @Transactional
    public EventParticipateResponse execute(Long eventId, Long userId) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ServiceErrorException(EventExceptionEnum.EVENT_NOT_FOUND));

        // 진행 중인 이벤트 여부 검증
        if (!event.isOngoing()) {
            throw new ServiceErrorException(EventExceptionEnum.EVENT_NOT_ONGOING);
        }

        // 중복 참여 검증
        if (eventParticipantRepository.existsByEventIdAndUserId(eventId, userId)) {
            throw new ServiceErrorException(EventExceptionEnum.EVENT_ALREADY_PARTICIPATED);
        }

        // 선착순 인원 검증
        long currentCount = eventParticipantRepository.countByEventId(eventId);
        if (currentCount >= event.getMaxParticipants()) {
            throw new ServiceErrorException(EventExceptionEnum.EVENT_PARTICIPANTS_FULL);
        }

        // 참여자 저장
        EventParticipant participant = EventParticipant.create(eventId, userId);
        eventParticipantRepository.save(participant);

        // 포인트 즉시 지급
        pointService.chargeEventReward(userId, event.getRewardPoints(), eventId);

        return EventParticipateResponse.from(participant, event.getRewardPoints());
    }
}