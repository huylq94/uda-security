package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.FakeImageService;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private final String uuid = UUID.randomUUID().toString();

    private Sensor sensor;

    @InjectMocks
    private SecurityService service;

    @Mock
    private FakeImageService imageService;

    @Mock
    private SecurityRepository securityRepository;

    @BeforeEach
    void init() {
        sensor = new Sensor(uuid, SensorType.DOOR);
    }

    /**
     * Case 1
     */
    @Test
    void sensorActivated_alarmArmedAndStatusNoAlarm_alarmStatusAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        service.changeSensorActivationStatus(sensor, Boolean.TRUE);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    /**
     * Case 2
     */
    @Test
    void sensorActivated_alarmArmedAndStatusPending_alarmStatusAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        service.changeSensorActivationStatus(sensor, Boolean.TRUE);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * Case 3
     */
    @Test
    void allSensorInactive_alarmStatusPending_alarmStatusNoAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(Boolean.FALSE);
        service.changeSensorActivationStatus(sensor);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * Case 4
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void alarmIsActive_sensorStateNotAffectAlarmState(Boolean alarmStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        service.changeSensorActivationStatus(sensor, alarmStatus);

        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    /**
     * Case 5
     */
    @Test
    void sensorIsActivatedWhileAlreadyActive_alarmStatusAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(Boolean.TRUE);
        service.changeSensorActivationStatus(sensor, Boolean.TRUE);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * Case 6
     */
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM", "ALARM"})
    void sensorDeactivatedWhileAlreadyInactive_alarmStatusNoChange(AlarmStatus alarmStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        sensor.setActive(Boolean.FALSE);
        service.changeSensorActivationStatus(sensor, Boolean.FALSE);

        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    /**
     * Case 7
     */
    @Test
    void imageServiceIdentifiesImageContainCat_systemArmedHome_alarmStatusAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(true);
        service.processImage(new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB));

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * Case 8
     */
    @Test
    void imageServiceIdentifiesImageNotContainCat_sensorInactive_alarmStatusNoLarm() {
        Set<Sensor> sensors = getAllSensors(false);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(false);
        service.processImage(mock(BufferedImage.class));

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * Case 9
     */
    @Test
    void systemDisarmed_alarmStatusNoAlarm() {
        service.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * Case 10
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void systemArmed_allSensorsToInActive(ArmingStatus status) {
        Set<Sensor> sensors = getAllSensors(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getSensors()).thenReturn(sensors);
        service.setArmingStatus(status);

        service.getSensors().forEach(sensor -> assertFalse(sensor.getActive()));
    }

    /**
     * Case 11
     */
    @Test
    void systemArmedHomeWhileCameraShowsACat_alarmStatusAlarm() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        service.processImage(new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB));
        service.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    private Set<Sensor> getAllSensors(boolean status) {
        HashSet<Sensor> sensors = IntStream
                .range(0, 3)
                .mapToObj(i -> new Sensor(uuid, SensorType.DOOR))
                .collect(Collectors.toCollection(HashSet::new));

        sensors.forEach(sensor -> sensor.setActive(status));
        return sensors;
    }
}
