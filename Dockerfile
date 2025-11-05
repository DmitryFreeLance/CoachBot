# ===== build =====
FROM maven:3.9-eclipse-temurin-21-jammy AS build
WORKDIR /app

# кэш зависимостей
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# исходники
COPY src ./src

# Сборка жирного JAR'а с фиксированным именем target/app.jar
# shade:shade запускаем явно и задаём параметры плагина через -D:
#  -DfinalName=app                -> target/app.jar
#  -DshadedArtifactAttached=false -> чтобы НЕ было app-shaded.jar + обычного jar
RUN mvn -q clean package -DskipTests \
    shade:shade -DfinalName=app -DshadedArtifactAttached=false \
 && ls -l target

# ===== runtime =====
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# готовый jar
COPY --from=build /app/target/app.jar /app/app.jar

# картинки, которые использует бот
COPY 2.jpg 3.png 4.png 7.png 8.png 10.png 11.png /app/

# каталог под SQLite
RUN mkdir -p /app/data

ENV TZ=Asia/Yekaterinburg
ENTRYPOINT ["java","-jar","/app/app.jar"]