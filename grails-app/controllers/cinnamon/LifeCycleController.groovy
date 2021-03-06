package cinnamon

import cinnamon.exceptions.CinnamonException
import grails.plugin.springsecurity.annotation.Secured
import cinnamon.lifecycle.LifeCycle
import cinnamon.lifecycle.LifeCycleState
import org.dom4j.DocumentHelper
import org.dom4j.Element

@Secured(["hasRole('_superusers')"])
class LifeCycleController extends BaseController {

    def create () {
        render(template: 'create', model: [
               states:getDefaultStates(null),
               defaultStates:getDefaultStates(null)])
    }

    def index () {
        params.max = params.max ?: 10
        params.sort = params.sort ?: 'id'
        [lifeCycleList: LifeCycle.list(params)]
    }

    def save () {
        LifeCycle lifeCycle = new LifeCycle()
        try {
            updateFields(lifeCycle)
            lifeCycle.save(flush: true)
            setListParams()
            render(template: 'list_table', model: [lifeCycleList: LifeCycle.list(params)])
        }
        catch (Exception e) {
            log.debug("failed to save lifeCycle: ", e)
            render(status: 503, template: 'create',
                    model: [lifeCycle: lifeCycle,
                            defaultStates:getDefaultStates(null),
                            states:getDefaultStates(null),
                            errorMessage: e.getLocalizedMessage().encodeAsHTML()])
        }

    }

    def list () {
        redirect(action: 'index')
    }

    def updateList () {
        setListParams()
        log.debug("updateList called")
        render(template: 'list_table', model:[lifeCycleList:LifeCycle.list(params)])
    }

    def edit () {
        LifeCycle lifeCycle = LifeCycle.get(Long.parseLong(params.id))
        log.debug("defaultStates: ${getDefaultStates(lifeCycle).dump()}")
        render(template: 'edit', model: [lifeCycle:lifeCycle,
                defaultStates:getDefaultStates(lifeCycle),
                states:getDefaultStates(lifeCycle)
        ])
    }

    def cancelEdit () {
        render(template: 'row', model: [lifeCycle: LifeCycle.get(Long.parseLong(params.id))])
    }

    def delete () {
        LifeCycle lifeCycle = LifeCycle.get(params.id)
        try {
            lifeCycle.fetchStates().each {state ->
                // first check if any states are in use at the moment -
                // we cannot leave objects lying around with orphaned LifeCycleStates.
                if (ObjectSystemData.findByState(state)) {
                    throw new RuntimeException("error.object.in.use")
                }
            }
            def states = lifeCycle.fetchStates().collect{it}
            states.each{LifeCycleState state ->
//                lifeCycle.removeFromStates(state)
                state.lifeCycle = null
            }
            if(lifeCycle.defaultState){
                lifeCycle.defaultState.lifeCycle = null
                lifeCycle.defaultState = null
            }
            lifeCycle.delete(flush: true)
        }
        catch (Exception e) {
//            log.debug("ex: ",e)
            render(status: 503, template: '/shared/showError', model: [infoMessage: message(code: e.getLocalizedMessage())])
            return
        }
        setListParams()
        render(template: 'list_table', model: [lifeCycleList: LifeCycle.list(params)])
    }

    protected void updateFields(LifeCycle lifeCycle) {
        lifeCycle.name = inputValidationService.checkAndEncodeName(params.name, lifeCycle)
        if(params.defaultState){
            lifeCycle.defaultState = (LifeCycleState) inputValidationService.checkObject(LifeCycleState.class, params.defaultState)
        }
        else if(lifeCycle.defaultState){
            log.debug("remove defaultState from lifeCycle")
            lifeCycle.defaultState = null
        }
        def states = params.list('states').collect {inputValidationService.checkObject(LifeCycleState.class, it)}
        if(! states.contains(lifeCycle.defaultState) ){
            states.add(lifeCycle.defaultState)
        }
        
        lifeCycle.save()
        
        /*
         * Try to remove LifeCycleStates which are no longer valid.
         */
        def currentStates = lifeCycle.fetchStates()
        currentStates.each {LifeCycleState state ->
            if (!states.contains(state)) {
                if (ObjectSystemData.findByState(state)) {
                    throw new RuntimeException("error.object.in.use")
                }
                log.debug("remove no longer valid LCS from lifeCycle.states")
                state.lifeCycle = null
            }
        }
        /*
         * Add all the new LifeCycleStates:
         */
        states.each {LifeCycleState state ->
            if (state && ! lifeCycle.fetchStates().contains(state)) {
                state.lifeCycle = lifeCycle
            }
        }
    }

