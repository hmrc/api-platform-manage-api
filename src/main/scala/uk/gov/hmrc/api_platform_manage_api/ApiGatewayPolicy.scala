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

import com.fasterxml.jackson.annotation.JsonProperty

case class ApiGatewayPolicy(@JsonProperty("Version") version: String = "2012-10-17",
                            @JsonProperty("Statement") statement: List[Statement])
case class Statement(@JsonProperty("Effect") effect: String = "Deny",
                     @JsonProperty("Principal") principal: String = "*",
                     @JsonProperty("Action") action: String = "execute-api:Invoke",
                     @JsonProperty("Resource") resource: String = "*",
                     @JsonProperty("Condition") condition: Condition)
sealed trait Condition
case class IpAddressCondition(@JsonProperty("IpAddress") ipAddress: IpAddress) extends Condition
case class IpAddress(@JsonProperty("aws:SourceIp") awsSourceIp: String)
case class VpceCondition(@JsonProperty("StringNotEquals") stringNotEquals: StringEquals) extends Condition
case class StringEquals(@JsonProperty("aws:sourceVpce") awsSourceVpce: String)
