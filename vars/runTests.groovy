#!/usr/bin/env groovy

def call(Map params = [:]) {
    // Default parameters with overrides
    def testType = params.type ?: 'unit'  // 'unit', 'integration', 'e2e'
    def testCommand = params.command ?: 'npm test'
    def testResultsPattern = params.resultsPattern ?: '**/test-results/**/*.xml'
    def coverageReportPattern = params.coveragePattern ?: '**/coverage/**/*.xml'
    def testDir = params.directory ?: '.'
    
    echo "Running ${testType} tests..."
    
    dir(testDir) {
        // Run the tests
        try {
            // For Python projects
            if (fileExists('requirements-test.txt')) {
                sh 'pip install -r requirements-test.txt'
            }
            
            // For Node.js projects
            if (fileExists('package.json')) {
                sh 'npm install'
            }
            
            // Execute the test command
            sh testCommand
            
        } finally {
            // Always archive test results and coverage reports
            junit testResultsPattern
            
            // Publish coverage reports if they exist
            if (fileExists('coverage/lcov.info')) {
                publishCoverage(
                    adapters: [coberturaAdapter('coverage/cobertura-coverage.xml')],
                    sourceFileResolver: sourceFiles('STORE_LAST_BUILD')
                )
            } else if (fileExists('coverage.xml')) {
                publishCoverage(
                    adapters: [coberturaAdapter('coverage.xml')],
                    sourceFileResolver: sourceFiles('STORE_LAST_BUILD')
                )
            }
            
            // Archive coverage reports for Cobertura
            if (fileExists('coverage/cobertura-coverage.xml') || fileExists('coverage.xml')) {
                step([
                    $class: 'CoberturaPublisher',
                    coberturaReportFile: fileExists('coverage/cobertura-coverage.xml') ? 'coverage/cobertura-coverage.xml' : 'coverage.xml',
                    onlyStable: false,
                    failUnhealthy: false,
                    failUnstable: false,
                    autoUpdateHealth: false,
                    autoUpdateStability: false,
                    zoomCoverageChart: false,
                    maxNumberOfBuilds: 0,
                    failNoReports: false,
                    sourceEncoding: 'ASCII'
                ])
            }
            
            // Publish HTML reports if they exist
            if (fileExists('coverage/lcov-report/index.html')) {
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'coverage/lcov-report',
                    reportFiles: 'index.html',
                    reportName: "${testType.capitalize()} Test Coverage Report",
                    reportTitles: 'Coverage Report'
                ])
            }
        }
    }
}
