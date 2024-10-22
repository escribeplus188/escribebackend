FROM maven:latest AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn package

FROM openjdk:17-alpine
COPY --from=build /app/target/app-0.0.1-SNAPSHOT.war /app/app.war
EXPOSE 8080
CMD ["java", "-jar", "/app/app.war"]