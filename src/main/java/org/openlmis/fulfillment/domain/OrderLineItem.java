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

package org.openlmis.fulfillment.domain;

import org.hibernate.annotations.Type;
import org.openlmis.fulfillment.service.referencedata.OrderableDto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "order_line_items")
@NoArgsConstructor
public class OrderLineItem extends BaseEntity {

  @ManyToOne(cascade = CascadeType.REFRESH)
  @JoinColumn(name = "orderId", nullable = false)
  @Getter
  @Setter
  private Order order;

  @Getter
  @Setter
  @Type(type = UUID_TYPE)
  private UUID orderableId;

  @Column(nullable = false)
  @Getter
  @Setter
  private Long orderedQuantity;

  @Column(nullable = false)
  @Getter
  @Setter
  private Long filledQuantity;

  @Column(nullable = false)
  @Getter
  @Setter
  private Long approvedQuantity;

  @Getter
  @Setter
  private Long packsToShip;

  /**
   * Create new instance of OrderLineItem based on given {@link OrderLineItem.Importer}
   * @param importer instance of {@link OrderLineItem.Importer}
   * @return new instance of OrderLineItem.
   */
  public static OrderLineItem newInstance(Importer importer) {
    OrderLineItem orderLineItem = new OrderLineItem();
    orderLineItem.setId(importer.getId());

    Optional.ofNullable(importer.getOrderable())
        .ifPresent(product -> orderLineItem.setOrderableId(product.getId()));

    orderLineItem.setOrderedQuantity(importer.getOrderedQuantity());
    orderLineItem.setFilledQuantity(importer.getFilledQuantity());
    orderLineItem.setApprovedQuantity(importer.getApprovedQuantity());
    orderLineItem.setPacksToShip(importer.getPacksToShip());

    return orderLineItem;
  }

  public interface Exporter {
    void setId(UUID id);

    void setOrderable(OrderableDto orderable);

    void setOrderedQuantity(Long orderedQuantity);

    void setFilledQuantity(Long filledQuantity);

    void setApprovedQuantity(Long approvedQuantity);

    void setPacksToShip(Long packsToShip);

  }

  public interface Importer {
    UUID getId();

    OrderableDto getOrderable();

    Long getOrderedQuantity();

    Long getFilledQuantity();

    Long getApprovedQuantity();
    
    Long getPacksToShip();

  }
}
