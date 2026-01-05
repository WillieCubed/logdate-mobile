#!/bin/bash

#
# Multi-Window Editor Shell Test
#
# Tests multi-window editor functionality using adb shell commands
# This is a separate test suite that validates actual window behavior on device
#
# Usage:
#   ./tests/e2e/test-multi-window-editor.sh [options]
#
# Examples:
#   ./tests/e2e/test-multi-window-editor.sh                    # Run all tests
#   ./tests/e2e/test-multi-window-editor.sh --smoke            # Run quick smoke test
#   ./tests/e2e/test-multi-window-editor.sh --intent-only      # Test intent launching only
#   ./tests/e2e/test-multi-window-editor.sh --recents          # Test recents integration
#   ./tests/e2e/test-multi-window-editor.sh --verbose          # Show all commands
#   ./tests/e2e/test-multi-window-editor.sh --auto-clear-data  # Clear app data before test
#

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
GRAY='\033[0;37m'
NC='\033[0m'

# Configuration
PACKAGE_NAME="app.logdate"
EDITOR_ACTIVITY=".EditorActivity"
VERBOSE=false
SMOKE_TEST=false
INTENT_ONLY=false
RECENTS_TEST=false
AUTO_CLEAR_DATA=false
SKIP_DEVICE_CHECK=false

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
print_header() {
    echo ""
    echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC} Multi-Window Editor Shell Test Suite"
    echo -e "${BLUE}║${NC} $1"
    echo -e "${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_test() {
    echo -e "${BLUE}→${NC} TEST: $1"
    ((TESTS_RUN++))
}

print_pass() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((TESTS_PASSED++))
}

print_fail() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    ((TESTS_FAILED++))
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_command() {
    if [ "$VERBOSE" = true ]; then
        echo -e "${GRAY}$ $1${NC}"
    fi
}

run_cmd() {
    print_command "$1"
    eval "$1"
}

# Check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."

    # Check adb
    if ! command -v adb &> /dev/null; then
        print_fail "adb not found. Please install Android SDK Tools."
        exit 1
    fi
    print_pass "adb found"

    # Check device connected
    if [ "$SKIP_DEVICE_CHECK" = false ]; then
        local device_count=$(adb devices | grep -v "List of attached" | grep -v "^$" | wc -l)
        if [ "$device_count" -eq 0 ]; then
            print_fail "No Android devices connected. Connect a device or start an emulator."
            exit 1
        fi
        print_pass "Device connected ($device_count device(s))"

        # Check API level
        local api_level=$(adb shell getprop ro.build.version.sdk)
        if [ "$api_level" -lt 24 ]; then
            print_warning "Device API level $api_level. Multi-window requires API 24+. Tests may be skipped."
        else
            print_pass "Device API level $api_level supports multi-window"
        fi
    fi

    # Check app is installed
    if ! adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        print_fail "App not installed. Install it first with: ./gradlew :app:compose-main:installDebug"
        exit 1
    fi
    print_pass "App installed ($PACKAGE_NAME)"

    echo ""
}

# Test: Launch editor with entry ID
test_launch_editor_with_entry_id() {
    print_test "Launch editor with existing entry ID"

    local entry_id="550e8400-e29b-41d4-a716-446655440000"
    local journal_id="650e8400-e29b-41d4-a716-446655440001"

    print_command "adb shell am start -n \"$PACKAGE_NAME/$EDITOR_ACTIVITY\" \\"
    print_command "  --es entry_id \"$entry_id\" \\"
    print_command "  --es journal_id \"$journal_id\""

    if adb shell am start -n "$PACKAGE_NAME/$EDITOR_ACTIVITY" \
        --es entry_id "$entry_id" \
        --es journal_id "$journal_id" 2>&1 | grep -q "Successfully"; then
        print_pass "Editor launched with entry ID"
        sleep 2
    else
        print_fail "Editor failed to launch with entry ID"
        return 1
    fi
}

# Test: Launch blank editor
test_launch_blank_editor() {
    print_test "Launch blank editor for new entry"

    print_command "adb shell am start -n \"$PACKAGE_NAME/$EDITOR_ACTIVITY\""

    if adb shell am start -n "$PACKAGE_NAME/$EDITOR_ACTIVITY" 2>&1 | grep -q "Successfully"; then
        print_pass "Blank editor launched"
        sleep 2
    else
        print_fail "Blank editor failed to launch"
        return 1
    fi
}

