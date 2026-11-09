# ==============================
# Build Stage
# ==============================
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

COPY src src

# GitHub Actions의 새 Docker 빌더는 Gradle 배포 파일을 매번 내려받는다.
# 기존 Wrapper 설정을 보존하고 컨테이너 빌드에서만 타임아웃과 재시도를 늘린다.
RUN printf '\nnetworkTimeout=60000\nretries=3\n' >> gradle/wrapper/gradle-wrapper.properties

RUN ./gradlew clean bootJar --no-daemon


# ==============================
# Runtime Stage
# ==============================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
