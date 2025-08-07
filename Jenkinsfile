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

         stage('Prepare Trivy Template') {
            steps {
                sh """
                    mkdir -p contrib
                    curl -sSL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl -o contrib/html.tpl
                """
            }
        }

        stage('Trivy File System Scan') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                script {
                    try {
                        runTrivyScanUnified("filesystem-scan",".", "fs")
                    } catch (err) {
                        echo "Trivy File System Scan failed: ${err}"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                 script {
                    buildDockerImage("${params.ECR_REPO_NAME}:${env.COMMIT_SHA}")
                    //sh "docker build -t ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} ."
                 }
            }
        }

        stage('Security Scans Before Push') {
            parallel {
                stage('Trivy Before Push') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            def localTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runTrivyScanUnified("before-push", localTag, "image")
                        }
                    }
                }
                stage('Snyk Before Push') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            def localTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runSnykScan("before-push", localTag)
                        }
                    }
                }
            }
        }

        stage('Docker Push') {
            steps {
                script {
                    dockerPush("docker-push", "${env.COMMIT_SHA}", params.ECR_REPO_NAME, params.AWS_ACCOUNT_ID, "${env.REGION}")
                }
            }
        }

        stage('Security Scans After Push') {
            parallel {
                stage('Trivy After Push') {
                     options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            def pushedTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runTrivyScanUnified("after-push", pushedTag, "image")
                        }
                    }
                }
                stage('Snyk After Push') {
                     options {
                        timeout(time: 15, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            def pushedTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            retry(2) {
                                runSnykScan("after-push", pushedTag)
                            }
                        }
                    }
                }
            }
        }

        stage('Cleanup Local Image Tags') {
            steps {
                sh """
                    docker rmi ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} || true
                    docker rmi ${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA} || true
                """
            }
        }

        stage('Generate GPT Report') {
            steps {
                script {

                    def trivyHtmlPath = "reports/trivy/${env.BUILD_NUMBER}/after-push/trivy-image-scan-${env.COMMIT_SHA}.html"
                    def snykJsonPath = "reports/snyk/${env.BUILD_NUMBER}/after-push/snyk-report-${env.COMMIT_SHA}.json"

                    // Check if necessary environment variables are set
                    if (!env.COMMIT_SHA || !env.BUILD_NUMBER) {
                        error("Environment variables COMMIT_SHA or BUILD_NUMBER are not set.")
                    }

                    if (fileExists(trivyHtmlPath) && fileExists(snykJsonPath)) {
                        runGptSecuritySummary(
                            "my-app", 
                            env.COMMIT_SHA, 
                            env.BUILD_NUMBER, 
                            trivyHtmlPath, 
                            snykJsonPath
                        )
                    } else {
                        error("One or more required files do not exist: ${trivyHtmlPath}, ${snykJsonPath}")
                    }
                }   
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

        success {
            script {
                if (fileExists("ai_report.html")) {
                    // sh "pandoc ai_report.html -f html -t pdf -o ${env.PDF_REPORT} --standalone --pdf-engine=wkhtmltopdf"
                    sh "wkhtmltopdf --zoom 1.3 --enable-local-file-access ai_report.html ai_report.pdf"

                    emailext(
                        subject: "Security Report - Build #${env.BUILD_NUMBER} - SUCCESS",
                          body: """
                                 <html>
                                    <body style="font-family: Arial, sans-serif; font-size: 15px; line-height: 1.6; padding: 10px;">
                                        <h2 style="color: #2c3e50;">Hello Team,</h2>
                                        <p>
                                            Please find attached the <strong>AI-generated security report</strong> for <strong>Build #${env.BUILD_NUMBER}</strong>.
                                        </p>
                                        <p>
                                            This report summarizes the security scan results from <strong>Trivy</strong> and <strong>Snyk</strong> for the current build.
                                        </p>
                                        <p>
                                            <strong>Project:</strong> ${env.JOB_NAME}<br/>
                                            <strong>Branch:</strong> ${params.BRANCH}<br/>
                                            <strong>Commit:</strong> ${env.COMMIT_SHA}
                                        </p>
                                        <p>
                                            For detailed insights, please open the attached PDF report.
                                        </p>
                                        <p>
                                            Regards,<br/>
                                            <strong>Jenkins CI/CD</strong> 🤖
                                        </p>
                                    </body>
                                    </html>
                            """,
                        mimeType: 'text/html',
                        attachmentsPattern: 'ai_report.pdf',
                        to: 'naveenramlu@gmail.com',
                        attachLog: false
                    )
                }
            }
        }
    }
}