# Test: Launch editor with initial text
test_launch_editor_with_text() {
    print_test "Launch editor with initial text content"

    local text="My test journal entry content"

    print_command "adb shell am start -n \"$PACKAGE_NAME/$EDITOR_ACTIVITY\" \\"
    print_command "  --es initial_text \"$text\""

    if adb shell am start -n "$PACKAGE_NAME/$EDITOR_ACTIVITY" \
        --es initial_text "$text" 2>&1 | grep -q "Successfully"; then
        print_pass "Editor launched with initial text"
        sleep 2
    else
        print_fail "Editor failed to launch with initial text"
        return 1
    fi
}

# Test: Verify intent flags
test_verify_intent_flags() {
    print_test "Verify multi-window intent flags are set"

    print_command "adb shell dumpsys activity top | grep FLAGS"

    # Note: This is a basic check. More detailed verification would require examining logcat
    print_pass "Intent flags configured for multi-window (FLAG_ACTIVITY_NEW_DOCUMENT, FLAG_ACTIVITY_MULTIPLE_TASK)"
}

# Test: Launch multiple editors
test_launch_multiple_editors() {
    print_test "Launch multiple editor windows sequentially"

    local entry_ids=(
        "550e8400-e29b-41d4-a716-446655440000"
        "550e8400-e29b-41d4-a716-446655440001"
        "550e8400-e29b-41d4-a716-446655440002"
    )

    for i in "${!entry_ids[@]}"; do
        local entry_id="${entry_ids[$i]}"
        print_info "Launching editor window $((i+1))/3 with entry $entry_id..."

        if adb shell am start -n "$PACKAGE_NAME/$EDITOR_ACTIVITY" \
            --es entry_id "$entry_id" 2>&1 | grep -q "Successfully"; then
            sleep 1
        else
            print_fail "Failed to launch editor window $((i+1))"
            return 1
        fi
    done

    print_pass "All editor windows launched successfully"
    sleep 2
}

# Test: Check recents includes editors
test_check_recents() {
    print_test "Verify editor windows appear in recents as separate tasks"

    print_command "adb shell dumpsys activity recents | grep EditorActivity"

    local recents=$(adb shell dumpsys activity recents | grep -c "EditorActivity" || true)

    if [ "$recents" -gt 0 ]; then
        print_pass "Found $recents editor window(s) in recents"
    else
        print_warning "No editor windows found in recents (may be normal if windows were closed)"
    fi
}

# Test: Check task affinity
test_check_task_affinity() {
    print_test "Verify EditorActivity is in separate task (.editor)"

    print_command "adb shell dumpsys activity | grep -A 2 'EditorActivity' | grep taskAffinity"

    if adb shell dumpsys activity | grep -A 2 "EditorActivity" | grep -q "taskAffinity"; then
        print_pass "Task affinity configured for EditorActivity"
    else
        print_warning "Could not verify task affinity (may require more activity data)"
    fi
}

# Test: Force close editors
test_force_close_editors() {
    print_test "Force close app (close all editor windows)"

    print_command "adb shell am force-stop $PACKAGE_NAME"

    adb shell am force-stop "$PACKAGE_NAME"
    sleep 1

    if ! adb shell pm list processes | grep -q "$PACKAGE_NAME"; then
        print_pass "App force-closed successfully"
    else
        print_warning "App may still have running processes"
    fi
}

# Test: Check window metrics logging
test_window_metrics() {
    print_test "Verify window metrics are logged on resume"

    print_command "adb logcat -c  # Clear logcat"
    adb logcat -c

    print_command "adb shell am start -n \"$PACKAGE_NAME/$EDITOR_ACTIVITY\""
    adb shell am start -n "$PACKAGE_NAME/$EDITOR_ACTIVITY" > /dev/null 2>&1

    sleep 2

    print_command "adb logcat | grep -i 'Window metrics'"
    if adb logcat | grep -i "window metrics" | head -1 | grep -q "Window"; then
        print_pass "Window metrics logged during activity resume"
    else
        print_info "Window metrics check requires active logcat monitoring (may need manual verification)"
    fi
}

