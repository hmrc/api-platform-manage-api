/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.api_platform_manage_api.utils

import java.net.HttpURLConnection._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import io.github.mkotsur.aws.handler.Lambda
import io.github.mkotsur.aws.handler.Lambda._

abstract class ProxiedRequestHandler extends Lambda[String, String] with JsonMapper {

  override final def handle(input: String, context: Context): Either[Nothing, String] = {
    Try(fromJson[APIGatewayProxyRequestEvent](input)) match {
      case Failure(e) => Right(toJson(new APIGatewayProxyResponseEvent().withStatusCode(HTTP_BAD_REQUEST).withBody(e.getMessage)))
      case Success(value) => {
        val logger: LambdaLogger = context.getLogger
        logger.log(s"Request body: ${value.getBody()}")
        Try(Right(toJson(handleInput(value, context)))) recover recovery get
      }
    }
  }

  private def recovery: PartialFunction[Throwable, Either[Nothing, String]] = {
    case e: Throwable => Right(toJson(new APIGatewayProxyResponseEvent().withStatusCode(HTTP_INTERNAL_ERROR).withBody(e.getMessage)))
  }

  protected def handleInput(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = handleInput(input)

  protected def handleInput(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent = ???

}
