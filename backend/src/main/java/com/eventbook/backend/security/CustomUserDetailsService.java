package com.eventbook.backend.security;

import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SRP: a única responsabilidade desta classe é traduzir nosso User (do
 * domínio) para o UserDetails que o Spring Security entende. Nenhuma
 * regra de negócio mora aqui.
 * <p>
 * O prefixo "ROLE_" na authority é uma convenção que o Spring Security
 * exige internamente — @PreAuthorize("hasRole('ORGANIZADOR')"), que vamos
 * usar no futuro, procura por "ROLE_ORGANIZADOR" por trás dos panos.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}