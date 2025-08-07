def call(String stageName, String imageTag, String ecrRepoName, String awsAccountId, String region) {
    
    def fullTag = "${awsAccountId}.dkr.ecr.${region}.amazonaws.com/${ecrRepoName}:${imageTag}"
    
    withCredentials([[
        $class: 'AmazonWebServicesCredentialsBinding',
        credentialsId: 'aws-jenkins-creds',
        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
    ]]) {
        script {
            sh """
                aws configure set aws_access_key_id \$AWS_ACCESS_KEY_ID
                aws configure set aws_secret_access_key \$AWS_SECRET_ACCESS_KEY
                aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${awsAccountId}.dkr.ecr.${region}.amazonaws.com
                docker tag ${imageTag} ${fullTag}
                docker push ${fullTag}
            """
        }
    }
}