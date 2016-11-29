package org.openlmis.fulfillment.web;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;

import com.google.common.collect.Lists;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openlmis.fulfillment.domain.Order;
import org.openlmis.fulfillment.domain.OrderLineItem;
import org.openlmis.fulfillment.domain.OrderStatus;
import org.openlmis.fulfillment.domain.ProofOfDelivery;
import org.openlmis.fulfillment.domain.ProofOfDeliveryLineItem;
import org.openlmis.fulfillment.domain.Template;
import org.openlmis.fulfillment.referencedata.model.FacilityDto;
import org.openlmis.fulfillment.referencedata.model.OrderableProductDto;
import org.openlmis.fulfillment.referencedata.model.ProcessingPeriodDto;
import org.openlmis.fulfillment.referencedata.model.ProcessingScheduleDto;
import org.openlmis.fulfillment.referencedata.model.ProgramDto;
import org.openlmis.fulfillment.referencedata.model.SupervisoryNodeDto;
import org.openlmis.fulfillment.repository.OrderRepository;
import org.openlmis.fulfillment.repository.ProofOfDeliveryRepository;
import org.openlmis.fulfillment.service.TemplateService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import guru.nidi.ramltester.junit.RamlMatchers;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

@SuppressWarnings("PMD.TooManyMethods")
public class ProofOfDeliveryControllerIntegrationTest extends BaseWebIntegrationTest {

  private static final String RESOURCE_URL = "/api/proofOfDeliveries";
  private static final String ID_URL = RESOURCE_URL + "/{id}";
  private static final String PRINT_URL = RESOURCE_URL + "/{id}/print";
  private static final String PRINT_POD = "Print POD";
  private static final String CONSISTENCY_REPORT = "Consistency Report";
  private static final String ACCESS_TOKEN = "access_token";
  private static final UUID ID = UUID.fromString("1752b457-0a4b-4de0-bf94-5a6a8002427e");

  @MockBean
  private TemplateService templateService;

  @MockBean
  private OrderRepository orderRepository;

  @MockBean
  private ProofOfDeliveryRepository proofOfDeliveryRepository;

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
    supervisoryNode.setId(UUID.randomUUID());
    supervisoryNode.setCode("NodeCode");
    supervisoryNode.setName("NodeName");
    supervisoryNode.setFacility(facility);

    ProgramDto program = new ProgramDto();
    program.setId(UUID.randomUUID());
    program.setCode("programCode");

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
    order.setStatus(OrderStatus.SHIPPED);
    order.setCreatedDate(LocalDateTime.now());
    order.setCreatedById(UUID.randomUUID());
    order.setOrderCode("O1");
    order.setProgramId(program.getId());
    order.setQuotedCost(new BigDecimal(100));
    order.setSupplyingFacilityId(facility.getId());
    order.setRequestingFacilityId(facility.getId());
    order.setReceivingFacilityId(facility.getId());
    order.setSupervisoryNodeId(supervisoryNode.getId());

    given(orderRepository.findOne(order.getId())).willReturn(order);
    given(orderRepository.exists(order.getId())).willReturn(true);

    OrderLineItem orderLineItem = new OrderLineItem();
    orderLineItem.setId(UUID.randomUUID());
    orderLineItem.setOrderableProductId(product.getId());
    orderLineItem.setOrderedQuantity(100L);
    orderLineItem.setFilledQuantity(100L);
    orderLineItem.setApprovedQuantity(0L);

    proofOfDeliveryLineItem.setId(UUID.randomUUID());
    proofOfDeliveryLineItem.setOrderLineItem(orderLineItem);
    proofOfDeliveryLineItem.setQuantityShipped(100L);
    proofOfDeliveryLineItem.setQuantityReturned(100L);
    proofOfDeliveryLineItem.setQuantityReceived(100L);
    proofOfDeliveryLineItem.setPackToShip(100L);
    proofOfDeliveryLineItem.setReplacedProductCode("replaced product code");
    proofOfDeliveryLineItem.setNotes("Notes");

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

