#!/bin/bash

# LogDate Development Environment Startup Script
# This script starts the development environment with PostgreSQL and Redis

set -e

echo "🚀 Starting LogDate development environment..."

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Start database services
echo -e "${BLUE}📊 Starting PostgreSQL and Redis...${NC}"
docker-compose up logdate-postgres logdate-redis -d

# Wait for services to be healthy
echo -e "${YELLOW}⏳ Waiting for services to be healthy...${NC}"
timeout=60
counter=0

while [ $counter -lt $timeout ]; do
    if docker-compose ps logdate-postgres | grep -q "healthy" && \
       docker-compose ps logdate-redis | grep -q "healthy"; then
        break
    fi
    sleep 2
    counter=$((counter + 2))
    echo -n "."
done

if [ $counter -ge $timeout ]; then
    echo -e "\n❌ Services failed to become healthy within ${timeout} seconds"
    docker-compose logs logdate-postgres logdate-redis
    exit 1
fi

echo -e "\n✅ Services are healthy!"

# Display connection information
echo -e "\n${GREEN}🎉 Development environment ready!${NC}"
echo ""
echo "📋 Connection Details:"
echo "   PostgreSQL: localhost:15432 (logdate/logdate)"
echo "   Redis:      localhost:16379"
echo ""
echo "🚀 To start the server:"
echo "   export DATABASE_URL=\"jdbc:postgresql://localhost:15432/logdate\""
echo "   ./gradlew :server:run"
echo ""
echo "🛠️  Development Tools:"
echo "   PgAdmin:        http://localhost:15050 (admin@logdate.app/admin)"
echo "   Redis Commander: http://localhost:18081"
echo ""
echo "To start with tools: ./scripts/dev-start.sh --with-tools"

# Start development tools if requested
if [[ "$1" == "--with-tools" ]]; then
    echo -e "${BLUE}🛠️  Starting development tools...${NC}"
    docker-compose --profile tools up -d
    echo -e "${GREEN}✅ Development tools started!${NC}"
fi