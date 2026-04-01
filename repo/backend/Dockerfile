FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport"

COPY --from=builder /app/target/*.jar app.jar
VOLUME ["/app/uploads", "/app/backups"]

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
