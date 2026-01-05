#!/bin/bash

#
# Account Management E2E Test Script
#
# Tests account creation, authentication, and management endpoints
#
# Usage:
#   ./tests/e2e/test-accounts-e2e.sh [options]
#
# Examples:
#   ./tests/e2e/test-accounts-e2e.sh                # Run all account tests
#   ./tests/e2e/test-accounts-e2e.sh --verbose      # Show detailed output
#

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose) VERBOSE=true; shift ;;
        --help) show_help; exit 0 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

show_help() {
    echo "Account Management E2E Test"
    echo "Usage: ./tests/e2e/test-accounts-e2e.sh [options]"
    echo "Options:"
    echo "  --verbose  Show detailed test output"
    echo "  --help     Show this help message"
}

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║${NC} Account Management E2E Tests"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""

CMD="./gradlew :server:test -k \"AccountE2ETest\""
[ "$VERBOSE" = true ] && CMD="$CMD --info"

echo -e "${GREEN}Running:${NC} $CMD"
echo ""

eval "$CMD"

echo ""
echo -e "${GREEN}✓ Account e2e tests completed${NC}"
