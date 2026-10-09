package com.example.ai_chatbot.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    public SecretKey getKey() {

        byte[] keys = Decoders.BASE64.decode(secret);

        return Keys.hmacShaKeyFor(keys);
    }

    public String generateToken(String username) {

        Map<String, Object> claims = new HashMap<>();

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + 1000 * 60 * 60
                        )
                )
                .signWith(getKey())
                .compact();
    }

    public Claims extractClaims(String token) {

        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {

        return extractClaims(token)
                .getSubject();
    }

    public Date extractExpiration(String token) {

        return extractClaims(token)
                .getExpiration();
    }

    public boolean isExpired(String token) {

        return extractExpiration(token)
                .before(new Date());
    }

    public boolean validateToken(
            String token,
            UserDetails userDetails) {

        String username = userDetails.getUsername();

        return extractUsername(token)
                .equals(username)
                && !isExpired(token);
    }
}