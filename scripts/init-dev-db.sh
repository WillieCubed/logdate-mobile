#!/bin/bash

# LogDate Development Database Initialization Script
# This script initializes the PostgreSQL database for local development

set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-logdate}"
DB_USER="${DB_USER:-logdate}"
DB_PASSWORD="${DB_PASSWORD:-logdate}"

echo "🚀 Initializing LogDate development database..."

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
until PGPASSWORD=$DB_PASSWORD psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c '\q' 2>/dev/null; do
    echo "PostgreSQL is unavailable - sleeping"
    sleep 2
done

echo "✅ PostgreSQL is ready!"

# Run migrations (handled by Flyway in the application)
echo "📊 Database migrations will be handled by the application using Flyway"

# Insert development data
echo "🔧 Inserting development data..."
PGPASSWORD=$DB_PASSWORD psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$(dirname "$0")/dev-data/01-dev-data.sql"

echo "✅ Development database initialized successfully!"
echo ""
echo "📋 Database connection details:"
echo "   Host: $DB_HOST"
echo "   Port: $DB_PORT"
echo "   Database: $DB_NAME"
echo "   Username: $DB_USER"
echo ""
echo "🚀 You can now start the LogDate server!"