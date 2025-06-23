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

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{DeserializationFeature, MapperFeature}
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule, JavaTypeable}
import com.fasterxml.jackson.databind.json

trait JsonMapper {
  val mapper = json.JsonMapper.builder()
    .addModule(DefaultScalaModule)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    .serializationInclusion(Include.NON_NULL)
    .build() :: ClassTagExtensions

  def fromJson[T](json: String)(implicit jt: JavaTypeable[T]): T = {
    mapper.readValue[T](json)
  }

  def toJson(value: Any): String = {
    mapper.writeValueAsString(value)
  }
}
