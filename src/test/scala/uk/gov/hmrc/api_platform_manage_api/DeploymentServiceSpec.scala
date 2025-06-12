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

import org.mockito.captor.ArgCaptor
import org.mockito.scalatest.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.Op.REPLACE
import software.amazon.awssdk.services.apigateway.model._

import scala.jdk.CollectionConverters._

class DeploymentServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {

  trait Setup {
    val context: String = "context"
    val version: String = "version"
    val accessLogConfiguration = AccessLogConfiguration("""{"foo": "bar"}""", "aws:arn:1234567890")
    val createDeploymentResponse = CreateDeploymentResponse.builder().build()
    val updateStageResponse = UpdateStageResponse.builder().build()
    val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
    val deploymentService = new DeploymentService(mockAPIGatewayClient)
  }

  "deployApi" should {
    "deploy the rest API" in new Setup {
      val importedRestApiId: String = UUID.randomUUID().toString
      when(mockAPIGatewayClient.createDeployment(*[CreateDeploymentRequest])).thenReturn(createDeploymentResponse)  
      when(mockAPIGatewayClient.updateStage(*[UpdateStageRequest])).thenReturn(updateStageResponse)

      deploymentService.deployApi(importedRestApiId, context, version, NoCloudWatchLogging, accessLogConfiguration)

      val createDeploymentRequestCaptor = ArgCaptor[CreateDeploymentRequest]
      verify(mockAPIGatewayClient).createDeployment(createDeploymentRequestCaptor.capture)
      val capturedRequest: CreateDeploymentRequest = createDeploymentRequestCaptor.value
      capturedRequest.restApiId shouldBe importedRestApiId
      capturedRequest.stageName shouldBe "current"
      val stageVars = capturedRequest.variables.asScala.to(LazyList)
      stageVars should contain("context" -> context)
      stageVars should contain("version" -> version)
    }

    "correctly handle UnauthorizedException thrown by AWS SDK when deploying API" in new Setup {
      val errorMessage = "You're an idiot"
      val exception = UnauthorizedException.builder().message(errorMessage).build()
      when(mockAPIGatewayClient.createDeployment(*[CreateDeploymentRequest])).thenThrow(exception)

      val ex: Exception = intercept[Exception]{
        deploymentService.deployApi("123", context, version, NoCloudWatchLogging, accessLogConfiguration)
      }

      ex.getMessage shouldEqual errorMessage
    }

    "update the stage with extra settings" in new Setup {
      val importedRestApiId: String = UUID.randomUUID().toString
      when(mockAPIGatewayClient.createDeployment(any[CreateDeploymentRequest])).thenReturn(createDeploymentResponse)
      when(mockAPIGatewayClient.updateStage(*[UpdateStageRequest])).thenReturn(updateStageResponse)

      deploymentService.deployApi(importedRestApiId, context, version, NoCloudWatchLogging, accessLogConfiguration)

      val updateStageRequestCaptor = ArgCaptor[UpdateStageRequest]
      verify(mockAPIGatewayClient).updateStage(updateStageRequestCaptor.capture)
      val capturedRequest: UpdateStageRequest = updateStageRequestCaptor.value
      capturedRequest.restApiId shouldEqual importedRestApiId
      capturedRequest.stageName shouldEqual "current"
      val operations = capturedRequest.patchOperations.asScala
      exactly(1, operations) should have(Symbol("op") (REPLACE), Symbol("path") ("/*/*/logging/loglevel"), Symbol("value") ("OFF"))
    }

    "correctly handle UnauthorizedException thrown by AWS SDK when updating stage with extra settings" in new Setup {
      val errorMessage = "You're an idiot"
      when(mockAPIGatewayClient.createDeployment(any[CreateDeploymentRequest])).thenReturn(createDeploymentResponse)
      when(mockAPIGatewayClient.updateStage(any[UpdateStageRequest])).thenThrow(UnauthorizedException.builder().message(errorMessage).build())

      val ex: Exception = intercept[Exception]{
        deploymentService.deployApi("123", context, version, NoCloudWatchLogging, accessLogConfiguration)
      }

      ex.getMessage shouldEqual errorMessage
    }
  }

  "StageLoggingLevel" should {
    "build correct PatchOperation object for NoLogging" in {
      NoCloudWatchLogging.patchOperation.op() shouldBe Op.REPLACE
      NoCloudWatchLogging.patchOperation.path() shouldBe "/*/*/logging/loglevel"
      NoCloudWatchLogging.patchOperation.value() shouldBe "OFF"
    }

    "build correct PatchOperation object for InfoLogging" in {
      CloudWatchInfoLogging.patchOperation.op() shouldBe Op.REPLACE
      CloudWatchInfoLogging.patchOperation.path() shouldBe "/*/*/logging/loglevel"
      CloudWatchInfoLogging.patchOperation.value() shouldBe "INFO"
    }

    "build correct PatchOperation object for WarnLogging" in {
      CloudWatchWarnLogging.patchOperation.op() shouldBe Op.REPLACE
      CloudWatchWarnLogging.patchOperation.path() shouldBe "/*/*/logging/loglevel"
      CloudWatchWarnLogging.patchOperation.value() shouldBe "WARN"
    }
  }

  "AccessLogConfiguration" should {
    "build correct PatchOperation objects" in {
      val logFormat = """{"foo": "bar"}"""
      val destinationArn = "aws:arn:1234567890"

      val accessLogConfiguration = AccessLogConfiguration(logFormat, destinationArn)

      accessLogConfiguration.formatPatchOperation.op() shouldBe Op.REPLACE
      accessLogConfiguration.formatPatchOperation.path() shouldBe "/accessLogSettings/format"
      accessLogConfiguration.formatPatchOperation.value() shouldBe logFormat

      accessLogConfiguration.logDestinationPatchOperation.op() shouldBe Op.REPLACE
      accessLogConfiguration.logDestinationPatchOperation.path() shouldBe "/accessLogSettings/destinationArn"
      accessLogConfiguration.logDestinationPatchOperation.value() shouldBe destinationArn
    }
  }
}
