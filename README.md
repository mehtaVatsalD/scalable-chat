# Scalable Chat Backend

A highly scalable chat messaging system built with Spring Boot and Redis. Designed to support multiple server nodes with global routing and inter-node communication.

## Key Features
- **Multi-Node Support**: Intelligent routing of messages between servers.
- **Redis Global State**: Tracks user connectivity across the entire cluster.
- **Inter-Node Pub/Sub**: Real-time message delivery between distributed nodes.
- **Self-Healing**: Automatic "Lazy Cleanup" of stale routing entries from crashed servers.
- **Type-Safe Messaging**: Custom DTOs for client delivery and inter-node communication.

## Testing Strategy

The project uses integration tests that require a running Redis instance.

### Prerequisites
1. **Redis**: Ensure a Redis instance is running (e.g., via Docker).
   ```bash
   docker run -d --name redis-chat -p 6379:6379 redis:7.0-alpine
   ```

### Running Tests
To run tests against the local Redis instance (localhost:6379):
```bash
mvn test
```

If your Redis is on a different host or port:
```bash
mvn test -Dspring.data.redis.host=your-host -Dspring.data.redis.port=your-port
```

## Running Locally

1. **Start Redis**:
   ```bash
   docker run -d --name redis-chat -p 6379:6379 redis:7.0-alpine
   ```

2. **Start Server A (Port 8080)**:
   ```bash
   mvn spring-boot:run
   ```

3. **Start Server B (Port 8081)**:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
   ```
