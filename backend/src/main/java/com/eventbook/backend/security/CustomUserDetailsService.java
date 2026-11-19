package com.eventbook.backend.security;

import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço do Spring Security que busca as informações do usuário no banco de dados.
 * Ele entrega o AuthenticatedUser com o ID (UUID) já preenchido para facilitar o uso do JWT.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Busca o usuário no banco através do e-mail digitado no login.
     * Transforma esse usuário no formato padrão que o Spring Security exige.
     * Normaliza o email removendo espaços externos antes da consulta.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return toAuthenticatedUser(user);
    }

    /**
     * Busca o usuário pelo UUID presente no JWT. Usado pelo filtro de autenticação
     * para evitar múltiplas consultas por requisição.
     */
    public Optional<AuthenticatedUser> loadUserById(UUID id) {
        return userRepository.findById(id).map(this::toAuthenticatedUser);
    }

    private AuthenticatedUser toAuthenticatedUser(User user) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPasswordHash(),
                authorities,
                user.getTokenVersion()
        );
    }

    /**
     * Normaliza o email removendo espaços em branco do início e fim.
     * Não altera espaços internos do email.
     */
    public static String normalizeEmail(String email) {
        return email != null ? email.trim() : email;
    }
}
