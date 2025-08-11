def call(String secretName, String region = 'ap-south-1') {
    def secretJson = sh(
        script: "aws secretsmanager get-secret-value --secret-id ${secretName} --region ${region} --query SecretString --output text",
        returnStdout: true,
        returnStatus: false
    ).trim()

    def secret = readJSON text: secretJson
    return secret
}
