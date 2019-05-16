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

import com.amazonaws.services.lambda.runtime.LambdaLogger
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.{GetApiKeysRequest, GetRestApisRequest, GetRestApisResponse, GetUsagePlansRequest}

import scala.annotation.tailrec
import scala.collection.JavaConverters._

trait AwsIdRetriever {

  val apiGatewayClient: ApiGatewayClient
  val Limit: Int = 100

  def getAwsRestApiIdByApiName(apiName: String, logger: LambdaLogger): Option[String] = {
    findAwsRestApiId(apiName, None, logger)
  }

  @tailrec
  private def findAwsRestApiId(apiName: String, position: Option[String], logger: LambdaLogger): Option[String] = {
    def buildRequest(position: Option[String]): GetRestApisRequest = {
      position match {
        case Some(p) => GetRestApisRequest.builder().limit(Limit).position(p).build()
        case None => GetRestApisRequest.builder().limit(Limit).build()
      }
    }

    logger.log(s"Getting APIs with position $position")
    val response: GetRestApisResponse = apiGatewayClient.getRestApis(buildRequest(position))
    logger.log(s"Got ${response.items().size()} APIs")

    response.items().asScala.find(restApi => restApi.name == apiName) match {
      case Some(restApi) => Some(restApi.id)
      case _ => if (response.items.size < Limit) None else findAwsRestApiId(apiName, Some(response.position), logger)
    }
  }

  def getAwsUsagePlanIdByApplicationName(applicationName: String): Option[String] = {
    findAwsUsagePlanId(applicationName, None)
  }

  @tailrec
  private def findAwsUsagePlanId(applicationName: String, position: Option[String]): Option[String] = {
    def buildUsagePlanRequest(position: Option[String]): GetUsagePlansRequest = {
      position match {
        case Some(p) => GetUsagePlansRequest.builder().limit(Limit).position(p).build()
        case None => GetUsagePlansRequest.builder().limit(Limit).build()
      }
    }

    val response = apiGatewayClient.getUsagePlans(buildUsagePlanRequest(position))

    response.items().asScala.find(usagePlan => usagePlan.name == applicationName) match {
      case Some(usagePlan) => Some(usagePlan.id)
      case _ => if (response.items.size < Limit) None else findAwsUsagePlanId(applicationName, Some(response.position))
    }
  }

  def getAwsApiKeyIdByApplicationName(applicationName: String): Option[String] = {
    findAwsApiKeyId(applicationName, None)
  }

  @tailrec
  private def findAwsApiKeyId(applicationName: String, position: Option[String]): Option[String] = {
    def buildGetApiKeysRequest(position: Option[String]): GetApiKeysRequest = {
      position match {
        case Some(p) => GetApiKeysRequest.builder().limit(Limit).position(p).build()
        case None => GetApiKeysRequest.builder().limit(Limit).build()
      }
    }

    val response = apiGatewayClient.getApiKeys(buildGetApiKeysRequest(position))

    response.items().asScala.find(apiKey => apiKey.name == applicationName) match {
      case Some(apiKey) => Some(apiKey.id)
      case _ => if (response.items.size < Limit) None else findAwsApiKeyId(applicationName, Some(response.position))
    }
  }
}
