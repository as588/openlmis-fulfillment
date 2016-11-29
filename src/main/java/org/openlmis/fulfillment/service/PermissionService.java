package org.openlmis.fulfillment.service;



import org.openlmis.fulfillment.domain.Order;
import org.openlmis.fulfillment.dto.ResultDto;
import org.openlmis.fulfillment.referencedata.model.RightDto;
import org.openlmis.fulfillment.referencedata.model.UserDto;
import org.openlmis.fulfillment.referencedata.service.InvalidOrderFacilityException;
import org.openlmis.fulfillment.referencedata.service.UserReferenceDataService;
import org.openlmis.fulfillment.util.AuthenticationHelper;
import org.openlmis.fulfillment.web.MissingPermissionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PermissionService {
  static final String REQUISITION_CONVERT_TO_ORDER = "REQUISITION_CONVERT_TO_ORDER";

  @Autowired
  private UserReferenceDataService userReferenceDataService;

  @Autowired
  private AuthenticationHelper authenticationHelper;

  @Autowired
  private OrderService orderService;

  /**
   * Checks if user has permission to create order.
   * @param order checked if can be created by user.
   * @throws MissingPermissionException when used do not have permission.
   * @throws InvalidOrderFacilityException when facility is not properly assigned to order.
   */
  public void canConvertToOrder(Order order) throws MissingPermissionException,
      InvalidOrderFacilityException {
    if (orderService.isFacilityValid(order, order.getCreatedById(),
        order.getSupplyingFacilityId())) {
      hasPermission(REQUISITION_CONVERT_TO_ORDER, order.getProgramId(),
          order.getSupplyingFacilityId());
    } else {
      throw new InvalidOrderFacilityException("Can not create order " + order.getId() + ". Must "
          + "have supplying facility");
    }
  }

  private void hasPermission(String rightName, UUID program, UUID facility)
      throws MissingPermissionException {
    UserDto user = authenticationHelper.getCurrentUser();
    RightDto right = authenticationHelper.getRight(rightName);
    ResultDto<Boolean> result = userReferenceDataService.hasRight(
        user.getId(), right.getId(), program, facility
    );

    if (null == result || !result.getResult().booleanValue()) {
      throw new MissingPermissionException(rightName);
    }
  }
}