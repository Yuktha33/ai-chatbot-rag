package com.example.ai_chatbot.filter;

import com.example.ai_chatbot.entity.User;
import com.example.ai_chatbot.repository.UserRepository;
import com.example.ai_chatbot.service.JwtService;
import com.example.ai_chatbot.CustomUserDetail;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtFilter(
            JwtService jwtService,
            UserRepository userRepository) {

        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        String username = null;
        String token = null;

        // 1. Get JWT from Authorization header
        if (authHeader != null &&
                authHeader.startsWith("Bearer ")) {

            token = authHeader.substring(7);

            try { username = jwtService.extractUsername(token); } catch (Exception e) { username = null; }
        }
        if (username != null &&
                SecurityContextHolder
                        .getContext()
                        .getAuthentication() == null) {

            Optional<User> account =
                    userRepository.findByUsername(username);

            if (account.isPresent()) {
                CustomUserDetail accountPrincipal =
                        new CustomUserDetail(account.get());
                boolean valid; try { valid = jwtService.validateToken(token, accountPrincipal); } catch (Exception e) { valid = false; }
                if (valid) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    accountPrincipal,
                                    null,
                                    accountPrincipal.getAuthorities()
                            );
                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authToken);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}