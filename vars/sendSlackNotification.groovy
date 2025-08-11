def call(Map config = [:]) {
    def color = config.color ?: '#36a64f'
    def status = (config.status ?: error("Missing 'status'")).toUpperCase()
    def secretName = config.secretName ?: error("Missing 'secretName'")

    // Get secrets from AWS

    def secrets
    try {
        secrets = getAwsSecret(secretName, 'ap-south-1')
    } catch (e) {
        error("Failed to retrieve AWS secret '${secretName}': ${e.message}")
    }

    def slackToken = secrets.slack_bot_token ?: error("Missing 'slack_bot_token' in secrets '${secretName}'")

    def emojiMap = [
        SUCCESS : "✅ Deployment Successful!",
        FAILURE : "❌ Deployment Failed!",
        UNSTABLE: "⚠️ Unstable Deployment!",
        ABORTED : "🛑 Deployment Aborted!"
    ]

    def triggeredBy = BUILD_USER ?: "Automated"

     wrap([$class: 'BuildUser']) {
        slackSend(
            channel: slackChannel,
            token: slackToken,
            color: color,
            message: """\
                *${emojiMap[status] ?: status}*
                *Project:* `${env.JOB_NAME}`
                *Commit:* `${env.COMMIT_SHA ?: 'N/A'}`
                *Build Number:* #${env.BUILD_NUMBER}
                *Branch:* `${params.BRANCH ?: 'N/A'}`
                *Triggered By:* ${triggeredBy} 👤
                *Build Link:* <${env.BUILD_URL}|Click to view in Jenkins>
                _This is an automated notification from Jenkins 🤖_
                """.stripIndent().trim()
            )
    }
}
