FROM openjdk:22-jdk

WORKDIR /app

COPY target/payment-app.jar app.jar

EXPOSE 2003

ENTRYPOINT ["java", "-jar", "app.jar"]