    given(proofOfDeliveryRepository.save(any(ProofOfDelivery.class)))
        .willAnswer(new SaveAnswer<ProofOfDelivery>());
  }

  @Ignore
  @Test
  public void shouldPrintProofOfDeliveryToPdf() throws Exception {
    ClassPathResource podReport = new ClassPathResource("reports/podPrint.jrxml");
    FileInputStream fileInputStream = new FileInputStream(podReport.getFile());
    MultipartFile templateOfProofOfDelivery = new MockMultipartFile("file",
        podReport.getFilename(), "multipart/form-data", IOUtils.toByteArray(fileInputStream));

    Template template = new Template(PRINT_POD, null, null, CONSISTENCY_REPORT, "");
    templateService.validateFileAndInsertTemplate(template, templateOfProofOfDelivery);

    restAssured.given()
        .pathParam("id", proofOfDelivery.getId())
        .queryParam(ACCESS_TOKEN, getToken())
        .when()
        .get(PRINT_URL)
        .then()
        .statusCode(200);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldDeleteProofOfDelivery() {
    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", proofOfDelivery.getId())
        .when()
        .delete(ID_URL)
        .then()
        .statusCode(204);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldUpdateProofOfDelivery() {
    proofOfDelivery.setTotalReceivedPacks(2);

    ProofOfDelivery response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", proofOfDelivery.getId())
        .body(proofOfDelivery)
        .when()
        .put(ID_URL)
        .then()
        .statusCode(200)
        .extract().as(ProofOfDelivery.class);

    assertTrue(response.getTotalReceivedPacks().equals(2));
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldCreateNewProofOfDeliveryIfDoesNotExist() {
    given(proofOfDeliveryRepository.findOne(proofOfDelivery.getId())).willReturn(null);
    given(proofOfDeliveryRepository.exists(proofOfDelivery.getId())).willReturn(false);

    proofOfDelivery.setTotalReceivedPacks(2);

    ProofOfDelivery response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", ID)
        .body(proofOfDelivery)
        .when()
        .put(ID_URL)
        .then()
        .statusCode(200)
        .extract().as(ProofOfDelivery.class);

    assertTrue(response.getTotalReceivedPacks().equals(2));
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldGetAllProofOfDeliveries() {
    given(proofOfDeliveryRepository.findAll()).willReturn(Lists.newArrayList(proofOfDelivery));

    ProofOfDelivery[] response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .when()
        .get(RESOURCE_URL)
        .then()
        .statusCode(200)
        .extract().as(ProofOfDelivery[].class);

    Iterable<ProofOfDelivery> proofOfDeliveries = Arrays.asList(response);
    assertTrue(proofOfDeliveries.iterator().hasNext());
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldGetChosenProofOfDelivery() {

    ProofOfDelivery response = restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", proofOfDelivery.getId())
        .when()
        .get(ID_URL)
        .then()
        .statusCode(200)
        .extract().as(ProofOfDelivery.class);

    assertTrue(proofOfDeliveryRepository.exists(response.getId()));
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldNotGetNonexistentProofOfDelivery() {
    given(proofOfDeliveryRepository.findOne(proofOfDelivery.getId())).willReturn(null);
    given(proofOfDeliveryRepository.exists(proofOfDelivery.getId())).willReturn(false);

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .pathParam("id", proofOfDelivery.getId())
        .when()
        .get(ID_URL)
        .then()
        .statusCode(404);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldCreateProofOfDelivery() {
    proofOfDelivery.getProofOfDeliveryLineItems().clear();

    given(proofOfDeliveryRepository.findOne(proofOfDelivery.getId())).willReturn(null);
    given(proofOfDeliveryRepository.exists(proofOfDelivery.getId())).willReturn(false);

    restAssured.given()
        .queryParam(ACCESS_TOKEN, getToken())
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .body(proofOfDelivery)
        .when()
        .post(RESOURCE_URL)
        .then()
        .statusCode(201);

    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }
}
