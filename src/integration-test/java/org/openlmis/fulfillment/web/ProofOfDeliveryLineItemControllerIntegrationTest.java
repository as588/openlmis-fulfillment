package org.openlmis.fulfillment.web;

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
import org.openlmis.fulfillment.domain.ProofOfDelivery;
import org.openlmis.fulfillment.domain.ProofOfDeliveryLineItem;
import org.openlmis.fulfillment.referencedata.model.FacilityDto;
import org.openlmis.fulfillment.referencedata.model.OrderableProductDto;
import org.openlmis.fulfillment.referencedata.model.ProcessingPeriodDto;
import org.openlmis.fulfillment.referencedata.model.ProcessingScheduleDto;
import org.openlmis.fulfillment.referencedata.model.ProgramDto;
import org.openlmis.fulfillment.referencedata.model.SupervisoryNodeDto;
import org.openlmis.fulfillment.repository.OrderLineItemRepository;
import org.openlmis.fulfillment.repository.OrderRepository;
import org.openlmis.fulfillment.repository.ProofOfDeliveryLineItemRepository;
import org.openlmis.fulfillment.repository.ProofOfDeliveryRepository;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import guru.nidi.ramltester.junit.RamlMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

@SuppressWarnings("PMD.TooManyMethods")
public class ProofOfDeliveryLineItemControllerIntegrationTest extends BaseWebIntegrationTest {

  private static final String RESOURCE_URL = "/api/proofOfDeliveryLineItems";
  private static final String ID_URL = RESOURCE_URL + "/{id}";
  private static final String ACCESS_TOKEN = "access_token";
  private static final UUID ID = UUID.fromString("1752b457-0a4b-4de0-bf94-5a6a8002427e");
  private static final String NOTES = "OpenLMIS";

  @MockBean
  private OrderRepository orderRepository;

  @MockBean
  private OrderLineItemRepository orderLineItemRepository;

  @MockBean
  private ProofOfDeliveryRepository proofOfDeliveryRepository;

  @MockBean
  private ProofOfDeliveryLineItemRepository proofOfDeliveryLineItemRepository;

  private ProofOfDelivery proofOfDelivery = new ProofOfDelivery();
  private ProofOfDeliveryLineItem proofOfDeliveryLineItem = new ProofOfDeliveryLineItem();

  /**
   * Prepare the test environment.
   */
  @Before
  public void setUp() {
    this.setUpBootstrapData();

    OrderableProductDto product = new OrderableProductDto();
    product.setId(UUID.randomUUID());

    FacilityDto facility = new FacilityDto();
    facility.setId(UUID.randomUUID());
    facility.setCode("facilityCode");
    facility.setName("facilityName");
    facility.setDescription("facilityDescription");
    facility.setActive(true);
    facility.setEnabled(true);

    SupervisoryNodeDto supervisoryNode = new SupervisoryNodeDto();
    supervisoryNode.setCode("NodeCode");
    supervisoryNode.setName("NodeName");
    supervisoryNode.setFacility(facility);

    ProgramDto program = new ProgramDto();
    program.setId(UUID.randomUUID());
    program.setCode("programCode");

    ProcessingScheduleDto schedule = new ProcessingScheduleDto();
    schedule.setId(UUID.randomUUID());
    schedule.setCode("scheduleCode");
    schedule.setName("scheduleName");

    ProcessingPeriodDto period = new ProcessingPeriodDto();
    period.setId(UUID.randomUUID());
    period.setProcessingSchedule(new ProcessingScheduleDto());
    period.setName("periodName");
    period.setStartDate(LocalDate.of(2015, Month.JANUARY, 1));
    period.setEndDate(LocalDate.of(2015, Month.DECEMBER, 31));

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setExternalId(UUID.randomUUID());
    order.setProgramId(program.getId());
    order.setFacilityId(facility.getId());
    order.setProcessingPeriodId(period.getId());
    order.setEmergency(false);
    order.setOrderCode("O");
    order.setQuotedCost(new BigDecimal("10.00"));
    order.setStatus(OrderStatus.ORDERED);
    order.setProgramId(program.getId());
    order.setCreatedById(UUID.randomUUID());
    order.setRequestingFacilityId(facility.getId());
    order.setReceivingFacilityId(facility.getId());
    order.setSupplyingFacilityId(facility.getId());

    given(orderRepository.findOne(order.getId())).willReturn(order);
    given(orderRepository.exists(order.getId())).willReturn(true);

    OrderLineItem orderLineItem = new OrderLineItem();
    orderLineItem.setId(UUID.randomUUID());
    orderLineItem.setOrderableProductId(product.getId());
    orderLineItem.setOrderedQuantity(100L);
    orderLineItem.setFilledQuantity(100L);
    orderLineItem.setApprovedQuantity(0L);

    given(orderLineItemRepository.findOne(orderLineItem.getId())).willReturn(orderLineItem);
    given(orderLineItemRepository.exists(orderLineItem.getId())).willReturn(true);

    proofOfDeliveryLineItem.setId(UUID.randomUUID());
    proofOfDeliveryLineItem.setOrderLineItem(orderLineItem);
    proofOfDeliveryLineItem.setQuantityShipped(100L);
    proofOfDeliveryLineItem.setQuantityReturned(100L);
    proofOfDeliveryLineItem.setQuantityReceived(100L);
    proofOfDeliveryLineItem.setPackToShip(100L);
    proofOfDeliveryLineItem.setReplacedProductCode("replaced product code");
    proofOfDeliveryLineItem.setNotes("Notes");

    given(proofOfDeliveryLineItemRepository.findOne(proofOfDeliveryLineItem.getId()))
        .willReturn(proofOfDeliveryLineItem);
    given(proofOfDeliveryLineItemRepository.exists(proofOfDeliveryLineItem.getId()))
        .willReturn(true);

    given(proofOfDeliveryLineItemRepository.save(any(ProofOfDeliveryLineItem.class)))
        .willAnswer(new SaveAnswer<ProofOfDeliveryLineItem>());

    proofOfDelivery.setId(UUID.randomUUID());
    proofOfDelivery.setOrder(order);
    proofOfDelivery.setTotalShippedPacks(100);
    proofOfDelivery.setTotalReceivedPacks(100);
    proofOfDelivery.setTotalReturnedPacks(10);
    proofOfDelivery.setDeliveredBy("delivered by");
    proofOfDelivery.setReceivedBy("received by");
    proofOfDelivery.setReceivedDate(LocalDate.now());
    proofOfDelivery.setProofOfDeliveryLineItems(new ArrayList<>());
    proofOfDelivery.getProofOfDeliveryLineItems().add(proofOfDeliveryLineItem);

    given(proofOfDeliveryRepository.findOne(proofOfDelivery.getId()))
        .willReturn(proofOfDelivery);
    given(proofOfDeliveryRepository.exists(proofOfDelivery.getId()))
        .willReturn(true);
  }

