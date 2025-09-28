# Jenkins Shared Library for Luxe Jewelry Store

This shared library provides reusable pipeline functions for the Luxe Jewelry Store CI/CD pipeline.

## 📁 Library Structure

```
jenkins-shared-library/
├── vars/                          # Global variables (pipeline steps)
│   ├── buildDockerImage.groovy    # Docker image building with tagging
│   ├── pushToRegistry.groovy      # Multi-registry image pushing
│   ├── runSecurityScan.groovy     # Snyk security scanning
│   ├── runTests.groovy            # Test execution with coverage
│   ├── runCodeQuality.groovy      # Code quality checks (linting)
│   ├── deployApplication.groovy   # Application deployment
│   └── notifySlack.groovy         # Slack notifications
├── src/                           # Groovy classes (optional)
└── resources/                     # Static resources (optional)
```

## 🚀 Usage in Jenkinsfile

### Basic Usage

```groovy
@Library('luxe-shared-library') _

pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                script {
                    def buildResult = buildDockerImage([
                        imageName: 'luxe-jewelry-store-backend',
                        dockerFile: 'backend/Dockerfile',
                        buildContext: './backend',
                        registry: 'localhost:8082'
                    ])
                    
                    pushToRegistry([
                        imageName: buildResult.fullName,
                        tags: buildResult.tags,
                        credentialsId: 'nexus-docker'
                    ])
                }
            }
        }
        
        stage('Test & Quality') {
            parallel {
                stage('Tests') {
                    steps {
                        script {
                            runTests([
                                framework: 'pytest',
                                testPath: 'tests/',
                                coverageThreshold: 80
                            ])
                        }
                    }
                }
                
                stage('Security') {
                    steps {
                        script {
                            runSecurityScan([
                                scanType: 'both',
                                images: ['luxe-jewelry-store-backend:latest'],
                                severityThreshold: 'high'
                            ])
                        }
                    }
                }
                
                stage('Code Quality') {
                    steps {
                        script {
                            runCodeQuality([
                                language: 'python',
                                sourcePath: 'backend/',
                                configFile: '.pylintrc'
                            ])
                        }
                    }
                }
            }
        }
        
        stage('Deploy') {
            steps {
                script {
                    deployApplication([
                        environment: params.DEPLOY_ENVIRONMENT,
                        registry: 'localhost:8082',
                        appName: 'luxe-jewelry-store',
                        healthCheck: true
                    ])
                }
            }
        }
    }
    
    post {
        always {
            script {
                notifySlack([
                    status: currentBuild.result ?: 'success',
                    channel: '#ci-cd'
                ])
            }
        }
    }
}
```

## 📚 Function Reference

### buildDockerImage(config)

Builds Docker images with standardized tagging strategy.

**Parameters:**
- `imageName` (required): Name of the Docker image
- `dockerFile` (optional): Path to Dockerfile (default: 'Dockerfile')
- `buildContext` (optional): Build context path (default: '.')
- `registry` (optional): Target registry
- `tags` (optional): Additional custom tags

**Returns:**
```groovy
[
    image: dockerImage,
    fullName: 'registry/imageName',
    tags: ['latest', '1.0.123', 'commit-abc123', ...],
    commit: 'abc123',
    semver: '1.0.123'
]
```

### pushToRegistry(config)

Pushes Docker images to registries with authentication.

**Parameters:**
- `imageName` (required): Full image name with registry
- `tags` (required): List of tags to push
- `credentialsId` (required): Jenkins credentials ID
- `registry` (optional): Registry URL

**Returns:** Array of push results with status

### runSecurityScan(config)

Runs security scans using Snyk.

**Parameters:**
- `scanType` (optional): 'container', 'dependencies', or 'both' (default: 'both')
- `images` (optional): List of Docker images to scan
- `projectPath` (optional): Path to project (default: '.')
- `severityThreshold` (optional): Minimum severity (default: 'high')
- `credentialsId` (optional): Snyk token credentials (default: 'snyk-token')
- `failOnIssues` (optional): Fail build on issues (default: false)

### runTests(config)

Runs tests with coverage reporting.

