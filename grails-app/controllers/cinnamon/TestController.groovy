package cinnamon

import cinnamon.index.SearchableDomain
import grails.plugin.springsecurity.annotation.Secured

@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
class TestController {

    def luceneService
    
    def search(){
        def q = "<BooleanQuery><Clause occurs='must'><TermQuery fieldName='name'>test</TermQuery></Clause></BooleanQuery>"
        def result = luceneService.search(q, "demo", SearchableDomain.OSD)
        log.debug(result)
        render(text:result.toString())
    }
    
    def echo(String msg) {
        log.info("TestController received msg: " + (msg?.encodeAsHTML()))
        log.info("Params: " + params)
        if (request.getHeader("microservice-test")?.equals("ok")) {
            log.info("Request was changed by microservice test.")
        }
        if (!msg) {
            msg = ""
        }
        render(text: msg.encodeAsHTML())
    }

    def echo2(String msg) {
        log.info("TestController received msg: " + (msg?.encodeAsHTML()))
        log.info("Params: " + params)
        if (request.getHeader("microservice-test")?.equals("ok")) {
            log.info("Request was changed by microservice test.")
        }
        if (!msg) {
            msg = ""
        }
        return [msg: msg, foo:'bar']
    }

    def microserviceChangeTriggerPreRequestTest(String msg) {
        log.info("microserviceChangeTriggerPreRequestTest received: " + (msg?.encodeAsHTML()))
        log.debug("Params: " + params)
        if (!msg) {
            throw new RuntimeException("parameter 'msg' is not set!")
        }
        response.setHeader("microservice-pre-test", "ok")
        render(text:"")
    }

    def microserviceChangeTriggerPostRequestTest(String msg) {
        log.info("microserviceChangeTriggerPostRequestTest received: " + (msg?.encodeAsHTML()))
        log.debug("Params: " + params)
        response.setHeader("microservice-post-test", "ok")
        render(text: "<xml>Just a trigger result</xml>")
    }


}
