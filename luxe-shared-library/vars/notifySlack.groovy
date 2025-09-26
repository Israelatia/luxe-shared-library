#!/usr/bin/env groovy

/**
 * Send notifications to Slack about pipeline status
 * 
 * @param config Map containing:
 *   - channel: Slack channel (default: '#ci-cd')
 *   - status: Pipeline status ('success', 'failure', 'unstable', 'started')
 *   - message: Custom message (optional)
 *   - credentialsId: Slack webhook credentials ID (default: 'slack-webhook')
 *   - includeDetails: Whether to include build details (default: true)
 */
def call(Map config = [:]) {
    def channel = config.channel ?: '#ci-cd'
    def status = config.status ?: 'unknown'
    def customMessage = config.message ?: ''
    def credentialsId = config.credentialsId ?: 'slack-webhook'
    def includeDetails = config.includeDetails ?: true
    
    def color = getStatusColor(status)
    def emoji = getStatusEmoji(status)
    def title = "${emoji} Pipeline ${status.toUpperCase()}"
    
    def message = buildSlackMessage(title, status, customMessage, includeDetails, color)
    
    try {
        withCredentials([string(credentialsId: credentialsId, variable: 'SLACK_WEBHOOK')]) {
            sh """
                curl -X POST -H 'Content-type: application/json' \
                --data '${message}' \
                \$SLACK_WEBHOOK
            """
        }
        echo "📱 Slack notification sent to ${channel}"
    } catch (Exception e) {
        echo "⚠️ Failed to send Slack notification: ${e.message}"
    }
}

def getStatusColor(status) {
    switch (status.toLowerCase()) {
        case 'success':
            return 'good'
        case 'failure':
            return 'danger'
        case 'unstable':
            return 'warning'
        case 'started':
            return '#36a64f'
        default:
            return '#808080'
    }
}

def getStatusEmoji(status) {
    switch (status.toLowerCase()) {
        case 'success':
            return '✅'
        case 'failure':
            return '❌'
        case 'unstable':
            return '⚠️'
        case 'started':
            return '🚀'
        default:
            return 'ℹ️'
    }
}

def buildSlackMessage(title, status, customMessage, includeDetails, color) {
    def fields = []
    
    if (includeDetails) {
        fields = [
            [
                title: "Job",
                value: "${env.JOB_NAME}",
                short: true
            ],
            [
                title: "Build",
                value: "#${env.BUILD_NUMBER}",
                short: true
            ],
            [
                title: "Branch",
                value: "${env.GIT_BRANCH ?: 'unknown'}",
                short: true
            ],
            [
                title: "Duration",
                value: "${currentBuild.durationString ?: 'unknown'}",
                short: true
            ]
        ]
        
        if (env.GIT_COMMIT) {
            fields.add([
                title: "Commit",
                value: "${env.GIT_COMMIT.take(8)}",
                short: true
            ])
        }
    }
    
    def attachment = [
        color: color,
        title: title,
        title_link: "${env.BUILD_URL}",
        fields: fields,
        footer: "Jenkins CI/CD",
        ts: System.currentTimeMillis() / 1000
    ]
    
    if (customMessage) {
        attachment.text = customMessage
    }
    
    def payload = [
        attachments: [attachment]
    ]
    
    return groovy.json.JsonBuilder(payload).toString()
}
