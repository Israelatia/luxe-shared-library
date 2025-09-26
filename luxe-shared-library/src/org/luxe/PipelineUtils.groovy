package org.luxe

/**
 * Utility methods for Jenkins pipeline operations
 */
class PipelineUtils implements Serializable {
    def script

    PipelineUtils(script) {
        this.script = script
    }

    /**
     * Get current Git branch name
     */
    String getGitBranch() {
        return script.env.GIT_BRANCH ? script.env.GIT_BRANCH.split('/').last() : 'main'
    }

    /**
     * Get short Git commit hash
     */
    String getGitCommit() {
        return script.sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    }

    /**
     * Generate Docker image tag based on branch and commit
     */
    String generateImageTag(String prefix = '') {
        def branch = getGitBranch()
        def commit = getGitCommit()
        return "${prefix}${branch}-${commit}".toLowerCase()
    }

    /**
     * Execute shell command with error handling
     */
    def safeSh(String command) {
        try {
            return script.sh(script: command, returnStdout: true).trim()
        } catch (Exception e) {
            script.error("Command failed: ${command}\nError: ${e.message}")
        }
    }
}
