FROM openjdk:22-jdk

WORKDIR /app

COPY target/fraud-detection-app.jar app.jar

EXPOSE 2002

ENTRYPOINT ["java", "-jar", "app.jar"]