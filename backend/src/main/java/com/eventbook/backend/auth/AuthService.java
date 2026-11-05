package com.eventbook.backend.auth;

import com.eventbook.backend.auth.dto.AuthResponse;
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
    private final UserService userService; // Dependência reinserida

    public AuthService(AuthenticationManager authManager,
                       JwtService jwtService,
                       UserService userService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    public AuthResponse login(LoginRequest request) {
        // Autentica usando email + senha
        var authentication = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        String token = jwtService.generateToken(user);
        
        long expirationTime = jwtService.validateToken(token).getPayload().getExpiration().getTime();
        long expiresIn = expirationTime - System.currentTimeMillis();

        UserResponse userDto = userService.getByEmail(user.getUsername());

        return new AuthResponse(token, "Bearer", expiresIn, userDto);
    }
}
