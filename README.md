# Payment Portal API

[![Build Status](https://github.com/dopaminelite/payment-portal/workflows/CI%2FCD%20Pipeline/badge.svg)](https://github.com/dopaminelite/payment-portal/actions)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.12-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

A robust microservice for managing payment submission portals and student payment submissions. Built with Spring Boot 3.4, PostgreSQL, and Liquibase for enterprise-grade reliability.

## 📋 Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Configuration](#configuration)
- [Database](#database)
- [Security](#security)
- [Testing](#testing)
- [Deployment](#deployment)
- [Monitoring](#monitoring)
- [Contributing](#contributing)

## ✨ Features

- **Payment Portal Management**: Create, update, and manage payment portals with flexible visibility controls
- **Student Submissions**: Handle payment submissions with file uploads and status tracking
- **Data Export**: Export submission data sheets in CSV and Excel formats
- **RESTful API**: Clean, versioned API endpoints (`/api/v1/*`)
- **Security**: Spring Security with role-based access control (ADMIN, STAFF, STUDENT)
- **Database Migrations**: Liquibase for version-controlled schema management
- **API Documentation**: Interactive Swagger UI for API exploration
- **Health Monitoring**: Actuator endpoints with Prometheus metrics
- **Audit Trail**: Automatic tracking of creation and modification timestamps
- **Pagination**: Efficient data retrieval with customizable pagination
- **Docker Support**: Complete containerization with PostgreSQL and pgAdmin

## 🛠 Tech Stack

- **Framework**: Spring Boot 3.4.12
- **Language**: Java 21
- **Database**: PostgreSQL 16
- **ORM**: Hibernate/JPA
- **Migrations**: Liquibase
- **Security**: Spring Security 6
- **API Docs**: SpringDoc OpenAPI 3
- **Testing**: JUnit 5, Mockito, Spring Test
- **Monitoring**: Spring Boot Actuator, Micrometer, Prometheus
- **Build Tool**: Maven 3.9+
- **Containerization**: Docker, Docker Compose

## 📦 Prerequisites

- Java 21 or higher ([Download](https://adoptium.net/))
- Maven 3.9+ ([Download](https://maven.apache.org/download.cgi))
- Docker & Docker Compose ([Download](https://www.docker.com/get-started))
- PostgreSQL 16 (optional, Docker preferred)

## 🚀 Getting Started

### Using Docker (Recommended)

1. **Clone the repository**
   ```bash
   git clone https://github.com/dopaminelite/payment-portal.git
   cd payment-portal
   ```

2. **Create environment file**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Start all services**
   ```bash
   docker compose up -d
   ```

4. **Access the application**
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - pgAdmin: http://localhost:5050 (admin@admin.com / admin123)
   - Health Check: http://localhost:8080/actuator/health

### Local Development

1. **Start PostgreSQL**
   ```bash
   docker compose up -d postgres
   ```

2. **Set environment variables**
   ```bash
   export DATABASE_URL=jdbc:postgresql://localhost:5432/payment_portal
   export DATABASE_USERNAME=admin
   export DATABASE_PASSWORD=admin123
   ```

3. **Build and run**
   ```bash
   mvn clean install
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

## 📚 API Documentation

### Interactive Documentation

Access Swagger UI at: **http://localhost:8080/swagger-ui.html**

### Authentication

All API endpoints (except public health checks) require HTTP Basic Authentication:

```bash
# Admin user
curl -u admin:admin123 http://localhost:8080/api/v1/portals

# Staff user
curl -u staff:staff123 http://localhost:8080/api/v1/portals

# Student user
curl -u student:student123 http://localhost:8080/api/v1/portals
```

### Key Endpoints

| Endpoint | Method | Role | Description |
|----------|--------|------|-------------|
| `/api/v1/portals` | GET | ALL | List payment portals |
| `/api/v1/portals` | POST | ADMIN | Create new portal |
| `/api/v1/portals/{id}` | GET | ALL | Get portal details |
| `/api/v1/portals/{id}` | PUT | ADMIN | Update portal |
| `/api/v1/submissions` | GET | ALL | List submissions |
| `/api/v1/submissions` | POST | STUDENT | Create submission |
| `/api/v1/data-sheets/export` | GET | ADMIN/STAFF | Export data |
| `/actuator/health` | GET | PUBLIC | Health status |
| `/actuator/metrics` | GET | ADMIN | Application metrics |

### Example: Create Portal

```bash
curl -X POST http://localhost:8080/api/v1/portals \
  -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "January 2025 Payments",
    "month": 1,
    "year": 2025,
    "visibility": "PUBLISHED",
    "createdByAdminId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

## ⚙️ Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/payment_portal` | Database connection URL |
| `DATABASE_USERNAME` | `admin` | Database username |
| `DATABASE_PASSWORD` | `admin123` | Database password |
| `SERVER_PORT` | `8080` | Application port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active profile (dev/prod) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowed CORS origins |

### Profiles

- **dev**: Development profile with debug logging, Swagger enabled
- **prod**: Production profile with optimized settings, Swagger disabled
- **test**: Test profile for integration tests

### Application Properties

Configuration files are located in `src/main/resources/`:
- `application.properties` - Base configuration
- `application-dev.properties` - Development overrides
- `application-prod.properties` - Production overrides

## 🗄 Database

### Schema Management

This project uses **Liquibase** for database migrations. Schema changes are version-controlled in:

```
src/main/resources/db/changelog/
├── db.changelog-master.yaml
├── payment_portals.yaml
├── payment_submissions.yaml
└── uploaded_files.yaml
```

### Running Migrations

Migrations run automatically on application startup. To run manually:

```bash
mvn liquibase:update
```

### Rollback

```bash
mvn liquibase:rollback -Dliquibase.rollbackCount=1
```

### Database Access via pgAdmin

1. Navigate to http://localhost:5050
2. Login with `admin@admin.com` / `admin123`
3. Server "Payment Portal DB" is pre-configured
4. Browse tables under: Servers → Payment Portal DB → Databases → payment_portal → Schemas → public → Tables

## 🔒 Security

### Authentication

Currently using **HTTP Basic Authentication** with in-memory users:

| Username | Password | Role | Permissions |
|----------|----------|------|-------------|
| admin | admin123 | ADMIN | Full access |
| staff | staff123 | STAFF | Read portals, export data |
| student | student123 | STUDENT | Read portals, create submissions |

⚠️ **Production**: Replace with JWT tokens or OAuth2 integration.

### CORS Configuration

Configure allowed origins in `application.properties`:

```properties
app.cors.allowed-origins=http://localhost:3000,https://app.dopaminelite.com
```

### CSRF Protection

Disabled for API mode. Enable for browser-based applications by removing:
```java
.csrf(csrf -> csrf.disable())
```

## 🧪 Testing

### Run All Tests

```bash
mvn test
```

### Run Integration Tests

```bash
mvn verify
```

### Test Coverage

```bash
mvn clean verify
# View report: target/site/jacoco/index.html
```

### Test Profiles

Tests run with the `test` profile using H2 in-memory database:
- No external dependencies required
- Isolated test data
- Fast execution

## 🚢 Deployment

### Docker Production Build

1. **Build production image**
   ```bash
   docker build -f Dockerfile.prod -t payment-portal:latest .
   ```

2. **Run with environment variables**
   ```bash
   docker run -d \
     -p 8080:8080 \
     -e DATABASE_URL=jdbc:postgresql://db:5432/payment_portal \
     -e DATABASE_USERNAME=prod_user \
     -e DATABASE_PASSWORD=secure_password \
     -e SPRING_PROFILES_ACTIVE=prod \
     payment-portal:latest
   ```

### Kubernetes Deployment

Example manifests available in `k8s/` directory:
- Deployment
- Service
- ConfigMap
- Secret
- Ingress

### Environment-Specific Deployment

**Development**:
```bash
docker compose up -d
```

**Staging**:
```bash
docker compose -f docker-compose.yml -f docker-compose.staging.yml up -d
```

**Production**:
```bash
docker compose -f docker-compose.prod.yml up -d
```

## 📊 Monitoring

### Health Checks

- **Liveness**: `GET /actuator/health/liveness`
- **Readiness**: `GET /actuator/health/readiness`
- **Overall Health**: `GET /actuator/health`

### Metrics

Prometheus metrics available at: `/actuator/prometheus`

Example metrics:
- HTTP request duration
- Database connection pool stats
- JVM memory usage
- Custom business metrics

### Logging

Logs are written to:
- **Console**: Pretty-printed colored output (dev)
- **File**: `/var/log/payment-portal/application.log` (prod)
- **Rolling**: 100MB max size, 30 days retention

Log levels configured in `logback-spring.xml`

### Grafana Dashboard

Import the provided dashboard (`monitoring/grafana-dashboard.json`) to visualize:
- Request rates and latencies
- Error rates
- Database performance
- JVM metrics

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Standards

- Follow Java code conventions
- Write unit tests for new features
- Update API documentation
- Add Liquibase changesets for schema changes

## 📝 License

Proprietary - © 2025 Dopamine Lite. All rights reserved.

## 📞 Support

- **Email**: support@dopaminelite.com
- **Documentation**: https://docs.dopaminelite.com
- **Issues**: https://github.com/dopaminelite/payment-portal/issues

## 🗺 Roadmap

- [ ] JWT authentication
- [ ] File upload to AWS S3
- [ ] Email notifications
- [ ] Webhooks for submission events
- [ ] Advanced reporting
- [ ] Multi-tenancy support
- [ ] Rate limiting
- [ ] GraphQL API

---

**Built with ❤️ by the Dopamine Lite Team**
