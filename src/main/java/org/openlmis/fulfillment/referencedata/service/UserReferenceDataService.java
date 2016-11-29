package org.openlmis.fulfillment.referencedata.service;

import org.openlmis.fulfillment.dto.ResultDto;
import org.openlmis.fulfillment.referencedata.model.UserDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserReferenceDataService extends BaseReferenceDataService<UserDto> {

  @Override
  protected String getUrl() {
    return "/api/users/";
  }

  @Override
  protected Class<UserDto> getResultClass() {
    return UserDto.class;
  }

  @Override
  protected Class<UserDto[]> getArrayResultClass() {
    return UserDto[].class;
  }

  /**
   * This method retrieves a user with given name.
   *
   * @param name the name of user.
   * @return UserDto containing user's data, or null if such user was not found.
   */
  public UserDto findUser(String name) {
    Map<String, Object> requestParameters = new HashMap<>();
    requestParameters.put("username", name);

    List<UserDto> users = new ArrayList<>(findAll("search", requestParameters));
    return users.size() > 0 ? users.get(0) : null;
  }

  /**
   * Check if user has a right with certain criteria.
   *
   * @param user     id of user to check for right
   * @param right    right to check
   * @param program  program to check (for supervision rights, can be {@code null})
   * @param facility facility to check (for supervision rights, can be {@code null})
   * @return an instance of {@link ResultDto} with result depending on if user has the right.
   */
  public ResultDto<Boolean> hasRight(UUID user, UUID right, UUID program, UUID facility) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("rightId", right);

    if (null != program) {
      parameters.put("programId", program);
    }

    if (null != facility) {
      parameters.put("facilityId", facility);
    }
    ResultDto<?> result = get(ResultDto.class, user + "/hasRight", parameters);

    return new ResultDto<>(Boolean.valueOf((String)result.getResult()));
  }

}