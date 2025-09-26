#!/usr/bin/env groovy

/**
 * Run security scans using Snyk
 * 
 * @param config Map containing:
 *   - scanType: 'container', 'dependencies', or 'both' (default: 'both')
 *   - images: List of Docker images to scan (for container scans)
 *   - projectPath: Path to project for dependency scan (default: '.')
 *   - severityThreshold: Minimum severity level (default: 'high')
 *   - credentialsId: Snyk token credentials ID (default: 'snyk-token')
 *   - failOnIssues: Whether to fail build on security issues (default: false)
 */
def call(Map config = [:]) {
    def scanType = config.scanType ?: 'both'
    def images = config.images ?: []
    def projectPath = config.projectPath ?: '.'
    def severityThreshold = config.severityThreshold ?: 'high'
    def credentialsId = config.credentialsId ?: 'snyk-token'
    def failOnIssues = config.failOnIssues ?: false
    
    echo "🔒 Running security scan (${scanType}) with threshold: ${severityThreshold}"
    
    def scanResults = [:]
    
    withCredentials([string(credentialsId: credentialsId, variable: 'SNYK_TOKEN')]) {
        try {
            if (scanType in ['container', 'both'] && images) {
                echo "🐳 Scanning container images..."
                scanResults.container = scanContainerImages(images, severityThreshold)
            }
            
            if (scanType in ['dependencies', 'both']) {
                echo "📦 Scanning dependencies..."
                scanResults.dependencies = scanDependencies(projectPath, severityThreshold)
            }
            
            // Generate summary report
            generateSecurityReport(scanResults)
            
            // Check if we should fail the build
            if (failOnIssues && hasHighSeverityIssues(scanResults)) {
                error("Security scan found high severity issues. Build failed.")
            }
            
            echo "✅ Security scan completed successfully"
            return scanResults
            
        } catch (Exception e) {
            echo "⚠️ Security scan encountered issues: ${e.message}"
            if (failOnIssues) {
                throw e
            }
            return [error: e.message]
        }
    }
}

def scanContainerImages(images, threshold) {
    def results = []
    images.each { image ->
        try {
            def output = sh(
                script: "snyk container test ${image} --severity-threshold=${threshold} --json",
                returnStdout: true
            )
            results.add([image: image, status: 'clean', output: output])
        } catch (Exception e) {
            results.add([image: image, status: 'issues', error: e.message])
        }
    }
    return results
}

def scanDependencies(projectPath, threshold) {
    try {
        dir(projectPath) {
            def output = sh(
                script: "snyk test --severity-threshold=${threshold} --json",
                returnStdout: true
            )
            return [status: 'clean', output: output]
        }
    } catch (Exception e) {
        return [status: 'issues', error: e.message]
    }
}

def generateSecurityReport(results) {
    def reportFile = 'security-report.json'
    writeJSON file: reportFile, json: results
    archiveArtifacts artifacts: reportFile, allowEmptyArchive: true
}

def hasHighSeverityIssues(results) {
    return results.any { key, value ->
        if (value instanceof List) {
            return value.any { it.status == 'issues' }
        } else {
            return value.status == 'issues'
        }
    }
}
