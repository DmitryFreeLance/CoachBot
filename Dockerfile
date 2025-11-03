# ===== build =====
FROM maven:3.9-amazoncorretto-21 AS build

WORKDIR /app
COPY pom.xml .
# кэш зависимостей (ускоряет последующие сборки)
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# НОРМАЛИЗУЕМ имя артефакта -> /app/app.jar
# Возьмём первый jar из target/ (подходит и для coachbot-1.0.0.jar, и для *-shaded.jar)
RUN set -eux; \
    ls -l target; \
    JAR="$(ls target/*.jar | head -n1)"; \
    cp "$JAR" /app/app.jar; \
    ls -l /app/app.jar

# ===== runtime =====
FROM openjdk:21-jdk-slim

WORKDIR /app

# кладём итоговый jar
COPY --from=build /app/app.jar /app/app.jar

# кладём изображения рядом (бот читает "3.png", "4.png", "2.jpg" из /app)
COPY 2.jpg 3.png 4.png /app/

# каталог под SQLite
RUN mkdir -p /app/data

ENV TZ=Asia/Yekaterinburg
# EXPOSE 8080  # long-polling, порт не обязателен

ENTRYPOINT ["java","-jar","/app/app.jar"]