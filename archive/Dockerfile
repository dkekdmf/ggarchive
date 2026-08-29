# 1. 경량화된 Java 런타임 베이스 이미지
FROM eclipse-temurin:21-jre-alpine

# 2. 컨테이너 내부 작업 디렉터리 설정
WORKDIR /app

# 3. 로컬에서 빌드된 JAR 파일을 컨테이너 내부로 복사
ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# 4. 애플리케이션 포트 지정 (8080)
EXPOSE 8080

# 5. 컨테이너 시작 시 JAR 실행
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "app.jar"]