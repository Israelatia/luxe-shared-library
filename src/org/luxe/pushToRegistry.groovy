#!/usr/bin/env groovy

/**
 * Push Docker image to registry with authentication
 * 
 * @param config Map containing:
 *   - imageName: Full image name with registry
 *   - tags: List of tags to push
 *   - credentialsId: Jenkins credentials ID for registry authentication
 *   - registry: Registry URL (optional, extracted from imageName if not provided)
 */
def call(Map config) {
    def imageName = config.imageName ?: error("imageName is required")
    def tags = config.tags ?: ['latest']
    def credentialsId = config.credentialsId ?: error("credentialsId is required")
    def registry = config.registry ?: imageName.split('/')[0]
    
    echo "📦 Pushing ${imageName} to registry: ${registry}"
    echo "🏷️  Tags: ${tags.join(', ')}"
    
    withCredentials([usernamePassword(
        credentialsId: credentialsId,
        usernameVariable: 'REGISTRY_USER',
        passwordVariable: 'REGISTRY_PASS'
    )]) {
        // Login to registry
        sh "echo \$REGISTRY_PASS | docker login ${registry} -u \$REGISTRY_USER --password-stdin"
        
        // Push all tags
        def pushResults = []
        tags.each { tag ->
            try {
                sh "docker push ${imageName}:${tag}"
                pushResults.add([tag: tag, status: 'success'])
                echo "✅ Pushed ${imageName}:${tag}"
            } catch (Exception e) {
                pushResults.add([tag: tag, status: 'failed', error: e.message])
                echo "❌ Failed to push ${imageName}:${tag}: ${e.message}"
            }
        }
        
        // Logout for security
        sh "docker logout ${registry}"
        
        def successCount = pushResults.count { it.status == 'success' }
        echo "📊 Push Summary: ${successCount}/${tags.size()} tags pushed successfully"
        
        return pushResults
    }
}
