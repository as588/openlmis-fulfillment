/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org. 
 */

package org.openlmis.fulfillment.service;

import static org.openlmis.fulfillment.i18n.MessageKeys.ERROR_ORDER_INVALID_STATUS;
import static org.springframework.util.CollectionUtils.isEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang3.StringUtils;
import org.openlmis.fulfillment.domain.OrderStatus;
import org.openlmis.fulfillment.web.ValidationException;
import org.springframework.data.domain.Pageable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSearchParams {
  private static final ToEnum TO_ENUM = new ToEnum();

  UUID supplyingFacility;
  UUID requestingFacility;
  UUID program;
  UUID processingPeriod;
  Set<String> status;
  Pageable pageable;

  /**
   * Tries to convert the string representation of each status in the <strong>status</strong> field
   * to an instance of {@link OrderStatus}.
   *
   * @return a set with instances of {@link OrderStatus} or {@code null} if the field is empty.
   * @throws ValidationException if the field contains a value that cannot be converted to enum.
   */
  @JsonIgnore
  Set<OrderStatus> getStatusAsEnum() {
    return !isEmpty(status)
        ? status.stream().filter(StringUtils::isNotBlank).map(TO_ENUM).collect(Collectors.toSet())
        : null;
  }

  private static final class ToEnum implements Function<String, OrderStatus> {

    @Override
    public OrderStatus apply(String status) {
      OrderStatus orderStatus = OrderStatus.fromString(status);

      if (null == orderStatus) {
        throw new ValidationException(ERROR_ORDER_INVALID_STATUS, status);
      }

      return orderStatus;
    }
  }
}
