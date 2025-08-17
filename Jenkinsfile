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
        SLACK_CHANNEL = '#java-app'
        DEPENDENCY_TRACK_URL = 'http://15.207.71.114:8081/api/v1/bom'
        SONAR_HOST = "http://65.1.132.166:9000"
        SONAR_PROJECT_KEY = 'Java-App'
    }

    tools {
        jdk 'Jdk17'
        maven 'Maven3'
    }

    stages {
       stage('Init & Checkout') {
            steps {
                echo "Cleaning workspace..."
                cleanWs()
                script {
                    echo "Calling checkoutGit..."
                    try {
                        checkoutGit(params.BRANCH, env.GIT_URL, 'my-app/secrets')
                        env.COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Stopping pipeline due to checkout failure")
                    }
                }
            }
        }

        stage('Gitleaks Scan') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    echo "Running Gitleaks full scan with custom config..."

                    sh """
                        curl -sSL https://github.com/gitleaks/gitleaks/releases/download/v8.18.1/gitleaks_8.18.1_linux_x64.tar.gz -o gitleaks.tar.gz
                        tar -xvf gitleaks.tar.gz
                        chmod +x gitleaks

                        mkdir -p reports/gitleaks

                        ./gitleaks detect \
                            --source . \
                            --config my-app/secrets/gitleaks.toml \
                            --report-format html \
                            --report-path reports/gitleaks/gitleaks-report-${env.COMMIT_SHA}.html \
                            --verbose

                        rm -f gitleaks gitleaks.tar.gz
                    """

                    def leaksFound = sh(script: "grep -i 'Secret' reports/gitleaks/gitleaks-report-${env.COMMIT_SHA}.html | wc -l", returnStdout: true).trim()
                    if (leaksFound != '0') {
                        error "Gitleaks found potential secrets in Git history!"
                    }
                }

                archiveArtifacts artifacts: "reports/gitleaks/gitleaks-report-${env.COMMIT_SHA}.html", allowEmptyArchive: true
            }
        }

        stage('Build + Test') {
            steps {
                echo "Building and running tests..."
                sh 'mvn clean verify'
            }
        }

        stage('SBOM + FS Scan') {
            parallel {
                // stage('Publish SBOM') {
                //     steps {
                //         script {
                //             if (!fileExists('target/bom.xml')) {
                //                 error "SBOM file target/bom.xml not found!"
                //             }
                //             echo "Uploading SBOM to Dependency Track..."
                //             uploadSbomToDependencyTrack(
                //                 sbomFile: 'target/bom.xml',
                //                 projectName: "${params.ECR_REPO_NAME}",
                //                 projectVersion: "${env.COMMIT_SHA}",
                //                 dependencyTrackUrl: "${env.DEPENDENCY_TRACK_URL}",
                //                 secretName: 'my-app/secrets'
                //             )
                //         }
                //     }
                // }
                stage('Trivy FS Scan') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        echo "Running Trivy filesystem scan..."
                        sh "mkdir -p contrib && curl -sSL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl -o contrib/html.tpl"
                        runTrivyScanUnified("filesystem-scan",".", "fs")
                    }
                }
            }
        }

        // stage('SonarQube Analysis & Gate') {
        //     steps {
        //         echo "Running SonarQube scan..."
        //         sonarScan(
        //             projectKey: env.SONAR_PROJECT_KEY,
        //             sources: 'src/main/java,src/test/java',
        //             binaries: 'target/classes',
        //             exclusions: '**/*.js',
        //             scannerTool: 'sonar-scanner',
        //             sonarEnv: 'sonar-server'
        //         )
        //         echo "Checking SonarQube quality gate..."
        //         sonarQualityGateCheck(
        //             projectKey: env.SONAR_PROJECT_KEY,
        //             secretName: 'my-app/secrets',
        //             timeoutMinutes: 5
        //         )
        //     }
        // }

        stage('Build Docker Image') {
            steps {
                 script {
                    echo "Building Docker image with tag: ${env.COMMIT_SHA}"
                    buildDockerImage("${params.ECR_REPO_NAME}:${env.COMMIT_SHA}")
                 }
            }
        }

        // stage('Security Scans Before Push') {
        //     parallel {
        //         stage('Trivy Before Push') {
        //             options {
        //                 timeout(time: 10, unit: 'MINUTES')
        //             }
        //             steps {
        //                 echo "Running Trivy scan before push..."
        //                 runTrivyScanUnified("before-push", "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}", "image")
        //             }
        //         }
        //         stage('Snyk Before Push') {
        //             options {
        //                 timeout(time: 10, unit: 'MINUTES')
        //             }
        //             steps {
        //                 echo "Running Snyk scan before push..."
        //                 runSnykScan(
        //                     stageName: "before-push",
        //                     imageTag: "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}",
        //                     secretName: 'my-app/secrets'
        //                 )
        //             }
        //         }
        //     }
        // }

        stage('Docker Push') {
            steps {
                script {
                    echo "Pushing Docker image: ${env.COMMIT_SHA}"
                    dockerPush(
                        imageTag: "${env.COMMIT_SHA}",
                        ecrRepoName: params.ECR_REPO_NAME,
                        awsAccountId: params.AWS_ACCOUNT_ID,
                        region: "${env.REGION}",
                        secretName: 'my-app/secrets'
                    )
                }
            }
        }

        // stage('Security Scans After Push') {
        //     parallel {
        //         stage('Trivy After Push') {
        //              options {
        //                 timeout(time: 10, unit: 'MINUTES')
        //             }
        //             steps {
        //                 echo "Running Trivy scan after push..."
        //                 runTrivyScanUnified("after-push",
        //                     "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}", "image")
        //             }
        //         }
        //         stage('Snyk After Push') {
        //              options {
        //                 timeout(time: 15, unit: 'MINUTES')
        //             }
        //             steps {
        //                 retry(2) {
        //                     echo "Running Snyk scan after push..."
        //                     runSnykScan(
        //                         stageName: "after-push",
        //                         imageTag: "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}",
        //                         secretName: 'my-app/secrets'
        //                     )
        //                 }
        //             }
        //         }
        //     }
        // }

        // stage('Confirm YAML Update') {
        //     steps {
        //         script {
        //             def approver = confirmYamlUpdate()
        //             echo "YAML update approved by: ${approver}"
        //         }
        //     }
        // }

        stage('Update Deployment Files') {
            steps {
                script {
                    echo "Updating deployment YAML with image tag: ${env.COMMIT_SHA}"
                    updateImageTag(
                        imageTag: env.COMMIT_SHA,
                        secretName: 'my-app/secrets'
                    )
                }
            }
        }

        // Testing 
        // stage('Deploy App') {
        //     steps {
        //         echo "Deploying application..."
        //         deployApp()
        //     }
        // }

        // stage('Generate GPT Security Report') {
        //     steps {
        //         script {

        //             def trivyHtmlPath = "reports/trivy/${env.BUILD_NUMBER}/after-push/trivy-image-scan-${env.COMMIT_SHA}.html"
        //             def snykJsonPath = "reports/snyk/${env.BUILD_NUMBER}/after-push/snyk-report-${env.COMMIT_SHA}.json"

        //             if (fileExists(trivyHtmlPath) && fileExists(snykJsonPath)) {
        //                 echo "Generating GPT security report..."
        //                 runGptSecuritySummary(
        //                     projectKey: env.SONAR_PROJECT_KEY, 
        //                     gitSha: "${env.COMMIT_SHA}",
        //                     buildNumber: "${env.BUILD_NUMBER}",
        //                     trivyHtmlPath: trivyHtmlPath,
        //                     snykJsonPath: snykJsonPath,
        //                     sonarHost: "${env.SONAR_HOST}",
        //                     secretName: 'my-app/secrets'
        //                 )

        //             } else {
        //                 error("Missing scan reports.")
        //             }
        //         }   
        //     } 
        // }

        stage('Cleanup') {
            steps {
                script {
                    echo "Cleaning up Docker images..."
                    cleanupDockerImages(
                        imageTag: env.COMMIT_SHA,
                        repoName: params.ECR_REPO_NAME,
                        awsAccountId: params.AWS_ACCOUNT_ID,
                        region: env.REGION
                    )
                }
            }
        }
    }

    post {
        always {
            echo "Archiving test reports..."
            postBuildTestArtifacts('My Test Report', '**/surefire-report.html')
        }

        success {
            script {
                echo "Build SUCCESS - sending reports and notifications..."
                // sendAiReportEmail(
                //     branch: params.BRANCH,
                //     commit: env.COMMIT_SHA ?: 'unknown',
                //     to: 'naveenramlu@gmail.com'
                // )

                sendSlackNotification(
                    status: 'SUCCESS',
                    color: 'good',
                    channel: env.SLACK_CHANNEL,
                    secretName: 'my-app/secrets'
                )
            }
        }

        failure {
            script {
                echo "Build FAILURE - sending failure notification..."
                sendSlackNotification(
                    status: 'FAILURE',
                    color: 'danger',
                    channel: env.SLACK_CHANNEL,
                    secretName: 'my-app/secrets'
                )
            }
        }

        unstable {
            script {
                echo "Build UNSTABLE - sending unstable notification..."
                sendSlackNotification(
                    status: 'UNSTABLE',
                    color: 'warning',
                    channel: env.SLACK_CHANNEL,
                    secretName: 'my-app/secrets'
                )
            }
        }

        aborted {
            script {
                echo "Build ABORTED - sending aborted notification..."
                sendSlackNotification(
                    status: 'ABORTED',
                    color: '#808080',
                    channel: env.SLACK_CHANNEL,
                    secretName: 'my-app/secrets'
                )
            }
        }

    }
}
