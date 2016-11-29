package org.openlmis.fulfillment.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.openlmis.fulfillment.domain.Order;
import org.openlmis.fulfillment.domain.OrderLineItem;
import org.openlmis.fulfillment.domain.OrderStatus;
import org.openlmis.fulfillment.repository.OrderRepository;
import org.openlmis.fulfillment.service.OrderFileStorage;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import guru.nidi.ramltester.junit.RamlMatchers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SuppressWarnings({"PMD.TooManyMethods", "PMD.UnusedPrivateField"})
public class OrderControllerIntegrationTest extends BaseWebIntegrationTest {

  private static final String RESOURCE_URL = "/api/orders";
  private static final String SEARCH_URL = RESOURCE_URL + "/search";
  private static final String ID_URL = RESOURCE_URL + "/{id}";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String REQUESTING_FACILITY = "requestingFacility";
  private static final String SUPPLYING_FACILITY = "supplyingFacility";
  private static final String PROGRAM = "program";
  private static final UUID ID = UUID.fromString("1752b457-0a4b-4de0-bf94-5a6a8002427e");
  private static final String NUMBER = "10.90";

  private UUID facility = UUID.randomUUID();
  private UUID facility1 = UUID.randomUUID();
  private UUID facility2 = UUID.randomUUID();
  private UUID program1 = UUID.randomUUID();
  private UUID program2 = UUID.randomUUID();
  private UUID period1 = UUID.randomUUID();
  private UUID period2 = UUID.randomUUID();
  private UUID product1 = UUID.randomUUID();
  private UUID product2 = UUID.randomUUID();

  @MockBean
  private OrderRepository orderRepository;

  @MockBean
  private OrderFileStorage orderStorage;

  private Order firstOrder = new Order();
  private Order secondOrder = new Order();
  private Order thirdOrder = new Order();

  @Before
  public void setUp() {
    this.setUpBootstrapData();

    firstOrder = addOrder(UUID.randomUUID(), facility, period1, "orderCode", UUID.randomUUID(),
        INITIAL_USER_ID, facility, facility, facility, OrderStatus.ORDERED,
        new BigDecimal("1" + ".29"), UUID.randomUUID());

    secondOrder = addOrder(UUID.randomUUID(), facility2, period1, "O2", program1, INITIAL_USER_ID,
        facility2, facility2, facility1, OrderStatus.RECEIVED, new BigDecimal(100),
        UUID.randomUUID());

    thirdOrder = addOrder(UUID.randomUUID(), facility2, period2, "O3", program2, INITIAL_USER_ID,
        facility2, facility2, facility1, OrderStatus.RECEIVED, new BigDecimal(200),
        UUID.randomUUID());

    addOrderLineItem(secondOrder, product1, 35L, 50L);

    addOrderLineItem(secondOrder, product2, 10L, 15L);

    addOrderLineItem(thirdOrder, product1, 50L, 50L);

    addOrderLineItem(thirdOrder, product2, 5L, 10L);

    OrderLineItem orderLineItem = addOrderLineItem(firstOrder, product1, 35L, 50L);

    List<OrderLineItem> orderLineItems = new ArrayList<>();
    orderLineItems.add(orderLineItem);
    firstOrder.setOrderLineItems(orderLineItems);
    firstOrder.setExternalId(secondOrder.getExternalId());

    given(orderRepository.findAll()).willReturn(
        Lists.newArrayList(firstOrder, secondOrder, thirdOrder)
    );

    given(orderRepository.save(any(Order.class)))
        .willAnswer(new SaveAnswer<Order>() {

          @Override
          void extraSteps(Order obj) {
            obj.setCreatedDate(LocalDateTime.now());
          }

        });
  }

  private Order addOrder(UUID requisition, UUID facility, UUID processingPeriod,
                         String orderCode, UUID program, UUID user,
                         UUID requestingFacility, UUID receivingFacility,
                         UUID supplyingFacility, OrderStatus orderStatus, BigDecimal cost,
                         UUID supervisoryNodeId) {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setExternalId(requisition);
    order.setEmergency(false);
    order.setFacilityId(facility);
    order.setProcessingPeriodId(processingPeriod);
    order.setOrderCode(orderCode);
    order.setQuotedCost(cost);
    order.setStatus(orderStatus);
    order.setProgramId(program);
    order.setCreatedDate(LocalDateTime.now());
    order.setCreatedById(user);
    order.setRequestingFacilityId(requestingFacility);
    order.setReceivingFacilityId(receivingFacility);
    order.setSupplyingFacilityId(supplyingFacility);
    order.setOrderLineItems(new ArrayList<>());
    order.setSupervisoryNodeId(supervisoryNodeId);

    given(orderRepository.findOne(order.getId())).willReturn(order);
    given(orderRepository.exists(order.getId())).willReturn(true);

    return order;
  }

