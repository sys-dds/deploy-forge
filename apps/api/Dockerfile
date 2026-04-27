FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x ./mvnw && ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/target/deploy-forge-api-*.jar /app/deploy-forge-api.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/deploy-forge-api.jar"]
