#!/bin/bash

# Colors for better readability
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== CLUSTER VALIDATION START (IN-MEMORY) ===${NC}"

echo -e "\n${GREEN}1. TESTING LOAD BALANCING (ROUND ROBIN)${NC}"
# Request 1
REQ1=$(curl -s http://localhost/api/status)
NODE1=$(echo "$REQ1" | grep -o 'chat-api-server-[0-9]-[a-z0-9-]*' | head -1)
echo -e "Request 1 Handler: ${GREEN}$NODE1${NC}"

# Request 2
REQ2=$(curl -s http://localhost/api/status)
NODE2=$(echo "$REQ2" | grep -o 'chat-api-server-[0-9]-[a-z0-9-]*' | head -1)
echo -e "Request 2 Handler: ${GREEN}$NODE2${NC}"

if [ "$NODE1" != "$NODE2" ]; then
    echo -e "${GREEN}✅ Load Balancing Verified: Requests hit different nodes.${NC}"
else
    echo -e "${RED}❌ Load Balancing FAILED: Both requests hit the same node ($NODE1).${NC}"
    exit 1
fi

echo -e "\n${GREEN}2. TESTING STICKY SESSIONS${NC}"
# Request 1: Get Cookie
RESP1=$(curl -s -i http://localhost/api/status)
INIT_NODE=$(echo "$RESP1" | grep -o 'chat-api-server-[0-9]-[a-z0-9-]*' | head -1)
COOKIE=$(echo "$RESP1" | grep -i "Set-Cookie:" | grep -o "SERVERID=[^;]*")

echo -e "Initial Node: ${GREEN}$INIT_NODE${NC}"
echo "In-memory Cookie: $COOKIE"

# Request 2: Use Cookie
RESP2=$(curl -s -H "Cookie: $COOKIE" http://localhost/api/status)
FOLLOW_NODE=$(echo "$RESP2" | grep -o 'chat-api-server-[0-9]-[a-z0-9-]*' | head -1)
echo -e "Follow-up Node: ${GREEN}$FOLLOW_NODE${NC}"

if [ "$INIT_NODE" == "$FOLLOW_NODE" ]; then
    echo -e "${GREEN}✅ Stickiness Verified: Session remained on the same node.${NC}"
else
    echo -e "${RED}❌ Stickiness FAILED: Session moved to $FOLLOW_NODE${NC}"
    exit 1
fi

echo -e "\n${BLUE}=== VERIFICATION SUCCESSFUL ===${NC}"