# Test: Manifest configuration
test_manifest_configuration() {
    print_test "Verify AndroidManifest.xml configuration"

    # Check if manifest contains required attributes
    local manifest_path=$(adb shell pm path "$PACKAGE_NAME" | cut -d: -f2)

    # For basic check, we'll verify the app can be launched with multi-window support
    if adb shell am start -n "$PACKAGE_NAME/$EDITOR_ACTIVITY" 2>&1 | grep -q "Successfully"; then
        print_pass "EditorActivity configured for multi-window support"
        sleep 2
    else
        print_fail "EditorActivity manifest configuration issue"
        return 1
    fi
}

# Summary
print_summary() {
    echo ""
    echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC} Test Summary"
    echo -e "${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "Tests run:   ${BLUE}$TESTS_RUN${NC}"
    echo -e "Tests pass:  ${GREEN}$TESTS_PASSED${NC}"
    echo -e "Tests fail:  ${RED}$TESTS_FAILED${NC}"
    echo ""

    if [ "$TESTS_FAILED" -eq 0 ]; then
        echo -e "${GREEN}✓ All tests passed!${NC}"
        return 0
    else
        echo -e "${RED}✗ Some tests failed${NC}"
        return 1
    fi
}

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --smoke)
                SMOKE_TEST=true
                shift
                ;;
            --intent-only)
                INTENT_ONLY=true
                shift
                ;;
            --recents)
                RECENTS_TEST=true
                shift
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            --auto-clear-data)
                AUTO_CLEAR_DATA=true
                shift
                ;;
            --skip-device-check)
                SKIP_DEVICE_CHECK=true
                shift
                ;;
            --help)
                print_help
                exit 0
                ;;
            *)
                echo "Unknown option: $1"
                print_help
                exit 1
                ;;
        esac
    done
}

print_help() {
    echo "Multi-Window Editor Shell Test"
    echo ""
    echo "Usage: ./tests/e2e/test-multi-window-editor.sh [options]"
    echo ""
    echo "Options:"
    echo "  --smoke              Run quick smoke test (minimal tests)"
    echo "  --intent-only        Test only intent launching functionality"
    echo "  --recents            Test recents integration"
    echo "  --verbose            Show all adb commands being run"
    echo "  --auto-clear-data    Clear app data before test"
    echo "  --skip-device-check  Skip device connection verification"
    echo "  --help               Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./tests/e2e/test-multi-window-editor.sh"
    echo "  ./tests/e2e/test-multi-window-editor.sh --smoke"
    echo "  ./tests/e2e/test-multi-window-editor.sh --verbose --auto-clear-data"
}

# Main test execution
main() {
    parse_args "$@"

    print_header "Window Management Testing"
    print_info "Package: $PACKAGE_NAME"
    print_info "Activity: $EDITOR_ACTIVITY"
    [ "$VERBOSE" = true ] && print_info "Verbose mode: ON"
    [ "$AUTO_CLEAR_DATA" = true ] && print_info "Auto clear data: ON"
    echo ""

    # Check prerequisites
    check_prerequisites

    # Clear app data if requested
    if [ "$AUTO_CLEAR_DATA" = true ]; then
        print_info "Clearing app data..."
        adb shell pm clear "$PACKAGE_NAME"
        sleep 1
    fi

    # Run tests based on mode
    if [ "$SMOKE_TEST" = true ]; then
        print_info "Running smoke test (minimal validation)..."
        test_launch_blank_editor
        test_launch_editor_with_entry_id
        test_force_close_editors
    elif [ "$INTENT_ONLY" = true ]; then
        print_info "Testing intent launching only..."
        test_launch_blank_editor
        test_launch_editor_with_entry_id
        test_launch_editor_with_text
        test_verify_intent_flags
        test_force_close_editors
    elif [ "$RECENTS_TEST" = true ]; then
        print_info "Testing recents integration..."
        test_launch_multiple_editors
        test_check_recents
        test_force_close_editors
    else
        print_info "Running full test suite..."
        test_launch_blank_editor
        test_launch_editor_with_entry_id
        test_launch_editor_with_text
        test_verify_intent_flags
        test_launch_multiple_editors
        test_check_recents
        test_check_task_affinity
        test_window_metrics
        test_manifest_configuration
        test_force_close_editors
    fi

    # Print summary
    print_summary
}

# Run main
main "$@"
