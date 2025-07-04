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

import java.util.UUID

import com.stephenn.scalatest.jsonassert.JsonMatchers
import io.swagger.models.Swagger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.api_platform_manage_api.utils.JsonMapper

import scala.jdk.CollectionConverters._

class SwaggerServiceSpec extends AnyWordSpec with Matchers with JsonMatchers with JsonMapper {

  val officeIpAddress = "192.168.1.1/32"
  val vpcEndpointId: String = UUID.randomUUID().toString

  trait Setup {
    val environment: Map[String, String] = Map(
      "domain" -> "integration.tax.service.gov.uk",
      "vpc_link_id" -> "gix6s7",
      "office_ip_address" -> officeIpAddress,
      "application_authorizer_uri" -> "arn:aws:apigateway:application_authorizer",
      "user_authorizer_uri" -> "arn:aws:apigateway:user_authorizer",
      "open_authorizer_uri" -> "arn:aws:apigateway:open_authorizer",
      "authorizer_credentials" -> "arn:aws:iam::account-id:foobar"
    )

    def swaggerJson(host: String = "api-example-microservice.protected.mdtp"): String =
      s"""{"host": "$host", "paths": {
          |"/application": {"get": {"responses": {"200": {"description": "OK"}},
          |"x-auth-type": "Application & Application User", "x-throttling-tier": "Unlimited"}},
          |"/user": {"get": {"responses": {"200": {"description": "OK"}},
          |"x-auth-type": "Application User", "x-throttling-tier": "Unlimited"}},
          |"/world": {"get": {"responses": {"200": {"description": "OK"}},
          |"x-auth-type": "None", "x-throttling-tier": "Unlimited"}}},
          |"info": {"title": "Test OpenAPI","version": "1.0"}, "swagger": "2.0"}""".stripMargin

    def swaggerJsonWithPathParameters(host: String = "api-example-microservice.protected.mdtp"): String =
      s"""{"host": "$host", "paths": {
         |"/{givenName}": {"get": {"parameters": [{"name": "givenName","required": true,"in": "path","type": "string"}],
         |"responses": {"200": {"description": "OK"}},
         |"x-auth-type": "None", "x-throttling-tier": "Unlimited"}}},
         |"info": {"title": "Test OpenAPI","version": "1.0"}, "swagger": "2.0"}""".stripMargin
  }

  trait StandardSetup extends Setup {
    val extraVariables: Map[String, String] = Map("vpc_endpoint_id" -> vpcEndpointId)
    val swaggerService = new SwaggerService(environment ++ extraVariables)
  }

  trait SetupWithoutVpcEndpointId extends Setup {
    val swaggerService = new SwaggerService(environment)
  }

  trait SetupForRegionalEndpoints extends Setup {
    val extraVariables: Map[String, String] = Map("endpoint_type" -> "REGIONAL")
    val swaggerService = new SwaggerService(environment ++ extraVariables)
  }