  @Test
  public void shouldDeleteProofOfDeliveryLineItem() {
    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", proofOfDeliveryLineItem.getId())
        .when()
        .delete(ID_URL)
        .then()
        .statusCode(204);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldNotDeleteNonexistentProofOfDeliveryLineItem() {
    given(proofOfDeliveryLineItemRepository.findOne(proofOfDeliveryLineItem.getId()))
        .willReturn(null);
    given(proofOfDeliveryLineItemRepository.exists(proofOfDeliveryLineItem.getId()))
        .willReturn(false);

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", proofOfDeliveryLineItem.getId())
        .when()
        .delete(ID_URL)
        .then()
        .statusCode(404);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldGetAllProofOfDeliveryLineItems() {
    given(proofOfDeliveryLineItemRepository.findAll())
        .willReturn(Lists.newArrayList(proofOfDeliveryLineItem));

    ProofOfDeliveryLineItem[] response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .when()
        .get(RESOURCE_URL)
        .then()
        .statusCode(200)
        .extract().as(ProofOfDeliveryLineItem[].class);

    Iterable<ProofOfDeliveryLineItem> proofOfDeliveryLineItems = Arrays.asList(response);
    assertTrue(proofOfDeliveryLineItems.iterator().hasNext());
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldGetChosenProofOfDeliveryLineItem() {

    ProofOfDeliveryLineItem response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", proofOfDeliveryLineItem.getId())
        .when()
        .get(ID_URL)
        .then()
        .statusCode(200)
        .extract().as(ProofOfDeliveryLineItem.class);

    assertEquals(proofOfDeliveryLineItem.getId(), response.getId());
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldNotGetNonexistentProofOfDeliveryLineItem() {
    given(proofOfDeliveryLineItemRepository.findOne(proofOfDeliveryLineItem.getId()))
        .willReturn(null);
    given(proofOfDeliveryLineItemRepository.exists(proofOfDeliveryLineItem.getId()))
        .willReturn(false);

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", proofOfDeliveryLineItem.getId())
        .when()
        .get(ID_URL)
        .then()
        .statusCode(404);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldCreateProofOfDeliveryLineItem() {
    given(proofOfDeliveryLineItemRepository.findOne(proofOfDeliveryLineItem.getId()))
        .willReturn(null);
    given(proofOfDeliveryLineItemRepository.exists(proofOfDeliveryLineItem.getId()))
        .willReturn(false);

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .body(proofOfDeliveryLineItem)
        .when()
        .post(RESOURCE_URL)
        .then()
        .statusCode(201);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldUpdateProofOfDeliveryLineItem() {
    proofOfDeliveryLineItem.setNotes(NOTES);

    ProofOfDeliveryLineItem response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", proofOfDeliveryLineItem.getId())
        .body(proofOfDeliveryLineItem)
        .when()
        .put(ID_URL)
        .then()
        .statusCode(200)
        .extract().as(ProofOfDeliveryLineItem.class);

    assertEquals(response.getNotes(), NOTES);
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldCreateNewProofOfDeliveryLineItemIfDoesNotExist() {
    given(proofOfDeliveryLineItemRepository.findOne(proofOfDeliveryLineItem.getId()))
        .willReturn(null);
    given(proofOfDeliveryLineItemRepository.exists(proofOfDeliveryLineItem.getId()))
        .willReturn(false);
    proofOfDeliveryLineItem.setNotes(NOTES);

    ProofOfDeliveryLineItem response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", ID)
        .body(proofOfDeliveryLineItem)
        .when()
        .put(ID_URL)
        .then()
        .statusCode(200)
        .extract().as(ProofOfDeliveryLineItem.class);

    assertEquals(response.getNotes(), NOTES);
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }
}