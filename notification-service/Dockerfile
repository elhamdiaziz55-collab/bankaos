FROM openjdk:22-jdk

WORKDIR /app

COPY target/notification-app.jar app.jar

EXPOSE 2004

ENTRYPOINT ["java", "-jar", "app.jar"]