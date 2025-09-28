#!/usr/bin/env groovy

def call(Map params = [:]) {
    // Required parameters
    def imageName = params.imageName ?: error('imageName parameter is required')
    def dockerfilePath = params.dockerfile ?: 'Dockerfile'
    def contextPath = params.context ?: '.'
    def buildArgs = params.buildArgs ?: ''
    
    // Optional parameters with defaults
    def registry = params.registry ?: env.DOCKER_REGISTRY
    def registryCreds = params.registryCreds ?: env.DOCKER_CREDENTIALS
    def tag = params.tag ?: "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
    def pushToRegistry = params.pushToRegistry != null ? params.pushToRegistry : true
    
    // Full image reference
    def fullImageName = "${registry}/${imageName}:${tag}"
    
    echo "Building Docker image: ${fullImageName}"
    
    // Build the Docker image
    docker.build(fullImageName, "-f ${dockerfilePath} ${buildArgs} ${contextPath}")
    
    // Push to registry if enabled
    if (pushToRegistry) {
        withCredentials([usernamePassword(
            credentialsId: registryCreds,
            usernameVariable: 'REGISTRY_USER',
            passwordVariable: 'REGISTRY_PASSWORD'
        )]) {
            sh """
                echo "Pushing ${fullImageName} to registry..."
                docker login ${registry} -u "$REGISTRY_USER" -p "$REGISTRY_PASSWORD"
                docker push ${fullImageName}
                
                # If on main branch, also tag as latest and push
                if [ "${env.BRANCH_NAME}" = "main" ]; then
                    docker tag ${fullImageName} ${registry}/${imageName}:latest
                    docker push ${registry}/${imageName}:latest
                fi
                
                # Clean up
                docker logout ${registry} || true
            """
        }
    }
    
    return fullImageName
}
