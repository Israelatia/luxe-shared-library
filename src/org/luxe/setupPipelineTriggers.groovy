#!/usr/bin/env groovy

/**
 * Setup automatic pipeline triggers for CI/CD
 * 
 * @param config Map containing:
 *   - gitWebhook: Enable Git webhook triggers (default: true)
 *   - pollSCM: SCM polling schedule (optional, e.g., 'H/5 * * * *')
 *   - cron: Cron schedule for periodic builds (optional)
 *   - upstreamProjects: List of upstream projects to trigger on
 *   - branchFilter: Branch filter pattern (default: 'main,develop,release/*')
 */
def call(Map config = [:]) {
    def gitWebhook = config.gitWebhook ?: true
    def pollSCM = config.pollSCM
    def cron = config.cron
    def upstreamProjects = config.upstreamProjects ?: []
    def branchFilter = config.branchFilter ?: 'main,develop,release/*'
    
    echo "🔧 Setting up pipeline triggers..."
    echo "📡 Git webhook: ${gitWebhook ? 'enabled' : 'disabled'}"
    echo "🔍 SCM polling: ${pollSCM ?: 'disabled'}"
    echo "⏰ Cron schedule: ${cron ?: 'disabled'}"
    echo "🌿 Branch filter: ${branchFilter}"
    
    def triggers = []
    
    // GitHub webhook trigger
    if (gitWebhook) {
        triggers.add('githubPush()')
        echo "✅ GitHub webhook trigger configured"
    }
    
    // SCM polling
    if (pollSCM) {
        triggers.add("pollSCM('${pollSCM}')")
        echo "✅ SCM polling configured: ${pollSCM}"
    }
    
    // Cron trigger
    if (cron) {
        triggers.add("cron('${cron}')")
        echo "✅ Cron trigger configured: ${cron}"
    }
    
    // Upstream project triggers
    if (upstreamProjects) {
        def upstreamList = upstreamProjects.join(',')
        triggers.add("upstream(upstreamProjects: '${upstreamList}', threshold: hudson.model.Result.SUCCESS)")
        echo "✅ Upstream triggers configured: ${upstreamList}"
    }
    
    // Generate triggers configuration
    def triggerConfig = generateTriggerConfig(triggers, branchFilter)
    
    echo "📋 Trigger configuration generated:"
    echo triggerConfig
    
    return [
        status: 'success',
        triggers: triggers,
        config: triggerConfig
    ]
}

def generateTriggerConfig(triggers, branchFilter) {
    if (!triggers) {
        return "// No automatic triggers configured"
    }
    
    def config = """
// Automatic Pipeline Triggers Configuration
pipeline {
    triggers {
        ${triggers.join('\n        ')}
    }
    
    // Branch-based trigger filtering
    when {
        anyOf {
            ${generateBranchConditions(branchFilter)}
        }
    }
}"""
    
    return config
}

def generateBranchConditions(branchFilter) {
    def patterns = branchFilter.split(',').collect { it.trim() }
    def conditions = []
    
    patterns.each { pattern ->
        if (pattern.contains('*')) {
            conditions.add("branch '${pattern}'")
        } else {
            conditions.add("branch '${pattern}'")
        }
    }
    
    return conditions.join('\n            ')
}

// Helper function to setup webhook in GitHub repository
def setupGitHubWebhook(repoUrl, jenkinsUrl, credentialsId) {
    echo "🔗 Setting up GitHub webhook..."
    
    try {
        withCredentials([string(credentialsId: credentialsId, variable: 'GITHUB_TOKEN')]) {
            def webhookUrl = "${jenkinsUrl}/github-webhook/"
            
            sh """
                # Create GitHub webhook using API
                curl -X POST \\
                    -H "Authorization: token \$GITHUB_TOKEN" \\
                    -H "Accept: application/vnd.github.v3+json" \\
                    ${repoUrl}/hooks \\
                    -d '{
                        "name": "web",
                        "active": true,
                        "events": ["push", "pull_request"],
                        "config": {
                            "url": "${webhookUrl}",
                            "content_type": "json",
                            "insecure_ssl": "0"
                        }
                    }'
            """
        }
        
        echo "✅ GitHub webhook configured successfully"
        return true
        
    } catch (Exception e) {
        echo "⚠️ Failed to setup GitHub webhook: ${e.message}"
        echo "Please configure webhook manually in GitHub repository settings"
        return false
    }
}
