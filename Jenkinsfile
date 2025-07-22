pipeline {
    
    agent {
        label "jenkins-agent"
    }

    parameters {
        string(name: 'ECR_REPO_NAME', defaultValue: 'java-app', description: 'Enter repository name')
        string(name: 'AWS_ACCOUNT_ID', defaultValue: '123456789012', description: 'Enter AWS Account ID')
        string(name: 'BRANCH', defaultValue: 'main', description: 'Deployment branch for CD repo')
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
                git branch: 'main', credentialsId: 'github-pat', url: 'https://github.com/stackcouture/Java-WebAPP-CI.git'
            }
        }

        stage('Compile') {
            steps {
                sh 'mvn clean compile'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }

        stage('File System Scan') {
            steps {
                sh 'trivy fs --format table -o fs.html .'
            }
        }

        stage('Archive Test Results') {
            steps {
                script {
                    def testResults = fileExists('target/surefire-reports')
                    if (testResults) {
                        junit 'target/surefire-reports/*.xml'
                    } else {
                        echo "No test results found to archive."
                    }
                }
            }
        }

        stage('Build with Maven') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${params.ECR_REPO_NAME} ."
            }
        }

        stage('Login to ECR & Tag Image') {
            steps {
                withCredentials([
                    string(credentialsId: 'access-key', variable: 'AWS_ACCESS_KEY'),
                    string(credentialsId: 'secret-key', variable: 'AWS_SECRET_KEY')
                ]) {
                    withEnv([
                        "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY}",
                        "AWS_SECRET_ACCESS_KEY=${AWS_SECRET_KEY}"
                    ]) {
                        sh """
                            aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com
                            docker tag ${params.ECR_REPO_NAME} ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:${env.BUILD_NUMBER}
                            docker tag ${params.ECR_REPO_NAME} ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:latest
                        """
                    }
                }
            }
        }

        stage('Trivy Image Scan') {
            steps {
                sh """
                    trivy image --format table -o trivy-image-scan.html ${params.ECR_REPO_NAME}
                """
            }
        }

        stage('Push Image to ECR') {
            steps {
                withCredentials([
                    string(credentialsId: 'access-key', variable: 'AWS_ACCESS_KEY'),
                    string(credentialsId: 'secret-key', variable: 'AWS_SECRET_KEY')
                ]) {
                    sh """
                        docker push ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:${env.BUILD_NUMBER}
                        docker push ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:latest
                    """
                }
            }
        }

        stage('Update YAML File - FINAL') {
            steps {
                withCredentials([
                    usernamePassword(credentialsId: 'github-pat', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')
                ]) {
                    script {
                        def imageTag = env.BUILD_NUMBER
                        def branch = params.BRANCH
                        def repoDir = 'Java-WebAPP-CD'

                        sh "rm -rf ${repoDir}"
                        sh "git clone https://${GIT_USER}:${GIT_TOKEN}@github.com/stackcouture/Java-WebAPP-CD.git ${repoDir}"

                        sh """
                            sed -i -E 's|(image:\\s*${params.AWS_ACCOUNT_ID}\\.dkr\\.ecr\\.ap-south-1\\.amazonaws\\.com/${params.ECR_REPO_NAME}:).*|\\1${imageTag}|' ${repoDir}/java-app/deployment.yaml
                        """

                        dir(repoDir) {
                            sh 'git config user.email "naveenramlu@gmail.com"'
                            sh 'git config user.name "Naveen"'
                            sh "git checkout ${branch}"
                            sh 'git add java-app/deployment.yaml'

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
            archiveArtifacts artifacts: '**/fs.html', allowEmptyArchive: true
            archiveArtifacts artifacts: '**/trivy-image-scan.html', allowEmptyArchive: true

            emailext(
                attachLog: true,
                attachmentsPattern: 'target/surefire-reports/*.xml',
                subject: "${env.JOB_NAME} - Build #${env.BUILD_NUMBER} - ${currentBuild.result}",
                body: """\
                        <p>Build Status: ${currentBuild.result}</p>
                        <p>Project: ${env.JOB_NAME}</p>
                        <p>Build Number: ${env.BUILD_NUMBER}</p>
                        <p>URL: <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>
                    """, // closing triple-quote and comma must be on the **same line**
                to: 'naveenramlu@gmail.com',
                mimeType: 'text/html'
            )
        }
    }
}

