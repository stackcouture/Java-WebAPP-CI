def call(String secretName, String region = 'ap-south-1') {
    def secretJson = sh(
        script: """#!/bin/bash
            aws secretsmanager get-secret-value \
                --secret-id '${secretName}' \
                --region '${region}' \
                --query SecretString \
                --output text
        """,
        returnStdout: true,
        label: 'Fetching secret'
    ).trim()

    return readJSON(text: secretJson)
}
