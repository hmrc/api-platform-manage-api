/*
 * Copyright 2019 HM Revenue & Customs
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
import org.scalatest._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConverters._

class SwaggerServiceSpec extends WordSpecLike with Matchers with JsonMatchers with JsonMapper {

  val officeIpAddress = "192.168.1.1/32"
  val vpcEndpointId: String = UUID.randomUUID().toString

  trait Setup {
    val environment: Map[String, String] = Map(
      "domain" -> "integration.tax.service.gov.uk",
      "vpc_link_id" -> "gix6s7",
      "office_ip_address" -> officeIpAddress,
      "authorizer_uri" -> "arn:aws:apigateway:authorizer",
      "authorizer_credentials" -> "arn:aws:iam::account-id:foobar"
    )

    def swaggerJson(host: String = "api-example-microservice.protected.mdtp"): String =
      s"""{"host": "$host", "paths": {
          |"/application": {"get": {"responses": {"200": {"description": "OK"}},
          |"x-auth-type": "Application & Application User", "x-throttling-tier": "Unlimited"}},
          |"/world": {"get": {"responses": {"200": {"description": "OK"}},
          |"x-auth-type": "None", "x-throttling-tier": "Unlimited"}}},
          |"info": {"title": "Test OpenAPI 2","version": "1.0"}, "swagger": "2.0"}""".stripMargin
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
      apiGatewayPolicy.statement.head.condition shouldBe a [VpceCondition]
      val condition: VpceCondition = apiGatewayPolicy.statement.head.condition.asInstanceOf[VpceCondition]
      condition.stringEquals.awsSourceVpce shouldEqual vpcEndpointId
    }

    "add IP address condition if endpoint type is regional" in new SetupForRegionalEndpoints {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      swagger.getVendorExtensions should contain key "x-amazon-apigateway-policy"
      val apiGatewayPolicy: ApiGatewayPolicy = swagger.getVendorExtensions.get("x-amazon-apigateway-policy").asInstanceOf[ApiGatewayPolicy]
      apiGatewayPolicy.statement should have length 1
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
          |  "MISSING_AUTHENTICATION_TOKEN":{
          |    "statusCode":"404",
          |    "responseTemplates":{
          |      "application/vnd.hmrc.1.0+json":"{\"code\": \"MATCHING_RESOURCE_NOT_FOUND\", \"message\": \"A resource with the name in the request can not be found in the API\"}",
          |      "application/vnd.hmrc.1.0+xml":"<errorResponse><code>MATCHING_RESOURCE_NOT_FOUND</code><message>A resource with the name in the request can not be found in the API</message></errorResponse>"
          |    }
          |  },
          |  "THROTTLED":{
          |    "statusCode":"429",
          |    "responseTemplates":{
          |      "application/vnd.hmrc.1.0+json":"{\"code\": \"MESSAGE_THROTTLED_OUT\", \"message\", \"The request for the API is throttled as you have exceeded your quota.\"}",
          |      "application/vnd.hmrc.1.0+xml":"<errorResponse><code>MESSAGE_THROTTLED_OUT</code><message>The request for the API is throttled as you have exceeded your quota.</message></errorResponse>"
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
          |    "application-authorizer": {
          |        "type": "apiKey",
          |        "name": "Authorization",
          |        "in": "header",
          |        "x-amazon-apigateway-authtype": "custom",
          |        "x-amazon-apigateway-authorizer": {
          |            "type": "token",
          |            "authorizerUri": "arn:aws:apigateway:authorizer",
          |            "authorizerCredentials": "arn:aws:iam::account-id:foobar",
          |            "authorizerResultTtlInSeconds": "300"
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
            case _ => throw new ClassCastException
          }
        }
      }
    }

    "add the application authorizer to the application restricted endpoints only" in new StandardSetup {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      val paths = swagger.getPaths.asScala
      paths("/world").getGet.getSecurity shouldBe null
      toJson(paths("/application").getGet.getSecurity) should matchJson("""[ {"application-authorizer" : [ ]} ]""")
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
