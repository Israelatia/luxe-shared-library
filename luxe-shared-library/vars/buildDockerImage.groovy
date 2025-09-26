#!/usr/bin/env groovy

/**
 * Build Docker image with standardized tagging strategy
 * 
 * @param config Map containing:
 *   - imageName: Name of the Docker image
 *   - dockerFile: Path to Dockerfile (optional, defaults to 'Dockerfile')
 *   - buildContext: Build context path (optional, defaults to '.')
 *   - registry: Target registry (optional)
 *   - tags: List of additional tags (optional)
 */
def call(Map config) {
    def imageName = config.imageName ?: error("imageName is required")
    def dockerFile = config.dockerFile ?: 'Dockerfile'
    def buildContext = config.buildContext ?: '.'
    def registry = config.registry ?: ''
    def additionalTags = config.tags ?: []
    
    // Generate standard tags
    def gitCommit = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
    def gitBranch = sh(returnStdout: true, script: 'git rev-parse --abbrev-ref HEAD').trim()
    def buildNumber = env.BUILD_NUMBER
    def semverVersion = "1.0.${buildNumber}"
    
    def standardTags = [
        'latest',
        semverVersion,
        "commit-${gitCommit}",
        "branch-${gitBranch}-${buildNumber}",
        "build-${buildNumber}"
    ]
    
    def allTags = standardTags + additionalTags
    def fullImageName = registry ? "${registry}/${imageName}" : imageName
    
    echo "🔨 Building Docker image: ${fullImageName}"
    echo "📋 Tags: ${allTags.join(', ')}"
    
    // Build the image
    def image = docker.build("${fullImageName}:latest", "-f ${dockerFile} ${buildContext}")
    
    // Apply all tags
    allTags.each { tag ->
        if (tag != 'latest') {
            sh "docker tag ${fullImageName}:latest ${fullImageName}:${tag}"
        }
    }
    
    echo "✅ Image built successfully with ${allTags.size()} tags"
    
    return [
        image: image,
        fullName: fullImageName,
        tags: allTags,
        commit: gitCommit,
        semver: semverVersion
    ]
}
