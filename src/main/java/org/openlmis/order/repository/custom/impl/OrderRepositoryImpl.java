package org.openlmis.order.repository.custom.impl;

import org.openlmis.order.domain.Order;
import org.openlmis.order.repository.custom.OrderRepositoryCustom;

import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

public class OrderRepositoryImpl implements OrderRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  /**
   * Method returns all Orders with matched parameters.
   *
   * @param supplyingFacility  supplyingFacility of searched Orders.
   * @param requestingFacility requestingFacility of searched Orders.
   * @param program            program of searched Orders.
   * @return List of Orders with matched parameters.
   */
  public List<Order> searchOrders(UUID supplyingFacility, UUID requestingFacility,
                                  UUID program) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Order> query = builder.createQuery(Order.class);
    Root<Order> root = query.from(Order.class);
    Predicate predicate = builder.conjunction();
    if (supplyingFacility != null) {
      predicate = builder.and(
          predicate,
          builder.equal(
              root.get("supplyingFacilityId"), supplyingFacility));
    }
    if (requestingFacility != null) {
      predicate = builder.and(
          predicate,
          builder.equal(
              root.get("requestingFacilityId"), requestingFacility));
    }
    if (program != null) {
      predicate = builder.and(predicate,
          builder.equal(
              root.get("programId"), program));
    }
    query.where(predicate);
    return entityManager.createQuery(query).getResultList();
  }

}