  "createSwagger" should {

    "add amazon extension for API gateway policy" in new StandardSetup {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      swagger.getVendorExtensions should contain key "x-amazon-apigateway-policy"
      val apiGatewayPolicy: ApiGatewayPolicy = swagger.getVendorExtensions.get("x-amazon-apigateway-policy").asInstanceOf[ApiGatewayPolicy]
      apiGatewayPolicy.statement should have length 1
      apiGatewayPolicy.statement.head.effect shouldEqual "Deny"
      apiGatewayPolicy.statement.head.condition shouldBe a [VpceCondition]
      val condition: VpceCondition = apiGatewayPolicy.statement.head.condition.asInstanceOf[VpceCondition]
      condition.stringNotEquals.awsSourceVpce shouldEqual vpcEndpointId
    }

    "add IP address condition if endpoint type is regional" in new SetupForRegionalEndpoints {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      swagger.getVendorExtensions should contain key "x-amazon-apigateway-policy"
      val apiGatewayPolicy: ApiGatewayPolicy = swagger.getVendorExtensions.get("x-amazon-apigateway-policy").asInstanceOf[ApiGatewayPolicy]
      apiGatewayPolicy.statement should have length 1
      apiGatewayPolicy.statement.head.effect shouldEqual "Allow"
      apiGatewayPolicy.statement.head.condition shouldBe a [IpAddressCondition]
      val condition: IpAddressCondition = apiGatewayPolicy.statement.head.condition.asInstanceOf[IpAddressCondition]
      condition.ipAddress.awsSourceIp shouldEqual officeIpAddress
    }

    "throw exception if no VPC endpoint ID specified in the environment and endpoint type is not regional" in new SetupWithoutVpcEndpointId {
      val ex: Exception = intercept[Exception] {
        swaggerService.createSwagger(swaggerJson())
      }
      ex.getMessage shouldEqual "key not found: vpc_endpoint_id"
    }

    "add amazon extension for API gateway responses" in new StandardSetup {
      val expectedJson: String =
        """{
          |  "THROTTLED":{
          |    "statusCode": "429",
          |    "responseTemplates":{
          |      "application/vnd.hmrc.1.0+json": "{\"code\": \"MESSAGE_THROTTLED_OUT\", \"message\": \"The request for the API is throttled as you have exceeded your quota.\"}",
          |      "application/vnd.hmrc.1.0+xml": "<errorResponse><code>MESSAGE_THROTTLED_OUT</code><message>The request for the API is throttled as you have exceeded your quota.</message></errorResponse>"
          |    }
          |  },
          |  "UNAUTHORIZED": {
          |    "statusCode": "401",
          |    "responseParameters": {
          |      "gatewayresponse.header.www-authenticate": "'Bearer realm=\"HMRC API Platform\"'"
          |    },
          |    "responseTemplates": {
          |      "application/vnd.hmrc.1.0+json": "{\"code\": \"MISSING_CREDENTIALS\", \"message\": \"Authentication information is not provided\"}",
          |      "application/vnd.hmrc.1.0+xml": "<errorResponse><code>MISSING_CREDENTIALS</code><message>Authentication information is not provided</message></errorResponse>"
          |    }
          |  },
          |  "INVALID_API_KEY": {
          |    "statusCode": "401",
          |    "responseParameters": {
          |      "gatewayresponse.header.www-authenticate": "'Bearer realm=\"HMRC API Platform\"'"
          |    },
          |    "responseTemplates": {
          |      "application/vnd.hmrc.1.0+json": "{\"code\": \"INVALID_CREDENTIALS\", \"message\": \"Invalid Authentication information provided\"}",
          |      "application/vnd.hmrc.1.0+xml": "<errorResponse><code>INVALID_CREDENTIALS</code><message>Invalid Authentication information provided</message></errorResponse>"
          |    }
          |  },
          |  "ACCESS_DENIED": {
          |    "statusCode": "403",
          |    "responseParameters": {
          |      "gatewayresponse.header.www-authenticate": "'Bearer realm=\"HMRC API Platform\"'"
          |    },
          |    "responseTemplates": {
          |      "application/vnd.hmrc.1.0+json": "{\"code\": \"$context.authorizer.code\", \"message\": \"$context.authorizer.message\"}",
          |      "application/vnd.hmrc.1.0+xml": "<errorResponse><code>$context.authorizer.code</code><message>$context.authorizer.message</message></errorResponse>"
          |    }
          |  },
          |  "DEFAULT_4XX": {
          |    "statusCode": "404",
          |    "responseTemplates": {
          |      "application/vnd.hmrc.1.0+json": "{\"code\": \"MATCHING_RESOURCE_NOT_FOUND\", \"message\": \"A resource with the name in the request can not be found in the API\"}",
          |      "application/vnd.hmrc.1.0+xml": "<errorResponse><code>MATCHING_RESOURCE_NOT_FOUND</code><message>A resource with the name in the request can not be found in the API</message></errorResponse>"
          |    }
          |  },
          |  "DEFAULT_5XX": {
          |    "statusCode": "503",
          |    "responseTemplates": {
          |      "application/vnd.hmrc.1.0+json": "{\"code\": \"SERVER_ERROR\", \"message\": \"A temporary problem occurred\"}",
          |      "application/vnd.hmrc.1.0+xml": "<errorResponse><code>SERVER_ERROR</code><message>A temporary problem occurred</message></errorResponse>"
          |    }
          |  }
          |}""".stripMargin

      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      swagger.getVendorExtensions should contain key "x-amazon-apigateway-gateway-responses"
      toJson(swagger.getVendorExtensions.get("x-amazon-apigateway-gateway-responses")) should matchJson(expectedJson)
    }

    "add security definitions" in new StandardSetup {
      val expectedJson: String =
        """{
          |    "api-key": {
          |      "type": "apiKey",
          |      "name": "x-api-key",
          |      "in": "header"
          |    },
          |    "application-authorizer": {
          |        "type": "apiKey",
          |        "name": "Authorization",
          |        "in": "header",
          |        "x-amazon-apigateway-authtype": "custom",
          |        "x-amazon-apigateway-authorizer": {
          |            "type": "request",
          |            "authorizerUri": "arn:aws:apigateway:application_authorizer",
          |            "authorizerCredentials": "arn:aws:iam::account-id:foobar",
          |            "authorizerResultTtlInSeconds": "0",
          |            "identitySource": "method.request.header.Authorization"
          |        }
          |    },
          |    "user-authorizer": {
          |        "type": "apiKey",
          |        "name": "Authorization",
          |        "in": "header",
          |        "x-amazon-apigateway-authtype": "custom",
          |        "x-amazon-apigateway-authorizer": {
          |            "authorizerUri": "arn:aws:apigateway:user_authorizer",
          |            "authorizerCredentials": "arn:aws:iam::account-id:foobar",
          |            "authorizerResultTtlInSeconds": "0",
          |            "identitySource": "method.request.header.Authorization",
          |            "type": "request"
          |        }
          |    },
          |    "open-authorizer": {
          |        "type": "apiKey",
          |        "name": "Unused",
          |        "in": "header",
          |        "x-amazon-apigateway-authtype": "custom",
          |        "x-amazon-apigateway-authorizer": {
          |            "authorizerUri": "arn:aws:apigateway:open_authorizer",
          |            "authorizerCredentials": "arn:aws:iam::account-id:foobar",
          |            "authorizerResultTtlInSeconds": "0",
          |            "identitySource": "context.httpMethod, context.path",
          |            "type": "request"
          |        }
          |    }
          |}""".stripMargin

      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      swagger.getVendorExtensions should contain key "securityDefinitions"
      toJson(swagger.getVendorExtensions.get("securityDefinitions")) should matchJson(expectedJson)
    }

    "add amazon extensions for API gateway integrations" in new StandardSetup {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      swagger.getPaths.asScala foreach { path =>
        path._2.getOperations.asScala foreach { op =>
          val vendorExtensions = op.getVendorExtensions.asScala
          vendorExtensions.keys should contain("x-amazon-apigateway-integration")
          vendorExtensions("x-amazon-apigateway-integration") match {
            case ve: Map[String, Object] =>
              ve("uri") shouldEqual s"https://api-example-microservice.integration.tax.service.gov.uk${path._1}"
              ve("connectionId") shouldEqual environment("vpc_link_id")
              ve("httpMethod") shouldEqual "GET"
              ve("requestParameters") shouldEqual Map(
                "integration.request.header.x-application-id" -> "context.authorizer.applicationId",
                "integration.request.header.Authorization" -> "context.authorizer.authBearerToken",
                "integration.request.header.X-Client-Authorization-Token" -> "context.authorizer.clientAuthToken",
                "integration.request.header.X-Client-Id" -> "context.authorizer.clientId")
            case _ => throw new ClassCastException
          }
        }
      }
    }

    "add path parameters to the request parameters in the API gateway integration extension" in new StandardSetup {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJsonWithPathParameters())

       swagger.getPaths.asScala.head._2.getOperations.asScala.head.getVendorExtensions.asScala("x-amazon-apigateway-integration") match {
        case ve: Map[String, Object] =>
          ve("requestParameters") shouldEqual Map(
            "integration.request.header.x-application-id" -> "context.authorizer.applicationId",
            "integration.request.header.Authorization" -> "context.authorizer.authBearerToken",
            "integration.request.header.X-Client-Authorization-Token" -> "context.authorizer.clientAuthToken",
            "integration.request.header.X-Client-Id" -> "context.authorizer.clientId",
            "integration.request.path.givenName" -> "method.request.path.givenName")
        case _ => throw new ClassCastException
      }
    }

    "add the correct authorizers to each endpoint" in new StandardSetup {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      private val paths = swagger.getPaths.asScala

      toJson(paths("/world").getGet.getSecurity) should matchJson("""[{"open-authorizer":[]}]""")
      toJson(paths("/application").getGet.getSecurity) should matchJson("""[{"api-key":[]}, {"application-authorizer":[]}]""")
      toJson(paths("/user").getGet.getSecurity) should matchJson("""[{"api-key":[]}, {"user-authorizer":[]}]""")
    }

    "add amazon extension for API key source" in new StandardSetup {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      swagger.getVendorExtensions should contain key "x-amazon-apigateway-api-key-source"
      swagger.getVendorExtensions.get("x-amazon-apigateway-api-key-source") shouldEqual "AUTHORIZER"
    }

    "handle a host with an incorrect format" in new StandardSetup {
      val ex: Exception = intercept[Exception] {
        swaggerService.createSwagger(swaggerJson(host = "api-example-microservice"))
      }
      ex.getMessage shouldEqual "Invalid host format"
    }
  }
}
