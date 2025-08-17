def call(Map config = [:]) {
    def imageTag = config.imageTag ?: env.COMMIT_SHA ?: error("Missing 'imageTag' and 'COMMIT_SHA' env var")
    def repoName = config.repoName ?: error("Missing 'repoName'")
    def awsAccountId = config.awsAccountId ?: error("Missing 'awsAccountId'")
    def region = config.region ?: env.REGION ?: 'ap-south-1'

    echo "Cleaning up local Docker images for tag: ${imageTag}"

    sh """
        docker rmi ${repoName}:${imageTag} || true
        docker rmi ${awsAccountId}.dkr.ecr.${region}.amazonaws.com/${repoName}:${imageTag} || true
    """
}
