# ===== build =====
FROM maven:3.9-eclipse-temurin-21-jammy AS build
WORKDIR /app

# кэш зависимостей
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# исходники и сборка
COPY src ./src
RUN mvn -q clean package -DskipTests

# НОРМАЛИЗУЕМ: кладём ИМЕННО shaded-джар в /app/app.jar
RUN set -eux; \
    ls -l target; \
    JAR="$(ls target/*-shaded.jar | head -n1)"; \
    cp "$JAR" /app/app.jar; \
    echo "Using shaded JAR: $JAR"; \
    ls -l /app/app.jar

# ===== runtime =====
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# готовый jar из build-стадии
COPY --from=build /app/app.jar /app/app.jar

# картинки, которые использует код (у вас фигурируют 2.jpg,3.png,4.png,7.png,8.png,10.png,11.png)
COPY 2.jpg 3.png 4.png 7.png 8.png 10.png 11.png /app/

# каталог под SQLite
RUN mkdir -p /app/data

ENV TZ=Asia/Yekaterinburg
ENTRYPOINT ["java","-jar","/app/app.jar"]