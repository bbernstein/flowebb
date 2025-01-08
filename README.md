# FlowEbb Tide Application

FlowEbb is a web application that provides tide information and predictions using NOAA data. It consists of a Next.js frontend, Go backend running on AWS Lambda, and infrastructure managed with Terraform.

[![Verify](https://github.com/bbernstein/flowebb/actions/workflows/verify.yml/badge.svg)](https://github.com/bbernstein/flowebb/actions/workflows/verify.yml)

## Prerequisites

- Node.js v18 or later
- Go 1.21 or later
- Docker Desktop
- AWS SAM CLI
- AWS CLI configured with appropriate credentials
- Terraform v1.5.0 or later
- Git
- IntelliJ IDEA (recommended IDE)

## Repository Structure

```
flowebb/
├── frontend/          # Next.js frontend application
├── backend-go/        # Go backend application
├── infrastructure/    # Terraform infrastructure code
├── scripts/          # Development and deployment scripts
└── docs/             # Documentation
```

## Initial Setup

1. Clone the repository:
```bash
git clone https://github.com/bbernstein/flowebb.git
cd flowebb
```

2. Install frontend dependencies:
```bash
cd frontend
npm install
cd ..
```

3. Configure Docker network for SAM:
```bash
docker network create sam-network
```

## Local Development

### Starting the Development Environment

The easiest way to start all components is using the development script:

```bash
./scripts/dev.sh
```

This script will:
- Start DynamoDB Local and Admin UI
- Initialize DynamoDB tables
- Start the SAM API
- Start the Next.js frontend

### Manual Component Startup

If you prefer to start components individually:

1. Start DynamoDB Local:
```bash
docker-compose up -d
./scripts/init-local-dynamo.sh
```

2. Start the backend:
```bash
cd backend-go
./scripts/gostart.sh
```

3. Start the frontend:
```bash
cd frontend
npm run dev
```

The application will be available at:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- DynamoDB Admin: http://localhost:8001

## Development Workflow

### Branch Strategy

1. Create a new feature branch from main:
```bash
git checkout -b feature/your-feature-name
```

2. Make your changes and commit following conventional commit messages:
```bash
git add .
git commit -m "feat: add new feature"
```

3. Push your branch and create a Pull Request on GitHub:
```bash
git push -u origin feature/your-feature-name
```

### Code Quality Checks

Before submitting a PR, run the verification script:
```bash
./scripts/verify-local.sh
```

This performs:
- Frontend linting (`npm run lint`)
- Go linting and tests
- Terraform formatting and validation

### Running Tests

#### Frontend Tests
```bash
cd frontend
npm test
```

#### Backend Tests
```bash
cd backend-go
# Run tests with coverage
go test -coverprofile=test-results/coverage.out -covermode=atomic -coverpkg=./... ./...
# Generate coverage report
go tool cover -html=test-results/coverage.out -o test-results/coverage.html
```

Coverage requirements:
- Backend: Minimum 80% coverage
- Frontend: Minimum 70% coverage

### Debugging

#### Backend Debugging
1. Use the debug script:
```bash
./scripts/debug-sam.sh TidesFunction
```

2. In IntelliJ IDEA:
    - Create a "Remote JVM Debug" configuration
    - Set port to 5005
    - Start debugging

#### Frontend Debugging
- Use Chrome DevTools or the built-in debugger in IntelliJ IDEA
- React Developer Tools extension is recommended

## Deployment

### Production Deployment

Production deployments are automated via GitHub Actions when merging to main:

1. Infrastructure changes are applied first using Terraform
2. Backend Lambda functions are deployed
3. Frontend is built and deployed to CloudFront/S3

### Manual Deployment

If needed, components can be deployed manually:

#### Frontend
```bash
./scripts/deploy-frontend.sh prod
```

#### Backend
```bash
./scripts/deploy-go-lambda.sh
```

#### Infrastructure
```bash
cd infrastructure/terraform/environments/prod
terraform init
terraform plan
terraform apply
```

## Configuration

### Environment Variables

Frontend (.env.development, .env.production):
```
NEXT_PUBLIC_API_URL=http://localhost:8080  # Development
NEXT_PUBLIC_API_URL=https://api.flowebb.com # Production
```

Backend (environment variables in template.yaml):
```yaml
Environment:
  Variables:
    LOG_LEVEL: debug
    CACHE_ENABLE_LRU: "true"
    CACHE_ENABLE_DYNAMO: "true"
```

## Monitoring and Maintenance

### CloudWatch Logs
- Lambda function logs: /aws/lambda/flowebb-*
- API Gateway logs: /aws/apigateway/flowebb-*

### DynamoDB Tables
- stations-cache: Cached station data
- tide-predictions-cache: Cached tide predictions

### Health Checks
- CloudWatch alarms are configured for Lambda errors
- API Gateway dashboard provides request metrics

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
