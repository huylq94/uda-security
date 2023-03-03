package com.udacity.catpoint.security.service;


import com.udacity.catpoint.image.service.FakeImageService;
import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static com.udacity.catpoint.security.data.AlarmStatus.*;
import static com.udacity.catpoint.security.data.ArmingStatus.ARMED_HOME;
import static com.udacity.catpoint.security.data.ArmingStatus.DISARMED;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 *
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private final ImageService imageService;
    private final SecurityRepository securityRepository;
    private final Set<StatusListener> statusListeners = new HashSet<>();
    private boolean catDetect = false;

    public SecurityService(SecurityRepository securityRepository, FakeImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if (catDetect && armingStatus == ARMED_HOME) {
            setAlarmStatus(ALARM);
        }
        if(armingStatus == DISARMED) {
            setAlarmStatus(NO_ALARM);
        } else {
            ConcurrentSkipListSet<Sensor> sensors = new ConcurrentSkipListSet<>(getSensors());
            sensors.forEach(sensor -> changeSensorActivationStatus(sensor, false));
        }
        securityRepository.setArmingStatus(armingStatus);
        statusListeners.forEach(StatusListener::sensorStatusChanged);
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        this.catDetect = cat;
        if(Boolean.TRUE.equals(cat) && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(ALARM);
        } else if (Boolean.FALSE.equals(cat) && getSensors().stream().allMatch(sensor -> Boolean.FALSE.equals(sensor.getActive()))) {
            setAlarmStatus(NO_ALARM);
        }

        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if(securityRepository.getArmingStatus() == DISARMED) {
            return; //no problem if the system is disarmed
        }
        switch(securityRepository.getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(ALARM);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated() {
        switch(securityRepository.getAlarmStatus()) {
            case PENDING_ALARM -> setAlarmStatus(NO_ALARM);
            case ALARM -> setAlarmStatus(PENDING_ALARM);
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        AlarmStatus alarmStatus = securityRepository.getAlarmStatus();
        ArmingStatus armingStatus = this.getArmingStatus();
        if (active == null) {
            if (alarmStatus == PENDING_ALARM && !sensor.getActive()) {
                handleSensorDeactivated();
            } else if (alarmStatus == ALARM && armingStatus == DISARMED) {
                handleSensorDeactivated();
            }
        } else {
            if (alarmStatus != ALARM) {
                if (active) {
                    handleSensorActivated();
                } else if (sensor.getActive()) {
                    handleSensorDeactivated();
                }
            }
            sensor.setActive(active);
        }

        securityRepository.updateSensor(sensor);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
