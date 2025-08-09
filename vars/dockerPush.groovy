def call(Map config = [:]) {

    def imageTag = config.imageTag ?: error("Missing 'stageName'")
    def ecrRepoName = config.ecrRepoName ?: error("Missing 'imageTag'")
    def awsAccountId = config.awsAccountId ?: error("Missing 'awsAccountId'")
    def region = config.region ?: error("Missing 'region'")
    def secretName = config.secretName ?: error("Missing 'secretName'")

    def fullTag = "${awsAccountId}.dkr.ecr.${region}.amazonaws.com/${ecrRepoName}:${imageTag}"

    // def secrets = getAwsSecret(secretName, 'ap-south-1')
    // def AWS_ACCESS_KEY_ID = secrets.AWS_ACCESS_KEY_ID
    // def AWS_SECRET_ACCESS_KEY = secrets.AWS_SECRET_ACCESS_KEY

    sh """
        
        aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${awsAccountId}.dkr.ecr.${region}.amazonaws.com
        
        docker tag ${ecrRepoName}:${imageTag} ${fullTag}
        docker push ${fullTag}
    """
}
