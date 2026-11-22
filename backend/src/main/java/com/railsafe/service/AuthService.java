package com.railsafe.service;

import com.railsafe.dto.RegisterRequest;
import com.railsafe.entity.Role;
import com.railsafe.entity.User;
import com.railsafe.exception.ConflictException;
import com.railsafe.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        // New self-registered accounts are customers. The legacy USER role is
        // preserved so existing commuter accounts keep working unchanged.
        user.setRole(Role.CUSTOMER);
        user.setEnabled(true);
        return userRepository.save(user);
    }
}
