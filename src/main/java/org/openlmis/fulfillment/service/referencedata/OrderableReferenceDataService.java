package org.openlmis.fulfillment.service.referencedata;

import org.springframework.stereotype.Service;

@Service
public class OrderableReferenceDataService
    extends BaseReferenceDataService<OrderableDto> {

  @Override
  protected String getUrl() {
    return "/api/orderables/";
  }

  @Override
  protected Class<OrderableDto> getResultClass() {
    return OrderableDto.class;
  }

  @Override
  protected Class<OrderableDto[]> getArrayResultClass() {
    return OrderableDto[].class;
  }
}
