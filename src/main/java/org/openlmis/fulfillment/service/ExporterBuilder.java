package org.openlmis.fulfillment.service;

import org.openlmis.fulfillment.domain.FtpTransferProperties;
import org.openlmis.fulfillment.domain.LocalTransferProperties;
import org.openlmis.fulfillment.domain.Order;
import org.openlmis.fulfillment.domain.OrderLineItem;
import org.openlmis.fulfillment.service.referencedata.BaseReferenceDataService;
import org.openlmis.fulfillment.service.referencedata.FacilityReferenceDataService;
import org.openlmis.fulfillment.service.referencedata.OrderableReferenceDataService;
import org.openlmis.fulfillment.service.referencedata.PeriodReferenceDataService;
import org.openlmis.fulfillment.service.referencedata.ProgramReferenceDataService;
import org.openlmis.fulfillment.service.referencedata.UserReferenceDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ExporterBuilder {

  @Autowired
  private FacilityReferenceDataService facilities;

  @Autowired
  private ProgramReferenceDataService programs;

  @Autowired
  private UserReferenceDataService users;

  @Autowired
  private PeriodReferenceDataService periods;

  @Autowired
  private OrderableReferenceDataService products;

  /**
   * Copy data from the given order to the instance that implemenet {@link Order.Exporter}
   * interface.
   */
  public void export(Order order, Order.Exporter exporter) {
    exporter.setId(order.getId());
    exporter.setExternalId(order.getExternalId());
    exporter.setEmergency(order.getEmergency());
    exporter.setFacility(getIfPresent(facilities, order.getFacilityId()));
    exporter.setProgram(getIfPresent(programs, order.getProgramId()));
    exporter.setProcessingPeriod(getIfPresent(periods, order.getProcessingPeriodId()));
    exporter.setRequestingFacility(getIfPresent(facilities, order.getRequestingFacilityId()));
    exporter.setReceivingFacility(getIfPresent(facilities, order.getReceivingFacilityId()));
    exporter.setSupplyingFacility(getIfPresent(facilities, order.getSupplyingFacilityId()));
    exporter.setOrderCode(order.getOrderCode());
    exporter.setStatus(order.getStatus());
    exporter.setQuotedCost(order.getQuotedCost());
    exporter.setCreatedBy(getIfPresent(users, order.getCreatedById()));
    exporter.setCreatedDate(order.getCreatedDate());
  }

  /**
   * Copy data from the given order line item to the instance that implemenet
   * {@link OrderLineItem.Exporter} interface.
   */
  public void export(OrderLineItem item, OrderLineItem.Exporter exporter) {
    exporter.setId(item.getId());
    exporter.setApprovedQuantity(item.getApprovedQuantity());
    exporter.setOrderable(getIfPresent(products, item.getOrderableId()));
    exporter.setFilledQuantity(item.getFilledQuantity());
    exporter.setOrderedQuantity(item.getOrderedQuantity());
    exporter.setPacksToShip(item.getPacksToShip());
  }

  /**
   * Copy data from the given local transfer properties to the instance that implemenet
   * {@link LocalTransferProperties.Exporter} interface.
   */
  public void export(LocalTransferProperties properties,
                     LocalTransferProperties.Exporter exporter) {
    exporter.setId(properties.getId());
    exporter.setFacility(getIfPresent(facilities, properties.getFacilityId()));
    exporter.setPath(properties.getPath());
  }

  /**
   * Copy data from the given ftp transfer properties to the instance that implemenet
   * {@link FtpTransferProperties.Exporter} interface.
   */
  public void export(FtpTransferProperties properties,
                     FtpTransferProperties.Exporter exporter) {
    exporter.setId(properties.getId());
    exporter.setFacility(getIfPresent(facilities, properties.getFacilityId()));
    exporter.setProtocol(properties.getProtocol().name());
    exporter.setUsername(properties.getUsername());
    exporter.setServerHost(properties.getServerHost());
    exporter.setServerPort(properties.getServerPort());
    exporter.setRemoteDirectory(properties.getRemoteDirectory());
    exporter.setLocalDirectory(properties.getLocalDirectory());
    exporter.setPassiveMode(properties.getPassiveMode());
  }

  private <T> T getIfPresent(BaseReferenceDataService<T> service, UUID id) {
    return Optional.ofNullable(id).isPresent() ? service.findOne(id) : null;
  }

}
