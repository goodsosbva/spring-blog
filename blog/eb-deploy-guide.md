# Spring Boot + AWS Elastic Beanstalk 배포 실전기

> RDS, OAuth, DB 툴, 트러블슈팅 전체 기록

로컬에서 잘 돌던 Spring Boot 프로젝트를 AWS Elastic Beanstalk(EB)에 배포하면서 겪은 문제들을,  
**「어디서 막히는지 → 왜 막히는지 → 무엇을 어떻게 고쳤는지」**를 실제 순서대로 정리한 기록입니다.

---

## 결론 요약

- **EB Health OK가 떠도 기능이 정상이라는 보장은 없다.**
- **OAuth 500**은 OAuth 자체가 아니라 **콜백 이후(특히 DB 저장)**에서 터지는 경우가 많다.
- 이번 이슈의 **진짜 원인은 "DB 스키마(테이블) 없음"**이었다.

---

## 1. 전체 배포 구조

| 구분 | 내용 |
|------|------|
| **로컬** | Spring Boot + (로컬 DB 혹은 RDS 접속) |
| **배포** | AWS Elastic Beanstalk (Corretto 17) + AWS RDS (MySQL) |
| **기능** | Google OAuth 로그인, 로그인 성공 시 사용자/토큰 저장, 글 목록(`/articles`) 등 |

---

## 2. 배포 기본 흐름 (정석 순서)

### (1) 로컬에서 JAR 빌드

Gradle로 빌드하면 `build/libs`에 JAR가 두 개 생긴다.

- `blog-0.0.1-SNAPSHOT.jar` ← **이걸 EB에 올린다**
- `blog-0.0.1-SNAPSHOT-plain.jar` ← 올리면 안 됨

**EB에 올릴 것은 plain이 아닌 큰 JAR(실행 가능한 Spring Boot fat JAR)**이다.  
`plain.jar`는 의존성이 빠진 “그냥 JAR”라 단독 실행이 안 되거나 EB에서 실패하기 쉽다.

### (2) Elastic Beanstalk 업로드 & 배포

EB 환경 화면 우측 상단의 **「Upload and deploy」** 버튼으로 JAR를 올린다.

### (3) EB 환경변수(Environment properties) 세팅

**EB → Configuration → Software → Environment properties**에 다음 값을 넣는다.

| 분류 | 키 | 예시 값 |
|------|----|---------|
| DB | `SPRING_DATASOURCE_URL` | `jdbc:mysql://<rds-endpoint>:3306/<db>?...` |
| DB | `SPRING_DATASOURCE_USERNAME` | `khs` |
| DB | `SPRING_DATASOURCE_PASSWORD` | `****` |
| OAuth | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | `...` |
| OAuth | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | `...` |
| (필요시) | scope 설정 | — |
| 포트 | `SERVER_PORT` | `5000` (환경에 따라) |

---

## 3. 도메인 접속 시 Whitelabel 404

EB 도메인으로 들어갔더니 **루트(`/`)에서 Whitelabel 404**가 떴다.

- **의미**: 서버가 안 뜬 게 아니라, **루트(`/`) 매핑이 없으면** 그냥 404가 뜨는 경우가 많다.
- **확인**: `/login`, `/articles`, `/api/...` 등 **실제로 존재하는 엔드포인트**로 접속해 본다.
- **원하면**: `/` 매핑을 만들어 redirect 시키면 된다.

---

## 4. 구글 로그인 후 `/login/oauth2/code/google`에서 500

- `/login` 화면은 정상적으로 떴다.
- **「Sign up with Google」** 클릭 → 구글 인증 후 돌아오는 콜백 **`/login/oauth2/code/google`**에서 **Whitelabel 500** 발생.

**의미:**

- 화면이 뜬다 → 앱 자체는 떠 있다.
- **500** → OAuth 콜백 처리 로직 **내부**에서 서버 예외가 난 것이다.

---

## 5. 핵심: EB 로그에서 원인 확정

**EB → Logs → Request logs / Full logs**에서 스택트레이스를 확인했다.

**나온 메시지:**

```
Table 'blog.users' doesn't exist
Table 'blog.article' doesn't exist
```

- **에러 위치**: `OAuth2UserCustomService.saveOrUpdate(...)`
- **해석**: 구글 OAuth 자체가 문제가 아니라, OAuth 성공 후 **「유저 저장/조회」** 단계에서 **DB 테이블이 없어서** 터진 것.

**결론: OAuth 500의 원인은 "DB 스키마 없음"이었다.**

---

## 6. RDS/DB 트러블슈팅 전체 흐름

### (1) RDS 엔드포인트 찾기

- EB Events에 「Created RDS database named …」가 찍혀 있음.
- **RDS 콘솔 → Connectivity & security** 탭에서 **Endpoint** 확인.

### (2) 로컬에서 RDS 접속 안 됨: Connection timed out

