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

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import org.mockito.MockitoSugar

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SqsHandlerSpec extends AnyWordSpec with Matchers with MockitoSugar with JsonMapper with EitherValues {

  trait Setup {
    val mockContext: Context = mock[Context]
    when(mockContext.getLogger).thenReturn(mock[LambdaLogger])

    val expectedStatusCode: Int = HTTP_OK
    val expectedResponseBody = """{"foo": "bar"}"""
    val sqsRequestHandler: SqsHandler = new SqsHandler {
      override protected def handleInput(input: SQSEvent): Unit = {
        ()
      }
    }

    val expectedErrorMessage = "something went wrong"
    val failingSqsHandler: SqsHandler = new SqsHandler {
      override protected def handleInput(input: SQSEvent): Unit = {
        throw new RuntimeException(expectedErrorMessage)
      }
    }

    val validInput: String = raw"""{
                                |    "httpMethod": "GET",
                                |    "body": "{\"host\": \"localhost\"}"
                                |}""".stripMargin

    val invalidInput: String = ""
  }

  "SQS handler" should {
    "return successful response from the handleInput method" in new Setup {
      val result: Either[Nothing, Unit] = sqsRequestHandler.handle(validInput, mockContext)

      result.isRight shouldEqual true
    }

    "throw exception from the handleInput method if input is invalid" in new Setup {
      val exception: MismatchedInputException = intercept[MismatchedInputException] {
        sqsRequestHandler.handle(invalidInput, mockContext)
      }
      exception.getMessage should include ("No content to map due to end-of-input")
    }

    "throw exception from the handleInput method if something goes wrong" in new Setup {
      val exception: RuntimeException = intercept[RuntimeException] {
        failingSqsHandler.handle(validInput, mockContext)
      }
      exception.getMessage shouldEqual "something went wrong"
    }
  }
}
