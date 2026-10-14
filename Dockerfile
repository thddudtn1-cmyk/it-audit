# Render 등 컨테이너 호스팅용 이미지. 온프레미스 운영은 CLAUDE.md 대로 실행 JAR + systemd 이며 이 파일과 무관하다.

# 1단계: 빌드 (테스트는 CI·로컬에서 돌리므로 이미지 빌드에서는 생략해 시간을 줄인다)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# 2단계: 실행 (JRE 만)
FROM eclipse-temurin:17-jre
RUN useradd --system --create-home --uid 10001 app
WORKDIR /app
COPY --from=build /build/target/it-audit-*.jar app.jar
# 영구 디스크를 /var/data 에 마운트한다. 마운트 전에도 쓸 수 있게 소유권을 맞춰 둔다.
RUN mkdir -p /var/data && chown -R app:app /var/data /app
USER app
ENV APP_DATA_DIR=/var/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
