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

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import io.github.mkotsur.aws.handler.Lambda
import io.github.mkotsur.aws.handler.Lambda._

abstract class SqsHandler extends Lambda[String, Unit] with JsonMapper {

  override final def handle(input: String, context: Context): Either[Nothing, Unit] = {
    val logger: LambdaLogger = context.getLogger
    logger.log(s"Input: $input")

    Try(fromJson[SQSEvent](input)) match {
      case Failure(e) => logger.log(s"Failed to deserialise SQS event: ${e.getMessage}"); Left(throw e)
      case Success(value) => Try(Right(handleInput(value, context))) recover recovery(logger) get
    }
  }

  private def recovery(logger: LambdaLogger): PartialFunction[Throwable, Either[Nothing, Unit]] = {
    case e: Throwable => logger.log(s"Failed to handle SQS event: ${e.getMessage}"); Left(throw e)
  }

  protected def handleInput(input: SQSEvent, context: Context): Unit = handleInput(input)

  protected def handleInput(input: SQSEvent): Unit = ???

}
