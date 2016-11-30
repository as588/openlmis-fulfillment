package org.openlmis.fulfillment.service;

import static ch.qos.logback.core.util.CloseUtil.closeQuietly;

import com.google.common.collect.ImmutableMap;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapArrayDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

import org.openlmis.fulfillment.domain.Order;
import org.openlmis.fulfillment.domain.OrderLineItem;
import org.openlmis.fulfillment.domain.OrderNumberConfiguration;
import org.openlmis.fulfillment.referencedata.model.FacilityDto;
import org.openlmis.fulfillment.referencedata.model.OrderableProductDto;
import org.openlmis.fulfillment.referencedata.model.ProgramDto;
import org.openlmis.fulfillment.referencedata.service.FacilityReferenceDataService;
import org.openlmis.fulfillment.referencedata.service.OrderableProductReferenceDataService;
import org.openlmis.fulfillment.referencedata.service.ProgramReferenceDataService;
import org.openlmis.fulfillment.referencedata.service.UserFulfillmentFacilitiesReferenceDataService;
import org.openlmis.fulfillment.repository.OrderNumberConfigurationRepository;
import org.openlmis.fulfillment.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

  public static final String[] DEFAULT_COLUMNS = {"facilityCode", "createdDate", "orderNum",
      "productName", "productCode", "orderedQuantity", "filledQuantity"};

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private OrderNumberConfigurationRepository orderNumberConfigurationRepository;

  @Autowired
  private FacilityReferenceDataService facilityReferenceDataService;

  @Autowired
  private ProgramReferenceDataService programReferenceDataService;

  @Autowired
  private OrderableProductReferenceDataService orderableProductReferenceDataService;
  @Autowired
  private UserFulfillmentFacilitiesReferenceDataService fulfillmentFacilitiesReferenceDataService;

  @Autowired
  private OrderStorage orderStorage;

  /**
   * Finds orders matching all of provided parameters.
   *
   * @param supplyingFacility  supplyingFacility of searched Orders.
   * @param requestingFacility requestingFacility of searched Orders.
   * @param program            program of searched Orders.
   * @return ist of Orders with matched parameters.
   */
  public List<Order> searchOrders(UUID supplyingFacility, UUID requestingFacility,
                                  UUID program) {
    return orderRepository.searchOrders(
        supplyingFacility,
        requestingFacility,
        program);
  }

  /**
   * Changes order to CSV formatted file.
   *
   * @param order         Order type object to be transformed into CSV
   * @param chosenColumns String array containing names of columns to be taken from order
   */
  public void orderToCsv(Order order, String[] chosenColumns,
                         Writer writer) throws OrderCsvWriteException {
    if (order != null) {
      List<Map<String, Object>> rows = orderToRows(order);

      if (!rows.isEmpty()) {
        ICsvMapWriter mapWriter = null;
        try {
          mapWriter = new CsvMapWriter(writer, CsvPreference.STANDARD_PREFERENCE);
          mapWriter.writeHeader(chosenColumns);

          for (Map<String, Object> row : rows) {
            mapWriter.write(row, chosenColumns);
          }
        } catch (IOException ex) {
          throw new OrderCsvWriteException("I/O while creating the order CSV file", ex);
        } finally {
          closeQuietly(mapWriter);
        }
      }
    }
  }

  /**
   * Changes order to PDF formatted file given at OutputStream.
   *
   * @param order         Order type object to be transformed into CSV
   * @param chosenColumns String array containing names of columns to be taken from order
   * @param out           OutputStream to which the pdf file content will be written
   */
  public void orderToPdf(Order order, String[] chosenColumns, OutputStream out)
      throws OrderPdfWriteException {
    if (order != null) {
      List<Map<String, Object>> rows = orderToRows(order);
      try {
        writePdf(rows, chosenColumns, out);
      } catch (JRException ex) {
        throw new OrderPdfWriteException("Jasper error", ex);
      } catch (IOException ex) {
        throw new OrderPdfWriteException("I/O error", ex);
      }
    }
  }

  private void writePdf(List<Map<String, Object>> data, String[] chosenColumns,
                        OutputStream out) throws JRException, IOException {
    String filePath = "jasperTemplates/ordersJasperTemplate.jrxml";
    ClassLoader classLoader = getClass().getClassLoader();

    try (InputStream fis = classLoader.getResourceAsStream(filePath)) {
      JasperReport pdfTemplate = JasperCompileManager.compileReport(fis);
      Object[] params = new Object[data.size()];

      for (int index = 0; index < data.size(); ++index) {
        Map<String, Object> dataRow = data.get(index);

        params[index] = ImmutableMap
            .<String, Object>builder()
            .put(DEFAULT_COLUMNS[3], dataRow.get(DEFAULT_COLUMNS[3]))
            .put(DEFAULT_COLUMNS[6], dataRow.get(DEFAULT_COLUMNS[6]))
            .put(DEFAULT_COLUMNS[5], dataRow.get(DEFAULT_COLUMNS[5]))
            .build();
      }

      JRMapArrayDataSource dataSource = new JRMapArrayDataSource(params);
      JasperPrint jasperPrint = JasperFillManager.fillReport(
          pdfTemplate, new HashMap<>(), dataSource
      );

      JRPdfExporter exporter = new JRPdfExporter();
      exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
      exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
      exporter.exportReport();
    }
  }

  private List<Map<String, Object>> orderToRows(Order order) {
    List<Map<String, Object>> rows = new ArrayList<>();

    List<OrderLineItem> orderLineItems = order.getOrderLineItems();
    String orderNum = order.getOrderCode();

    FacilityDto requestingFacility = facilityReferenceDataService.findOne(
        order.getRequestingFacilityId());
    String facilityCode = requestingFacility.getCode();
    LocalDateTime createdDate = order.getCreatedDate();

    for (OrderLineItem orderLineItem : orderLineItems) {
      Map<String, Object> row = new HashMap<>();

      OrderableProductDto product = orderableProductReferenceDataService
          .findOne(orderLineItem.getOrderableProductId());

      row.put(DEFAULT_COLUMNS[0], facilityCode);
      row.put(DEFAULT_COLUMNS[1], createdDate);
      row.put(DEFAULT_COLUMNS[2], orderNum);
      row.put(DEFAULT_COLUMNS[3], product.getName());
      row.put(DEFAULT_COLUMNS[4], product.getProductCode());
      row.put(DEFAULT_COLUMNS[5], orderLineItem.getOrderedQuantity());
      row.put(DEFAULT_COLUMNS[6], orderLineItem.getFilledQuantity());

      //products which have a final approved quantity of zero are omitted
      if (orderLineItem.getOrderedQuantity() > 0) {
        rows.add(row);
      }
    }
    return rows;
  }

  /**
   * Saves a new instance of order.
   *
   * @param order instance
   * @return passed instance after save.
   */
  public Order save(Order order) throws OrderStorageException {
    ProgramDto program = programReferenceDataService.findOne(order.getProgramId());
    OrderNumberConfiguration orderNumberConfiguration =
        orderNumberConfigurationRepository.findAll().iterator().next();

    order.setOrderCode(
        orderNumberConfiguration.generateOrderNumber(order, program)
    );

    order = orderRepository.save(order);
    orderStorage.store(order);

    return order;
  }

  /**
   * Checks if provided facility is assigned to an order.
   * @param order checked order.
   * @param userId UUID of user.
   * @param facilityId UUID of facility.
   * @return result of check.
   */
  public boolean isFacilityValid(Order order,UUID userId, UUID facilityId ) {
    Set<UUID> userFacilities = fulfillmentFacilitiesReferenceDataService
        .getFulfillmentFacilities(userId).stream().map(FacilityDto::getId)
        .collect(Collectors.toSet());

    Set<UUID> validFacilities = getAvailableSupplyingDepots(order)
        .stream().filter(f -> userFacilities.contains(f.getId())).map(FacilityDto::getId)
        .collect(Collectors.toSet());

    return validFacilities.contains(facilityId);
  }

  private List<FacilityDto> getAvailableSupplyingDepots(Order order) {
    Collection<FacilityDto> facilityDtos = facilityReferenceDataService
        .searchSupplyingDepots(order.getProgramId(), order.getSupervisoryNodeId());
    return new ArrayList<>(facilityDtos);
  }

}
