package cinnamon

import grails.plugin.springsecurity.SpringSecurityUtils

class LogoutController {

    def repositoryService
    def userService

	/**
	 * Index action. Redirects to the Spring security logout uri.
	 */
	def index = {
		// TODO  put any pre-logout code here
        UserAccount user = userService.user
        if (session.ticket) {
            def cmnSession = Session.findByTicket(session.ticket)
            cmnSession?.delete()
        }

		redirect uri: SpringSecurityUtils.securityConfig.logout.filterProcessesUrl // '/j_spring_security_logout'
	}

    def info = {
        return [logoutMessage:params.logoutMessage?.encodeAsHTML()]
    }
}
