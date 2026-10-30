FROM openjdk:22-jdk

WORKDIR /app

COPY target/account-app.jar app.jar

EXPOSE 2000

ENTRYPOINT ["java", "-jar", "app.jar"]