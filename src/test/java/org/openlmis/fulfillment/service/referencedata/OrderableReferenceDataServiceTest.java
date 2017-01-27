package org.openlmis.fulfillment.service.referencedata;

public class OrderableReferenceDataServiceTest
    extends BaseReferenceDataServiceTest<OrderableDto> {

  @Override
  protected BaseReferenceDataService<OrderableDto> getService() {
    return new OrderableReferenceDataService();
  }

  @Override
  OrderableDto generateInstance() {
    return new OrderableDto();
  }
}
