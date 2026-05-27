package com.example.hot6novelcraft.common.security;

import com.example.hot6novelcraft.common.exception.ServiceErrorException;
import com.example.hot6novelcraft.common.exception.domain.ChatExceptionEnum;
import com.example.hot6novelcraft.domain.user.entity.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith(JwtUtil.BEARER_PREFIX)) {
                throw new ServiceErrorException(ChatExceptionEnum.ERR_WEBSOCKET_UNAUTHORIZED);
            }

            String token = jwtUtil.substringToken(authHeader);
            if (!jwtUtil.validateToken(token)) {
                throw new ServiceErrorException(ChatExceptionEnum.ERR_WEBSOCKET_UNAUTHORIZED);
            }

            String email = jwtUtil.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            Long userId = ((UserDetailsImpl) userDetails).getUser().getId();

            // userId를 Principal로 등록 → 컨트롤러에서 principal.getName()으로 접근
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userId.toString(), null, userDetails.getAuthorities()
            ));
            log.info("[WebSocket] 연결 인증 완료 userId={}", userId);

        } else if (StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            // CONNECT 없이 직접 SEND/SUBSCRIBE를 보내는 비인증 세션 차단
            if (accessor.getUser() == null) {
                throw new ServiceErrorException(ChatExceptionEnum.ERR_WEBSOCKET_UNAUTHORIZED);
            }
        }

        return message;
    }
}
