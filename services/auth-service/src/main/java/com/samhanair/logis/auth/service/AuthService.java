package com.samhanair.logis.auth.service;

import com.samhanair.logis.auth.config.JwtIssueProperties;
import com.samhanair.logis.auth.domain.Account;
import com.samhanair.logis.auth.repository.AccountRepository;
import com.samhanair.logis.auth.service.dto.LoginResponse;
import com.samhanair.logis.auth.service.dto.RegisterResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.common.security.JwtTokenProvider;
import com.samhanair.logis.common.security.Role;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authentication + registration use-cases. All errors are surfaced as {@link BusinessException}. */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssueProperties jwtIssueProperties;

    public LoginResponse login(String loginId, String rawPassword) {
        Account account = accountRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다"));

        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다");
        }

        account.markLogin(LocalDateTime.now());

        String userId = account.getId().toString();
        String role = account.getRole().name();
        String token = JwtTokenProvider.generate(
                userId, role, jwtIssueProperties.getTtlSeconds(), jwtIssueProperties.getSecretBytes());

        return new LoginResponse(token, userId, role, account.getDisplayName());
    }

    public RegisterResponse register(String loginId, String rawPassword, String displayName, Role role) {
        if (accountRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용중인 아이디입니다");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        Account account = accountRepository.save(Account.create(loginId, passwordHash, displayName, role));

        return new RegisterResponse(account.getId().toString(), account.getLoginId(), account.getRole().name());
    }
}
