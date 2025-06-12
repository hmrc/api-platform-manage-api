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

import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.Op.REPLACE
import software.amazon.awssdk.services.apigateway.model.{CreateDeploymentRequest, PatchOperation, UpdateStageRequest}

import scala.jdk.CollectionConverters.MapHasAsJava

class DeploymentService(apiGatewayClient: ApiGatewayClient) {

  def deployApi(restApiId: String,
                context: String,
                version: String,
                cloudWatchLoggingLevel: CloudWatchLoggingLevel,
                accessLogConfiguration: AccessLogConfiguration): Unit = {
    apiGatewayClient.createDeployment(buildCreateDeploymentRequest(restApiId, context, version))
    apiGatewayClient.updateStage(buildUpdateStageRequest(restApiId, cloudWatchLoggingLevel, accessLogConfiguration))
  }

  private def buildCreateDeploymentRequest(restApiId: String, context: String, version: String): CreateDeploymentRequest = {
    CreateDeploymentRequest
      .builder()
      .restApiId(restApiId)
      .stageName("current")
      .variables(Map("context" -> context, "version" -> version).asJava)
      .build()
  }

  private def buildUpdateStageRequest(restApiId: String,
                                      cloudWatchLoggingLevel: CloudWatchLoggingLevel,
                                      accessLogConfiguration: AccessLogConfiguration): UpdateStageRequest = {
    UpdateStageRequest
      .builder()
      .restApiId(restApiId)
      .stageName("current")
      .patchOperations(
        cloudWatchLoggingLevel.patchOperation,
        accessLogConfiguration.formatPatchOperation,
        accessLogConfiguration.logDestinationPatchOperation)
      .build()
  }

}

case class CloudWatchLoggingLevel(level: String) {
  def patchOperation: PatchOperation = PatchOperation.builder().op(REPLACE).path("/*/*/logging/loglevel").value(level).build()
}

object CloudWatchInfoLogging extends CloudWatchLoggingLevel("INFO")
object CloudWatchWarnLogging extends CloudWatchLoggingLevel("WARN")
object NoCloudWatchLogging extends CloudWatchLoggingLevel("OFF")

case class AccessLogConfiguration(format: String, destinationArn: String) {
  def formatPatchOperation: PatchOperation =
    PatchOperation.builder().op(REPLACE).path("/accessLogSettings/format").value(format).build()

  def logDestinationPatchOperation: PatchOperation =
    PatchOperation.builder().op(REPLACE).path("/accessLogSettings/destinationArn").value(destinationArn).build()
}