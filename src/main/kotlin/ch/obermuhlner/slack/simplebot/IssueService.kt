package ch.obermuhlner.slack.simplebot

interface IssueService {

    fun connect(uri: String)

    fun getIssue(issueNumber: Int): Issue

    data class Issue(
            val id: String,
            val title: String,
            val description: String)

}