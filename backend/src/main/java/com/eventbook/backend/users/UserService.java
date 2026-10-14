package com.eventbook.backend.users;

import com.eventbook.backend.users.dto.RegisterUserRequest;
import com.eventbook.backend.users.dto.UserResponse;
import com.eventbook.backend.users.exception.DuplicateEmailException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Já existe um usuário cadastrado com este email");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado"));
        return userMapper.toResponse(user);
    }
}