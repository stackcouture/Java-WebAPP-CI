// vars/checkoutGit.groovy
def call(String gitBranch, String gitUrl, String secretName) {
    echo "Fetching AWS secrets..."
    def secrets = getAwsSecret(secretName, 'ap-south-1')
    if (secrets == null || !secrets.github_pat) {
        error("Failed to retrieve GitHub PAT from AWS secrets")
    }
    def credentialsId = secrets.github_pat

    checkout([
        $class: 'GitSCM',
        branches: [[name: "*/${gitBranch}"]],
        userRemoteConfigs: [[url: gitUrl, credentialsId: credentialsId]]
    ])
}
