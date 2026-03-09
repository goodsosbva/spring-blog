# CD 추가 및 배포 트러블슈팅 정리

## 1. 이번에 추가한 범위

이번 작업의 핵심은 기존 GitHub Actions 빌드 흐름에 **CD 배포 단계**를 붙인 것이옵니다.

기존에는 [`/.github/workflows/ci.yml`](/c:/Users/admin/Desktop/side/spring-blog/.github/workflows/ci.yml)에서 아래처럼 빌드까지만 수행하고 있었사옵니다.

```yaml
- uses: actions/checkout@v3
- uses: actions/setup-java@v3
- name: Grant execute permission for gradlew
  run: chmod +x gradlew
- name: Build with Gradle
  run: ./gradlew build
```

즉 기존 파이프라인은:

1. 소스 checkout
2. Java 설치
3. Gradle build

까지만 담당하고 있었사옵니다.

이번에는 여기에 **Elastic Beanstalk 자동 배포 단계**를 추가했사옵니다.

---

## 2. `ci.yml`에 추가한 CD 단계

현재 [`ci.yml`](/c:/Users/admin/Desktop/side/spring-blog/.github/workflows/ci.yml#L27)에서 새로 붙인 CD 관련 단계는 아래이옵니다.

```yaml
- name: Get current time
  uses: josStorer/get-current-time@v2.0.2
  id: current-time
  with:
    format: 'yyyy-MM-DDTHH-mm-ss'
    utcOffset: "+09:00"

- name: Set artifact
  run: echo "artifact=$(ls ./build/libs)" >> $GITHUB_ENV

- name: Beanstalk Deploy
  uses: einaregilsson/beanstalk-deploy@v20
  with:
    aws_access_key: ${{ secrets.AWS_ACCESS_KEY_ID }}
    aws_secret_key: ${{ secrets.AWS_SECRET_ACCESS_KEY_ID }}
    region: ap-northeast-2
    application_name: spring-blog
    environment_name: spring-blog-env
    version_label: github-actions-${{ steps.current-time.outputs.formattedTime }}
    deployment_package: ./blog/build/libs/${{ env.artifact }}
```

이 CD 흐름은 아래 의미를 갖사옵니다.

1. 현재 시각을 읽어 배포 버전 이름으로 사용
2. `build/libs` 안의 jar 파일 이름을 환경변수로 저장
3. 그 jar 파일을 AWS Elastic Beanstalk에 배포

즉 “빌드 성공 후 자동 배포”가 가능하도록 파이프라인을 확장한 것이옵니다.

---

## 3. CD 추가 후 발생한 문제와 해결 과정

CD는 기존에 없던 단계였기 때문에, 빌드가 통과한 뒤에야 배포 단계 관련 오류가 순차적으로 드러났사옵니다.

핵심은 아래 순서였사옵니다.

1. 외부 배포 액션 저장소 이름 오타
2. 배포 액션 입력 키 이름 오류
3. GitHub Secrets 참조 이름 불일치

즉 앞 문제를 해결해야 다음 문제가 드러나는 구조였사옵니다.

---

## 4. 1차 문제: 배포 액션 저장소를 찾지 못함

### 증상

아래 오류가 발생했사옵니다.

```text
Error: Unable to resolve action einaregilesson/beanstalk-deploy, repository not found
```

### 원인 판단

이 오류는 애플리케이션 코드 문제가 아니옵니다.  
GitHub Actions가 `uses:`로 참조한 외부 액션 저장소를 찾지 못했다는 뜻이옵니다.

문제 코드:

```yaml
uses: einaregilesson/beanstalk-deploy@v20
```

정상 코드:

```yaml
uses: einaregilsson/beanstalk-deploy@v20
```

즉 액션 소유자 이름 오타가 원인이었사옵니다.

### 해결

`einaregilesson`을 `einaregilsson`으로 수정했사옵니다.

---

## 5. 2차 문제: 배포 액션 입력 키 이름이 틀렸음

### 증상

다음에는 아래 경고와 에러가 나왔사옵니다.

```text
Warning: Unexpected input(s) 'aws_access_key_id', 'aws_secret_access_key', 'aws_region'
Error: Deployment failed: Region not specified!
```

### 원인 판단

이 메시지는 배포 액션이 해당 입력 키들을 받지 않는다는 뜻이옵니다.  
즉 값이 틀린 것이 아니라, **키 이름 자체가 액션 문법과 맞지 않았사옵니다**.

기존 코드는 아래였사옵니다.

```yaml
aws_access_key_id: ...
aws_secret_access_key: ...
aws_region: ...
```

그런데 `einaregilsson/beanstalk-deploy` 액션이 기대하는 입력 키는 아래였사옵니다.

- `aws_access_key`
- `aws_secret_key`
- `region`

즉 인과관계는 아래와 같았사옵니다.

1. 워크플로가 잘못된 입력 키를 사용
2. 액션이 해당 값을 무시
3. `region`도 액션에 전달되지 않음
4. 결국 `Region not specified!`로 실패

### 해결

입력 키를 아래처럼 액션 문법에 맞게 수정했사옵니다.

```yaml
aws_access_key: ${{ secrets.AWS_ACCESS_KEY_ID }}
aws_secret_key: ${{ secrets.AWS_SECRET_ACCESS_KEY_ID }}
region: ap-northeast-2
```

---

## 6. 3차 문제: GitHub Secrets 이름과 워크플로 참조 이름이 달랐음

### 증상

그 다음에는 아래 오류가 발생했사옵니다.

```text
Error: Deployment failed: AWS Secret Key not specified!
```

### 원인 판단

처음에는 시크릿이 없는 줄로 볼 수 있으나, 실제로는 시크릿은 등록되어 있었고 **이름이 달랐사옵니다**.

실제 저장소 Secrets:

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY_ID`

즉 현재 저장소는 일반적인 `AWS_SECRET_ACCESS_KEY`가 아니라 `AWS_SECRET_ACCESS_KEY_ID`라는 이름으로 시크릿을 등록해 둔 상태였사옵니다.

따라서 인과관계는 아래와 같았사옵니다.

1. 워크플로가 `AWS_SECRET_ACCESS_KEY`를 찾음
2. 저장소에는 `AWS_SECRET_ACCESS_KEY_ID`만 존재함
3. 참조 결과가 빈 값이 됨
4. 배포 액션이 secret key를 전달받지 못함
5. `AWS Secret Key not specified!`로 실패

### 해결

워크플로가 실제 저장소 시크릿 이름을 보도록 아래처럼 수정했사옵니다.

```yaml
aws_secret_key: ${{ secrets.AWS_SECRET_ACCESS_KEY_ID }}
```

---

## 7. 추가로 함께 정리한 부분

배포 파일 경로도 함께 조정했사옵니다.

현재 코드는 아래이옵니다.

```yaml
deployment_package: ./blog/build/libs/${{ env.artifact }}
```

이렇게 둔 이유는:

- `run:` 단계는 `working-directory: blog` 기준으로 수행되지만
- `uses:`로 실행되는 외부 액션은 저장소 루트 기준으로 파일을 볼 수 있기 때문이옵니다

즉 배포 액션이 jar 파일을 확실히 찾도록 루트 기준 경로를 명시한 것이옵니다.

---

## 8. 최종 정리

이번 CD 추가의 본질은 아래와 같사옵니다.

1. 기존 GitHub Actions는 빌드까지만 수행하고 있었음
2. 여기에 Elastic Beanstalk 자동 배포 단계를 추가했음
3. 배포 단계 추가 후 외부 액션 이름, 액션 입력 키, 시크릿 이름 문제들이 순차적으로 드러났음
4. 각 문제를 로그 기준으로 분리하여 수정함으로써 CD가 실제로 동작 가능한 구조로 정리했음

즉 이번 작업은 단순한 배포 명령 추가가 아니라, **GitHub Actions가 빌드 이후 실제 AWS 배포까지 수행하도록 CD 경로를 완성한 작업**이었사옵니다.

