<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" trimDirectiveWhitespaces="true"%>
    
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://eclipse.org/packagedrone/web/form" prefix="form"%>

<%@ taglib tagdir="/WEB-INF/tags/main" prefix="h" %>

<h:main title="Import Test" subtitle="Result">

<script type="text/javascript">
function doAction(action) {
    var form = $('#command');
    form.attr("action", action);
    form.submit();
    return false;
} 
</script>

<form class="form-inline" method="GET" action="" id="command">

<div class="container-fluid form-padding">
	<div class="row">
	    <div class="col-md-6">
	        <h3 class="details-heading">Request</h3>
	        <dl class="dl-horizontal details">
	            <dt>Repository</dt>
	            <dd>
	                <c:choose>
	                    <c:when test="${empty configuration.repositoryUrl }"><em>Maven Central</em></c:when>
	                    <c:otherwise>${fn:escapeXml(configuration.repositoryUrl) }</c:otherwise>
	                </c:choose>
	            </dd>
	            
	            <dt>Coordinates</dt>
	            <dd>${fn:escapeXml(configuration.coordinates) }</dd>
	        </dl>
	    </div>
	</div>
</div>

<div class="table-responsive">
<table class="table table-hover table-condensed">
    <thead>
        <tr>
            <th></th>
            <th>Group ID</th>
            <th>Artifact ID</th>
            <th>Version</th>
            <th>Classifier</th>
            <th>Extension</th>
            <th></th>
        </tr>
    </thead>
    <tbody>
	    <c:forEach var="entry" items="${result.artifacts }">
	        <tr class="${ (not entry.resolved ) ? 'danger' : '' }">
                <td><input type="checkbox" name="${fn:escapeXml(entry.coordinates) }" checked="checked" /></td>
	            <td>${fn:escapeXml(entry.coordinates.groupId) }</td>
	            <td>${fn:escapeXml(entry.coordinates.artifactId) }</td>
	            <td>${fn:escapeXml(entry.coordinates.version) }</td>
	            <td>${fn:escapeXml(entry.coordinates.classifier) }</td>
	            <td>${fn:escapeXml(entry.coordinates.extension) }</td>
	            <td>${fn:escapeXml(entry.error) }</td>
	        </tr>
	    </c:forEach>
    </tbody>
</table>
</div>

<div class="container-fluid">

	<div class="row">
	    <div class="col-md-11 col-md-offset-1">
	            <input type="hidden" name=importConfig value="${fn:escapeXml(importConfig) }"/>
	            <button class="btn btn-primary" type="button" onclick="doAction('perform');">Import</button>
	            
	            <c:if test="${not empty cfgJson }">
	                <input type="hidden" name=configuration value="${fn:escapeXml(cfgJson) }"/>
	                <button class="btn btn-default" type="button" onclick="doAction('start');">Edit</button>
	            </c:if>
	    </div>
	</div>

</div>

</form>

</h:main>