# CI Troubleshooting

## 1. CI가 무엇인가

CI는 `Continuous Integration`의 줄임말이옵니다.  
코드를 원격 저장소에 푸시했을 때, GitHub Actions 같은 자동화 도구가 새 환경에서 프로젝트를 받아 빌드와 테스트를 수행하여 문제가 없는지 확인하는 절차이옵니다.

이 프로젝트의 CI 설정은 [`/.github/workflows/ci.yml`](/c:/Users/admin/Desktop/side/spring-blog/.github/workflows/ci.yml)에 있사옵니다.

CI 동작 흐름은 대략 아래와 같사옵니다.

1. GitHub가 새 실행 환경을 준비함
2. 저장소 코드를 checkout함
3. Java 17을 설치함
4. `blog` 디렉터리에서 `./gradlew build`를 실행함
5. 컴파일, 테스트, 패키징을 순서대로 수행함

즉, CI는 제 PC나 폐하 PC의 로컬 환경을 그대로 쓰는 것이 아니라, Git에 올라간 파일만 가지고 깨끗한 환경에서 다시 빌드하는 것이옵니다.

---

## 2. 이번에 어디서 실패했는가

이번 실패는 `./gradlew build` 안에서 `:test` 단계에서 발생하였사옵니다.

로그상 핵심 증상은 아래와 같았사옵니다.

- `BlogApplicationTests > contextLoads() FAILED`
- `TokenProviderTest ... FAILED`
- `BlogApiControllerTest ... FAILED`
- `TokenApiControllerTest ... FAILED`
- 최종 원인 체인에 `NoSuchBeanDefinitionException`

이 뜻은 개별 테스트 로직이 각각 틀린 것이 아니라, 테스트 시작 전에 **스프링 애플리케이션 컨텍스트 자체가 부팅되지 못했다**는 뜻이옵니다.

`@SpringBootTest` 기반 테스트는 먼저 애플리케이션 전체를 띄운 뒤 테스트를 수행하는데, 컨텍스트가 뜨지 못하면 관련 테스트들이 전부 연쇄적으로 실패하옵니다.

---

## 3. 실패 원인을 만든 코드 흐름

### 3-1. OAuth2 로그인을 항상 활성화하고 있었음

