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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.{GetRestApisRequest, GetRestApisResponse, GetUsagePlansRequest, GetUsagePlansResponse, RestApi, UsagePlan}

import scala.collection.JavaConverters._

class AwsIdRetrieverSpec extends WordSpecLike with Matchers with MockitoSugar {

  trait Setup extends AwsIdRetriever {
    val mockApiGatewayClient = mock[ApiGatewayClient]

    override val apiGatewayClient = mockApiGatewayClient
    override val Limit = 2
  }

  "getAwsRestApiIdByApiName" should {
    "find id on first page of results" in new Setup {
      val apiId = UUID.randomUUID().toString
      val apiName = "foo--1.0"

      when(mockApiGatewayClient.getRestApis(any[GetRestApisRequest])).thenReturn(buildMatchingRestApisResponse(apiId, apiName))

      val returnedId = getAwsRestApiIdByApiName(apiName)

      returnedId shouldEqual Some(apiId)
    }

    "find id when results are paged" in new Setup {
      val apiId = UUID.randomUUID().toString
      val apiName = "foo--1.0"

      when(mockApiGatewayClient.getRestApis(any[GetRestApisRequest]))
        .thenReturn(
          buildNonMatchingRestApisResponse(Limit),
          buildMatchingRestApisResponse(apiId, apiName))

      val returnedId = getAwsRestApiIdByApiName(apiName)

      returnedId shouldEqual Some(apiId)
      verify(mockApiGatewayClient, times(2)).getRestApis(any[GetRestApisRequest])
    }
    
    "return None if api name is not found" in new Setup {
      val apiName = "foo--1.0"

      when(mockApiGatewayClient.getRestApis(any[GetRestApisRequest])).thenReturn(GetRestApisResponse.builder().build())

      val returnedId = getAwsRestApiIdByApiName(apiName)

      returnedId shouldEqual None
    }
  }

  def buildMatchingRestApisResponse(matchingId: String, matchingName: String): GetRestApisResponse = {
    GetRestApisResponse.builder()
      .items(RestApi.builder().id(matchingId).name(matchingName).build())
      .build()
  }

  def buildNonMatchingRestApisResponse(count: Int): GetRestApisResponse = {
    val items = (1 to count).map(c => RestApi.builder().id(s"$c").name(s"Item $c").build())

    GetRestApisResponse.builder()
      .items(items.asJava)
      .build()
  }

  "getAwsUsagePlanIdByApiName" should {
    "find id on first page of results" in new Setup {
      val usagePlanId = UUID.randomUUID().toString
      val applicationName = "foo"

      when(mockApiGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(buildMatchingUsagePlansResponse(usagePlanId, applicationName))

      val returnedId = getAwsUsagePlanIdByApplicationName(applicationName)

      returnedId shouldEqual Some(usagePlanId)
    }

    "find id when results are paged" in new Setup {
      val usagePlanId = UUID.randomUUID().toString
      val applicationName = "foo"

      when(mockApiGatewayClient.getUsagePlans(any[GetUsagePlansRequest]))
        .thenReturn(
          buildNonMatchingUsagePlansResponse(Limit),
          buildMatchingUsagePlansResponse(usagePlanId, applicationName))

      val returnedId = getAwsUsagePlanIdByApplicationName(applicationName)

      returnedId shouldEqual Some(usagePlanId)
      verify(mockApiGatewayClient, times(2)).getUsagePlans(any[GetUsagePlansRequest])
    }

    "return None if application name is not found" in new Setup {
      val applicationName = "foo"

      when(mockApiGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(GetUsagePlansResponse.builder().build())

      val returnedId = getAwsUsagePlanIdByApplicationName(applicationName)

      returnedId shouldEqual None
    }
  }

  def buildMatchingUsagePlansResponse(matchingId: String, matchingName: String): GetUsagePlansResponse = {
    GetUsagePlansResponse.builder()
      .items(UsagePlan.builder().id(matchingId).name(matchingName).build())
      .build()
  }

  def buildNonMatchingUsagePlansResponse(count: Int): GetUsagePlansResponse = {
    val items = (1 to count).map(c => UsagePlan.builder().id(s"$c").name(s"Item $c").build())

    GetUsagePlansResponse.builder()
      .items(items.asJava)
      .build()
  }
}
