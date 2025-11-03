# ===== build =====
FROM maven:3.9-amazoncorretto-21 AS build

WORKDIR /app
COPY pom.xml .
# ускорит последующие сборки (кэш зависимостей)
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# ===== runtime =====
FROM openjdk:21-jdk-slim

WORKDIR /app
# забираем "толстый" jar, который делает maven-shade-plugin
COPY --from=build /app/target/*-shaded.jar /app/app.jar

# картинки нужны в рабочей папке /app
COPY 2.jpg 3.png 4.png /app/

# SQLite сохраняем в /app/data (смонтируем как volume)
RUN mkdir -p /app/data

ENV TZ=Asia/Yekaterinburg
# EXPOSE не обязателен для long-polling, можно пропустить
# EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]