def call(Map config = [:]) {
    def color = config.color ?: '#36a64f'
    def status = config.status ?: error("Missing 'status'")
    def secretName = config.secretName ?: error("Missing 'secretName'")

    // Get secrets from AWS
    def secrets = getAwsSecret(secretName, 'ap-south-1')
    def slackToken = secrets.slack_bot_token

    // Status → Message mapping
    def emojiMap = [
        SUCCESS : "✅ Deployment Successful!",
        FAILURE : "❌ Deployment Failed!",
        UNSTABLE: "⚠️ Unstable Deployment!",
        ABORTED : "🛑 Deployment Aborted!"
    ]

    wrap([$class: 'BuildUser']) {
        slackSend(
            channel: env.SLACK_CHANNEL,
            token: slackToken,   
            color: color,
            message: """\
                *${emojiMap[status] ?: status}*
                *Project:* `${env.JOB_NAME}`
                *Commit:* `${env.COMMIT_SHA}`
                *Build Number:* #${env.BUILD_NUMBER}
                *Branch:* `${params.BRANCH}`
                *Triggered By:* ${BUILD_USER} 👤
                *Build Link:* <${env.BUILD_URL}|Click to view in Jenkins>
                _This is an automated notification from Jenkins 🤖_
                """
        )
    }
}
