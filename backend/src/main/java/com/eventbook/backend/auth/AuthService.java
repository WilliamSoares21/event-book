package com.eventbook.backend.auth;

import com.eventbook.backend.auth.dto.LoginRequest;
import com.eventbook.backend.security.JwtService;
import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.users.dto.UserResponse;
import com.eventbook.backend.users.UserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserService userService; // para montar UserResponse

    public AuthService(AuthenticationManager authManager,
                       JwtService jwtService,
                       UserService userService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    public AuthResponse login(LoginRequest request) {
        // Autentica usando email + senha (BCrypt)
        var authentication = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        // O principal será um AuthenticatedUser (graças ao UserDetailsService)
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        String token = jwtService.generateToken(user);
        long expiresIn = Long.parseLong(
                jwtService.validateToken(token).getBody().getExpiration().getTime()
                        - System.currentTimeMillis());

        // Converte para DTO de resposta (a gnt pode adaptar futuramente se necessário)
        UserResponse userDto = userService.findById(user.id())
                .map(u -> new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole()))
                .orElseThrow();

        return new AuthResponse(token, "Bearer", expiresIn, userDto);
    }
}
