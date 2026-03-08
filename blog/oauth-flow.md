# OAuth 로그인 흐름 — 부팅·요청별 호출자/원인/피호출자/내부로직

핵심 한 줄:

> `authorizationRequestRepository(...)` 는 **save를 호출하는 줄이 아니라**,  
> **"OAuth2 필터가 나중에 save/load/remove 할 때 쓸 저장소 객체를 등록하는 줄"** 이고,  
> 실제 `saveAuthorizationRequest(...)` 호출은 **Spring Security 내부 필터**가 요청 처리 중에 합니다.

---

## 0) 부팅 시점 (서버 켤 때 1번): "연결(set)"만 한다 — save는 절대 안 불림

| 구분 | 내용 |
|------|------|
| **트리거(원인)** | 서버 부팅(애플리케이션 시작) |
| **호출자** | Spring Boot / Spring Framework가 `@Configuration` 스캔 → `@Bean` 메서드 실행 |
| **피호출자 & 내부로직** | `WebOAuthSecurityConfig.java` 가 부팅 때 실행되며, "누가 누구를 쓸지"를 **연결**합니다. |

```java
// khs/blog/config/WebOAuthSecurityConfig.java
.oauth2Login(oauth -> oauth
    .authorizationEndpoint(authorization -> authorization
        .authorizationRequestRepository(oAuth2AuthorizationRequestBasedOnCookieRepository())
    )
    .successHandler(oAuth2SuccessHandler())
    .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserCustomService))
)
```

**여기서 중요한 분리:**

| 표현 | 의미 |
|------|------|
| `authorizationRequestRepository(...)` | ✅ **저장소를 등록하는 설정 메서드(Setter)** — ❌ `saveAuthorizationRequest` 를 호출하는 줄이 아님 |
| `oAuth2AuthorizationRequestBasedOnCookieRepository()` | ✅ **저장소 객체를 만들어서 반환하는 @Bean 메서드** |

```java
@Bean
public OAuth2AuthorizationRequestBasedOnCookieRepository oAuth2AuthorizationRequestBasedOnCookieRepository() {
    return new OAuth2AuthorizationRequestBasedOnCookieRepository();
}
```

**부팅 단계 결론:**  
"OAuth2 필터가 AuthorizationRequest를 저장/로드해야 할 때, **우리 `OAuth2AuthorizationRequestBasedOnCookieRepository` 객체를 쓰도록** 필터에 꽂아둔다."  
→ 여기까지는 **연결만** 했고, save/load/remove 는 한 번도 안 불립니다.

---

## 1) 요청 0: GET /login (로그인 페이지 렌더)

| 구분 | 내용 |
|------|------|
| **트리거** | 사용자가 `/login` 접속 |
| **호출자 → 피호출자** | 브라우저 → Tomcat 요청 수신 → `FilterChainProxy.doFilter(...)` (Spring Security) → 체인 안에서 우리 JWT 필터 실행(토큰 없으니 통과) |

```java
// khs/blog/config/TokenAuthenticationFilter.java
doFilterInternal(...) {
    token = getAccessToken(header);
    if (tokenProvider.validToken(token)) { ... }
    filterChain.doFilter(request, response); // 다음 단계로 넘김
}
```

- `/login` 은 설정에서 `permitAll()` 이라 막지 않음 → 계속 진행
- 필터들이 "응답을 직접 만들어 종료"하지 않았으므로 → **DispatcherServlet** 로 넘어감
- DispatcherServlet → 컨트롤러 호출

```java
// khs/blog/controller/UserViewController.java
@GetMapping("/login")
public String login() {
    return "oauthLogin";
}
```

템플릿의 링크가 **요청 1**을 트리거합니다:

```html
<!-- templates/oauthLogin.html -->
<a href="/oauth2/authorization/google">
    <img src="/img/google.png">
</a>
```

---

## 2) 요청 1: GET /oauth2/authorization/google (구글 로그인 "시작")

