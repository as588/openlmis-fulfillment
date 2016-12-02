package org.openlmis.fulfillment.service;

import static org.supercsv.prefs.CsvPreference.STANDARD_PREFERENCE;

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
import org.openlmis.fulfillment.repository.OrderNumberConfigurationRepository;
import org.openlmis.fulfillment.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OrderService {

  static final String[] DEFAULT_COLUMNS = {"facilityCode", "createdDate", "orderNum",
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
  private OrderStorage orderStorage;

  @Autowired
  private OrderSender orderSender;

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
    if (null == order) {
      return;
    }

    List<Map<String, Object>> rows = orderToRows(order);

    if (rows.isEmpty()) {
      return;
    }

    try (ICsvMapWriter mapWriter = new CsvMapWriter(writer, STANDARD_PREFERENCE)) {
      mapWriter.writeHeader(chosenColumns);

      for (Map<String, Object> row : rows) {
        mapWriter.write(row, chosenColumns);
      }
    } catch (IOException ex) {
      throw new OrderCsvWriteException("I/O while creating the order CSV file", ex);
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

        params[index] = Stream.of(chosenColumns)
            .collect(Collectors.toMap(column -> column, dataRow::get));
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
  public Order save(Order order) throws OrderSaveException {
    ProgramDto program = programReferenceDataService.findOne(order.getProgramId());
    OrderNumberConfiguration orderNumberConfiguration =
        orderNumberConfigurationRepository.findAll().iterator().next();

    order.setOrderCode(
        orderNumberConfiguration.generateOrderNumber(order, program)
    );

    Order saved = orderRepository.save(order);

    try {
      orderStorage.store(saved);
      boolean success = orderSender.send(saved);

      if (success) {
        orderStorage.delete(saved);
      }
    } catch (OrderStorageException exp) {
      throw new OrderSaveException("Unable to storage the order", exp);
    } catch (OrderSenderException exp) {
      throw new OrderSaveException("Unable to send the order", exp);
    }

    return saved;
  }

}
