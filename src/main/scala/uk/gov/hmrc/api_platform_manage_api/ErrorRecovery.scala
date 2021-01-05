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

import java.net.HttpURLConnection._

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import software.amazon.awssdk.services.apigateway.model._

object ErrorRecovery {

  val TooManyRequests: Int = 429

  def recovery(logger: LambdaLogger): PartialFunction[Throwable, APIGatewayProxyResponseEvent] = {
    case e: UnauthorizedException => exceptionResponse(HTTP_UNAUTHORIZED, e, logger)
    case e: LimitExceededException => exceptionResponse(TooManyRequests, e, logger)
    case e: BadRequestException => exceptionResponse(HTTP_BAD_REQUEST, e, logger)
    case e: TooManyRequestsException => exceptionResponse(TooManyRequests, e, logger)
    case e: ConflictException => exceptionResponse(HTTP_CONFLICT, e, logger)
    case e: ServiceUnavailableException => exceptionResponse(HTTP_UNAVAILABLE, e, logger)
    case e: NotFoundException => exceptionResponse(HTTP_NOT_FOUND, e, logger)

    // Allow AwsServiceException, SdkClientException and ApiGatewayException to fall through and return 500
    case e: Throwable => exceptionResponse(HTTP_INTERNAL_ERROR, e, logger)
  }

  def exceptionResponse(statusCode: Int, exception: Throwable, logger: LambdaLogger): APIGatewayProxyResponseEvent = {
    logger.log(exception.getMessage)
    new APIGatewayProxyResponseEvent().withStatusCode(statusCode).withBody(exception.getMessage)
  }
}
