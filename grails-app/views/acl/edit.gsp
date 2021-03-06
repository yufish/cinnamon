<!DOCTYPE HTML>
<html>
<head>
    <meta name="layout" content="main"/>
	<title><g:message code="acl.edit.title"/></title>
</head>
<body>
<div class="nav">
	<g:homeButton><g:message code="home"/></g:homeButton><g:adminButton/>
	<span class="menuButton"><g:link class="list" action="list"><g:message code="acl.list"/></g:link></span>
	<span class="menuButton"><g:link class="create" action="create"><g:message code="acl.create"/></g:link></span>
</div>
<div class="content">
	<h1><g:message code="acl.edit.title"/></h1>
	<g:if test="${flash.message}">
		<div class="message">${flash.message}</div>
	</g:if>
	<g:hasErrors bean="${acl}">
		<div class="errors">
			<g:renderErrors bean="${acl}" as="list"/>
		</div>
	</g:hasErrors>
	<g:form method="post">
		<input type="hidden" name="id" value="${acl?.id}"/>
			<table>
				<tbody>

				<tr class="prop">
					<td class="name">
						<label for="name"><g:message code="acl.name"/></label>
					</td>
					<td class="value ${hasErrors(bean: acl, field: 'name', 'errors')}">
						<input type="text" name="name" id="name" value="${fieldValue(bean: acl, field: 'name')}"/>
					</td>
				</tr>

				</tbody>
			</table>
			<div class="buttons">
				<span class="button"><g:actionSubmit class="save" value="${message(code:'update')}"/></span>
			</div>

	</g:form>
	<br>
	<div class="dialog">
		<g:form>
			<table>
				<tbody id="aclEntryManagement">
					<g:render template="aclEntryManagement" model="[acl:acl, freeGroups:freeGroups]"/>
				</tbody>

			</table>
		</g:form>
	</div>
</div>

</body></html>