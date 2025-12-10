FROM eclipse-temurin:21-jdk

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy Maven files
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./

# Download dependencies (this layer will be cached)
RUN ./mvnw dependency:go-offline -B || true

# Copy source code
COPY src ./src

# Expose Spring Boot default port
EXPOSE 8080

# Run Spring Boot with DevTools for hot reloading
CMD ["./mvnw", "spring-boot:run"]
