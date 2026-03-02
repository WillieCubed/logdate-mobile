#!/bin/bash

#
# LogDate E2E Test Runner
#
# Convenient script for running end-to-end tests for LogDate
#
# Usage:
#   ./tests/e2e/run-e2e-tests.sh [test-type] [options]
#
# Examples:
#   ./tests/e2e/run-e2e-tests.sh multi-window              # Run multi-window editor tests
#   ./tests/e2e/run-e2e-tests.sh accounts                  # Run account tests
#   ./tests/e2e/run-e2e-tests.sh all                       # Run all e2e tests
#   ./tests/e2e/run-e2e-tests.sh multi-window --debug      # Run with verbose output
#   ./tests/e2e/run-e2e-tests.sh multi-window --verify-adb # Verify adb before running
#

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test type (default: all)
TEST_TYPE="${1:-all}"
shift || true

# Options
VERBOSE=false
VERIFY_ADB=false
ENABLE_DEBUG=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose)
            VERBOSE=true
            shift
            ;;
        --verify-adb)
            VERIFY_ADB=true
            shift
            ;;
        --debug)
            ENABLE_DEBUG=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Helper functions
print_banner() {
    echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC} LogDate E2E Test Suite $1"
    echo -e "${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Verify adb if requested
verify_adb() {
    print_info "Verifying adb connection..."

    if ! command -v adb &> /dev/null; then
        print_error "adb not found in PATH. Please install Android SDK Tools."
        exit 1
    fi

    local device_count=$(adb devices | grep -v "List of attached" | grep -v "^$" | wc -l)

    if [ "$device_count" -eq 0 ]; then
        print_error "No Android devices found. Connect a device or start an emulator."
        echo ""
        echo "  Connected devices:"
        adb devices
        exit 1
    fi

    print_success "adb connected with $device_count device(s)"

    # Check API level
    api_level=$(adb shell getprop ro.build.version.sdk)
    if [ "$api_level" -lt 24 ]; then
        print_warning "Device API level $api_level detected. Multi-window tests require API 24+"
    else
        print_success "Device API level $api_level supports multi-window"
    fi
}

# Run test based on type
run_test() {
    case "$TEST_TYPE" in
        multi-window)
            print_banner "Multi-Window Editor Tests"

            if [ "$VERIFY_ADB" = true ]; then
                verify_adb
            fi

            print_info "Running multi-window editor e2e tests..."

            local cmd="./gradlew :app:android-main:connectedDebugAndroidTest -k \"MultiWindowEditorE2ETest\""

            if [ "$VERBOSE" = true ] || [ "$ENABLE_DEBUG" = true ]; then
                cmd="$cmd --info"
            fi

            eval "$cmd"
            print_success "Multi-window tests completed"
            ;;

        accounts)
            print_banner "Account Tests"
            print_info "Running account e2e tests..."

            local cmd="./gradlew :server:test -k \"AccountE2ETest\""

            if [ "$VERBOSE" = true ] || [ "$ENABLE_DEBUG" = true ]; then
                cmd="$cmd --info"
            fi

            eval "$cmd"
            print_success "Account tests completed"
            ;;

        auth)
            print_banner "Authentication Tests"
            print_info "Running authentication flow e2e tests..."

            local cmd="./gradlew :server:test -k \"AuthenticationFlowsE2ETest\""

            if [ "$VERBOSE" = true ] || [ "$ENABLE_DEBUG" = true ]; then
                cmd="$cmd --info"
            fi

            eval "$cmd"
            print_success "Authentication tests completed"
            ;;

        all)
            print_banner "All E2E Tests"

            if [ "$VERIFY_ADB" = true ]; then
                verify_adb
            fi

            print_info "Running all e2e tests (client + server)..."

            # Run server tests
            print_info "Running server e2e tests..."
            ./gradlew :server:test -k "E2ETest" $([ "$VERBOSE" = true ] && echo "--info" || echo "")
            print_success "Server tests completed"

            # Run client tests
            print_info "Running client e2e tests..."
            ./gradlew :app:android-main:connectedDebugAndroidTest $([ "$VERBOSE" = true ] && echo "--info" || echo "")
            print_success "Client tests completed"

            print_success "All e2e tests completed"
            ;;

        list)
            print_banner "Available E2E Tests"
            echo ""
            echo "Client-side tests (Android):"
            echo "  - multi-window    : Multi-window editor tests (requires API 24+)"
            echo ""
            echo "Server-side tests (Ktor):"
            echo "  - accounts        : Account management e2e tests"
            echo "  - auth            : Authentication flow e2e tests"
            echo ""
            echo "Combined:"
            echo "  - all             : Run all client and server e2e tests"
            echo ""
            echo "Options:"
            echo "  --verify-adb      : Verify adb connection before running"
            echo "  --verbose         : Show detailed test output"
            echo "  --debug           : Enable debug output"
            echo ""
            ;;

        *)
            print_error "Unknown test type: $TEST_TYPE"
            echo ""
            echo "Available test types:"
            echo "  - multi-window    : Multi-window editor tests"
            echo "  - accounts        : Account management tests"
            echo "  - auth            : Authentication tests"
            echo "  - all             : All e2e tests"
            echo "  - list            : Show all available tests"
            echo ""
            echo "Run: ./tests/e2e/run-e2e-tests.sh list"
            exit 1
            ;;
    esac
}

# Main execution
main() {
    print_banner "Starting"
    echo ""

    # Show test type and options
    print_info "Test Type: $TEST_TYPE"
    [ "$VERIFY_ADB" = true ] && print_info "ADB Verification: Enabled"
    [ "$VERBOSE" = true ] && print_info "Verbose Output: Enabled"
    [ "$ENABLE_DEBUG" = true ] && print_info "Debug Mode: Enabled"
    echo ""

    # Run the test
    run_test

    echo ""
    print_success "Done!"
}

# Run main
main