| 구분 | 내용 |
|------|------|
| **트리거(원인)** | 사용자가 위 링크 클릭 → 브라우저가 `GET /oauth2/authorization/google` 요청 |
| **호출자(누가 잡나) → 피호출자(누가 실행되나)** | Tomcat → `FilterChainProxy.doFilter(...)` → (먼저) `TokenAuthenticationFilter.doFilterInternal(...)` 실행 → 토큰 없어서 통과 → 그 다음 **Spring Security 내부 필터**가 URL 패턴을 보고 "내 담당"이라고 잡음: **`OAuth2AuthorizationRequestRedirectFilter`** 가 `/oauth2/authorization/{registrationId}` 패턴 처리 (google 이 registrationId) |

**내부 로직 (여기서 "호출부"가 나옴):**  
이 필터가 내부에서 실제로 하는 호출 흐름은 개념적으로 다음과 같습니다.

```java
// (Spring Security 내부 필터의 핵심 호출 흐름)
OAuth2AuthorizationRequest req = resolve(...); // state 포함 AuthorizationRequest 생성

authorizationRequestRepository.saveAuthorizationRequest(req, request, response);
// ✅ 바로 여기서 우리 저장소의 saveAuthorizationRequest(...) 가 호출됩니다.

redirectStrategy.sendRedirect(request, response, googleLoginUrl); // 302 응답 작성
return; // 응답을 이미 썼으니 DispatcherServlet 로 안 감
```

그래서 **우리 코드에서 진짜 호출되는 메서드**는 이겁니다:

```java
// khs/blog/config/oauth/OAuth2AuthorizationRequestBasedOnCookieRepository.java
@Override
public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
        HttpServletRequest request,
        HttpServletResponse response) {
    if (authorizationRequest == null) {
        removeAuthorizationRequestCookies(request, response);
        return;
    }
    CookieUtil.addCookie(response,
            OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
            CookieUtil.serialize(authorizationRequest),
            COOKIE_EXPIRE_SECONDS);
}
```

**왜 save 가 호출되나 (인과)?**  
OAuth2 는 외부(구글)로 나갔다가 콜백으로 돌아옵니다. 콜백이 위조인지 막으려면 **state** 를 "시작 요청에서 저장"해 둬야 합니다. 그래서 RedirectFilter 는 구글로 302 보내기 **직전에** 저장소의 `saveAuthorizationRequest(...)` 를 반드시 호출합니다. 그리고 302 응답을 써버리니, 이 요청은 여기서 끝나서 **컨트롤러로 안 갑니다.**

---

## 3) 요청 2: 구글 콜백 GET /login/oauth2/code/google?code=...&state=...

| 구분 | 내용 |
|------|------|
| **트리거** | 구글 로그인 완료 후, 구글이 `redirect_uri` 로 302 → 브라우저가 콜백 URL 호출 |
| **호출자 → 피호출자** | Tomcat → `FilterChainProxy.doFilter(...)` → `TokenAuthenticationFilter` 먼저 실행(대개 토큰 없음 → 통과) → 콜백 URL 을 보고 **`OAuth2LoginAuthenticationFilter`** 가 처리 시작 |

**내부 로직: remove/load 가 왜 호출되는지**  
콜백에서 제일 먼저 필요한 건 **"요청 1에서 저장한 AuthorizationRequest(state 포함)"** 입니다. 그래야 콜백 파라미터의 `state` 와 비교할 수 있기 때문입니다.

그래서 Spring Security 는 저장소에 이렇게 요청합니다:  
**"아까 저장한 AuthorizationRequest 꺼내줘 (= removeAuthorizationRequest)"**

우리 저장소는 `remove` 가 `load` 로 위임합니다:

```java
@Override
public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
        HttpServletResponse response) {
    return this.loadAuthorizationRequest(request);
}

@Override
public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
    return CookieUtil.deserialize(cookie, OAuth2AuthorizationRequest.class);
}
```

즉 런타임 실제 호출 순서:

1. `OAuth2LoginAuthenticationFilter`  
   → `repository.removeAuthorizationRequest(...)`  
   → 우리 `removeAuthorizationRequest(...)`  
   → 우리 `loadAuthorizationRequest(...)` (쿠키 역직렬화)
