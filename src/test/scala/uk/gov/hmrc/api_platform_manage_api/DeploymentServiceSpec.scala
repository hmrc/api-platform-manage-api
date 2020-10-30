/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.Op.REPLACE
import software.amazon.awssdk.services.apigateway.model._

import scala.collection.JavaConverters._

class DeploymentServiceSpec extends WordSpecLike with Matchers with MockitoSugar {

  trait Setup {
    val context: String = "context"
    val version: String = "version"
    val accessLogConfiguration = AccessLogConfiguration("""{"foo": "bar"}""", "aws:arn:1234567890")
    val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
    val deploymentService = new DeploymentService(mockAPIGatewayClient)
  }

  "deployApi" should {
    "deploy the rest API" in new Setup {
      val importedRestApiId: String = UUID.randomUUID().toString
      val createDeploymentRequestCaptor: ArgumentCaptor[CreateDeploymentRequest] = ArgumentCaptor.forClass(classOf[CreateDeploymentRequest])
      when(mockAPIGatewayClient.createDeployment(createDeploymentRequestCaptor.capture())).thenReturn(CreateDeploymentResponse.builder().build())

      deploymentService.deployApi(importedRestApiId, context, version, NoCloudWatchLogging, accessLogConfiguration)

      val capturedRequest: CreateDeploymentRequest = createDeploymentRequestCaptor.getValue
      capturedRequest.restApiId shouldEqual importedRestApiId
      capturedRequest.stageName shouldEqual "current"
      val stageVars = capturedRequest.variables.asScala.toStream
      stageVars should contain("context" -> context)
      stageVars should contain("version" -> version)
    }

    "correctly handle UnauthorizedException thrown by AWS SDK when deploying API" in new Setup {
      val errorMessage = "You're an idiot"
      when(mockAPIGatewayClient.createDeployment(any[CreateDeploymentRequest])).thenThrow(UnauthorizedException.builder().message(errorMessage).build())

      val ex: Exception = intercept[Exception]{
        deploymentService.deployApi("123", context, version, NoCloudWatchLogging, accessLogConfiguration)
      }

      ex.getMessage shouldEqual errorMessage
    }

    "update the stage with extra settings" in new Setup {
      val importedRestApiId: String = UUID.randomUUID().toString
      when(mockAPIGatewayClient.createDeployment(any[CreateDeploymentRequest])).thenReturn(CreateDeploymentResponse.builder().build())
      val updateStageRequestCaptor: ArgumentCaptor[UpdateStageRequest] = ArgumentCaptor.forClass(classOf[UpdateStageRequest])
      when(mockAPIGatewayClient.updateStage(updateStageRequestCaptor.capture())).thenReturn(UpdateStageResponse.builder().build())

      deploymentService.deployApi(importedRestApiId, context, version, NoCloudWatchLogging, accessLogConfiguration)

      val capturedRequest: UpdateStageRequest = updateStageRequestCaptor.getValue
      capturedRequest.restApiId shouldEqual importedRestApiId
      capturedRequest.stageName shouldEqual "current"
      val operations = capturedRequest.patchOperations.asScala
      exactly(1, operations) should have('op (REPLACE), 'path ("/*/*/logging/loglevel"), 'value ("OFF"))
    }

    "correctly handle UnauthorizedException thrown by AWS SDK when updating stage with extra settings" in new Setup {
      val errorMessage = "You're an idiot"
      when(mockAPIGatewayClient.createDeployment(any[CreateDeploymentRequest])).thenReturn(CreateDeploymentResponse.builder().build())
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
