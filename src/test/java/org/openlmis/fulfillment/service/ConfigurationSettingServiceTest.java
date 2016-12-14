package org.openlmis.fulfillment.service;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.fulfillment.domain.ConfigurationSetting;
import org.openlmis.fulfillment.repository.ConfigurationSettingRepository;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationSettingServiceTest {
  private static final String KEY = "key";
  private static final String VALUE = "value";

  @Mock
  private ConfigurationSettingRepository repository;

  @InjectMocks
  private ConfigurationSettingService service;

  @Mock
  private ConfigurationSetting setting;

  @Test(expected = ConfigurationSettingException.class)
  public void shouldThrowExceptionIfSettingCannotBeFound() throws ConfigurationSettingException {
    service.getStringValue(KEY);
  }

  @Test(expected = ConfigurationSettingException.class)
  public void shouldThrowExceptionIfSettingValueIsNull() throws ConfigurationSettingException {
    when(repository.findOne(KEY)).thenReturn(setting);
    when(setting.getValue()).thenReturn(null);

    service.getStringValue(KEY);
  }

  @Test
  public void shouldReturnStringValue() throws ConfigurationSettingException {
    when(repository.findOne(KEY)).thenReturn(setting);
    when(setting.getValue()).thenReturn(VALUE);

    assertThat(service.getStringValue(KEY), is(VALUE));
  }

  @Test
  public void shouldUpdateSetting() throws Exception {
    when(repository.findOne(KEY)).thenReturn(setting);
    when(repository.save(any(ConfigurationSetting.class))).thenReturn(setting);

    service.update(new ConfigurationSetting(KEY, VALUE + "2"));

    verify(setting).setValue(VALUE + "2");
    verify(repository).save(setting);

  }
}