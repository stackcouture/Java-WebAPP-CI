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
        SLACK_CHANNEL = '#all-jenkins'
        SLACK_TOKEN = credentials('slack-token')
        COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        REGION = 'ap-south-1'
        SNYK_TOKEN = credentials('SNYK_TOKEN')
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

        // stage('Get Commit SHA') {
        //     steps {
        //         script {
        //             env.COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        //             echo "Using commit SHA: ${env.COMMIT_SHA}"
        //         }
        //     }
        // }

        stage('Build + Test + SBOM') {
            steps {
                sh 'mvn clean verify'
            }
        }

        stage('Publish SBOM') {
            steps {
                script {
                    def sbomFile = 'target/bom.xml'
                    if (fileExists(sbomFile)) {
                        archiveArtifacts artifacts: sbomFile, allowEmptyArchive: true
                    }
                }
            }
        }

        stage('File System Scan') {
            steps {
                script {
                    try {
                        sh 'trivy fs --format table -o fs.html .'
                        publishHTML(target: [
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: '.',
                            reportFiles: 'fs.html',
                            reportName: 'Trivy Filesystem Scan'
                        ])
                    } catch (err) {
                        echo "Trivy scan failed: ${err}"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} ."
            }
        }

        stage('Security Scans Before Push') {
            parallel {
                stage('Trivy Before Push') {
                    steps {
                        script {
                            def localTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runTrivyScan("before-push", localTag)
                        }
                    }
                }
                stage('Snyk Before Push') {
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
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-jenkins-creds',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    script {
                        def fullTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                        sh """
                            aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID
                            aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY
                            aws ecr get-login-password --region ${env.REGION} | docker login --username AWS --password-stdin ${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com
                            docker tag ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} ${fullTag}
                            docker push ${fullTag}
                        """
                    }
                }
            }
        }

        stage('Security Scans After Push') {
            parallel {
                stage('Trivy After Push') {
                    steps {
                        script {
                            def pushedTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runTrivyScan("after-push", pushedTag)
                        }
                    }
                }
                stage('Snyk After Push') {
                    steps {
                        script {
                            def pushedTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runSnykScan("after-push", pushedTag)
                        }
                    }
                }
            }
        }

        stage('Update YAML File - FINAL') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-pat', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                    script {
                        def imageTag = env.COMMIT_SHA
                        def branch = params.BRANCH
                        def repoDir = 'Java-WebAPP-CD'

                        sh "rm -rf ${repoDir}"
                        sh "git clone https://${GIT_USER}:${GIT_TOKEN}@github.com/stackcouture/Java-WebAPP-CD.git ${repoDir}"

                        dir(repoDir) {
                            sh "git checkout ${branch}"

                            if (!fileExists("java-app-chart/values.yaml")) {
                                error("File java-app-chart/values.yaml not found in branch ${branch}")
                            }

                            def tagExists = sh(script: "grep -q '^\\s*tag:' java-app-chart/values.yaml && echo found || echo notfound", returnStdout: true).trim()
                            if (tagExists != 'found') {
                                error("Could not find 'tag:' field in values.yaml — aborting.")
                            }

                            sh """
                                sed -i -E "s|(^\\s*tag:\\s*\\\").*(\\\")|\\1${imageTag}\\2|" java-app-chart/values.yaml
                            """

                            sh 'git config user.email "naveenramlu@gmail.com"'
                            sh 'git config user.name "Naveen"'
                            sh 'git add java-app-chart/values.yaml'

                            def changes = sh(script: 'git diff --cached --quiet || echo "changed"', returnStdout: true).trim()
                            if (changes == "changed") {
                                echo "Changes detected — committing and pushing."
                                sh "git commit -m 'chore: update image tag to ${imageTag}'"
                                sh "git push origin ${branch}"
                            } else {
                                echo "No changes detected — skipping commit."
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/fs.html, **/trivy-image-scan-*.html, **/snyk-report-*.json', allowEmptyArchive: true
            script {
                if (fileExists('target/surefire-reports')) {
                    junit 'target/surefire-reports/*.xml'
                } else {
                    echo "ℹNo test results found."
                }
            }
        }

        success {
        mail to: 'naveenramlu@gmail.com',
             subject: "Build #${env.BUILD_NUMBER} Succeeded",
             body: "Build ${env.BUILD_NUMBER} for commit ${env.COMMIT_SHA} succeeded. View in Jenkins: ${env.BUILD_URL}"
        }
        failure {
            mail to: 'naveenramlu@gmail.com',
                subject: "Build #${env.BUILD_NUMBER} Failed",
                body: "Build ${env.BUILD_NUMBER} for commit ${env.COMMIT_SHA} failed. Please check: ${env.BUILD_URL}"
        }
    }
}

def runTrivyScan(stageName, imageTag) {
    def reportDir = "reports/trivy/${env.BUILD_NUMBER}/${stageName}"
    def htmlFile = "${reportDir}/trivy-image-scan-${env.COMMIT_SHA}.html"
    sh """
        mkdir -p ${reportDir}

        # Download Trivy HTML template
        mkdir -p contrib
        curl -sSL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl -o contrib/html.tpl

        trivy image --format template --template "@contrib/html.tpl" -o ${htmlFile} ${imageTag}
    """
    publishHTML(target: [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: reportDir,
        reportFiles: "*.html",
        reportName: "Trivy Image Scan (${stageName}) - Build ${env.BUILD_NUMBER}"
    ])
    archiveArtifacts artifacts: "${reportDir}/*.html", allowEmptyArchive: true
}

def runSnykScan(stageName, imageTag) {
    def reportDir = "reports/snyk/${env.BUILD_NUMBER}/${stageName}"
    def jsonFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.json"
    def htmlFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.html"

    withEnv(["SNYK_TOKEN=${env.SNYK_TOKEN}"]) {
        sh """
            mkdir -p ${reportDir}
            snyk auth $SNYK_TOKEN
            snyk container test ${imageTag} --severity-threshold=high --json > ${jsonFile} || true

            echo "<html><body><pre>" > ${htmlFile}
            cat ${jsonFile} | jq . >> ${htmlFile}
            echo "</pre></body></html>" >> ${htmlFile}
        """
    }

    publishHTML(target: [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: reportDir,
        reportFiles: htmlFile.replace("${reportDir}/", ""),
        reportName: "Snyk Image Scan (${stageName}) - Build ${env.BUILD_NUMBER}"
    ])
    archiveArtifacts artifacts: "${jsonFile},${htmlFile}", allowEmptyArchive: true
}