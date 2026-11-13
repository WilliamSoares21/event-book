package com.eventbook.backend.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtService.validateToken(token).getPayload();
                UUID userId = UUID.fromString(claims.getSubject());
                Integer tokenVersion = claims.get("tokenVersion", Integer.class);

                // Token sem claim de versão (emitido antes da migration) é tratado como inválido:
                // comportamento seguro por padrão, conforme decisão de projeto.
                if (tokenVersion == null) {
                    throw new IllegalArgumentException("Token sem versão");
                }

                // Uma única consulta ao banco por requisição autenticada.
                AuthenticatedUser principal = userDetailsService.loadUserById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

                // Versão divergente significa que o usuário fez logout depois da emissão deste token.
                if (!tokenVersion.equals(principal.getTokenVersion())) {
                    throw new IllegalArgumentException("Token revogado");
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities());

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // token inválido → deixa a request sem autenticação (Será 401 depois)
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
