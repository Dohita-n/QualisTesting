# QualisDS >>

## Project Overview
- **Purpose**: Data management solution for uploading, analyzing, transforming, and exporting datasets
- **Frontend**: Angular 19+ with modern UI components and JWT authentication
- **Backend**: Spring Boot 3.x with PostgreSQL, Spring Security, and REST API

## Key Features
- File upload and dataset creation
- Data profiling and analysis
- Transformation pipeline creation
- Role-based access control
- Secure JWT authentication
- Dataset export functionality


## API Endpoints
- Authentication: `/api/auth/**`
- User Management: `/api/user/**`
- File Upload: `/api/files/**`
- Datasets: `/api/datasets/**`
- Data Preparation: `/api/preparations/**`

## Security
- JWT token-based authentication
- Role-based access control (ADMIN,UPLOAD_DATA VIEW_DATA, EDIT_DATA, etc.)
- Rate limiting and brute force protection
- Secure file upload validation

## Development Setup
### Prerequisites
- Angular 19+
- Openssl 3.4.1
- npm 9.2.0e+ 
- node 20+
- Java 21+, Maven 3.8+
- PostgreSQL 14+

### Running Locally
1. cp .env.example .env (Configure the .env for local credentials and setups)
2. Start backend: `mvn clean && mvn install && mvn spring-boot:run`
3. Start frontend: `npm run start:secure`

## Future Improvements
- Enhanced modularity and state management
- Performance optimizations for large datasets
- Expanded test coverage
- Event-driven architecture for long-running processes
- Adding Mappers & DTOs
- Add Interfaces for each Service
- Store CSV files in the database as Binaries
- Frontend Improvments

## Coding Standards
- Follow Angular style guide (frontend)
- Follow Google Java Style Guide (backend)
- Comprehensive documentation
- Constructor-based dependency injection
