<<<<<<< HEAD
# Data Preparation App - Frontend

A modern Angular application for data preparation, cleaning, transformation, and analysis.

## Features

- **File Upload**: Support for CSV files with drag-and-drop interface
- **Data Profiling**: Visual data quality metrics and analytics
- **Pipeline Editor**: Drag-and-drop transformation workflow builder
- **Job Monitoring**: Track and manage data processing jobs

## Technology Stack

- **Angular 19**: Modern framework for building reactive web applications
- **Bootstrap 5**: Responsive UI framework for clean, accessible interfaces
- **NgRx Charts**: Data visualization for quality metrics
- **Angular CDK**: Virtual scrolling for large dataset performance

## Getting Started

### Prerequisites

- Node.js (v18+)
- npm (v9+)

### Installation

1. Clone the repository
2. Navigate to the frontend directory:
   ```
   cd Data-Preparation-App/frontend
   ```
3. Install dependencies:
   ```
   npm install
   ```

### Development Server

Run the development server:

```
npm start
```

Navigate to `https://localhost:8443/` in your browser.

### Building for Production

```
npm run build
```

The build artifacts will be stored in the `dist/` directory.

## UI/UX Design

### Color Palette

- Primary: Deep Ocean Blue (#2C3E50)
- Secondary: Fresh Teal (#18BC9C)
- Neutral: Soft Gray (#F8F9FA)
- Alerts: Coral Red (#FF6B6B), Sunny Yellow (#FFD93D)

### Typography

- Primary Font: Inter (Google Fonts)
- Headers: 24px (h1), 20px (h2)
- Body: 16px (default), 14px (subtle text)

## Accessibility

This application is designed with WCAG standards in mind:
- Contrast ratios ≥ 4.5:1 for text
- ARIA labels for screen readers
- Keyboard navigation support

## API Integration

The frontend connects to a Spring Boot backend via RESTful APIs for:
- File uploads with JWT authentication
- Data profiling and transformation
- Job management and monitoring

## License

This project is licensed under the MIT License - see the LICENSE file for details.
=======
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
>>>>>>> 1d91c842f638ad1bb744670d23d48d0e719a25b8
