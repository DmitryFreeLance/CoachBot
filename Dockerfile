# ===== build =====
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

# В вашем pom.xml shade уже привязан к фазе package — этого достаточно
RUN mvn -q clean package -DskipTests

# ===== runtime =====
# Лучше не зависеть от docker.io/openjdk: берем AWS ECR (обычно доступнее)
FROM public.ecr.aws/amazoncorretto/amazoncorretto:21-al2023-headless
WORKDIR /app

# Берём именно FAT-jar из target и даём фиксированное имя
COPY --from=build /app/target/*-shaded.jar /app/app.jar

# Картинки, которые использует бот
COPY 2.jpg 3.png 4.png 7.png 8.png 10.png 11.png /app/

# Каталог под SQLite
RUN mkdir -p /app/data

ENV TZ=Asia/Yekaterinburg
ENTRYPOINT ["java","-jar","/app/app.jar"]