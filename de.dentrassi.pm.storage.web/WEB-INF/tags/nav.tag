<%@ tag language="java" pageEncoding="UTF-8" trimDirectiveWhitespaces="true"%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://dentrassi.de/osgi/web" prefix="web" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="h" %>

<%@attribute name="menu" type="de.dentrassi.pm.storage.web.menu.Menu"%>

<c:if test="${not empty menu }">

<ul class="nav nav-tabs">
  <c:forEach items="${menu.nodes }" var="entry">
  
    <c:choose>
        <c:when test="${entry.getClass().simpleName eq 'Entry'}">
            <c:set var="url" value="${entry.target.renderFull(pageContext)}" />
            <li role="presentation" class='${web:active(pageContext.request, url)}'><a href="${url }"><h:menuEntry entry="${entry }"  /></a></li>
        </c:when>
        
        <c:when test="${entry.getClass().simpleName eq 'SubMenu' }">
             <li role="presentation" class="dropdown">
                 <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button">${fn:escapeXml(entry.label)} <span class="caret"></span></a>
                 <ul class="dropdown-menu" role="menu">
                      <c:forEach items="${entry.nodes }" var="subEntry">
                         <c:choose>
                             <c:when test="${subEntry.getClass().simpleName eq 'Entry'}">
                                 <c:set var="url" value="${subEntry.target.renderFull(pageContext)}" />
                                 <li <c:if test="${currentUrl eq url}" >class="active"</c:if>><a href="<c:url value="${url }" />" <c:if test="${subEntry.newWindow }"> target="_blank"</c:if> >${fn:escapeXml(subEntry.label) }</a></li>
                             </c:when>
                         </c:choose>
                      </c:forEach>
                 </ul>
             </li>
         </c:when>
        
    </c:choose>
  
  </c:forEach>
</ul>

</c:if>
