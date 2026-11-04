package com.realticket.domain.user.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realticket.domain.user.dto.GuestCleanupResponse;
import com.realticket.domain.user.dto.UserCredentialsRequest;
import com.realticket.domain.user.entity.User;
import com.realticket.domain.user.entity.UserRole;
import com.realticket.domain.user.repository.UserRepository;
import com.realticket.global.auth.SessionRepository;
import com.realticket.global.auth.SessionUser;
import com.realticket.global.auth.UserStatus;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String LOGIN_FAILED = "아이디/비밀번호를 잘못 입력하셨습니다. 다시 입력해주세요";

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long register(UserCredentialsRequest request, UserRole role) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw ApiException.conflict("이미 존재하는 사용자입니다.");
        }
        try {
            User user = User.member(request.loginId(), passwordEncoder.encode(request.loginPassword()), role);
            return userRepository.saveAndFlush(user).getId();
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 아이디로 가입한 경우 (login_id UNIQUE)
            throw ApiException.conflict("이미 존재하는 사용자입니다.");
        }
    }

    /**
     * 아이디/비밀번호를 확인하고 새 세션을 만든다. 같은 계정의 기존 세션은 끊는다.
     * (Nest 버전은 비밀번호가 틀려도 기존 세션을 먼저 끊었다)
     */
    public SessionUser login(String loginId, String password) {
        User user = userRepository.findByLoginId(loginId)
                .filter(found -> found.getLoginPassword() != null)
                .filter(found -> passwordEncoder.matches(password, found.getLoginPassword()))
                .orElseThrow(() -> ApiException.unauthorized(LOGIN_FAILED));

        UserStatus status = user.isAdmin() ? UserStatus.ADMIN : UserStatus.LOGIN;
        return sessionRepository.create(user.getId(), user.getLoginId(), status);
    }

    @Transactional
    public SessionUser loginAsGuest() {
        User guest = userRepository.save(User.guest("guest-" + UUID.randomUUID()));
        return sessionRepository.create(guest.getId(), guest.getLoginId(), UserStatus.LOGIN);
    }

    public boolean isAvailableLoginId(String loginId) {
        return !userRepository.existsByLoginId(loginId);
    }

    public void logout(SessionUser session) {
        sessionRepository.delete(session.sid(), session.loginId());
    }

    /** 게스트 계정과 그 예약(좌석 포함)을 모두 지운다. */
    @Transactional
    public GuestCleanupResponse removeAllGuests() {
        int seats = userRepository.deleteGuestReservedSeats();
        int reservations = userRepository.deleteGuestReservations();
        int users = userRepository.deleteGuests();
        log.info("게스트 정리: users={}, reservations={}, reservedSeats={}", users, reservations, seats);
        return new GuestCleanupResponse(users, reservations, seats);
    }
}
