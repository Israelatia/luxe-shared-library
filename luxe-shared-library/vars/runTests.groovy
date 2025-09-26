#!/usr/bin/env groovy

/**
 * Run tests with coverage and reporting
 * 
 * @param config Map containing:
 *   - testType: 'unit', 'integration', or 'both' (default: 'unit')
 *   - testPath: Path to test files (default: 'tests/')
 *   - coverageThreshold: Minimum coverage percentage (default: 80)
 *   - framework: Testing framework ('pytest', 'unittest', 'jest', etc.)
 *   - requirements: Path to requirements file (default: 'requirements.txt')
 *   - publishResults: Whether to publish test results (default: true)
 */
def call(Map config = [:]) {
    def testType = config.testType ?: 'unit'
    def testPath = config.testPath ?: 'tests/'
    def coverageThreshold = config.coverageThreshold ?: 80
    def framework = config.framework ?: 'pytest'
    def requirements = config.requirements ?: 'requirements.txt'
    def publishResults = config.publishResults ?: true
    
    echo "🧪 Running ${testType} tests with ${framework}"
    echo "📊 Coverage threshold: ${coverageThreshold}%"
    
    def testResults = [:]
    
    try {
        // Install dependencies if requirements file exists
        if (fileExists(requirements)) {
            echo "📦 Installing test dependencies..."
            sh "pip3 install --user -r ${requirements}"
        }
        
        // Run tests based on framework
        switch (framework) {
            case 'pytest':
                testResults = runPytestSuite(testPath, coverageThreshold)
                break
            case 'unittest':
                testResults = runUnittestSuite(testPath, coverageThreshold)
                break
            case 'jest':
                testResults = runJestSuite(testPath, coverageThreshold)
                break
            default:
                error("Unsupported test framework: ${framework}")
        }
        
        // Publish results if enabled
        if (publishResults) {
            publishTestResults(testResults, framework)
        }
        
        // Check coverage threshold
        if (testResults.coverage < coverageThreshold) {
            echo "⚠️ Coverage ${testResults.coverage}% is below threshold ${coverageThreshold}%"
            currentBuild.result = 'UNSTABLE'
        }
        
        echo "✅ Tests completed successfully"
        echo "📈 Coverage: ${testResults.coverage}%"
        echo "🎯 Tests passed: ${testResults.passed}/${testResults.total}"
        
        return testResults
        
    } catch (Exception e) {
        echo "❌ Test execution failed: ${e.message}"
        currentBuild.result = 'FAILURE'
        throw e
    }
}

def runPytestSuite(testPath, coverageThreshold) {
    def output = sh(
        script: """
            python3 -m pytest ${testPath} \
                --junitxml=test-results.xml \
                --cov=backend \
                --cov-report=xml:coverage.xml \
                --cov-report=html:htmlcov \
                --cov-fail-under=${coverageThreshold} \
                --verbose
        """,
        returnStdout: true
    )
    
    return parseTestResults('test-results.xml', 'coverage.xml')
}

def runUnittestSuite(testPath, coverageThreshold) {
    def output = sh(
        script: """
            python3 -m coverage run --source=. -m unittest discover ${testPath}
            python3 -m coverage xml
            python3 -m coverage html
            python3 -m coverage report --fail-under=${coverageThreshold}
        """,
        returnStdout: true
    )
    
    return parseTestResults('test-results.xml', 'coverage.xml')
}

def runJestSuite(testPath, coverageThreshold) {
    def output = sh(
        script: """
            npm test -- --coverage --coverageThreshold='{"global":{"lines":${coverageThreshold}}}'
        """,
        returnStdout: true
    )
    
    return parseTestResults('test-results.xml', 'coverage/coverage-final.json')
}

def parseTestResults(testFile, coverageFile) {
    def results = [:]
    
    if (fileExists(testFile)) {
        def testXml = readFile(testFile)
        // Parse test results (simplified)
        results.total = (testXml =~ /tests="(\d+)"/).collect { it[1] }[0] as Integer
        results.passed = results.total - ((testXml =~ /failures="(\d+)"/).collect { it[1] }[0] as Integer)
    }
    
    if (fileExists(coverageFile)) {
        if (coverageFile.endsWith('.xml')) {
            def coverageXml = readFile(coverageFile)
            def coverageMatch = (coverageXml =~ /line-rate="([0-9.]+)"/)
            results.coverage = coverageMatch ? (coverageMatch[0][1] as Double) * 100 : 0
        }
    }
    
    return results
}

def publishTestResults(results, framework) {
    // Publish JUnit test results
    if (fileExists('test-results.xml')) {
        junit allowEmptyResults: true, testResults: 'test-results.xml'
    }
    
    // Publish coverage report
    if (fileExists('htmlcov/index.html')) {
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'htmlcov',
            reportFiles: 'index.html',
            reportName: 'Coverage Report'
        ])
    }
    
    // Archive artifacts
    archiveArtifacts artifacts: 'test-results.xml,coverage.xml,htmlcov/**', allowEmptyArchive: true
}
