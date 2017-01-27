package org.openlmis.fulfillment.service;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openlmis.fulfillment.util.ConfigurationSettingKeys.FULFILLMENT_EMAIL_NOREPLY;
import static org.openlmis.fulfillment.util.ConfigurationSettingKeys.FULFILLMENT_EMAIL_ORDER_CREATION_BODY;
import static org.openlmis.fulfillment.util.ConfigurationSettingKeys.FULFILLMENT_EMAIL_ORDER_CREATION_SUBJECT;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.fulfillment.domain.FtpTransferProperties;
import org.openlmis.fulfillment.domain.Order;
import org.openlmis.fulfillment.domain.OrderLineItem;
import org.openlmis.fulfillment.domain.OrderNumberConfiguration;
import org.openlmis.fulfillment.domain.OrderStatus;
import org.openlmis.fulfillment.repository.OrderNumberConfigurationRepository;
import org.openlmis.fulfillment.repository.OrderRepository;
import org.openlmis.fulfillment.repository.TransferPropertiesRepository;
import org.openlmis.fulfillment.service.notification.NotificationRequest;
import org.openlmis.fulfillment.service.notification.NotificationService;
import org.openlmis.fulfillment.service.referencedata.FacilityDto;
import org.openlmis.fulfillment.service.referencedata.FacilityReferenceDataService;
import org.openlmis.fulfillment.service.referencedata.OrderableDto;
import org.openlmis.fulfillment.service.referencedata.OrderableReferenceDataService;
import org.openlmis.fulfillment.service.referencedata.ProgramDto;
import org.openlmis.fulfillment.service.referencedata.ProgramReferenceDataService;
import org.openlmis.fulfillment.service.referencedata.UserDto;
import org.openlmis.fulfillment.service.referencedata.UserReferenceDataService;

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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@SuppressWarnings({"PMD.TooManyMethods"})
@RunWith(MockitoJUnitRunner.class)
public class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private OrderNumberConfigurationRepository orderNumberConfigurationRepository;

  @Mock
  private FacilityReferenceDataService facilityReferenceDataService;

  @Mock
  private ProgramReferenceDataService programReferenceDataService;

  @Mock
  private OrderableReferenceDataService orderableReferenceDataService;

  @Mock
  private UserReferenceDataService userReferenceDataService;

  @Mock
  private TransferPropertiesRepository transferPropertiesRepository;

  @Mock
  private NotificationService notificationService;

  @Mock
  private ConfigurationSettingService configurationSettingService;

  @Mock
  private OrderStorage orderStorage;

  @Mock
  private OrderSender orderSender;

  @InjectMocks
  private OrderService orderService;

  @Mock
  private ProgramDto program;

  @Captor
  private ArgumentCaptor<NotificationRequest> notificationCaptor;

  @Before
  public void setUp() throws ConfigurationSettingException {
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
    order.setId(UUID.randomUUID());
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
    when(orderSender.send(order)).thenReturn(true);
    Order created = orderService.save(order);

    // then
    validateCreatedOrder(created, order);
    assertEquals(OrderStatus.IN_ROUTE, created.getStatus());

    InOrder inOrder = inOrder(orderRepository, orderStorage, orderSender);
    inOrder.verify(orderRepository).save(order);
    inOrder.verify(orderStorage).store(order);
    inOrder.verify(orderSender).send(order);
    inOrder.verify(orderStorage).delete(order);

    verify(notificationService).send(notificationCaptor.capture());

    NotificationRequest notification = notificationCaptor.getValue();
    assertThat(notification, is(notNullValue()));

    assertThat(notification.getFrom(), is("noreply@openlmis.org"));
    assertThat(notification.getTo(), is("user@openlmis.org"));
    assertThat(notification.getSubject(), is("New order"));
    assertThat(notification.getContent(),
        is("Create an order: " + order.getId() + " with status: IN_ROUTE"));
    assertThat(notification.getHtmlContent(), is(nullValue()));
  }

  @Test
  public void shouldSaveOrderAndNotDeleteFileIfFtpSendFailure() throws Exception {
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
    when(orderSender.send(order)).thenReturn(false);
    Order created = orderService.save(order);

    // then
    validateCreatedOrder(created, order);
    assertEquals(OrderStatus.TRANSFER_FAILED, created.getStatus());

    InOrder inOrder = inOrder(orderRepository, orderStorage, orderSender);
    inOrder.verify(orderRepository).save(order);
    inOrder.verify(orderStorage).store(order);
    inOrder.verify(orderSender).send(order);
    inOrder.verify(orderStorage, never()).delete(order);
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

    OrderableDto orderableDto = mock(OrderableDto.class);
    when(orderableReferenceDataService.findOne(any())).thenReturn(orderableDto);
    when(orderableDto.getProductCode()).thenReturn("productCode");
    when(orderableDto.getName()).thenReturn("product");

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
    assertEquals(expectedLineItem.getOrderableId(), actualLineItem.getOrderableId());
  }

  private void generateMocks() throws ConfigurationSettingException {
    ProgramDto programDto = new ProgramDto();
    programDto.setCode("programCode");
    when(programReferenceDataService.findOne(any())).thenReturn(programDto);

    FacilityDto facilityDto = new FacilityDto();
    facilityDto.setCode("FacilityCode");
    when(facilityReferenceDataService.findOne(any())).thenReturn(facilityDto);

    UserDto userDto = new UserDto();
    userDto.setEmail("user@openlmis.org");
    when(userReferenceDataService.findOne(any())).thenReturn(userDto);

    FtpTransferProperties properties = new FtpTransferProperties();
    when(transferPropertiesRepository.findFirstByFacilityId(any())).thenReturn(properties);

    when(configurationSettingService.getStringValue(FULFILLMENT_EMAIL_NOREPLY))
        .thenReturn("noreply@openlmis.org");
    when(configurationSettingService.getStringValue(FULFILLMENT_EMAIL_ORDER_CREATION_SUBJECT))
        .thenReturn("New order");
    when(configurationSettingService.getStringValue(FULFILLMENT_EMAIL_ORDER_CREATION_BODY))
        .thenReturn("Create an order: {id} with status: {status}");
  }
}
