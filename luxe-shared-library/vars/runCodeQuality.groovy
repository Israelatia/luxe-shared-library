#!/usr/bin/env groovy

/**
 * Run code quality checks including linting and static analysis
 * 
 * @param config Map containing:
 *   - language: Programming language ('python', 'javascript', 'java', etc.)
 *   - sourcePath: Path to source code (default: '.')
 *   - configFile: Path to linter config file (optional)
 *   - failOnIssues: Whether to fail build on quality issues (default: false)
 *   - tools: List of tools to run (default: language-specific)
 */
def call(Map config = [:]) {
    def language = config.language ?: 'python'
    def sourcePath = config.sourcePath ?: '.'
    def configFile = config.configFile
    def failOnIssues = config.failOnIssues ?: false
    def tools = config.tools ?: getDefaultTools(language)
    
    echo "🔍 Running code quality checks for ${language}"
    echo "📁 Source path: ${sourcePath}"
    echo "🛠️  Tools: ${tools.join(', ')}"
    
    def results = [:]
    
    try {
        tools.each { tool ->
            echo "🔧 Running ${tool}..."
            results[tool] = runQualityTool(tool, language, sourcePath, configFile)
        }
        
        // Generate quality report
        generateQualityReport(results)
        
        // Check if we should fail the build
        if (failOnIssues && hasQualityIssues(results)) {
            error("Code quality checks found issues. Build failed.")
        }
        
        echo "✅ Code quality checks completed"
        return results
        
    } catch (Exception e) {
        echo "❌ Code quality checks failed: ${e.message}"
        if (failOnIssues) {
            throw e
        }
        return [error: e.message]
    }
}

def getDefaultTools(language) {
    switch (language.toLowerCase()) {
        case 'python':
            return ['pylint', 'flake8', 'bandit']
        case 'javascript':
            return ['eslint', 'jshint']
        case 'java':
            return ['checkstyle', 'spotbugs']
        case 'go':
            return ['golint', 'go vet']
        default:
            return ['generic']
    }
}

def runQualityTool(tool, language, sourcePath, configFile) {
    def result = [tool: tool, status: 'success', issues: 0]
    
    try {
        switch (tool) {
            case 'pylint':
                result = runPylint(sourcePath, configFile)
                break
            case 'flake8':
                result = runFlake8(sourcePath, configFile)
                break
            case 'bandit':
                result = runBandit(sourcePath)
                break
            case 'eslint':
                result = runESLint(sourcePath, configFile)
                break
            default:
                echo "⚠️ Unknown tool: ${tool}"
                result.status = 'skipped'
        }
    } catch (Exception e) {
        result.status = 'failed'
        result.error = e.message
    }
    
    return result
}

def runPylint(sourcePath, configFile) {
    def configArg = configFile ? "--rcfile=${configFile}" : ""
    def output = sh(
        script: """
            python3 -m pylint ${sourcePath} \
                ${configArg} \
                --output-format=parseable \
                --reports=yes \
                --exit-zero > pylint-report.txt
            cat pylint-report.txt
        """,
        returnStdout: true
    )
    
    def issues = (output =~ /\d+:\d+:/).size()
    
    return [
        tool: 'pylint',
        status: issues > 0 ? 'issues' : 'clean',
        issues: issues,
        reportFile: 'pylint-report.txt'
    ]
}

def runFlake8(sourcePath, configFile) {
    def configArg = configFile ? "--config=${configFile}" : ""
    def output = sh(
        script: "python3 -m flake8 ${sourcePath} ${configArg} --format=default > flake8-report.txt || true",
        returnStdout: true
    )
    
    def reportContent = readFile('flake8-report.txt')
    def issues = reportContent.split('\n').findAll { it.trim() }.size()
    
    return [
        tool: 'flake8',
        status: issues > 0 ? 'issues' : 'clean',
        issues: issues,
        reportFile: 'flake8-report.txt'
    ]
}

def runBandit(sourcePath) {
    def output = sh(
        script: "python3 -m bandit -r ${sourcePath} -f json -o bandit-report.json || true",
        returnStdout: true
    )
    
    def reportContent = readFile('bandit-report.json')
    def report = readJSON text: reportContent
    def issues = report.results?.size() ?: 0
    
    return [
        tool: 'bandit',
        status: issues > 0 ? 'issues' : 'clean',
        issues: issues,
        reportFile: 'bandit-report.json'
    ]
}

def runESLint(sourcePath, configFile) {
    def configArg = configFile ? "--config ${configFile}" : ""
    def output = sh(
        script: "npx eslint ${sourcePath} ${configArg} --format json --output-file eslint-report.json || true",
        returnStdout: true
    )
    
    def reportContent = readFile('eslint-report.json')
    def report = readJSON text: reportContent
    def issues = report.sum { it.errorCount + it.warningCount } ?: 0
    
    return [
        tool: 'eslint',
        status: issues > 0 ? 'issues' : 'clean',
        issues: issues,
        reportFile: 'eslint-report.json'
    ]
}

def generateQualityReport(results) {
    def reportFile = 'quality-report.json'
    writeJSON file: reportFile, json: results
    
    // Archive all report files
    def reportFiles = results.values()
        .findAll { it.reportFile }
        .collect { it.reportFile }
        .join(',')
    
    if (reportFiles) {
        archiveArtifacts artifacts: reportFiles, allowEmptyArchive: true
    }
    
    archiveArtifacts artifacts: reportFile, allowEmptyArchive: true
}

def hasQualityIssues(results) {
    return results.values().any { it.status == 'issues' || it.status == 'failed' }
}
