/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org. 
 */

package org.openlmis.fulfillment.service.notification;

import org.openlmis.fulfillment.service.BaseCommunicationService;
import org.openlmis.util.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService extends BaseCommunicationService {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Value("${notification.url}")
  private String notificationUrl;

  /**
   * Send a notification request.
   *
   * @param request details about notification.
   * @return true if success, false if failed.
   */
  public boolean send(NotificationRequest request) {
    String url = getNotificationUrl() + "/api/notification";

    Map<String, String> params = new HashMap<>();
    params.put(ACCESS_TOKEN, obtainAccessToken());

    HttpEntity<NotificationRequest> body = new HttpEntity<>(request);

    try {
      restTemplate.postForEntity(buildUri(url, params), body, NotificationRequest.class);
    } catch (RestClientException ex) {
      logger.error("Can not send a notification request", ex);
      return false;
    }

    return true;
  }

  String getNotificationUrl() {
    return notificationUrl;
  }

}
