#!/usr/bin/env groovy

/**
 * Deploy application using Docker Compose with environment-specific configuration
 * 
 * @param config Map containing:
 *   - environment: Target environment (development, staging, production)
 *   - registry: Docker registry to pull images from
 *   - appName: Application name
 *   - composeFile: Path to docker-compose file (default: 'docker-compose.yml')
 *   - envFile: Environment-specific .env file (optional)
 *   - healthCheck: Whether to perform health checks (default: true)
 *   - timeout: Deployment timeout in seconds (default: 300)
 */
def call(Map config) {
    def environment = config.environment ?: error("environment is required")
    def registry = config.registry ?: error("registry is required")
    def appName = config.appName ?: error("appName is required")
    def composeFile = config.composeFile ?: 'docker-compose.yml'
    def envFile = config.envFile
    def healthCheck = config.healthCheck ?: true
    def timeout = config.timeout ?: 300
    
    echo "🚀 Deploying ${appName} to ${environment} environment"
    echo "📦 Registry: ${registry}"
    echo "🐳 Compose file: ${composeFile}"
    
    try {
        // Set environment variables for deployment
        def envVars = [
            "DEPLOY_ENV=${environment}",
            "DEPLOY_REGISTRY=${registry}",
            "APP_NAME=${appName}",
            "DEPLOY_TAG=${environment}"
        ]
        
        // Add custom environment file if specified
        if (envFile && fileExists(envFile)) {
            echo "📄 Using environment file: ${envFile}"
            envVars.add("ENV_FILE=${envFile}")
        }
        
        // Pull latest images
        echo "📥 Pulling latest images..."
        pullImages(registry, appName, environment)
        
        // Deploy with docker-compose
        echo "🐳 Starting deployment..."
        def deployResult = deployWithCompose(composeFile, envVars, timeout)
        
        // Perform health checks if enabled
        if (healthCheck) {
            echo "🏥 Performing health checks..."
            performHealthChecks(appName, environment, timeout)
        }
        
        // Validate deployment
        validateDeployment(appName, environment)
        
        echo "✅ Deployment completed successfully"
        return [
            status: 'success',
            environment: environment,
            registry: registry,
            timestamp: new Date().toString()
        ]
        
    } catch (Exception e) {
        echo "❌ Deployment failed: ${e.message}"
        
        // Attempt rollback
        echo "🔄 Attempting rollback..."
        rollbackDeployment(composeFile, envVars)
        
        throw e
    }
}

def pullImages(registry, appName, environment) {
    def images = [
        "${registry}/${appName}-backend:${environment}",
        "${registry}/${appName}-frontend:${environment}"
    ]
    
    images.each { image ->
        try {
            sh "docker pull ${image}"
            echo "✅ Pulled ${image}"
        } catch (Exception e) {
            echo "⚠️ Failed to pull ${image}, trying latest tag..."
            def latestImage = image.replaceAll(":${environment}", ":latest")
            sh "docker pull ${latestImage}"
            sh "docker tag ${latestImage} ${image}"
        }
    }
}

def deployWithCompose(composeFile, envVars, timeout) {
    def envString = envVars.join(' ')
    
    sh """
        # Stop existing containers
        ${envString} docker-compose -f ${composeFile} down --remove-orphans || true
        
        # Start new containers
        ${envString} docker-compose -f ${composeFile} up -d
        
        # Wait for containers to start
        sleep 10
    """
    
    return true
}

def performHealthChecks(appName, environment, timeout) {
    def healthEndpoints = [
        [name: 'backend', url: 'http://localhost:8000/', port: 8000],
        [name: 'frontend', url: 'http://localhost:80/', port: 80]
    ]
    
    def startTime = System.currentTimeMillis()
    def timeoutMs = timeout * 1000
    
    healthEndpoints.each { endpoint ->
        echo "🔍 Checking ${endpoint.name} health..."
        
        def healthy = false
        def attempts = 0
        def maxAttempts = 30
        
        while (!healthy && attempts < maxAttempts && (System.currentTimeMillis() - startTime) < timeoutMs) {
            try {
                def response = sh(
                    script: "curl -f -s -o /dev/null -w '%{http_code}' ${endpoint.url}",
                    returnStdout: true
                ).trim()
                
                if (response == '200') {
                    healthy = true
                    echo "✅ ${endpoint.name} is healthy"
                } else {
                    echo "⏳ ${endpoint.name} not ready (HTTP ${response}), attempt ${attempts + 1}/${maxAttempts}"
                }
            } catch (Exception e) {
                echo "⏳ ${endpoint.name} not ready, attempt ${attempts + 1}/${maxAttempts}"
            }
            
            if (!healthy) {
                sleep(10)
                attempts++
            }
        }
        
        if (!healthy) {
            error("Health check failed for ${endpoint.name} after ${maxAttempts} attempts")
        }
    }
}

def validateDeployment(appName, environment) {
    echo "🔍 Validating deployment..."
    
    // Check running containers
    def containers = sh(
        script: "docker ps --filter 'name=${appName}' --format '{{.Names}}\t{{.Status}}'",
        returnStdout: true
    ).trim()
    
    if (!containers) {
        error("No containers found for application ${appName}")
    }
    
    echo "📊 Running containers:"
    echo containers
    
    // Check container health
    def healthyContainers = sh(
        script: "docker ps --filter 'name=${appName}' --filter 'status=running' --quiet | wc -l",
        returnStdout: true
    ).trim() as Integer
    
    if (healthyContainers < 2) {
        error("Expected at least 2 healthy containers, found ${healthyContainers}")
    }
    
    echo "✅ Deployment validation passed"
}

def rollbackDeployment(composeFile, envVars) {
    try {
        def envString = envVars.join(' ')
        sh "${envString} docker-compose -f ${composeFile} down --remove-orphans"
        echo "🔄 Rollback completed"
    } catch (Exception e) {
        echo "⚠️ Rollback failed: ${e.message}"
    }
}
