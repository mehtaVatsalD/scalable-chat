-- 1. Chats Table
-- Sharded by: id
CREATE TABLE chats (
    id BIGINT PRIMARY KEY,              -- Generated as Snowflake/TSID in Java
    type VARCHAR(50) NOT NULL,          -- e.g., 'PRIVATE', 'GROUP'
    name VARCHAR(255),                  -- NULL for private chats
    created_at BIGINT NOT NULL,         -- Unix timestamp (ms)
    last_activity BIGINT NOT NULL       -- Updated on every new message for sorting chat lists
);

-- 2. Chat Participants
-- Sharded by: chat_id (To keep all members of a chat on the same node)
CREATE TABLE chat_participants (
    id BIGINT PRIMARY KEY,              -- Snowflake/TSID
    chat_id BIGINT NOT NULL,            -- No hard FK if using cross-shard, managed in app
    user_id VARCHAR(255) NOT NULL,
    joined_at BIGINT NOT NULL,
    last_read_message_id BIGINT DEFAULT 0, -- Essential for the "Ack/Unread" flow
    
    -- Ensures a user can't join the same chat twice
    CONSTRAINT uk_chat_user UNIQUE (chat_id, user_id)
);

-- Index for "Show me all chats User X is in"
-- Note: In massive scale, this index might trigger cross-shard queries 
-- unless this table is "Global" or "Reference" type.
CREATE INDEX idx_participants_user_id ON chat_participants(user_id);

-- 3. Messages Table
-- Sharded by: chat_id
CREATE TABLE messages (
    id BIGINT PRIMARY KEY,              -- Snowflake/TSID (Globally unique & Time-sortable)
    chat_id BIGINT NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(50) DEFAULT 'TEXT', -- 'TEXT', 'IMAGE', 'SYSTEM'
    timestamp BIGINT NOT NULL           -- Unix timestamp (ms)
);

-- Optimized Index for fetching chat history
-- Sorting by id DESC is the same as sorting by time because of TSIDs
CREATE INDEX idx_messages_chat_id_id ON messages (chat_id, id DESC);

-- Index for analytics or searching by user
CREATE INDEX idx_messages_sender_id ON messages (sender_id);