  private OrderLineItem addOrderLineItem(Order order, UUID product, Long filledQuantity,
                                         Long orderedQuantity) {
    OrderLineItem orderLineItem = new OrderLineItem();
    orderLineItem.setId(UUID.randomUUID());
    orderLineItem.setOrder(order);
    orderLineItem.setOrderableProductId(product);
    orderLineItem.setOrderedQuantity(orderedQuantity);
    orderLineItem.setFilledQuantity(filledQuantity);
    orderLineItem.setApprovedQuantity(3L);

    order.getOrderLineItems().add(orderLineItem);

    return orderLineItem;
  }

  @Test
  public void shouldNotFinalizeIfWrongOrderStatus() {
    firstOrder.setStatus(OrderStatus.SHIPPED);

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .pathParam("id", firstOrder.getId().toString())
        .contentType("application/json")
        .when()
        .put("/api/orders/{id}/finalize")
        .then()
        .statusCode(400);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.responseChecks());
  }

  @Test
  public void shouldPrintOrderAsCsv() {
    String csvContent = restAssured.given()
        .queryParam("format", "csv")
        .queryParam(ACCESS_TOKEN, getToken())
        .pathParam("id", secondOrder.getId())
        .when()
        .get("/api/orders/{id}/print")
        .then()
        .statusCode(200)
        .extract().body().asString();

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertTrue(csvContent.startsWith("productName,filledQuantity,orderedQuantity"));
  }

  @Test
  public void shouldPrintOrderAsPdf() {
    restAssured.given()
        .queryParam("format", "pdf")
        .queryParam(ACCESS_TOKEN, getToken())
        .pathParam("id", thirdOrder.getId().toString())
        .when()
        .get("/api/orders/{id}/print")
        .then()
        .statusCode(200);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldFindBySupplyingFacility() {
    given(orderRepository.searchOrders(firstOrder.getSupplyingFacilityId(), null, null))
        .willReturn(Lists.newArrayList(firstOrder));

    Order[] response = restAssured.given()
        .queryParam(SUPPLYING_FACILITY, firstOrder.getSupplyingFacilityId())
        .queryParam(ACCESS_TOKEN, getToken())
        .when()
        .get(SEARCH_URL)
        .then()
        .statusCode(200)
        .extract().as(Order[].class);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertEquals(1, response.length);
    for (Order order : response) {
      assertEquals(
          order.getSupplyingFacilityId(),
          firstOrder.getSupplyingFacilityId());
    }
  }

  @Test
  public void shouldFindBySupplyingFacilityAndRequestingFacility() {
    given(orderRepository.searchOrders(
        firstOrder.getSupplyingFacilityId(), firstOrder.getRequestingFacilityId(), null
    )).willReturn(Lists.newArrayList(firstOrder));

    Order[] response = restAssured.given()
        .queryParam(SUPPLYING_FACILITY, firstOrder.getSupplyingFacilityId())
        .queryParam(REQUESTING_FACILITY, firstOrder.getRequestingFacilityId())
        .queryParam(ACCESS_TOKEN, getToken())
        .when()
        .get(SEARCH_URL)
        .then()
        .statusCode(200)
        .extract().as(Order[].class);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertEquals(1, response.length);
    for (Order order : response) {
      assertEquals(
          order.getSupplyingFacilityId(),
          firstOrder.getSupplyingFacilityId());
      assertEquals(
          order.getRequestingFacilityId(),
          firstOrder.getRequestingFacilityId());
    }
  }

  @Test
  public void shouldFindBySupplyingFacilityAndRequestingFacilityAndProgram() {
    given(orderRepository.searchOrders(
        firstOrder.getSupplyingFacilityId(), firstOrder.getRequestingFacilityId(),
        firstOrder.getProgramId()
    )).willReturn(Lists.newArrayList(firstOrder));

    Order[] response = restAssured.given()
        .queryParam(SUPPLYING_FACILITY, firstOrder.getSupplyingFacilityId())
        .queryParam(REQUESTING_FACILITY, firstOrder.getRequestingFacilityId())
        .queryParam(PROGRAM, firstOrder.getProgramId())
        .queryParam(ACCESS_TOKEN, getToken())
        .when()
        .get(SEARCH_URL)
        .then()
        .statusCode(200)
        .extract().as(Order[].class);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertEquals(1, response.length);
    for (Order order : response) {
      assertEquals(
          order.getSupplyingFacilityId(),
          firstOrder.getSupplyingFacilityId());
      assertEquals(
          order.getRequestingFacilityId(),
          firstOrder.getRequestingFacilityId());
      assertEquals(
          order.getProgramId(),
          firstOrder.getProgramId());
    }
  }

  @Test
  public void shouldDeleteOrder() {
    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", firstOrder.getId())
        .when()
        .delete(ID_URL)
        .then()
        .statusCode(204);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldNotDeleteNonexistentOrder() {
    given(orderRepository.findOne(firstOrder.getId())).willReturn(null);

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", firstOrder.getId())
        .when()
        .delete(ID_URL)
        .then()
        .statusCode(404);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldCreateOrder() {
    firstOrder.getOrderLineItems().clear();
    firstOrder.setSupplyingFacilityId(UUID.fromString(FACILITY_ID));

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .body(firstOrder)
        .when()
        .post(RESOURCE_URL)
        .then()
        .statusCode(201);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldUpdateOrder() {
    firstOrder.setQuotedCost(new BigDecimal(NUMBER));

    Order response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", firstOrder.getId())
        .body(firstOrder)
        .when()
        .put(ID_URL)
        .then()
        .statusCode(200)
        .extract().as(Order.class);

    assertEquals(response.getQuotedCost(), new BigDecimal(NUMBER));
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldCreateNewOrderIfDoesNotExist() {
    given(orderRepository.findOne(firstOrder.getId())).willReturn(null);
    firstOrder.setQuotedCost(new BigDecimal(NUMBER));

    Order response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", ID)
        .body(firstOrder)
        .when()
        .put(ID_URL)
        .then()
        .statusCode(200)
        .extract().as(Order.class);

    assertEquals(response.getQuotedCost(), new BigDecimal(NUMBER));
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldGetAllOrders() {

    Order[] response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .when()
        .get(RESOURCE_URL)
        .then()
        .statusCode(200)
        .extract().as(Order[].class);

    Iterable<Order> orders = Arrays.asList(response);
    assertTrue(orders.iterator().hasNext());
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldGetChosenOrder() {

    Order response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", firstOrder.getId())
        .when()
        .get(ID_URL)
        .then()
        .statusCode(200)
        .extract().as(Order.class);

    assertTrue(orderRepository.exists(response.getId()));
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldNotGetNonexistentOrder() {
    given(orderRepository.findOne(firstOrder.getId())).willReturn(null);

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", firstOrder.getId())
        .when()
        .get(ID_URL)
        .then()
        .statusCode(404);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnConflictForExistingOrderCode() {
    given(orderRepository.save(any(Order.class))).willThrow(DataIntegrityViolationException.class);

    firstOrder.getOrderLineItems().clear();
    firstOrder.setSupplyingFacilityId(UUID.fromString(FACILITY_ID));

    given(orderRepository.findOne(firstOrder.getId())).willReturn(firstOrder);
    firstOrder.setOrderLineItems(null);

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .body(firstOrder)
        .when()
        .post(RESOURCE_URL)
        .then()
        .statusCode(409);
  }

  @Test
  public void shouldExportOrderIfTypeIsNotSpecified() {
    String csvContent = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .pathParam("id", secondOrder.getId())
        .when()
        .get("/api/orders/{id}/export")
        .then()
        .statusCode(200)
        .extract().body().asString();

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertTrue(csvContent.startsWith("Order number,Facility code,Product code,Product name,"
        + "Approved quantity,Period,Order date"));

    String orderDate = secondOrder.getCreatedDate().format(DateTimeFormatter.ofPattern("dd/MM/yy"));

    for (OrderLineItem lineItem : secondOrder.getOrderLineItems()) {
      String string = secondOrder.getOrderCode()
          + ",facilityCode,Product Code,Product Name," + lineItem.getApprovedQuantity()
          + ",03/16," + orderDate;
      assertThat(csvContent, containsString(string));
    }
  }

  @Test
  public void shouldExportOrderIfTypeIsCsv() {
    String csvContent = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .pathParam("id", secondOrder.getId())
        .queryParam("type", "csv")
        .when()
        .get("/api/orders/{id}/export")
        .then()
        .statusCode(200)
        .extract().body().asString();

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
    assertTrue(csvContent.startsWith("Order number,Facility code,Product code,Product name,"
        + "Approved quantity,Period,Order date"));
  }

  @Test
  public void shouldNotExportOrderIfTypeIsDifferentThanCsv() {
    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .pathParam("id", secondOrder.getId())
        .queryParam("type", "pdf")
        .when()
        .get("/api/orders/{id}/export")
        .then()
        .statusCode(400);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }
}
