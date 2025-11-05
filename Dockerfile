# ===== build =====
FROM maven:3.9-eclipse-temurin-21-jammy AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
# Сборка с фиксированным именем артефакта target/app.jar
RUN mvn -q -DskipTests -Djar.finalName=app package

# ===== runtime =====
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/app.jar /app/app.jar
COPY 2.jpg 3.png 4.png 7.png 8.png 10.png 11.png /app/
RUN mkdir -p /app/data

ENV TZ=Asia/Yekaterinburg
ENTRYPOINT ["java","-jar","/app/app.jar"]