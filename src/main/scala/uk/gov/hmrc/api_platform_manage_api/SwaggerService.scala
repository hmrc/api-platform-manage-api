/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.api_platform_manage_api

import io.swagger.models.{HttpMethod, Operation, Swagger}
import io.swagger.parser.SwaggerParser

import scala.collection.JavaConverters._
import scala.util.matching.Regex

class SwaggerService(environment: Map[String, String]) {

  val serviceNameRegex: Regex = """(.+)\.protected\.mdtp""".r

  def this() {
    this(sys.env)
  }

  def createSwagger(swaggerJson: String): Swagger = {
    val swagger: Swagger = new SwaggerParser().parse(swaggerJson)
    swagger.getPaths.asScala foreach { path =>
      path._2.getOperationMap.asScala foreach { op =>
        op._2.setVendorExtension("x-amazon-apigateway-integration", amazonApigatewayIntegration(swagger.getHost, path._1, op))

        op._2.getVendorExtensions.getOrDefault("x-auth-type", "") match {
          case "Application & Application User" =>
            op._2.addSecurity("api-key", List.empty.asJava)
            op._2.addSecurity("application-authorizer", List.empty.asJava)
          case "Application User" =>
            op._2.addSecurity("api-key", List.empty.asJava)
            op._2.addSecurity("user-authorizer", List.empty.asJava)
          case "None" => op._2.addSecurity("open-authorizer", List.empty.asJava)
        }
      }
    }
    swagger.vendorExtension("x-amazon-apigateway-policy", amazonApigatewayPolicy)
    swagger.vendorExtension("x-amazon-apigateway-gateway-responses", amazonApigatewayResponses(swagger.getInfo.getVersion))
    swagger.vendorExtension("securityDefinitions", securityDefinitions)
    swagger.vendorExtension("x-amazon-apigateway-api-key-source", "AUTHORIZER")
  }

  private def amazonApigatewayIntegration(host: String, path: String, operation: (HttpMethod, Operation)): Map[String, Object] = {
    val requestParameters: Seq[(String, String)] =
      Seq("integration.request.header.x-application-id" -> "context.authorizer.applicationId",
        "integration.request.header.Authorization" -> "context.authorizer.authBearerToken",
        "integration.request.header.X-Client-Authorization-Token" -> "context.authorizer.clientAuthToken",
        "integration.request.header.X-Client-Id" -> "context.authorizer.clientId") ++:
        operation._2.getParameters.asScala
          .filter(p => p.getIn == "path")
          .map(p => s"integration.request.path.${p.getName}" -> s"method.request.path.${p.getName}")

    serviceNameRegex.findFirstMatchIn(host) match {
      case Some(serviceNameMatch) =>
        Map("uri" -> s"https://${serviceNameMatch.group(1)}.${environment("domain")}$path",
          "responses" -> Map("default" -> Map("statusCode" -> "200")),
          "requestParameters" -> requestParameters.toMap,
          "passthroughBehavior" -> "when_no_match",
          "connectionType" -> "VPC_LINK",
          "connectionId" -> environment("vpc_link_id"),
          "httpMethod" -> operation._1.name,
          "type" -> "http_proxy")
      case None => throw new RuntimeException("Invalid host format")
    }
  }

  private def amazonApigatewayPolicy: ApiGatewayPolicy = {
    val statement = if (environment.isDefinedAt("endpoint_type") && environment("endpoint_type") == "REGIONAL") {
      Statement(effect = "Allow", condition = IpAddressCondition(IpAddress(environment("office_ip_address"))))
    } else {
      Statement(condition = VpceCondition(StringEquals(environment("vpc_endpoint_id"))))
    }
    ApiGatewayPolicy(statement = List(statement))
  }

