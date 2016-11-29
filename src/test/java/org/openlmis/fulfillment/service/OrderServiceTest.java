package org.openlmis.fulfillment.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.fulfillment.domain.Order;
import org.openlmis.fulfillment.domain.OrderLineItem;
import org.openlmis.fulfillment.domain.OrderNumberConfiguration;
import org.openlmis.fulfillment.domain.OrderStatus;
import org.openlmis.fulfillment.referencedata.model.FacilityDto;
import org.openlmis.fulfillment.referencedata.service.FacilityReferenceDataService;
import org.openlmis.fulfillment.referencedata.model.OrderableProductDto;
import org.openlmis.fulfillment.referencedata.service.OrderableProductReferenceDataService;
import org.openlmis.fulfillment.referencedata.model.ProgramDto;
import org.openlmis.fulfillment.referencedata.service.ProgramReferenceDataService;
import org.openlmis.fulfillment.referencedata.service.UserFulfillmentFacilitiesReferenceDataService;
import org.openlmis.fulfillment.repository.OrderNumberConfigurationRepository;
import org.openlmis.fulfillment.repository.OrderRepository;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@SuppressWarnings({"PMD.TooManyMethods", "PMD.UnusedPrivateField"})
@RunWith(MockitoJUnitRunner.class)
public class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private OrderNumberConfigurationRepository orderNumberConfigurationRepository;

  @Mock
  private ProgramDto program;

  @Mock
  private ProgramReferenceDataService programReferenceDataService;

  @Mock
  private UserFulfillmentFacilitiesReferenceDataService fulfillmentFacilitiesReferenceDataService;

  @Mock
  private FacilityReferenceDataService facilityReferenceDataService;

  @Mock
  private OrderableProductReferenceDataService orderableProductReferenceDataService;

  @Mock
  private OrderStorage orderStorage;

  @InjectMocks
  private OrderService orderService;

  @Before
  public void setUp() {
    generateMocks();
  }

  @Test
  public void shouldSaveOrder() throws Exception {
    // given
    OrderNumberConfiguration orderNumberConfiguration =
        new OrderNumberConfiguration("prefix", true, true, true);
    when(orderNumberConfigurationRepository.findAll())
        .thenReturn(Collections.singletonList(orderNumberConfiguration));

    Order order = new Order();
    order.setExternalId(UUID.randomUUID());
    order.setEmergency(true);
    order.setProgramId(program.getId());
    order.setStatus(OrderStatus.ORDERED);
    order.setQuotedCost(BigDecimal.ZERO);
    order.setSupplyingFacilityId(UUID.randomUUID());

    OrderLineItem orderLineItem = new OrderLineItem();
    orderLineItem.setOrderedQuantity(1000L);

    order.setOrderLineItems(Lists.newArrayList(orderLineItem));
    order.setCreatedById(UUID.randomUUID());

    // when
    when(orderRepository.save(any(Order.class))).thenReturn(order);
    Order created = orderService.save(order);

    // then
    validateCreatedOrder(created, order);
    verify(orderRepository).save(any(Order.class));
    verify(orderStorage).store(any(Order.class));
  }

  @Test
  public void shouldFindOrderIfMatchedSupplyingAndRequestingFacilitiesAndProgram() {
    // given
    Order order = generateOrder();

    when(orderRepository.searchOrders(
        order.getSupplyingFacilityId(), order.getRequestingFacilityId(), order.getProgramId()))
        .thenReturn(Collections.singletonList(order));

    // when
    List<Order> receivedOrders = orderService.searchOrders(
        order.getSupplyingFacilityId(), order.getRequestingFacilityId(), order.getProgramId());

    // then
    assertEquals(1, receivedOrders.size());
    assertEquals(receivedOrders.get(0).getSupplyingFacilityId(), order.getSupplyingFacilityId());
    assertEquals(receivedOrders.get(0).getRequestingFacilityId(), order.getRequestingFacilityId());
    assertEquals(receivedOrders.get(0).getProgramId(), order.getProgramId());

    verify(orderRepository, atLeastOnce()).searchOrders(anyObject(), anyObject(), anyObject());
  }

  @Test
  public void shouldConvertOrderToCsvIfItExists()
      throws IOException, URISyntaxException, OrderCsvWriteException {
    // given
    Order order = generateOrder();
    when(order.getRequestingFacilityId()).thenReturn(UUID.randomUUID());

    //Creation date has to be static because we need to read expected csv from file
    order.setCreatedDate(ZonedDateTime.parse("2016-08-27T11:30Z").toLocalDateTime());

    List<String> header = new ArrayList<>();
    header.add(OrderService.DEFAULT_COLUMNS[0]);
    header.add(OrderService.DEFAULT_COLUMNS[1]);
    header.add(OrderService.DEFAULT_COLUMNS[3]);
    header.add(OrderService.DEFAULT_COLUMNS[4]);
    header.add(OrderService.DEFAULT_COLUMNS[5]);

    OrderableProductDto orderableProductDto = mock(OrderableProductDto.class);
    when(orderableProductReferenceDataService.findOne(any())).thenReturn(orderableProductDto);
    when(orderableProductDto.getProductCode()).thenReturn("productCode");
    when(orderableProductDto.getName()).thenReturn("product");

    String expectedOutput = prepareExpectedCsvOutput(order, header);

    // when
    String receivedOutput;
    try (StringWriter writer = new StringWriter()) {
      orderService.orderToCsv(order, header.toArray(new String[header.size()]), writer);
      receivedOutput = writer.toString().replace("\r\n", "\n");
    }

    // then
    assertEquals(expectedOutput, receivedOutput);
  }

  @Test
  public void shouldValidateFacility() {
    // given
    UUID facilityId = UUID.randomUUID();

    FacilityDto facilityDto =  new FacilityDto();
    facilityDto.setId(facilityId);

    Collection<FacilityDto> facilityDtos = new ArrayList<>();
    facilityDtos.add(facilityDto);

    Order order = new Order();

    UUID programId = UUID.randomUUID();
    UUID supervisoryNodeId = UUID.randomUUID();

    order.setProgramId(programId);
    order.setSupervisoryNodeId(supervisoryNodeId);
    UUID userId = UUID.randomUUID();
    // when

    when(fulfillmentFacilitiesReferenceDataService.getFulfillmentFacilities(userId))
        .thenReturn(facilityDtos);
    when(facilityReferenceDataService.searchSupplyingDepots(programId, supervisoryNodeId))
        .thenReturn(facilityDtos);

    boolean result = orderService.isFacilityValid(order, userId, facilityId);
    // then
    assertTrue(result);
  }

  @Test
  public void shouldNotValidateFacility() {
    // given
    UUID facilityId = UUID.randomUUID();
    UUID facilityId2 = UUID.randomUUID();

    FacilityDto facilityDto =  new FacilityDto();
    facilityDto.setId(facilityId);

    Collection<FacilityDto> facilityDtos = new ArrayList<>();
    facilityDtos.add(facilityDto);

    FacilityDto facilityDto2 =  new FacilityDto();
    facilityDto.setId(facilityId2);

    Collection<FacilityDto> facilityDtos2 = new ArrayList<>();
    facilityDtos2.add(facilityDto2);

    UUID programId = UUID.randomUUID();
    UUID supervisoryNodeId = UUID.randomUUID();

    Order order = new Order();
    order.setProgramId(programId);
    order.setSupervisoryNodeId(supervisoryNodeId);

    UUID userId = UUID.randomUUID();
    // when

    when(fulfillmentFacilitiesReferenceDataService.getFulfillmentFacilities(userId))
        .thenReturn(facilityDtos);
    when(facilityReferenceDataService.searchSupplyingDepots(programId, supervisoryNodeId))
        .thenReturn(facilityDtos2);

    boolean result = orderService.isFacilityValid(order, userId, facilityId);
    // then
    assertFalse(result);
  }

  private Order generateOrder() {
    int number = new Random().nextInt();
    Order order = new Order();
    order.setProgramId(program.getId());
    order.setCreatedDate(LocalDateTime.now().plusDays(number));
    order.setCreatedById(UUID.randomUUID());
    order.setQuotedCost(BigDecimal.valueOf(1));
    order.setOrderCode("OrderCode " + number);
    order.setStatus(OrderStatus.ORDERED);
    order.setOrderLineItems(new ArrayList<>());
    order.getOrderLineItems().add(generateOrderLineItem(order));
    return order;
  }

  private OrderLineItem generateOrderLineItem(Order order) {
    OrderLineItem orderLineItem = new OrderLineItem();
    orderLineItem.setId(UUID.randomUUID());
    orderLineItem.setFilledQuantity(1000L);
    orderLineItem.setOrder(order);
    orderLineItem.setOrderedQuantity(1000L);
    return orderLineItem;
  }

  private String prepareExpectedCsvOutput(Order order, List<String> header)
      throws IOException, URISyntaxException {
    URL url =
        Thread.currentThread().getContextClassLoader().getResource("OrderServiceTest_expected.csv");
    byte[] encoded = Files.readAllBytes(Paths.get(url.getPath()));
    return new String(encoded, Charset.defaultCharset());
  }

  private void validateCreatedOrder(Order actual, Order expected) {
    assertEquals(OrderStatus.ORDERED, actual.getStatus());
    assertEquals(actual.getExternalId(), expected.getExternalId());
    assertEquals(actual.getReceivingFacilityId(), expected.getReceivingFacilityId());
    assertEquals(actual.getRequestingFacilityId(), expected.getRequestingFacilityId());
    assertEquals(actual.getProgramId(), expected.getProgramId());
    assertEquals(actual.getSupplyingFacilityId(), expected.getSupplyingFacilityId());
    assertEquals(1, actual.getOrderLineItems().size());
    assertEquals(1, expected.getOrderLineItems().size());

    OrderLineItem actualLineItem = actual.getOrderLineItems().iterator().next();
    OrderLineItem expectedLineItem = expected.getOrderLineItems().iterator().next();

    assertEquals(expectedLineItem.getOrderedQuantity(), actualLineItem.getOrderedQuantity());
    assertEquals(expectedLineItem.getOrderableProductId(), actualLineItem.getOrderableProductId());
  }

  private void generateMocks() {
    ProgramDto programDto = new ProgramDto();
    programDto.setCode("programCode");
    when(programReferenceDataService.findOne(any())).thenReturn(programDto);

    FacilityDto facilityDto = new FacilityDto();
    facilityDto.setCode("FacilityCode");
    when(facilityReferenceDataService.findOne(any())).thenReturn(facilityDto);

  }
}
