package ch.obermuhlner.slack.simplebot.xentis

import ch.obermuhlner.slack.simplebot.IssueService
import com.perforce.p4java.option.server.TrustOptions
import com.perforce.p4java.server.IServer
import com.perforce.p4java.server.ServerFactory

class HelixIssueService : IssueService {

    lateinit var server: IServer

    override fun connect(uri: String) {
        val optionsServer = ServerFactory.getOptionsServer(uri, null)
        optionsServer.addTrust(TrustOptions().setAutoAccept(true))

        server = ServerFactory.getServer(uri, null)
        server.connect()

        val info = server.serverInfo
        println(info)
    }

    override fun getIssue(issueNumber: Int): IssueService.Issue {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}