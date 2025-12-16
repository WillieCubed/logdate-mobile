#!/bin/bash

# LogDate Development Environment Reset Script
# This script resets the development environment (stops services, removes data, and restarts)

set -e

echo "🔄 Resetting LogDate development environment..."

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Confirm reset
echo -e "${RED}⚠️  This will remove all database data and restart the environment.${NC}"
read -p "Are you sure? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Reset cancelled."
    exit 0
fi

# Stop and clean everything
echo -e "${YELLOW}🛑 Stopping all services and removing data...${NC}"
docker-compose --profile tools down -v

# Remove any orphaned containers
echo -e "${YELLOW}🧹 Cleaning up orphaned containers...${NC}"
docker-compose down --remove-orphans

# Start fresh
echo -e "${BLUE}🚀 Starting fresh environment...${NC}"
./scripts/dev-start.sh

echo -e "${GREEN}✅ Environment reset complete!${NC}"
echo ""
echo "🎉 Fresh development environment ready with clean database!"