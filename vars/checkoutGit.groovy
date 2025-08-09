def call(String gitBranch, String gitUrl, String secretName) {

    def secrets = getAwsSecret(secretName, 'ap-south-1')
    def credentialsId = secrets.github_pat
    checkout([
        $class: 'GitSCM',
        branches: [[name: "*/${gitBranch}"]],
        userRemoteConfigs: [
            [url: gitUrl, credentialsId: credentialsId]
        ]
    ])
}