  private def amazonApigatewayResponses(version: String): Map[String, Object] = {
    Map(
      "THROTTLED" -> Map("statusCode" -> "429", "responseTemplates" ->
        Map(s"application/vnd.hmrc.$version+json" -> """{"code": "MESSAGE_THROTTLED_OUT", "message": "The request for the API is throttled as you have exceeded your quota."}""",
            s"application/vnd.hmrc.$version+xml" -> "<errorResponse><code>MESSAGE_THROTTLED_OUT</code><message>The request for the API is throttled as you have exceeded your quota.</message></errorResponse>")),
      "UNAUTHORIZED" -> Map("statusCode" -> "401", "responseParameters" -> Map("gatewayresponse.header.www-authenticate" -> """'Bearer realm="HMRC API Platform"'"""),
        "responseTemplates" ->
          Map(s"application/vnd.hmrc.$version+json" -> """{"code": "MISSING_CREDENTIALS", "message": "Authentication information is not provided"}""",
              s"application/vnd.hmrc.$version+xml" -> "<errorResponse><code>MISSING_CREDENTIALS</code><message>Authentication information is not provided</message></errorResponse>")),
      "INVALID_API_KEY" -> Map("statusCode" -> "401", "responseParameters" -> Map("gatewayresponse.header.www-authenticate" -> """'Bearer realm="HMRC API Platform"'"""),
        "responseTemplates" ->
          Map(s"application/vnd.hmrc.$version+json" -> """{"code": "INVALID_CREDENTIALS", "message": "Invalid Authentication information provided"}""",
              s"application/vnd.hmrc.$version+xml" -> "<errorResponse><code>INVALID_CREDENTIALS</code><message>Invalid Authentication information provided</message></errorResponse>")),
      "ACCESS_DENIED" -> Map("statusCode" -> "403", "responseParameters" -> Map("gatewayresponse.header.www-authenticate" -> """'Bearer realm="HMRC API Platform"'"""),
        "responseTemplates" ->
          Map(s"application/vnd.hmrc.$version+json" -> """{"code": "$context.authorizer.code", "message": "$context.authorizer.message"}""",
              s"application/vnd.hmrc.$version+xml" -> "<errorResponse><code>$context.authorizer.code</code><message>$context.authorizer.message</message></errorResponse>")),
      "DEFAULT_4XX" -> Map("statusCode" -> "404", "responseTemplates" ->
        Map(s"application/vnd.hmrc.$version+json" -> """{"code": "MATCHING_RESOURCE_NOT_FOUND", "message": "A resource with the name in the request can not be found in the API"}""",
            s"application/vnd.hmrc.$version+xml" -> "<errorResponse><code>MATCHING_RESOURCE_NOT_FOUND</code><message>A resource with the name in the request can not be found in the API</message></errorResponse>")),
      "DEFAULT_5XX" -> Map("statusCode" -> "503", "responseTemplates" ->
        Map(s"application/vnd.hmrc.$version+json" -> """{"code": "SERVER_ERROR", "message": "A temporary problem occurred"}""",
          s"application/vnd.hmrc.$version+xml" -> "<errorResponse><code>SERVER_ERROR</code><message>A temporary problem occurred</message></errorResponse>"))
    )
  }

  private def securityDefinitions: Map[String, Object] = {
    val appAuthorizer = Map(
      "type" -> "apiKey",
      "name" -> "Authorization",
      "in" -> "header",
      "x-amazon-apigateway-authtype" -> "custom",
      "x-amazon-apigateway-authorizer" -> Map(
        "type" -> "request",
        "authorizerUri" -> environment("application_authorizer_uri"),
        "authorizerCredentials" -> environment("authorizer_credentials"),
        "authorizerResultTtlInSeconds" -> "0",
        "identitySource" -> "method.request.header.Authorization"))

    val userAuthorizer = Map(
      "type" -> "apiKey",
      "name" -> "Authorization",
      "in" -> "header",
      "x-amazon-apigateway-authtype" -> "custom",
      "x-amazon-apigateway-authorizer" -> Map(
        "type" -> "request",
        "authorizerUri" -> environment("user_authorizer_uri"),
        "authorizerCredentials" -> environment("authorizer_credentials"),
        "authorizerResultTtlInSeconds" -> "0",
        "identitySource" -> "method.request.header.Authorization"))

    val openAuthorizer = Map(
      "type" -> "apiKey",
      "name" -> "Unused",
      "in" -> "header",
      "x-amazon-apigateway-authtype" -> "custom",
      "x-amazon-apigateway-authorizer" -> Map(
        "type" -> "request",
        "authorizerUri" -> environment("open_authorizer_uri"),
        "authorizerCredentials" -> environment("authorizer_credentials"),
        "authorizerResultTtlInSeconds" -> "0",
        "identitySource" -> "context.httpMethod, context.path"))

    Map("api-key"-> Map("type" -> "apiKey", "name" -> "x-api-key", "in" -> "header"),
        "application-authorizer" -> appAuthorizer,
        "user-authorizer" -> userAuthorizer,
        "open-authorizer" -> openAuthorizer
    )
  }
}
