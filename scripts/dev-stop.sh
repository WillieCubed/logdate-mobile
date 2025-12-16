#!/bin/bash

# LogDate Development Environment Stop Script
# This script stops all development services

set -e

echo "🛑 Stopping LogDate development environment..."

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# Stop all services including tools
echo -e "${YELLOW}📊 Stopping all services...${NC}"
docker-compose --profile tools down

# Check if we should remove volumes
if [[ "$1" == "--clean" ]]; then
    echo -e "${RED}🧹 Removing all data volumes...${NC}"
    docker-compose down -v
    echo -e "${GREEN}✅ All data removed!${NC}"
else
    echo -e "${GREEN}✅ Services stopped (data preserved)${NC}"
    echo ""
    echo "💡 To remove all data, run: ./scripts/dev-stop.sh --clean"
fi

echo ""
echo "🏁 Development environment stopped."