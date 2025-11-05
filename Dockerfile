# ===== build =====
FROM maven:3.9-eclipse-temurin-21-jammy AS build
WORKDIR /app

# кэш зависимостей (ускоряет последующие сборки)
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# исходники и сборка
COPY src ./src
RUN mvn -q clean package -DskipTests

# нормализуем имя артефакта -> /app/app.jar
RUN set -eux; \
    ls -l target; \
    JAR="$(ls target/*.jar | head -n1)"; \
    cp "$JAR" /app/app.jar; \
    ls -l /app/app.jar

# ===== runtime =====
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# итоговый jar
COPY --from=build /app/app.jar /app/app.jar

# статика для бота (бот читает их из /app)
COPY 2.jpg 3.png 4.png 7.png 8.png 10.png 11.png /app/

# каталог под SQLite
RUN mkdir -p /app/data

ENV TZ=Asia/Yekaterinburg
# EXPOSE 8080  # long-polling порты не требуются
ENTRYPOINT ["java","-jar","/app/app.jar"]
