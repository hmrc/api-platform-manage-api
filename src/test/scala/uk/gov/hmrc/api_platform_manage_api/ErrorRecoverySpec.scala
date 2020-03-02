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

import java.net.HttpURLConnection._

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.api_platform_manage_api.ErrorRecovery.TooManyRequests

class ErrorRecoverySpec extends WordSpecLike with Matchers with MockitoSugar {

  val errorMessage = "something went wrong"
  val errors: Map[Exception, Int] = Map(
    UnauthorizedException.builder().message(errorMessage).build() -> HTTP_UNAUTHORIZED,
    LimitExceededException.builder().message(errorMessage).build() -> TooManyRequests,
    BadRequestException.builder().message(errorMessage).build() -> HTTP_BAD_REQUEST,
    TooManyRequestsException.builder().message(errorMessage).build() -> TooManyRequests,
    ConflictException.builder().message(errorMessage).build() -> HTTP_CONFLICT,
    ServiceUnavailableException.builder().message(errorMessage).build() -> HTTP_UNAVAILABLE,
    NotFoundException.builder().message(errorMessage).build() -> HTTP_NOT_FOUND,
    new RuntimeException(errorMessage) -> HTTP_INTERNAL_ERROR
  )

  "error recovery" should {
    errors foreach { ex =>
      s"handle ${ex._1.getClass.getSimpleName}" in {
        val responseEvent: APIGatewayProxyResponseEvent = ErrorRecovery.recovery(mock[LambdaLogger])(ex._1)
        responseEvent should have('statusCode (ex._2), 'body (errorMessage))
      }
    }
  }
}
