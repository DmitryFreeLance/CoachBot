# ===== build =====
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
# у вас shade-привязан к фазе package → жирный jar появится в target/
RUN mvn -q clean package -DskipTests
RUN ls -l target

# ===== runtime =====
FROM public.ecr.aws/amazoncorretto/amazoncorretto:21-al2023-headless
WORKDIR /app

# забираем ВСЕ .jar из target (и shaded, и обычный — что будет)
COPY --from=build /app/target/*.jar /app/

# картинки, которые использует бот
COPY 2.jpg 3.png 4.png 7.png 8.png 10.png 11.png /app/

RUN mkdir -p /app/data
ENV TZ=Asia/Yekaterinburg

# Стартуем тем jar'ом, который есть: сперва *-shaded.jar, иначе любой *.jar
ENTRYPOINT ["sh","-lc","set -e; JAR=\"$(ls /app/*-shaded.jar 2>/dev/null || ls /app/*.jar | head -n1)\"; echo \"Starting $JAR\"; exec java -jar \"$JAR\""]