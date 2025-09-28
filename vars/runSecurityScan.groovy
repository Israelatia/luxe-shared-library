#!/usr/bin/env groovy

def call(Map params = [:]) {
    // Required parameters with defaults
    def scanType = params.type ?: 'all'  // 'all', 'sast', 'dependencies', 'container'
    def target = params.target ?: '.'
    def tool = params.tool ?: 'snyk'     // 'snyk', 'trivy', 'bandit', etc.
    
    // Snyk-specific parameters
    def snykOrg = params.snykOrg ?: env.SNYK_ORG
    def snykProject = params.snykProject ?: env.JOB_NAME
    def snykSeverity = params.snykSeverity ?: 'medium'
    
    echo "Running ${tool} security scan (${scanType}) on ${target}"
    
    dir(target) {
        try {
            // Install required tools
            if (tool == 'snyk') {
                // Install Snyk CLI if not already installed
                if (!fileExists('node_modules/.bin/snyk')) {
                    sh 'npm install -g snyk'
                }
                
                // Run Snyk auth
                withCredentials([string(credentialsId: 'snyk-auth-token', variable: 'SNYK_TOKEN')]) {
                    sh 'snyk auth ${SNYK_TOKEN}'
                    
                    // Run appropriate scan based on type
                    if (scanType == 'all' || scanType == 'sast') {
                        echo 'Running SAST scan...'
                        sh 'snyk code test --severity-threshold=${snykSeverity} --json > snyk-sast-results.json || true'
                        archiveArtifacts 'snyk-sast-results.json'
                    }
                    
                    if (scanType == 'all' || scanType == 'dependencies') {
                        echo 'Scanning dependencies...'
                        if (fileExists('package.json')) {
                            sh 'snyk test --severity-threshold=${snykSeverity} --json > snyk-deps-results.json || true'
                            archiveArtifacts 'snyk-deps-results.json'
                        }
                        if (fileExists('requirements.txt')) {
                            sh 'snyk test --file=requirements.txt --severity-threshold=${snykSeverity} --json > snyk-python-deps-results.json || true'
                            archiveArtifacts 'snyk-python-deps-results.json'
                        }
                    }
                    
                    if (scanType == 'all' || scanType == 'container') {
                        echo 'Scanning container images...'
                        if (params.dockerImage) {
                            sh 'snyk container test ${params.dockerImage} --severity-threshold=${snykSeverity} --json > snyk-container-results.json || true'
                            archiveArtifacts 'snyk-container-results.json'
                        }
                    }
                    
                    // Monitor project in Snyk UI
                    if (snykOrg) {
                        sh 'snyk monitor --org=${snykOrg} --project-name="${snykProject}"'
                    } else {
                        sh 'snyk monitor --project-name="${snykProject}"'
                    }
                }
                
            } else if (tool == 'bandit' && fileExists('**/*.py')) {
                // Python SAST with Bandit
                sh 'pip install bandit'
                sh 'bandit -r . -f json -o bandit-results.json || true'
                archiveArtifacts 'bandit-results.json'
                
            } else if (tool == 'trivy' && params.dockerImage) {
                // Container scanning with Trivy
                sh 'wget https://github.com/aquasecurity/trivy/releases/latest/download/trivy_0.45.1_Linux-64bit.deb'
                sh 'sudo dpkg -i trivy_0.45.1_Linux-64bit.deb'
                sh 'trivy image --format template --template "@/usr/local/share/trivy/templates/junit.tpl" -o trivy-scan-results.xml ${params.dockerImage} || true'
                junit 'trivy-scan-results.xml'
                archiveArtifacts 'trivy-scan-results.xml'
            }
            
        } catch (Exception e) {
            echo "Security scan failed: ${e.message}"
            if (params.failOnError) {
                error('Security scan found vulnerabilities above the threshold')
            }
        } finally {
            // Always publish results
            if (fileExists('**/*-results.json') || fileExists('**/*-results.xml')) {
                echo 'Publishing security scan results...'
            }
            
            // Send notification if configured
            if (params.notifyOnFailure && currentBuild.result == 'UNSTABLE') {
                emailext (
                    subject: "⚠️ Security Scan Results - ${currentBuild.currentResult}",
                    body: """
                        <p>Security scan for ${env.JOB_NAME} #${env.BUILD_NUMBER} found issues that require attention.</p>
                        <p>Build URL: ${env.BUILD_URL}</p>
                        <p>Please review the scan results and address any critical/high severity findings.</p>
                    """,
                    to: 'security-team@example.com',
                    recipientProviders: [[$class: 'DevelopersRecipientProvider']]
                )
            }
        }
    }
}
