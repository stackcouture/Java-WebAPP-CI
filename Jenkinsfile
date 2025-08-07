import groovy.json.JsonOutput

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
    }

    tools {
        jdk 'Jdk17'
        maven 'Maven3'
    }

    stages {

        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

        stage('Checkout Code') {
            steps {
                git branch: "${params.BRANCH}", credentialsId: 'github-pat', url: 'https://github.com/stackcouture/Java-WebAPP-CI.git'
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
                // Archive Surefire test reports
                archiveArtifacts artifacts: "target/surefire-reports/*.xml", allowEmptyArchive: true
                
                // Check if the test reports exist and publish them
                if (fileExists('target/surefire-reports')) {
                    junit 'target/surefire-reports/*.xml'
                } else {
                    echo "No test results found."
                }

                // Check if any HTML report files exist in target directory
                def htmlReportExists = sh(script: "find target -name '*.html' | wc -l", returnStdout: true).trim()

                if (htmlReportExists.toInteger() > 0) {
                    // If HTML reports exist, publish the HTML report
                    publishHTML(target: [
                        reportName: 'Test Report',
                        reportFiles: 'target/**/*.html', // Adjust this path if needed
                        reportTitles: 'Test Report'
                    ])
                } else {
                    echo "No HTML report found."
                }
            }
        }
    }
}
