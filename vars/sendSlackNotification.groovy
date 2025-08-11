def call(Map config = [:]) {
    def color = config.color ?: '#36a64f'
    def status = (config.status ?: error("Missing 'status'")).toUpperCase()
    def secretName = config.secretName ?: error("Missing 'secretName'")
    def slackChannel = config.channel ?: "#app-demo" // You can override this via config

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

    // Wrap to access BUILD_USER info
    wrap([$class: 'BuildUser']) {
        def triggeredBy = env.BUILD_USER_ID ?: "Automated"
        def commitSha = env.COMMIT_SHA ?: "N/A"
        def buildUrl = env.BUILD_URL ?: "#"
        def jobName = env.JOB_NAME ?: "N/A"
        def buildNumber = env.BUILD_NUMBER ?: "N/A"
        def branch = params.BRANCH ?: env.GIT_BRANCH ?: "N/A"

        slackSend(
            channel: slackChannel,
            token: slackToken,
            color: color,
            message: """\
                *${emojiMap[status] ?: status}*
                *Project:* `${jobName}`
                *Commit:* `${commitSha}`
                *Build Number:* #${buildNumber}
                *Branch:* `${branch}`
                *Triggered By:* ${triggeredBy} 👤
                *Build Link:* <${buildUrl}|Click to view in Jenkins>
                _This is an automated notification from Jenkins 🤖_
                """.stripIndent().trim()
        )
    }
}
