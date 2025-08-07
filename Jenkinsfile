import groovy.json.JsonOutput

@Library('my-shared-library') _

pipeline {
    agent {
        label "jenkins-agent"
    }

    parameters {
        string(name: 'ECR_REPO_NAME', defaultValue: 'javaspring', description: 'Enter repository name')
        string(name: 'AWS_ACCOUNT_ID', defaultValue: '104824081961', description: 'Enter AWS Account ID')
        string(name: 'BRANCH', defaultValue: 'dev', description: 'Deployment branch for CD repo')
    }

    environment {
        REGION = 'ap-south-1'
        GIT_URL = 'https://github.com/stackcouture/Java-WebAPP-CI.git'
    }

    tools {
        jdk 'Jdk17'
        maven 'Maven3'
    }

    stages {

        stage('Clean Workspace') {
            steps {
                cleanWorkspace()
            }
        }

      stage('Checkout Code') {
            steps {
                script {
                    checkoutGit(params.BRANCH, env.GIT_URL) 
                }
            }
        }

        stage('Commit SHA') {
            steps {
                script {
                    env.COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                }
            }
        }

        stage('Build + Test + SBOM') {
            steps {
                sh 'mvn clean verify'
            }
        }
    }

    post {
        always {
            script {
                // Archive all test result XML files, if any exist
                archiveArtifacts artifacts: "target/surefire-reports/*.xml", allowEmptyArchive: true
                if (fileExists('target/surefire-reports')) {
                    junit 'target/surefire-reports/*.xml'
                } else {
                    echo "No test results found."
                }

                // Check if HTML report exists before publishing
                def htmlReportExists = fileExists('target/**.html')
                if (htmlReportExists) {
                    // Publish HTML reports only if they exist
                    publishHTML(target: [
                        reportName: 'Test Report',
                        reportFiles: 'target/**.html',
                        reportTitles: 'Test Report'
                    ])
                } else {
                    echo "No HTML report found to publish."
                }
            }
        }
    }
}
