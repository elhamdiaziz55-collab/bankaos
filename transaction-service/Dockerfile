FROM openjdk:22-jdk

WORKDIR /app

COPY target/transaction-app.jar app.jar

EXPOSE 2001

ENTRYPOINT ["java", "-jar", "app.jar"]