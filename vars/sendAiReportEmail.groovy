def call(Map args = [:]) {
    def branch = args.get('branch', 'main')
    def commitSha = args.get('commit', 'N/A')
    def toEmail = args.get('to', 'you@example.com')

    if (!fileExists("ai_report.html")) {
        echo "No ai_report.html found. Skipping email and report publishing."
        return
    }

    echo "ai_report.html found. Converting to PDF..."
    sh "wkhtmltopdf --zoom 1.3 --enable-local-file-access ai_report.html ai_report.pdf"

    echo "Sending email with PDF attachment..."
    sendReportEmail(branch, commitSha, toEmail, (status == 0))

    echo "Publishing HTML report to Jenkins UI..."
    publishHtmlReport('AI Security Report', '.', 'ai_report.html')
}

// --- helper method to send email
def sendReportEmail(String branch, String commitSha, String toEmail, boolean attachPdf = true) {
    def attachments = attachPdf ? 'ai_report.pdf' : ''
    emailext(
        subject: "Security Report - Build #${env.BUILD_NUMBER} - SUCCESS",
        body: """
            <html>
                <body style="font-family: Arial, sans-serif; font-size: 15px; line-height: 1.6; padding: 10px;">
                    <h2 style="color: #2c3e50;">Hello Team,</h2>
                    <p>
                        Please find attached the <strong>AI-generated security report</strong> for <strong>Build #${env.BUILD_NUMBER}</strong>.
                    </p>
                    <p>
                        This report summarizes security scan results from <strong>Trivy</strong>, <strong>Snyk</strong> and <strong>SonarQube</strong>.
                    </p>
                    <p>
                        <strong>Project:</strong> ${env.JOB_NAME}<br/>
                        <strong>Branch:</strong> ${branch}<br/>
                        <strong>Commit:</strong> ${commitSha}
                    </p>
                    <p>
                        For details, please open the attached PDF.
                    </p>
                    <p>
                        Regards,<br/>
                        <strong>Jenkins CI/CD</strong>
                    </p>
                </body>
            </html>
        """,
        mimeType: 'text/html',
        attachmentsPattern: attachments,
        to: toEmail,
        attachLog: false
    )
}

// --- helper method to publish HTML report
def publishHtmlReport(String reportName, String reportDir, String reportFile) {
    publishHTML([
        allowMissing: false,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: reportDir,
        reportFiles: reportFile,
        reportName: reportName
    ])
}
