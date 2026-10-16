package com.eventbook.backend.users;

import com.eventbook.backend.users.dto.RegisterUserRequest;
import com.eventbook.backend.users.dto.UserResponse;
import com.eventbook.backend.users.exception.DuplicateEmailException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void deveLancarExcecaoQuandoEmailJaExiste() {
        RegisterUserRequest request = new RegisterUserRequest("Ana", "ana@email.com", "senha1234", Role.COMPRADOR);
        when(userRepository.existsByEmail("ana@email.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void deveRegistrarUsuarioComSenhaCriptografada() {
        RegisterUserRequest request = new RegisterUserRequest("Ana", "ana@email.com", "senha1234", Role.COMPRADOR);
        when(userRepository.existsByEmail("ana@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("hash-fake");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(
                new UserResponse(null, "Ana", "ana@email.com", Role.COMPRADOR, null)
        );

        UserResponse response = userService.register(request);

        assertThat(response.email()).isEqualTo("ana@email.com");

        verify(userRepository).save(argThat(user -> user.getPasswordHash().equals("hash-fake")));
    }
}
