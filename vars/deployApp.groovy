#!/usr/bin/env groovy

def call(Map params = [:]) {
    // Required parameters
    def environment = params.environment ?: error('environment parameter is required')
    def composeFile = params.composeFile ?: "docker-compose.${environment}.yml"
    
    // Optional parameters with defaults
    def registry = params.registry ?: env.DOCKER_REGISTRY
    def registryCreds = params.registryCreds ?: env.DOCKER_CREDENTIALS
    def tag = params.tag ?: "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
    def healthCheckUrl = params.healthCheckUrl
    def healthCheckTimeout = params.healthCheckTimeout ?: 300 // 5 minutes
    def rollbackOnFailure = params.rollbackOnFailure != null ? params.rollbackOnFailure : true
    
    echo "Deploying to ${environment} environment using ${composeFile}"
    
    // Login to registry if credentials are provided
    if (registryCreds) {
        withCredentials([usernamePassword(
            credentialsId: registryCreds,
            usernameVariable: 'REGISTRY_USER',
            passwordVariable: 'REGISTRY_PASSWORD'
        )]) {
            sh """
                echo "Logging into Docker registry at ${registry}"
                docker login ${registry} -u "$REGISTRY_USER" -p "$REGISTRY_PASSWORD"
            """
        }
    }
    
    try {
        // Pull the latest images
        sh """
            # Set environment variables for docker-compose
            export IMAGE_TAG=${tag}
            export DOCKER_REGISTRY=${registry}
            
            # Pull the latest images
            docker-compose -f ${composeFile} pull || echo "Warning: Failed to pull some images"
            
            # Start the services
            docker-compose -f ${composeFile} up -d --build --force-recreate
            
            # Check if all services are running
            docker-compose -f ${composeFile} ps
        """
        
        // Perform health check if URL is provided
        if (healthCheckUrl) {
            echo "Performing health check on ${healthCheckUrl}"
            def healthy = false
            def startTime = System.currentTimeMillis()
            def timeout = healthCheckTimeout * 1000 // Convert to milliseconds
            
            while ((System.currentTimeMillis() - startTime) < timeout) {
                try {
                    def response = httpRequest(
                        url: healthCheckUrl,
                        httpMode: 'GET',
                        validResponseCodes: '100:599',
                        timeout: 30
                    )
                    
                    if (response.status == 200) {
                        echo "Health check passed: ${response.content}"
                        healthy = true
                        break
                    } else {
                        echo "Health check failed with status ${response.status}, retrying..."
                        sleep(10) // Wait 10 seconds before retrying
                    }
                } catch (Exception e) {
                    echo "Health check error: ${e.message}, retrying..."
                    sleep(10) // Wait 10 seconds before retrying
                }
            }
            
            if (!healthy) {
                error("Health check failed after ${healthCheckTimeout} seconds")
            }
        }
        
        // Clean up old images
        sh """
            # Remove unused images
            docker image prune -af --filter "until=24h"
            
            # Remove stopped containers
            docker container prune -f --filter "until=24h"
            
            # Remove unused volumes
            docker volume prune -f
        """
        
        // Send deployment notification
        emailext (
            subject: "✅ Deployment to ${environment} completed - SUCCESS",
            body: """
                <p>Successfully deployed ${env.JOB_NAME} #${env.BUILD_NUMBER} to ${environment} environment.</p>
                <p>Commit: ${env.GIT_COMMIT.take(7)} - ${env.GIT_COMMIT_MESSAGE ?: 'No commit message'}</p>
                <p>Build URL: ${env.BUILD_URL}</p>
                <p>Deployed Images:</p>
                <ul>
                    <li>Backend: ${registry}/${env.BACKEND_IMAGE}:${tag}</li>
                    <li>Frontend: ${registry}/${env.FRONTEND_IMAGE}:${tag}</li>
                </ul>
            """,
            to: 'dev-team@example.com',
            recipientProviders: [[$class: 'DevelopersRecipientProvider']]
        )
        
    } catch (Exception e) {
        // Rollback on failure if enabled
        if (rollbackOnFailure) {
            echo "Deployment failed, attempting rollback..."
            
            try {
                sh """
                    # Rollback to previous version
                    docker-compose -f ${composeFile} down || true
                    
                    # Start previous version if exists
                    if docker ps -a | grep -q "${environment}_app_"; then
                        docker start ${environment}_app_previous || true
                    fi
                """
                
                // Send rollback notification
                emailext (
                    subject: "🔄 Rollback on ${environment} - FAILED",
                    body: """
                        <p>Deployment of ${env.JOB_NAME} #${env.BUILD_NUMBER} to ${environment} failed and was rolled back.</p>
                        <p>Error: ${e.message}</p>
                        <p>Build URL: ${env.BUILD_URL}</p>
                        <p>Previous version has been restored.</p>
                    """,
                    to: 'devops-alerts@example.com',
                    recipientProviders: [[$class: 'RequestorRecipientProvider']]
                )
                
            } catch (rollbackError) {
                echo "Rollback failed: ${rollbackError.message}"
                
                // Send rollback failure notification
                emailext (
                    subject: "❌ CRITICAL: Rollback on ${environment} FAILED",
                    body: """
                        <p>CRITICAL: Deployment of ${env.JOB_NAME} #${env.BUILD_NUMBER} to ${environment} failed and rollback also failed!</p>
                        <p>Deployment Error: ${e.message}</p>
                        <p>Rollback Error: ${rollbackError.message}</p>
                        <p>Build URL: ${env.BUILD_URL}</p>
                        <p>MANUAL INTERVENTION REQUIRED!</p>
                    """,
                    to: 'devops-alerts@example.com',
                    cc: 'engineering-managers@example.com',
                    recipientProviders: [[$class: 'RequestorRecipientProvider']]
                )
            }
        }
        
        // Re-throw the exception to fail the build
        throw e
        
    } finally {
        // Always logout from registry
        if (registryCreds) {
            sh "docker logout ${registry} || true"
        }
    }
}
