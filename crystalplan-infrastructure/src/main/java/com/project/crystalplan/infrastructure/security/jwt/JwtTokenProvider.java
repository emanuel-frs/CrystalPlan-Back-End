package com.project.crystalplan.infrastructure.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.nio.charset.StandardCharsets;

@Component
public class JwtTokenProvider {

    // Chave fixa temporária para testar (32+ caracteres)
    private static final String FIXED_SECRET = "mySecretKeyForJWTTokenGeneration123456789012";

    @Value("${jwt.expiration:86400000}")
    private long validityInMilliseconds;

    private Key key;

    @PostConstruct
    protected void init() {
        // Usar chave fixa para eliminar problemas de configuração
        this.key = Keys.hmacShaKeyFor(FIXED_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(String userId, String email) {
        Claims claims = Jwts.claims().setSubject(userId);
        claims.put("email", email);

        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserId(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}