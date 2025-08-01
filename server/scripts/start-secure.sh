#!/bin/bash

# Make sure we're in the server directory
cd "$(dirname "$0")/.."

# Start the Spring Boot application with HTTPS enabled
echo "Starting Spring Boot backend with HTTPS on port 8443..."
./mvnw spring-boot:run 