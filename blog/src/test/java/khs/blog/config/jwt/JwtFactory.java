package khs.blog.config.jwt;

import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Getter
public class JwtFactory {

    private String subject = "test@email.com";
    private Date issuedAt = new Date();
    private Date expiration = new Date(new Date().getTime() + Duration.ofDays(14).toMillis());
    private Map<String, Object> claims = new HashMap<>();

    @Builder
    public JwtFactory(String subject, Date issuedAt, Date expiration, Map<String, Object> claims) {
        if (subject != null) this.subject = subject;
        if (issuedAt != null) this.issuedAt = issuedAt;
        if (expiration != null) this.expiration = expiration;
        if (claims != null) this.claims = new HashMap<>(claims);
    }

    // ✅ 1) static 이어야 함 (테스트가 JwtFactory.withDefaultValues()로 호출)
    public static JwtFactory withDefaultValues() {
        return JwtFactory.builder().build();
    }

    // ✅ 2) 인자를 받는 createToken이 있어야 함 (테스트가 createToken(jwtProperties)로 호출)
    public String createToken(JwtProperties jwtProperties) {
        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .setSubject(subject)
                .addClaims(claims)
                .signWith(SignatureAlgorithm.HS256, jwtProperties.getSecretKey())
                .compact();
    }
}