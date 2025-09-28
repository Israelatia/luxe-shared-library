#!/usr/bin/env groovy

def call(Closure body) {
    // Define default configuration
    def pipelineParams = [
        projectName: 'luxe-jewelry-store',
        registry: '',
        branch: '',
        buildArgs: '',
        testCommand: 'pytest',
        buildCommand: 'docker-compose build',
        deployCommand: 'docker-compose up -d'
    ]
    
    // Apply user configuration
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()
    
    // Start pipeline
    pipeline {
        agent any
        
        environment {
            DOCKER_BUILDKIT = 1
            COMPOSE_DOCKER_CLI_BUILD = 1
            DOCKER_CREDS = credentials('docker-hub')
        }
        
        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                }
            }
            
            stage('Build') {
                steps {
                    script {
                        buildDockerImage(
                            imageName: pipelineParams.projectName,
                            dockerFile: 'Dockerfile',
                            buildContext: '.',
                            tags: [
                                "${env.BUILD_NUMBER}",
                                "${env.GIT_COMMIT.take(7)}"
                            ]
                        )
                    }
                }
            }
            
            stage('Test') {
                steps {
                    script {
                        runTests(
                            testCommand: pipelineParams.testCommand,
                            coverageReport: true
                        )
                    }
                }
            }
            
            stage('Deploy') {
                when {
                    branch 'main'
                }
                steps {
                    script {
                        pushToRegistry(
                            imageName: pipelineParams.projectName,
                            registry: pipelineParams.registry,
                            tags: ['latest', env.BUILD_NUMBER]
                        )
                        
                        if (pipelineParams.deployCommand) {
                            sh pipelineParams.deployCommand
                        }
                    }
                }
            }
        }
        
        post {
            always {
                // Clean up workspace
                cleanWs()
            }
            success {
                notifySlack(
                    message: "✅ Pipeline Succeeded: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    channel: '#builds',
                    color: 'good'
                )
            }
            failure {
                notifySlack(
                    message: "❌ Pipeline Failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    channel: '#builds',
                    color: 'danger'
                )
            }
        }
    }
}
