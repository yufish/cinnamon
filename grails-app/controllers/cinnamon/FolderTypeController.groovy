package cinnamon

import org.dom4j.Element
import org.dom4j.DocumentHelper
import grails.plugin.springsecurity.annotation.Secured

@Secured(["isAuthenticated()"])
class FolderTypeController extends BaseController{

    def create() {
//        FolderType ft = new FolderType(params)
//        return [folderType:null]
    }

    def index() {
        redirect(action: 'list')
    }

    @Secured(["hasRole('_superusers')"])
    def save(String name, String config) {
        try {
            FolderType folderType = new FolderType(name: name)
            folderType.config = config
            folderType.save(failOnError: true)
            log.debug("folderType: ${folderType}")
            return redirect(action: 'show', params: [id: folderType?.id])
        }
        catch (Exception e) {
            log.debug("failed to save FolderType:", e)
            flash.message = e.getLocalizedMessage().encodeAsHTML()
            return redirect(action: 'create', controller: 'folderType', params: params) //, params:[folderType:folderType])
        }

        
    }

    def list() {
        setListParams()
        [folderTypeList: FolderType.list(params)]
    }

    def show () {
        if (params.id) {
            FolderType ft = FolderType.get(params.id)
            if (ft != null) {
                return [folderType: ft]
            }
        }
        flash.message = message(code: 'error.access.failed')
        return redirect(action: 'list', controller: 'folderType')
    }

    @Secured(["hasRole('_superusers')"])
    def edit () {
        [folderType: FolderType.get(params.id)]
    }

    @Secured(["hasRole('_superusers')"])
    def delete () {
        FolderType ft = FolderType.get(params.id)
        try {
            ft.delete(flush: true)
            flash.message = message(code: 'folderType.delete.success', args: [ft.name.encodeAsHTML()])
        }
        catch (Exception e) {
            log.debug("failed to delete FolderType:", e)
            flash.message = message(code: 'folderType.delete.fail', args: [e.getLocalizedMessage()?.encodeAsHTML()])
        }
        return redirect(action: 'list')
    }

    @Secured(["hasRole('_superusers')"])
    def update (String name, String config, Long id) {
        FolderType ft = FolderType.get(id)
        try {
            ft.name = name
            ft.config = config
            ft.save(flush: true)
        }
        catch (Exception e) {
            log.debug("failed to update FolderType:",e)
            flash.message = e.getLocalizedMessage()
            return redirect(action: 'edit', params: [id: id])
        }
        return redirect(action: 'show', params: [id: id])
    }

    def updateList () {
        setListParams()
        render(template: 'folderTypeList', model: [folderTypeList: FolderType.list(params)])
    }

    //---------------------------------------------------
    // Cinnamon XML Server API
    def listXml() {
        def doc = DocumentHelper.createDocument()
        Element root = doc.addElement("folderTypes");
        FolderType.list().each{folderType ->
            root.add(FolderType.asElement("folderType", folderType));
        }
        render(contentType: 'application/xml', text: doc.asXML())
    }

    @Secured(["hasRole('_superusers')"])
    def saveXml(String name, String config){
        def conf = config
        if( config == null ){
            conf = '<meta />'
        }
        def doc = DocumentHelper.createDocument()
        FolderType folderType = new FolderType(name: name, config: conf )
        if(folderType.validate()){
            folderType.save()
            log.debug("folderType: ${folderType}")
            Element root = doc.addElement("folderTypes");
            root.add(FolderType.asElement("folderType", folderType))
            log.debug("request success: "+doc.asXML())
            render(contentType: 'application/xml', text: doc.asXML())
        }
        else{
            // TODO: XML error list.
            // TODO: generic method for all domain objects to turn their errors into a list
            def errorList = folderType.errors.allErrors
            def errorsFlat = errorList.collect(errorList){error ->
                error.toString()
            }.join(" ") 
            log.debug("request has errors: "+errorsFlat)
            renderException(errorsFlat)
        }
        
    }
}
