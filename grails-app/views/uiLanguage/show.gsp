<!DOCTYPE HTML>
<html>
<head>
	<meta name="layout" content="main"/>

        <title><g:message code="uiLanguage.show.title"/></title>
    </head>
    <body>
   	
  
        <div class="nav">
			<g:homeButton><g:message code="home"/></g:homeButton><g:adminButton/>
            <span class="menuButton"><g:link class="list" action="list"><g:message code="uiLanguage.list"/></g:link></span>
            <span class="menuButton"><g:link class="create" action="create"><g:message code="uiLanguage.create"/></g:link></span>
        </div>
        <div class="content">
            <h1><g:message code="uiLanguage.show.title"/></h1>
			<g:render template="/shared/message"/>

            <div class="dialog">
                <table>
                    <tbody>

                    
                        <tr class="prop">
                            <td class="name"><g:message code="id"/></td>
                            
                            <td class="value">${fieldValue(bean:language, field:'id')}</td>
                            
                        </tr>
                    
                        <tr class="prop">
                            <td class="name"><g:message code="uiLanguage.isoCode"/></td>
                            
                            <td class="value">${fieldValue(bean:language, field:'isoCode')}</td>
                            
                        </tr>
           
                    </tbody>
                </table>
            </div>
            <div class="buttons">
                <g:form>
                    <input type="hidden" name="id" value="${language?.id}" />
                    <span class="button"><g:actionSubmit action="edit" class="edit" value="${message(code:'edit')}" /></span>
                    <span class="button"><g:actionSubmit action="delete" class="delete" onclick="return confirm('Are you sure?');" value="${message(code:'delete')}" /></span>
                </g:form>
            </div>
        </div>


</body></html>
