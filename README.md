# 🌌 Scalable Chat (Vibe Coding Edition)

> **Developed entirely through the power of Vibe Coding.**  
> Built by **[Vatsal Mehta](https://github.com/mehtaVatsalD)** in collaboration with **Antigravity (Gemini)**.

---

## ⚡ The Vibe Coding Philosophy
This project is a testament to the **Vibe Coding** movement. It wasn't built by strictly following rigid specifications, but by maintaining a high-level creative flow between human intent and AI execution.

- **Collaborative Architecture**: The system's design was forged through deep architectural discussions between human and AI, ensuring every component (Redis routing, heartbeat mechanisms, lazy cleanup) aligns with a shared vision.
- **Iterative Step-by-Step Flow**: Instead of delegating full control, the project evolved through a disciplined step-by-step process—building, testing, and refining one feature at a time.
- **Zero Hand-Coding**: While the architecture was a joint effort, 100% of the code implementation, logic, and tests were generated and refined by the AI.

---

## 🚀 Overview
A high-performance, distributed chat messaging system featuring both a **Spring Boot** backend and a modern frontend. Designed to handle massive scale by distributing users across multiple server nodes while maintaining seamless, global communication.

### 🏗️ Design Choices
- **🌐 Global Routing Table**: Real-time tracking of concurrent users across a distributed cluster using Redis Hashes.
- **🏹 Inter-Node Federation**: Intelligent message relaying via Redis Pub/Sub topics—messages only travel to the server where the recipient is actually connected.
- **🛠️ Self-Healing & Resilience**: Automatic "Lazy Cleanup" mechanism. If a server node crashes, other nodes detect the stale routing entries and clean them up on-the-fly.
- **🧵 Performance Oriented**: Minimal overhead. Local delivery happens in-memory; cross-server delivery is optimized through dedicated inter-node channels.
- **🧪 Robust Integration Suite**: 
  - **Server Liveness**: Death-watch and auto-registration verification.
  - **User Routing**: Cross-node connection tracking validation.
  - **Local/Remote Flow**: Proof that local messages stay local and remote messages hop correctly.

---

## 🛠️ Getting Started (Backend)

### 1️⃣ Spin up the Backbone (Redis)
Ensure a Redis instance is running. The fastest way is via Docker:
```bash
docker run -d --name redis-chat -p 6379:6379 redis:7.0-alpine
```

### 2️⃣ Run the Test Suite
Verification is at the heart of Vibe Coding. Run the comprehensive integration suite to see the system in action:
```bash
mvn test
```

### 3️⃣ Start the Cluster
Launch multiple instances to simulate a distributed environment:

**Node 1 (Port 8080):**
```bash
mvn spring-boot:run
```

**Node 2 (Port 8081):**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

---

## 💻 Tech Stack
- **Backend**: Spring Boot, WebSocket (STOMP), Spring Data Redis (Lettuce).
- **Frontend**: (Incoming)
- **Infrastructure**: Redis (Hashes & Pub/Sub).
- **Testing**: JUnit 5, Awaitility.

---

## 🤖 Credits
Built by **[Vatsal Mehta](https://github.com/mehtaVatsalD)** with the agentic help of **Antigravity**.  
*The "Vibe" is strong with this one.*
