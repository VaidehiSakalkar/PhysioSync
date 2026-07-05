#!/bin/bash

# start_local.sh - A script to run all Physiosync services locally for development

# Exit on any error during setup
set -e

echo "Starting infrastructure containers (Redis, Kafka, MinIO, Chroma, etc.)..."
docker compose -f infra/docker-compose.dev.yml up -d

echo "Waiting a few seconds for infrastructure to initialize..."
sleep 5

# Create a logs directory
mkdir -p logs

echo "Starting Spring Boot Services (logs will be saved to logs/)..."
(cd services/api-gateway && ./mvnw spring-boot:run > ../../logs/api-gateway.log 2>&1) &
PID_API=$!

(cd services/patient-service && ./mvnw spring-boot:run > ../../logs/patient-service.log 2>&1) &
PID_PATIENT=$!

(cd services/exercise-service && ./mvnw spring-boot:run > ../../logs/exercise-service.log 2>&1) &
PID_EXERCISE=$!

(cd services/appointment-service && ./mvnw spring-boot:run > ../../logs/appointment-service.log 2>&1) &
PID_APPOINT=$!

(cd services/video-service && ./mvnw spring-boot:run > ../../logs/video-service.log 2>&1) &
PID_VIDEO=$!

echo "Starting ML Service..."
(cd ml-service && source .venv/bin/activate && uvicorn app.main:app --reload --port 8000 > ../logs/ml-service.log 2>&1) &
PID_ML=$!

echo "Starting Frontend..."
(cd frontend && npm run dev > ../logs/frontend.log 2>&1) &
PID_FE=$!

echo "------------------------------------------------------"
echo "All services are starting up!"
echo "Check the 'logs' folder for output of each service."
echo "API Gateway: http://localhost:8080"
echo "Frontend: http://localhost:3000"
echo "ML Service: http://localhost:8000"
echo "Press Ctrl+C to stop all services."
echo "------------------------------------------------------"

# Trap SIGINT to gracefully kill child processes
trap "echo -e '\nStopping all services...'; kill $PID_API $PID_PATIENT $PID_EXERCISE $PID_APPOINT $PID_VIDEO $PID_ML $PID_FE 2>/dev/null; docker compose -f infra/docker-compose.dev.yml stop; exit 0" SIGINT SIGTERM

# Wait indefinitely until interrupted
wait
