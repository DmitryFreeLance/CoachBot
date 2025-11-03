# Вариант А: запускаем уже собранный JAR
FROM eclipse-temurin:21-jre-alpine

# Рабочая папка приложения
WORKDIR /app

# Кладём архив и изображения
COPY coachbot.jar /app/app.jar
COPY 2.jpg 3.png 4.png /app/

# Порт не обязателен (Telegram Long Polling), но оставим на будущее
EXPOSE 8080

# Включим предсказуемую TZ внутри контейнера (можно переопределить через ENV)
ENV TZ=Asia/Yekaterinburg

RUN mkdir -p /app/data

# Запуск
ENTRYPOINT ["java","-jar","/app/app.jar"]
