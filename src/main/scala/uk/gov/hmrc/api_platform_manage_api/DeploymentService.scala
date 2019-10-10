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

import scala.collection.JavaConverters.mapAsJavaMapConverter
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.Op.REPLACE
import software.amazon.awssdk.services.apigateway.model.{CreateDeploymentRequest, PatchOperation, UpdateAuthorizerRequest, UpdateStageRequest}

class DeploymentService(apiGatewayClient: ApiGatewayClient) {

  def deployApi(restApiId: String, context: String, version: String): Unit = {
    apiGatewayClient.createDeployment(buildCreateDeploymentRequest(restApiId, context, version))
    apiGatewayClient.updateStage(buildUpdateStageRequest(restApiId, NoLogging))
  }

  private def buildCreateDeploymentRequest(restApiId: String, context: String, version: String): CreateDeploymentRequest = {
    CreateDeploymentRequest
      .builder()
      .restApiId(restApiId)
      .stageName("current")
      .variables(Map("context" -> context, "version" -> version).asJava)
      .build()
  }

  private def buildUpdateStageRequest(restApiId: String, loggingLevel: StageLoggingLevel): UpdateStageRequest = {
    UpdateStageRequest
      .builder()
      .restApiId(restApiId)
      .stageName("current")
      .patchOperations(loggingLevel.patchOperation)
      .build()
  }
}

case class StageLoggingLevel(level: String) {
  def patchOperation: PatchOperation = PatchOperation.builder().op(REPLACE).path("/*/*/logging/loglevel").value(level).build()
}

object InfoLogging extends StageLoggingLevel("INFO")
object WarnLogging extends StageLoggingLevel("WARN")
object NoLogging extends StageLoggingLevel("OFF")