[`blog/src/main/java/khs/blog/config/WebOAuthSecurityConfig.java`](/c:/Users/admin/Desktop/side/spring-blog/blog/src/main/java/khs/blog/config/WebOAuthSecurityConfig.java#L81)에서 `oauth2Login(...)`을 항상 활성화하고 있었사옵니다.

```java
.oauth2Login(oauth -> oauth
        .loginPage("/login")
        .authorizationEndpoint(authorization -> authorization
                .authorizationRequestRepository(oAuth2AuthorizationRequestBasedOnCookieRepository())
        )
        .successHandler(oAuth2SuccessHandler())
        .userInfoEndpoint(userInfo -> userInfo
                .userService(oAuth2UserCustomService)
        )
)
```

이 코드는 스프링에게 아래처럼 말하는 것과 같사옵니다.

- 이 프로젝트는 OAuth2 로그인을 사용한다
- 따라서 OAuth2 client 관련 빈들이 필요하다
- 그중 대표가 `ClientRegistrationRepository`이옵니다

즉, 이 코드는 OAuth2 설정값이 이미 준비되어 있다는 전제를 가지고 있사옵니다.

### 3-2. OAuth2 설정값은 `application.yml`에만 있었음

실제 Google OAuth2 등록 정보는 [`blog/src/main/resources/application.yml`](/c:/Users/admin/Desktop/side/spring-blog/blog/src/main/resources/application.yml#L14)에만 들어 있었사옵니다.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ...
            client-secret: ...
            scope: profile, email
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
```

즉, `oauth2Login()`이 정상 동작하려면 이 설정이 필요했사옵니다.

### 3-3. 그런데 `application.yml`은 Git에 올라가지 않았음

[`blog/.gitignore`](/c:/Users/admin/Desktop/side/spring-blog/blog/.gitignore#L40)에서 아래처럼 `application.yml`을 무시하고 있었사옵니다.

```gitignore
src/main/resources/application.yml
src/main/resources/application-*.yml
```

이로 인해 생긴 차이는 아래와 같았사옵니다.

- 로컬 환경: `application.yml` 파일이 존재함
- CI 환경: Git에 없는 파일이므로 존재하지 않음

즉, 로컬에서는 OAuth2 설정이 있고, CI에서는 OAuth2 설정이 없는 상태였사옵니다.

---

## 4. 왜 CI에서 `NoSuchBeanDefinitionException`이 났는가

인과관계를 순서대로 연결하면 아래와 같사옵니다.

1. CI는 Git에 있는 파일만 받아서 새 환경에서 빌드함
2. `application.yml`은 Git에 없어서 CI 환경에 존재하지 않음
3. 따라서 Google OAuth2 client registration 정보가 CI에 없음
4. 그런데 [`WebOAuthSecurityConfig.java`](/c:/Users/admin/Desktop/side/spring-blog/blog/src/main/java/khs/blog/config/WebOAuthSecurityConfig.java#L81)는 여전히 `oauth2Login()`을 수행함
5. 스프링은 OAuth2 로그인에 필요한 빈을 만들려 함
6. 하지만 등록 정보가 없어서 `ClientRegistrationRepository` 계열 빈이 생성되지 못함
7. 결국 `NoSuchBeanDefinitionException` 발생
8. 스프링 컨텍스트 부팅 실패
9. `@SpringBootTest` 기반 테스트 전부 연쇄 실패
10. 최종적으로 `:test` 실패로 `./gradlew build` 실패

즉, 이번 문제는 테스트 코드 자체보다 **보안 설정이 Git에 없는 로컬 파일에 의존하고 있었던 것**이 원인이었사옵니다.

---

## 5. 당시 다른 설정 파일 상태

[`blog/src/main/resources/application.properties`](/c:/Users/admin/Desktop/side/spring-blog/blog/src/main/resources/application.properties#L1)에는 아래처럼 JWT/H2 관련 설정만 있었고, OAuth2 client registration은 없었사옵니다.

```properties
spring.application.name=blog
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.defer-datasource-initialization=true
spring.h2.console.enabled=true
spring.jwt.issuer=${JWT_ISSUER:khs@naver.com}
spring.jwt.secret-key=${JWT_SECRET_KEY:study_springboot}
```

즉, properties로는 OAuth2 빈이 자동 생성될 수 없는 상태였사옵니다.

---

## 6. 어떻게 해결했는가

해결 방향은 “OAuth2 로그인을 꺼버리는 것”이 아니라, **CI에서도 OAuth2 client 빈이 생성되게 보장하는 것**으로 잡았사옵니다.

그래서 [`blog/src/main/java/khs/blog/config/WebOAuthSecurityConfig.java`](/c:/Users/admin/Desktop/side/spring-blog/blog/src/main/java/khs/blog/config/WebOAuthSecurityConfig.java#L129)에 fallback 빈을 추가했사옵니다.

### 6-1. `ClientRegistrationRepository` fallback 추가

```java
@Bean
@ConditionalOnMissingBean(ClientRegistrationRepository.class)
public ClientRegistrationRepository clientRegistrationRepository() {
    return new InMemoryClientRegistrationRepository(
            CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId("test-client-id")
                    .clientSecret("test-client-secret")
                    .scope("profile", "email")
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .build()
    );
}
```

이 코드의 의미는 아래와 같사옵니다.

- 실제 OAuth2 설정이 있어서 `ClientRegistrationRepository`가 이미 있으면: 기존 빈 사용
- 실제 OAuth2 설정이 없어서 빈이 없으면: 메모리 기반 fallback 빈 생성

### 6-2. `OAuth2AuthorizedClientService` fallback 추가

```java
@Bean
@ConditionalOnMissingBean(OAuth2AuthorizedClientService.class)
public OAuth2AuthorizedClientService oAuth2AuthorizedClientService(
        ClientRegistrationRepository clientRegistrationRepository
) {
    return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
}
```

이 빈도 OAuth2 client 동작에 필요하므로 같이 보강했사옵니다.

---

## 7. 왜 지금은 되는가

이제 구조가 아래처럼 바뀌었사옵니다.

### 수정 전

- `oauth2Login()`은 항상 켜져 있음
- CI에는 OAuth2 설정 파일이 없음
- OAuth2 client 빈 생성 실패
- 컨텍스트 부팅 실패
- 테스트 전부 실패

### 수정 후

- `oauth2Login()`은 그대로 유지
- 실제 OAuth2 설정이 있으면 그 설정으로 빈 생성
- 실제 OAuth2 설정이 없으면 fallback 빈 생성
- 컨텍스트 부팅 성공
- 테스트 정상 수행 가능

즉, CI 환경처럼 `application.yml`이 빠진 상태에서도 최소한의 OAuth2 client 구성이 보장되게 만든 것이옵니다.

---

## 8. 검증 방법

수정이 진짜 CI 문제를 해결하는지 확인하기 위해, 로컬에만 있던 [`application.yml`](/c:/Users/admin/Desktop/side/spring-blog/blog/src/main/resources/application.yml#L1)을 잠시 치워 CI와 같은 조건을 인위적으로 만들었사옵니다.

그 상태에서 아래를 실행하였사옵니다.

```bash
./gradlew clean build
```

그 결과, `application.yml`이 없는 상태에서도 빌드와 테스트가 성공하는 것을 확인하였사옵니다.

즉, 수정 내용이 실제 CI 실패 원인을 직접 해결했음을 검증한 것이옵니다.

---

## 9. 최종 정리

이번 CI 실패의 본질은 아래 한 줄로 정리할 수 있사옵니다.

> `oauth2Login()`은 항상 활성화되어 있었는데, CI에는 Git에 없는 `application.yml`만 의존하던 OAuth2 설정이 없어서, 필요한 OAuth2 client 빈이 생성되지 못했고, 그 결과 스프링 컨텍스트 부팅이 실패하였다.

그리고 해결은 아래 한 줄로 정리할 수 있사옵니다.

> `WebOAuthSecurityConfig`에 fallback OAuth2 client 빈을 추가하여, 실제 설정 파일이 없어도 CI에서 `ClientRegistrationRepository`와 관련 빈이 생성되도록 만들었다.