`timed out`은 **인증 이전에** 네트워크 레벨에서 DB까지 못 닿는 상태다.

**체크리스트:**

- [ ] RDS **Publicly accessible** 여부
- [ ] RDS **보안 그룹 Inbound** 3306 열림 여부
- [ ] 「내 IP」를 **사설 IP(172.x, 192.168.x)**로 착각하지 않았는지

사설 IP(예: `ipconfig`에 나온 172.30.x.x)는 인터넷에서 의미가 없다.  
보안 그룹은 **공인 IP(/32)**를 열어야 한다.

### (3) 네트워크 바뀌면 또 안 됨

와이파이/회사망을 바꾸면 **공인 IP가 바뀌어서**, 보안 그룹에 등록된 /32가 전부 무효가 된다.  
이때는 SG Inbound에서 **Source**를 다시 **「My IP」**로 지정해야 한다.

### (4) timed out 해결 후 1045 Access denied

`TcpTestSucceeded=True`로 네트워크가 열리면, 그다음은 **계정/비밀번호** 문제로 1045가 날 수 있다.

**대응:**

- RDS **Modify**에서 **Master password** 리셋
- IntelliJ/DB 툴 비밀번호도 동일하게 업데이트
- **EB의 `SPRING_DATASOURCE_PASSWORD`**도 같이 변경

### (5) IDE에서 mysql DB 접근 에러

DB 툴이 메타데이터 조회하느라 `mysql`(시스템 DB)을 건드리다가  
`Access denied to database 'mysql'` 같은 메시지를 낼 수 있다. 앱 DB(`ebdb`/`blog`)와 별개다.

**대응:**

- **기본 DB**를 `ebdb` 또는 `blog`로 지정
- **Schemas**에서 `mysql`/`sys`/`performance_schema` 등을 제외하고 **사용 DB만** 선택

---

## 7. blog DB 만들고 테이블 생성

로그가 `blog.users` / `blog.article`을 찾고 있었으므로, 해결책은 둘 중 하나다.

| 선택 | 내용 |
|------|------|
| **A** | 앱이 바라보는 DB를 `ebdb`로 통일 → `SPRING_DATASOURCE_URL`을 `/ebdb`로 하고, `ebdb`에 테이블 생성 |
| **B** | **blog DB를 만들고** 거기에 테이블 생성 (이번에 진행한 방식) |

**B 예시:**

```sql
CREATE DATABASE blog;
USE blog;
-- users, article, refresh_token 등 필요한 테이블을 blog에 생성
```

배포 중간(환경 업데이트 중)에 접속하면 404/500이 왔다 갔다 할 수 있으니,  
**Events에서 update completed + Health OK 이후**에 기능 테스트하는 게 정확하다.

---

## 8. Auto-Commit OFF / SAVEPOINT does not exist 경고

DDL(`CREATE DATABASE`/`TABLE`)은 MySQL에서 **암묵적 COMMIT**이 발생할 수 있고,  
그때 DB 툴이 잡아둔 SAVEPOINT가 사라지면서 다음 경고가 난다.

```
SAVEPOINT … does not exist
```

- **의미**: 작업이 실패한 게 아니라 **「툴의 트랜잭션 관리 경고」**인 경우가 많다.
- **확인**: `SHOW DATABASES`, `SHOW TABLES`로 실제 생성 여부를 확인하는 게 정답이다.

---

## 9. 최종 체크리스트

### 배포

- [ ] 업로드한 JAR가 **plain.jar가 아닌가?**
- [ ] **EB Health OK**인가?
- [ ] **EB Logs**에서 Spring이 정상 기동했는가?

### DB

- [ ] `SPRING_DATASOURCE_URL`의 **DB명**이 실제 테이블이 있는 DB와 같은가?
- [ ] `SHOW TABLES`에서 **users / article / refresh_token** 등이 있는가?
- [ ] 비밀번호 바꿨으면 **EB 환경변수**도 같이 바꿨는가?

### OAuth

- [ ] Google Console **Redirect URI**에  
  `https://<eb-domain>/login/oauth2/code/google` 를 **정확히** 넣었는가?
- [ ] **`https://`** 스킴을 빠뜨리지 않았는가?
- [ ] **client-id / client-secret**이 EB 환경변수로 들어가 있는가?

---

## 10. 결론

이번 배포에서의 핵심 교훈은 **「로그가 답」**이었다.  
처음엔 OAuth 문제처럼 보였지만, 실제 원인은 **콜백 이후 DB 작업**에서 터진 **「테이블 없음」**이었다.

정리하면:

1. **배포가 떴다고 끝난 게 아니다.**
2. **DB 스키마가 맞아야 기능이 돈다.**
3. **OAuth 500은 대부분 콜백 이후의 저장/조회 로직**에서 터진다.
4. **EB Logs를 보면 원인이 1초 만에 나온다.**