    def update () {
        LifeCycle lifeCycle = LifeCycle.get(params.id)
        try {
            updateFields(lifeCycle)
            lifeCycle.save(flush: true)
            render(template: 'row', model: [lifeCycle: lifeCycle])
        }
        catch (Exception e) {
            log.debug("failed to save lifeCycle: ", e)
            render( status:503,
                    template: 'edit',
                    model: [lifeCycle: lifeCycle,
                            defaultStates:getDefaultStates(lifeCycle),
                            states:getDefaultStates(lifeCycle),
                            errorMessage: e.getLocalizedMessage()])
        }

    }

    protected List<LifeCycleState> getDefaultStates(LifeCycle lc){
        List<LifeCycleState> lcList = new ArrayList<LifeCycleState>()
        if(lc == null){
            // used where we have an unsaved lifeCycle instance.
            lcList.addAll(LifeCycleState.findAll("from LifeCycleState lcs where lcs.lifeCycle=null"))
        }
        else{
            lcList.addAll(LifeCycleState.findAll("from LifeCycleState lcs where lcs.lifeCycle=null or lcs.lifeCycle=:lifeCycle",[lifeCycle:lc]))
        }
        return lcList
    }
    
    //----------------------- XML API -------------------------
    
    /**
    * Get all lifecycle objects from the database and return
    * them as an XML list.
    * @return a Response object which sends an XML document to the user which
    * contains all lifecycle objects.
    */
    @Secured(["isAuthenticated()"])
	def listLifeCyclesXml() {
        try{
            List<LifeCycle> cycles = LifeCycle.list()
            def doc = DocumentHelper.createDocument()
            def  root = doc.addElement("lifecycles");
            for(LifeCycle lc : cycles){
                lc.toXmlElement(root);
            }
            render(contentType: 'application/xml', text: doc.asXML())
        }
        catch (Exception e){
            renderExceptionXml('Failed to listLifeCycles as XML.',e)
        }
	}

    /**
     * Fetch a specific lifecycle by name or id from the database as XML. If both id and name
     * parameters are set, it will ignore the name parameter.
     * @param name  Name of the lifecycle - you must specify either the name or the id
     * @param id Id of the lifecycle - you must specify either the name or the id.
     * @return an XML document which
     * contains the requested lifecycle object 
     * (or an error message if the lifecycle could not be found).<br>
     *     Example:
     * <pre>
     *  {@code
     *  <lifecycles>
     *      <lifecycle>
     *          <id>1234</id>
     *          <name>DemoLC</name>
     *          <sysName>lifecycle.demo</sysName>
     *          <defaultState> ... (if set, contains serialized LCS)</defaultState>
     *          <states>
     *            <lifecycleState>
     *              <id>543</id>
     *              <name>TestState</name>
     *              <sysName>example.test.state</sysName>
     *              <stateClass>server.lifecycle.state.NopState</stateClass>
     *              <parameter>&lt;config /&gt;</parameter> (encoded XML string)
     *              <lifeCycle>44</lifeCycle> (may be empty)
     *              <lifeCycleStateForCopy>7</lifeCycleStateForCopy> (may be empty)
     *            </lifecycleState>
     *            ... (other states)
     *          </states>
     *      </lifecycle>
     *  </lifecycles>
     *  }
     * </pre>
     */
    @Secured(["isAuthenticated()"])
    def getLifeCycle(Long id, String name) {
        try {
            LifeCycle lifeCycle;
            if(id){
                lifeCycle = LifeCycle.get(id)
            }
            else if(name){
                lifeCycle = LifeCycle.findByName(name)
            }
            else{
                throw new CinnamonException("error.param.lc.missing");
            }

            if(lifeCycle == null){
                throw new CinnamonException("error.lifecycle.missing");
            }
            
            def doc = DocumentHelper.createDocument()
            Element root = doc.addElement('lifecycles')
            lifeCycle.toXmlElement(root)
            render(contentType: 'application/xml', text:doc.asXML() )            
        }
        catch (Exception e) {
            renderExceptionXml('Failed to ', e)
        }
    }
    
    
}
