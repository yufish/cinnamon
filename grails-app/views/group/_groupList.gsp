<%@ page import="cinnamon.CmnGroup;" %>
<table>
    <thead>
    <tr>

        <g:sortableColumn property="id" title="${message(code:'id')}"/>

        <g:sortableColumn property="name" title="${message(code:'group.name')}"/>

        <th>${message(code: 'group.parent')}</th>

    </tr>
    </thead>
    <tbody>
    <g:each in="${groupList}" status="i" var="group">
        <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">

            <td><g:link action="show" id="${group.id}">${fieldValue(bean: group, field: 'id')}</g:link></td>

            <td>${fieldValue(bean: group, field: 'name')}</td>

            <td>${group?.parent?.name ?: "-"}</td>

        </tr>
    </g:each>
    </tbody>
</table>

<div class="paginateButtons">
    <util:remotePaginate controller="group" action="updateList" total="${CmnGroup.count()}"
                         update="groupList" max="100" pageSizes="[100, 250, 500, 1000]"/>
</div>