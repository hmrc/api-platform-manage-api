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

import com.stephenn.scalatest.jsonassert.JsonMatchers
import io.swagger.models.Swagger
import org.scalatest._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConverters._

class SwaggerServiceSpec extends WordSpecLike with Matchers with JsonMatchers with JsonMapper {

  val officeIpAddress = "192.168.1.1/32"

  trait Setup {
//    def requestEvent(host: String = "api-example-microservice.protected.mdtp"): APIGatewayProxyRequestEvent = {
//      new APIGatewayProxyRequestEvent()
//        .withHttpMethod("POST")
//        .withRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext()
//          .withIdentity(new APIGatewayProxyRequestEvent.RequestIdentity()
//            .withSourceIp("127.0.0.1")))
//        .withBody(
//          s"""{"host": "$host", "paths": {"/world": {"get": {"responses": {"200": {"description": "OK"}},
//             |"x-auth-type": "Application User", "x-throttling-tier": "Unlimited",
//             |"x-scope": "read:state-pension-calculation"}}}, "info": {"title": "Test OpenAPI 2","version": "1.0"},
//             |"swagger": "2.0"}""".stripMargin
//        )
//    }

    def swaggerJson(host: String = "api-example-microservice.protected.mdtp"): String =
      s"""{"host": "$host", "paths": {"/world": {"get": {"responses": {"200": {"description": "OK"}},
          |"x-auth-type": "Application User", "x-throttling-tier": "Unlimited",
          |"x-scope": "read:state-pension-calculation"}}}, "info": {"title": "Test OpenAPI 2","version": "1.0"},
          |"swagger": "2.0"}""".stripMargin
  }

  trait StandardSetup extends Setup {
    val environment: Map[String, String] = Map(
      "domain" -> "integration.tax.service.gov.uk",
      "vpc_link_id" -> "gix6s7",
      "vpc_endpoint_id" -> "abc2d3",
      "office_ip_address" -> officeIpAddress
    )
    val swaggerService = new SwaggerService(environment)
  }

  trait SetupWithoutVpcEndpointId extends Setup {
    val environment: Map[String, String] = Map(
      "domain" -> "integration.tax.service.gov.uk",
      "vpc_link_id" -> "gix6s7",
      "office_ip_address" -> officeIpAddress
    )
    val swaggerService = new SwaggerService(environment)
  }

  trait SetupForRegionalEndpoints extends Setup {
    val environment: Map[String, String] = Map(
      "domain" -> "integration.tax.service.gov.uk",
      "vpc_link_id" -> "gix6s7",
      "endpoint_type" -> "REGIONAL",
      "office_ip_address" -> officeIpAddress
    )
    val swaggerService = new SwaggerService(environment)
  }

  "createSwagger" should {

    "add amazon extension for API gateway policy" in new StandardSetup {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      swagger.getVendorExtensions should contain key "x-amazon-apigateway-policy"
      val apiGatewayPolicy: ApiGatewayPolicy = swagger.getVendorExtensions.get("x-amazon-apigateway-policy").asInstanceOf[ApiGatewayPolicy]
      apiGatewayPolicy.statement should have length 1
      apiGatewayPolicy.statement.head.condition shouldBe a [VpceCondition]
      val condition: VpceCondition = apiGatewayPolicy.statement.head.condition.asInstanceOf[VpceCondition]
      condition.stringEquals.awsSourceVpce shouldEqual environment("vpc_endpoint_id")
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

    "add amazon extensions for API gateway integrations" in new StandardSetup {
      val swagger: Swagger = swaggerService.createSwagger(swaggerJson())

      swagger.getPaths.asScala foreach { path =>
        path._2.getOperations.asScala foreach { op =>
          val vendorExtensions = op.getVendorExtensions.asScala
          vendorExtensions.keys should contain("x-amazon-apigateway-integration")
          vendorExtensions("x-amazon-apigateway-integration") match {
            case ve: Map[String, Object] =>
              ve("uri") shouldEqual "https://api-example-microservice.integration.tax.service.gov.uk/world"
              ve("connectionId") shouldEqual environment("vpc_link_id")
              ve("httpMethod") shouldEqual "GET"
            case _ => throw new ClassCastException
          }
        }
      }
    }

    "handle a host with an incorrect format" in new StandardSetup {
      val ex: Exception = intercept[Exception] {
        swaggerService.createSwagger(swaggerJson(host = "api-example-microservice"))
      }
      ex.getMessage shouldEqual "Invalid host format"
    }
  }
}