**Parameters:**
- `testType` (optional): 'unit', 'integration', or 'both' (default: 'unit')
- `testPath` (optional): Path to test files (default: 'tests/')
- `coverageThreshold` (optional): Minimum coverage % (default: 80)
- `framework` (optional): Testing framework (default: 'pytest')
- `requirements` (optional): Requirements file (default: 'requirements.txt')
- `publishResults` (optional): Publish results (default: true)

### runCodeQuality(config)

Runs code quality checks and linting.

**Parameters:**
- `language` (optional): Programming language (default: 'python')
- `sourcePath` (optional): Source code path (default: '.')
- `configFile` (optional): Linter config file
- `failOnIssues` (optional): Fail on quality issues (default: false)
- `tools` (optional): List of tools to run

### deployApplication(config)

Deploys application using Docker Compose.

**Parameters:**
- `environment` (required): Target environment
- `registry` (required): Docker registry
- `appName` (required): Application name
- `composeFile` (optional): Docker Compose file (default: 'docker-compose.yml')
- `envFile` (optional): Environment file
- `healthCheck` (optional): Perform health checks (default: true)
- `timeout` (optional): Deployment timeout (default: 300s)

### notifySlack(config)

Sends Slack notifications.

**Parameters:**
- `channel` (optional): Slack channel (default: '#ci-cd')
- `status` (optional): Pipeline status
- `message` (optional): Custom message
- `credentialsId` (optional): Slack webhook credentials (default: 'slack-webhook')
- `includeDetails` (optional): Include build details (default: true)

## ⚙️ Jenkins Configuration

### 1. Add Shared Library

In Jenkins:
1. Go to **Manage Jenkins** → **Configure System**
2. Scroll to **Global Pipeline Libraries**
3. Add library:
   - **Name**: `luxe-shared-library`
   - **Default version**: `main`
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Repository URL**: `https://github.com/your-org/jenkins-shared-library`

### 2. Required Credentials

Configure these credentials in Jenkins:
- `docker-hub`: Username/Password for Docker Hub
- `nexus-docker`: Username/Password for Nexus
- `snyk-token`: Secret Text for Snyk API
- `slack-webhook`: Secret Text for Slack webhook URL

### 3. Required Plugins

Install these Jenkins plugins:
- Pipeline: Groovy
- Docker Pipeline
- HTML Publisher
- Warnings Next Generation
- Slack Notification

## 🔧 Development Guidelines

### Adding New Functions

1. Create new `.groovy` file in `vars/` directory
2. Follow naming convention: `camelCase`
3. Include comprehensive documentation
4. Add error handling and logging
5. Return structured results

### Function Template

```groovy
#!/usr/bin/env groovy

/**
 * Function description
 * 
 * @param config Map containing:
 *   - param1: Description (required/optional, default: value)
 *   - param2: Description
 */
def call(Map config = [:]) {
    def param1 = config.param1 ?: error("param1 is required")
    def param2 = config.param2 ?: 'default-value'
    
    echo "🔧 Starting function..."
    
    try {
        // Function logic here
        
        echo "✅ Function completed successfully"
        return [status: 'success', data: result]
        
    } catch (Exception e) {
        echo "❌ Function failed: ${e.message}"
        throw e
    }
}
```

### Best Practices

1. **Error Handling**: Always include try-catch blocks
2. **Logging**: Use descriptive echo statements with emojis
3. **Validation**: Validate required parameters
4. **Documentation**: Include comprehensive parameter documentation
5. **Return Values**: Return structured data for chaining
6. **Flexibility**: Support optional parameters with sensible defaults

## 🧪 Testing

### Local Testing

```bash
# Validate Groovy syntax
groovy -cp . vars/functionName.groovy

# Run pipeline locally (requires Jenkins CLI)
java -jar jenkins-cli.jar build job-name
```

### Integration Testing

Create test pipelines in `test/` directory to validate functions work correctly in different scenarios.

## 📈 Benefits

### Code Reusability
- Shared functions across multiple pipelines
- Consistent implementation patterns
- Reduced code duplication

### Maintainability
- Centralized updates and improvements
- Version-controlled shared code
- Easier debugging and troubleshooting

### Standardization
- Consistent error handling and logging
- Standardized parameter patterns
- Uniform return value structures

### Team Productivity
- Faster pipeline development
- Reduced learning curve for new team members
- Focus on business logic rather than infrastructure code

This shared library provides a solid foundation for building maintainable, scalable CI/CD pipelines.
