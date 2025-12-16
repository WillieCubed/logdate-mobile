# LogDate Database Setup

LogDate server supports both **PostgreSQL** (production) and **in-memory storage** (development/fallback).

## PostgreSQL Setup (Recommended)

### 1. Install PostgreSQL

**macOS (Homebrew):**
```bash
brew install postgresql@16
brew services start postgresql@16
```

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**Docker:**
```bash
docker run --name logdate-postgres \
  -e POSTGRES_DB=logdate \
  -e POSTGRES_USER=logdate \
  -e POSTGRES_PASSWORD=logdate \
  -p 5432:5432 \
  -d postgres:16
```

### 2. Create Database and User

```sql
-- Connect to PostgreSQL as superuser
sudo -u postgres psql

-- Create database and user
CREATE DATABASE logdate;
CREATE USER logdate WITH ENCRYPTED PASSWORD 'logdate';
GRANT ALL PRIVILEGES ON DATABASE logdate TO logdate;

-- For development, you might also want:
ALTER USER logdate CREATEDB;

-- Exit
\q
```

### 3. Environment Variables

Set these environment variables for production:

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/logdate"
export DATABASE_USER="logdate"
export DATABASE_PASSWORD="your-secure-password"

# Optional
export JWT_SECRET="your-jwt-secret"
export WEBAUTHN_RP_ID="your-domain.com"
export WEBAUTHN_ORIGIN="https://your-domain.com"
```

### 4. Run the Server

The server will automatically:
1. Try to connect to PostgreSQL
2. Run database migrations (create tables)
3. Fall back to in-memory storage if database unavailable

```bash
./gradlew :server:run
```

## Database Schema

The server uses [Flyway](https://flywaydb.org/) for migrations. Schema files are in:
```
server/src/main/resources/db/migration/
├── V1__Initial_schema.sql
└── (future migrations...)
```

### Tables Created

- **accounts** - User accounts with passkey authentication
- **passkeys** - WebAuthn credentials linked to accounts  
- **sessions** - Temporary authentication/registration sessions
- **webauthn_challenges** - WebAuthn challenges for security

## Development Mode

Without PostgreSQL, the server automatically uses in-memory storage:
- ✅ All features work normally
- ⚠️ Data is lost when server restarts
- 💡 Perfect for development and testing

## Production Checklist

- [ ] PostgreSQL installed and running
- [ ] Database and user created with proper permissions
- [ ] Environment variables set securely
- [ ] Database connection tested
- [ ] Backups configured
- [ ] SSL/TLS enabled for database connections

## Troubleshooting

**Connection refused:**
```bash
# Check if PostgreSQL is running
brew services list | grep postgres
# or
sudo systemctl status postgresql
```

**Authentication failed:**
```bash
# Test connection manually
psql -h localhost -U logdate -d logdate
```

**Migration errors:**
```bash
# Check migration status
./gradlew flywayInfo

# Repair if needed
./gradlew flywayRepair
```

**Fall back to in-memory:**
If you see this log message, PostgreSQL connection failed:
```
Database not available, using in-memory repositories
```

## Docker Development Setup

### Quick Start with Docker Compose

The easiest way to get started with LogDate development is using Docker Compose:

```bash
# Start PostgreSQL and Redis
docker-compose up postgres redis

# Or start everything including development tools
docker-compose --profile tools up

# Start with server (requires Dockerfile)
docker-compose --profile full-stack up
```

### Docker Services

**PostgreSQL Database:**
- Image: `postgres:16-alpine`
- Port: `5432`
- Database: `logdate`
- User/Password: `logdate/logdate`
- Volume: `postgres_data` (persistent)

**Redis Cache:**
- Image: `redis:7-alpine`
- Port: `6379`
- Volume: `redis_data` (persistent)
- Configuration: AOF persistence, 256MB memory limit

**Development Tools:**
- **PgAdmin**: `http://localhost:5050` (admin@logdate.app/admin)
- **Redis Commander**: `http://localhost:8081`

### Environment Variables

Docker Compose automatically configures these environment variables:

```bash
DATABASE_URL="jdbc:postgresql://postgres:5432/logdate"
DATABASE_USER="logdate"
DATABASE_PASSWORD="logdate"
REDIS_URL="redis://redis:6379"
AUTO_MIGRATE="true"
```

## Cloud Run Production Deployment

### Database Configuration

For Google Cloud Run deployment, use managed services:

**Cloud SQL (PostgreSQL):**
```bash
# Create Cloud SQL instance
gcloud sql instances create logdate-db \
  --database-version=POSTGRES_16 \
  --cpu=1 \
  --memory=4GB \
  --region=us-central1

# Create database and user
gcloud sql databases create logdate --instance=logdate-db
gcloud sql users create logdate --instance=logdate-db --password=SECURE_PASSWORD
```

**Memorystore (Redis):**
```bash
# Create Memorystore Redis instance
gcloud redis instances create logdate-cache \
  --size=1 \
  --region=us-central1 \
  --redis-version=redis_7_0
```

### Cloud Run Environment Variables

Configure these environment variables in Cloud Run:

```bash
DATABASE_URL="jdbc:postgresql://CLOUD_SQL_PRIVATE_IP:5432/logdate"
DATABASE_USER="logdate"
DATABASE_PASSWORD="SECURE_PASSWORD"
REDIS_URL="redis://MEMORYSTORE_IP:6379"

JWT_SECRET="SECURE_JWT_SECRET"
WEBAUTHN_RP_ID="your-domain.com"
WEBAUTHN_ORIGIN="https://your-domain.com"
```

### Cloud Run Deployment

```bash
# Build and deploy to Cloud Run
gcloud run deploy logdate-server \
  --source . \
  --region us-central1 \
  --cpu 1 \
  --memory 2Gi \
  --min-instances 0 \
  --max-instances 10 \
  --port 8080 \
  --allow-unauthenticated
```

## Docker Commands Reference

### Development Workflow

```bash
# Start only database services
docker-compose up postgres redis -d

# Start with development tools
docker-compose --profile tools up -d

# View logs
docker-compose logs postgres
docker-compose logs redis

# Connect to database
docker-compose exec postgres psql -U logdate -d logdate

# Connect to Redis
docker-compose exec redis redis-cli

# Stop services
docker-compose down

# Reset data (removes volumes)
docker-compose down -v
```

### Production Build

```bash
# Build production image
docker build --target production -t logdate-server .

# Run production container
docker run -p 8080:8080 \
  -e DATABASE_URL="jdbc:postgresql://host:5432/logdate" \
  -e DATABASE_USER="logdate" \
  -e DATABASE_PASSWORD="password" \
  logdate-server
```

## Monitoring

Monitor database performance:
- Connection pool usage
- Query performance
- Database size growth
- Backup status

The server logs database connection status on startup.