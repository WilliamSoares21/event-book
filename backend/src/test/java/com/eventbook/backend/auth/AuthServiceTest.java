package com.eventbook.backend.auth;

import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.security.JwtService;
import com.eventbook.backend.users.UserRepository;
import com.eventbook.backend.users.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void deveIncrementarTokenVersionComSucessoAoLogout() {
        UUID userId = UUID.randomUUID();
        when(userRepository.incrementTokenVersion(userId)).thenReturn(1);

        authService.logout(userId);

        verify(userRepository).incrementTokenVersion(userId);
    }

    @Test
    void deveLancarBusinessException404QuandoUsuarioNaoEncontradoNoLogout() {
        UUID userId = UUID.randomUUID();
        when(userRepository.incrementTokenVersion(userId)).thenReturn(0);

        assertThatThrownBy(() -> authService.logout(userId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Usuário não encontrado")
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
