#!/bin/bash

# Configuration
CONTAINER_NAME="scalable-chat-db"
DB_NAME="scalable_chat"
DB_USER="chat_user"
SQL_FILE="scripts/init_db.sql"

echo "🚀 Starting database migration..."

# Check if the container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "❌ Error: Container '$CONTAINER_NAME' is not running."
    exit 1
fi

# Check if SQL file exists
if [ ! -f "$SQL_FILE" ]; then
    echo "❌ Error: SQL file '$SQL_FILE' not found."
    exit 1
fi

echo "📂 Copying $SQL_FILE to container..."
docker cp "$SQL_FILE" "${CONTAINER_NAME}:/tmp/init_db.sql"

echo "⚙️ Executing migration..."
docker exec -i "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -f /tmp/init_db.sql

if [ $? -eq 0 ]; then
    echo "✅ Migration completed successfully!"
else
    echo "❌ Migration failed."
    exit 1
fi
