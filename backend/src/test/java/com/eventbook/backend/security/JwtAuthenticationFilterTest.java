package com.eventbook.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica a rejeição de tokens revogados (logout por tokenVersion) no filtro JWT.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    private AuthenticatedUser user(UUID id, int version) {
        return new AuthenticatedUser(
                id,
                "user@email.com",
                "User",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMPRADOR")),
                version
        );
    }

    @SuppressWarnings("unchecked")
    private void stubToken(String token, UUID userId, Integer tokenVersion) {
        Jws<Claims> jws = mock(Jws.class);
        Claims claims = mock(Claims.class);
        when(jwtService.validateToken(token)).thenReturn(jws);
        when(jws.getPayload()).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("tokenVersion", Integer.class)).thenReturn(tokenVersion);
    }

    @Test
    void deveAutenticarQuandoVersaoDoTokenConfere() throws Exception {
        UUID userId = UUID.randomUUID();
        stubToken("token-ok", userId, 3);
        when(userDetailsService.loadUserById(userId))
                .thenReturn(Optional.of(user(userId, 3)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-ok");

        filter().doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    }

    @Test
    void deveRejeitarTokenDeVersaoAnteriorAposLogout() throws Exception {
        UUID userId = UUID.randomUUID();
        // Token emitido com versão 3, mas o usuário já está na versão 4 (fez logout).
        stubToken("token-antigo", userId, 3);
        when(userDetailsService.loadUserById(userId))
                .thenReturn(Optional.of(user(userId, 4)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-antigo");

        filter().doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void deveRejeitarTokenSemClaimDeVersao() throws Exception {
        UUID userId = UUID.randomUUID();
        stubToken("token-legado", userId, null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-legado");

        filter().doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void naoDeveAutenticarSemHeaderAuthorization() throws Exception {
        filter().doFilterInternal(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (req, res) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
