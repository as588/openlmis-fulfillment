package org.openlmis.fulfillment.utils;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.fulfillment.referencedata.model.RightDto;
import org.openlmis.fulfillment.referencedata.model.UserDto;
import org.openlmis.fulfillment.referencedata.service.RightReferenceDataService;
import org.openlmis.fulfillment.referencedata.service.UserReferenceDataService;
import org.openlmis.fulfillment.util.AuthenticationException;
import org.openlmis.fulfillment.util.AuthenticationHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationHelperTest {

  @Mock
  private UserReferenceDataService userReferenceDataService;

  @Mock
  private RightReferenceDataService rightReferenceDataService;

  @InjectMocks
  private AuthenticationHelper authenticationHelper;

  @Before
  public void setUp() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn("username");

    SecurityContext securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);

    SecurityContextHolder.setContext(securityContext);
  }

  @Test
  public void shouldReturnUser() {
    // given
    UserDto userMock = mock(UserDto.class);
    when(userReferenceDataService.findUser(any(String.class))).thenReturn(userMock);

    // when
    UserDto user = authenticationHelper.getCurrentUser();

    // then
    assertNotNull(user);
  }

  @Test(expected = AuthenticationException.class)
  public void shouldThrowExceptionIfUserDoesNotExist() {
    // given
    when(userReferenceDataService.findUser(any(String.class))).thenReturn(null);

    // when
    authenticationHelper.getCurrentUser();
  }

  @Test
  public void shouldReturnRight() throws Exception {
    // given
    RightDto right = mock(RightDto.class);
    when(rightReferenceDataService.findRight(anyString())).thenReturn(right);

    // when
    RightDto dto = authenticationHelper.getRight("rightName");

    // then
    assertNotNull(dto);
    assertThat(dto, is(right));
  }

  @Test(expected = AuthenticationException.class)
  public void shouldThrowExceptionIfRightDoesNotExist() {
    // given
    when(rightReferenceDataService.findRight(anyString())).thenReturn(null);

    // when
    authenticationHelper.getRight("rightName");
  }
}
