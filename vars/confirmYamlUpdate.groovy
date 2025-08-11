def call(String message = 'Update deployment YAML with new Docker tag?') {
    def userInput = input(
        id: 'userApproval',
        message: message,
        parameters: [
            choice(name: 'Confirmation', choices: ['Yes', 'No'], description: 'Proceed with update?')
        ]
    )

    def approver = 'unknown'
    try {
        def causes = currentBuild.rawBuild.getCauses()
        def inputCause = causes.find { it.class.simpleName.contains("UserIdCause") }
        if (inputCause?.getUserId()) {
            approver = inputCause.getUserId()
        }
    } catch (e) {
        echo "Unable to determine approver: ${e.getMessage()}"
    }

    echo "Approval received from: ${approver}"

    if (userInput == 'No') {
        error 'Aborted by user.'
    }

    currentBuild.description = "Approved by ${approver}"

    return approver
}