2. 그 다음에: **state 검증** → **code → token 교환** → **userinfo 조회**

**userinfo 단계에서 왜 `OAuth2UserCustomService.loadUser()` 가 호출되나?**  
부팅 때 `userService` 로 꽂아놨기 때문입니다.

```java
// khs/blog/config/oauth/OAuth2UserCustomService.java
@Override
public OAuth2User loadUser(OAuth2UserRequest userRequest) {
    OAuth2User user = super.loadUser(userRequest); // 구글 userinfo 호출
    saveOrUpdate(user); // 우리 DB 반영
    return user;
}
```

**성공하면 왜 `OAuth2SuccessHandler.onAuthenticationSuccess()` 가 호출되나?**  
마찬가지로 부팅 때 `successHandler` 를 연결해놨기 때문입니다. 콜백 인증이 "성공"으로 끝나는 순간, Spring Security 가 "성공 후처리"로 이 메서드를 호출합니다:

```java
// khs/blog/config/oauth/OAuth2SuccessHandler.java
@Override
public void onAuthenticationSuccess(HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication) throws IOException {
    // refresh/access 토큰 생성 + refresh DB 저장 + 쿠키 세팅
    // 그리고 마지막에 /articles?token=... 로 302 리다이렉트
    clearAuthenticationAttributes(request, response);
    getRedirectStrategy().sendRedirect(request, response, targetUrl);
}
```

그리고 **oauth2_auth_request 쿠키 삭제**는 여기서 합니다:

```java
private void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
    super.clearAuthenticationAttributes(request);
    authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
}
```

**정리:**

- **요청 1**에서 저장 (save)
- **요청 2**에서 꺼내서 검증 (remove → load)
- **요청 2 성공 직후** 임시 쿠키 삭제 (`removeAuthorizationRequestCookies`)

이 순서가 우리 코드의 진짜 흐름입니다.

---

## 4) 요청 3: 이후 /api/** 호출 — JWT 필터가 인증을 "만들어 넣고" 컨트롤러 호출

| 구분 | 내용 |
|------|------|
| **트리거** | 프론트가 API 호출할 때 `Authorization: Bearer <accessToken>` 을 붙여서 요청 |
| **호출자 → 피호출자** | Tomcat → `FilterChainProxy.doFilter(...)` → `TokenAuthenticationFilter.doFilterInternal(...)` 실행 → 헤더에서 토큰 추출 → `tokenProvider.validToken(token)` 검증 → `SecurityContextHolder` 에 `Authentication` 세팅 → 우리 설정에서 `/api/**` 는 `authenticated()` 라 인증 없으면 차단되는데, 이미 위에서 인증이 들어갔으니 통과 → **DispatcherServlet** → **BlogApiController** 호출 |

`Principal principal` 은 SecurityContext 의 인증에서 만들어져 주입됩니다.

```java
@PostMapping("/api/articles")
public ResponseEntity<Article> addArticle(@RequestBody AddArticleRequest request, Principal principal) {
    Article savedArticle = blogService.save(request, principal.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(savedArticle);
}
```

---

## 총 흐름 한 줄 버전

| 단계 | 내용 |
|------|------|
| **부팅 때** | `authorizationRequestRepository(저장소객체)` 로 **연결만** 함 (= set) |
| **요청 1** (`/oauth2/authorization/google`) | Spring Security RedirectFilter 가 `repo.saveAuthorizationRequest(...)` 호출 → 쿠키 저장 → 302 구글로 이동 |
| **요청 2** (콜백 `/login/oauth2/code/google`) | Spring Security LoginFilter 가 `repo.removeAuthorizationRequest(...)` 호출 (→ 우리 구현은 load 로 위임) → state 검증 → userinfo → 성공 시 `OAuth2SuccessHandler.onAuthenticationSuccess(...)` 호출 → JWT 발급/쿠키/302 |
| **요청 3** (`/api/**`) | `TokenAuthenticationFilter` 가 JWT 검증해서 SecurityContext 세팅 → 컨트롤러 호출 |
