# Local Development Setup Guide

## Prerequisites

- Node.js (v18 or later)
- Java Development Kit (JDK) 11
- Docker Desktop
- AWS SAM CLI
- AWS CLI
- Git

## Initial Setup

1. Clone the repository and install dependencies:
```bash
git clone <repository-url>
cd tidechart

# Install frontend dependencies
cd frontend
npm install
cd ..
```

2. Configure Docker socket (MacOS only):
```bash
# For MacOS users running newer versions of Docker Desktop
sudo ln -s $HOME/.docker/run/docker.sock /var/run/docker.sock
```

3. Create Docker network for SAM:
```bash
docker network create sam-network
```

## Running DynamoDB Local

1. Start DynamoDB Local and admin interface:
```bash
docker-compose up -d
```

2. Initialize DynamoDB tables:
```bash
./scripts/init-local-dynamo.sh
```

You can access the DynamoDB admin interface at http://localhost:8001

## Starting the Backend

1. Build the backend:
```bash
cd backend
./gradlew build
cd ..
```

2. Build and start SAM local API:
```bash
sam build
sam local start-api --warm-containers LAZY --docker-network sam-network --port 8080 --debug-port 5005
```

The backend API will be available at http://localhost:8080

## Starting the Frontend

1. Start the Next.js development server:
```bash
cd frontend
npm run dev
```

The frontend will be available at http://localhost:3000

## Development Scripts

The project includes several utility scripts in the `scripts/` directory:

- `scripts/dev.sh`: Starts both frontend and backend services
- `scripts/clear-dynamo.sh`: Clears all DynamoDB tables and recreates them
- `scripts/init-local-dynamo.sh`: Initializes DynamoDB tables
- `scripts/debug-sam.sh`: Starts SAM in debug mode

## Environment Variables

### Frontend (.env.development)
```
NEXT_PUBLIC_API_URL=http://localhost:8080
```

### Backend
The backend uses default local development settings. No additional environment variables are required for local development.

## Debugging

### Backend Debugging
You can attach a debugger to the SAM Lambda functions using port 5005.

In IntelliJ IDEA:
1. Go to Run -> Edit Configurations
2. Add new "Remote JVM Debug"
3. Set port to 5005
4. Start debugging

### Frontend Debugging
You can use Chrome DevTools or the built-in debugger in your IDE for frontend debugging.

## Common Issues and Solutions

### Docker Socket Not Found
If you see an error about Docker not being reachable:
```bash
# For MacOS
sudo ln -s $HOME/.docker/run/docker.sock /var/run/docker.sock
```

### DynamoDB Tables Not Created
If the DynamoDB tables are missing:
```bash
./scripts/clear-dynamo.sh
./scripts/init-local-dynamo.sh
```

### SAM Build Issues
If you encounter SAM build issues:
```bash
sam build --force-image-build
```

## Testing

### Backend Tests
```bash
cd backend
./gradlew test
```

### Frontend Tests
```bash
cd frontend
npm test
```

## Additional Tools

### DynamoDB Admin UI
- URL: http://localhost:8001
- Useful for viewing and modifying DynamoDB data

### API Documentation
- Available at http://localhost:8080/api/docs when running locally

## Project Structure

```
tidechart/
├── frontend/          # Next.js frontend application
├── backend/           # Kotlin/SAM backend application
├── scripts/           # Development utility scripts
├── docker-compose.yml # Local development services
└── template.yaml      # SAM template
```

## Contributing

1. Create a new branch for your feature
2. Make your changes
3. Run tests
4. Submit a pull request

For more detailed information about specific components, please refer to:
- [Frontend Documentation](frontend/README.md)
- [Backend Documentation](backend/README.md)
- [API Documentation](backend/docs/api-docs.md)
