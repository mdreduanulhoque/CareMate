package com.carebridge.auth;

import com.carebridge.auth.dto.AuthResponse;
import com.carebridge.auth.dto.CaretakerRegisterRequest;
import com.carebridge.auth.dto.CustomerRegisterRequest;
import com.carebridge.auth.dto.LoginRequest;
import com.carebridge.auth.dto.UserSummaryResponse;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.common.ConflictException;
import com.carebridge.common.ForbiddenException;
import com.carebridge.common.PhoneNumberNormalizer;
import com.carebridge.common.UnauthorizedException;
import com.carebridge.security.JwtService;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid mobile number or password.";

    private final UserRepository userRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse registerCustomer(CustomerRegisterRequest request) {
        return registerWithRole(
                request.fullName(),
                request.mobileNumber(),
                request.address(),
                request.email(),
                request.password(),
                Role.CUSTOMER
        );
    }

    @Transactional
    public AuthResponse registerCaretaker(CaretakerRegisterRequest request) {
        return registerWithRole(
                request.fullName(),
                request.mobileNumber(),
                request.address(),
                request.email(),
                request.password(),
                Role.CARETAKER
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedMobile = PhoneNumberNormalizer.normalize(request.mobileNumber());

        User user = userRepository.findByMobileNumber(normalizedMobile)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        if (!user.isActive()) {
            throw new ForbiddenException("Your account is currently deactivated. Please contact support.");
        }

        String token = jwtService.generateToken(user);
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds(), toSummary(user));
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User session is no longer valid."));
        if (!user.isActive()) {
            throw new ForbiddenException("Your account is currently deactivated.");
        }
        return toSummary(user);
    }

    private AuthResponse registerWithRole(
            String rawFullName,
            String rawMobileNumber,
            String rawAddress,
            String rawEmail,
            String rawPassword,
            Role assignedRole
    ) {
        String normalizedMobile = PhoneNumberNormalizer.normalize(rawMobileNumber);
        if (userRepository.existsByMobileNumber(normalizedMobile)) {
            throw new ConflictException("An account with this mobile number already exists.");
        }

        String normalizedEmail = normalizeOptionalEmail(rawEmail);
        String passwordHash = passwordEncoder.encode(rawPassword);

        User newUser = new User(
                rawFullName.trim(),
                normalizedMobile,
                normalizedEmail,
                passwordHash,
                rawAddress.trim(),
                assignedRole
        );

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(newUser);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An account with this mobile number already exists.");
        }

        String token = jwtService.generateToken(savedUser);
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds(), UserSummaryResponse.from(savedUser, false));
    }

    private UserSummaryResponse toSummary(User user) {
        boolean caretakerApproved = user.getRole() == Role.CARETAKER
                && caretakerProfileRepository.existsByUserId(user.getId());
        return UserSummaryResponse.from(user, caretakerApproved);
    }

    private String normalizeOptionalEmail(String rawEmail) {
        if (rawEmail == null) {
            return null;
        }
        String trimmed = rawEmail.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
