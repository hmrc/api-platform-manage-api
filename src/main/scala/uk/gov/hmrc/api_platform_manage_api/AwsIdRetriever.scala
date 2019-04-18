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

import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.{GetRestApisRequest, GetRestApisResponse}

import scala.annotation.tailrec
import scala.collection.JavaConverters._

trait AwsIdRetriever {

  val apiGatewayClient: ApiGatewayClient
  val Limit: Int = 100

  def getAwsIdByApiName(apiName: String): Option[String] = {
    findAwsId(apiName, None)
  }

  @tailrec
  private def findAwsId(apiName: String, position: Option[String]): Option[String] = {
    val response: GetRestApisResponse = apiGatewayClient.getRestApis(buildRequest(position))

    response.items().asScala.find(restApi => restApi.name == apiName) match {
      case Some(restApi) => Some(restApi.id)
      case _ => if (response.items.size < Limit) None else findAwsId(apiName, Some(response.position))
    }
  }

  private def buildRequest(position: Option[String]): GetRestApisRequest = {
    position match {
      case Some(p) => GetRestApisRequest.builder().limit(Limit).position(p).build()
      case None => GetRestApisRequest.builder().limit(Limit).build()
    }
  }
}
