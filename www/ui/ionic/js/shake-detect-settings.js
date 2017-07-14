angular.module('emission.main.control.sdetect', [])
.factory("ControlShakeDetectHelper", function($window, 
        $ionicActionSheet, $ionicPopup, $ionicPopover, $rootScope) {

    var csdh = {};
    var CONFIG_LIST = "config_list";
    var MUTED_LIST = "muted_list";
    csdh.incident_list = [
        "trip_started", "trip_ended", "tracking_started", "tracking_stopped", "potential_incident"
    ];
    csdh.new_configList = [];
    csdh.incident2configList = [];
    csdh.mergedShakeDetectEnableList = [];
    csdh.settingsPopup = {};

    /* 
     * Functions to read and format values for display
     */

    csdh.getSDetecttSettings = function() {
        var promiseList = csdh.incident_list.map(function(tn) {
            return csdh.getConfigForIncident(tn, true);
        });
        return Promise.all(promiseList).then(function(resultList){
            csdh.incident2configList = resultList;
            var notifyEnableLists = resultList.filter(non_null).map(csdh.config2notifyList);
            csdh.mergedShakeDetectEnableList = notifyEnableLists.reduce(
                function(acc, val) {
                return acc.concat(val);
            });
            return csdh.mergedShakeDetectEnableList;
        })
    };

    var non_null = function(el) {
        return el != null;
    }

    /*
     * Output of this function is a map of the form:
     * { incidentName: "trip_ended",
         notifyOptions: {
            id: "737678",
            title: "Trip just ended",
            ...
         },
         enabled: true/false
     * }
     */

    csdh.config2notifyList = function(configWithMetadata) {
        var configList = configWithMetadata.data[CONFIG_LIST];
        var mutedList = configWithMetadata.data[MUTED_LIST];
        var enabledList = configList.map(function(config, i) {
            return !(isMutedEntry(config.id, mutedList));
        });
        var retVal = configList.map(function(config, i) {
            return {
                incidentName: configWithMetadata.metadata.key,
                incidentOptions: config,
                enabled: enabledList[i]
            };
        });
        return retVal;
    }

    var isMutedEntry = function(id, mutedList) {
        if (angular.isUndefined(mutedList)) {
            return false;
        };
        var foundMuted = mutedList.find(function(mutedEntry) {
            if (mutedEntry.id == id) {
                return true;
            }
        });
        // if we found a muted entry, foundMuted is defined
        // so if it is undefined, it is not muted, and we want to return false
        return !(angular.isUndefined(foundMuted));
    }

    /*
     * Currently unused - we're displaying a real template, not just key-value pairs
     */
    csdh.formatConfigForDisplay = function(tnce) {
        return {'key': tnce.incidentName + " "+tnce.incidentOptions.id +
                " "+tnce.incidentOptions.title, 'val': tnce.enabled};
    }

    /* 
     * Functions to edit and save values
     */

    var getPopoverScope = function() {
        var new_scope = $rootScope.$new();
        new_scope.saveAndReload = csdh.saveAndReload;
        new_scope.isIOS = ionic.Platform.isIOS;
        new_scope.isAndroid = ionic.Platform.isAndroid;
        new_scope.toggleEnable = csdh.toggleEnable;
        return new_scope;
    }

    csdh.editConfig = function($event) {
        csdh.editedDisplayConfig = angular.copy(csdh.mergedShakeDetectEnableList);
        csdh.toggledSet = new Set();
        var popover_scope = getPopoverScope();
        popover_scope.display_config = csdh.editedDisplayConfig;
        $ionicPopover.fromTemplateUrl('templates/control/main-shake-dectect-settings.html', {
            scope: popover_scope
        }).then(function(popover) {
            csdh.settingsPopup = popover;
            console.log("settings popup = "+csdh.settingsPopup);
            csdh.settingsPopup.show($event);
        });
        return csdh.new_config;
    }

    csdh.saveAndReload = function() {
        console.log("new config = "+csdh.editedDisplayConfig);
        var toggledArray = [];
        csdh.toggledSet.forEach(function(v) {
            toggledArray.push(v);
        });
        var promiseList = toggledArray.map(function(currConfigWrapper) {
            // TODO: I think we can use apply here since these are
            // basically the fields.
            return csdh.setEnabled(currConfigWrapper.incidentName, 
                currConfigWrapper.incidentOptions, currConfigWrapper.enabled);
        });
        Promise.all(promiseList).then(function(resultList) {
            // reset temporary state after all promises are resolved.
            csdh.mergedShakeDetectEnableList = csdh.editedDisplayConfig;
            csdh.toggledSet = [];
            $rootScope.$broadcast('control.update.complete', 'collection config');
        }).catch(function(error) {
            console.log("setConfig Error: " + err);
        });

        csdh.settingsPopup.hide();
        csdh.settingsPopup.remove();
    };

    /* 
     * Edit helpers for values that selected from actionSheets
     */

    csdh.toggleEnable = function(entry) {
        console.log(JSON.stringify(entry));
        csdh.toggledSet.add(entry);
    };

    csdh..forceState = function() {
        var forceStateActions = [{text: "Initialize",
                                  transition: "INITIALIZE"},
                                 {text: 'Start trip',
                                  transition: "EXITED_GEOFENCE"},
                                 {text: 'End trip',
                                  transition: "STOPPED_MOVING"},
                                 {text: 'Visit ended',
                                  transition: "VISIT_ENDED"},
                                 {text: 'Visit started',
                                  transition: "VISIT_STARTED"},
                                 {text: 'Remote push',
                                  transition: "RECEIVED_SILENT_PUSH"},
                                 {text: 'potential_incident',
                                  transition: "POTENTIAL_INCIDENT"}];
        $ionicActionSheet.show({
            buttons: forceStateActions,
            titleText: "Force state",
            cancelText: "Cancel",
            buttonClicked: function(index, button) {
                ctnh.forceTransition(button.transition);
                return true;
            }
        });
    };
    csdh.forceTransition = function(transition) {
        csdh.forceTransitionWrapper(transition).then(function(result) {
            $rootScope.$broadcast('control.update.complete', 'forceTransition');
            $ionicPopup.alert({template: 'success -> '+result});
        }, function(error) {
            $rootScope.$broadcast('control.update.complete', 'forceTransition');
            $ionicPopup.alert({template: 'error -> '+error});
        });
    };


    /* 
     * Functions for the separate accuracy toggle 
     */

    var accuracy2String = function() {
        var accuracy = csdh.config.accuracy;
        for (var k in csdh.accuracyOptions) {
            if (csdh.accuracyOptions[k] == accuracy) {
                return k;
            }
        }
    }

    csdh.isMediumAccuracy = function() {
        if (csdh.config == null) {
            return undefined; // config not loaded when loading ui, set default as false
        } else {
            var v = accuracy2String();
            if (ionic.Platform.isIOS()) {
                return v != "kCLLocationAccuracyBestForNavigation" && v != "kCLLocationAccuracyBest" && v != "kCLLocationAccuracyTenMeters";
            } else if (ionic.Platform.isAndroid()) {
                return v != "PRIORITY_HIGH_ACCURACY";
            } else {
                $ionicPopup.alert("Emission does not support this platform");
            }
        }
    }

    csdh.toggleLowAccuracy = function() {
        csdh.new_config = JSON.parse(JSON.stringify(csdh.config));
        if (csdh.isMediumAccuracy()) {
            if (ionic.Platform.isIOS()) {
                csdh.new_config.accuracy = csdh.accuracyOptions["kCLLocationAccuracyBest"];
            } else if (ionic.Platform.isAndroid()) {
                accuracy = csdh.accuracyOptions["PRIORITY_HIGH_ACCURACY"];
            }
        } else {
            if (ionic.Platform.isIOS()) {
                csdh.new_config.accuracy = csdh.accuracyOptions["kCLLocationAccuracyHundredMeters"];
            } else if (ionic.Platform.isAndroid()) {
                csdh.new_config.accuracy = csdh.accuracyOptions["PRIORITY_BALANCED_POWER_ACCURACY"];
            }
        }
        csdh.setConfig(csdh.new_config)
        .then(function(){
            console.log("setConfig Sucess");
        }, function(err){
            console.log("setConfig Error: " + err);
        });
    }

    /*
     * BEGIN: Simple read/write wrappers
     */

    csdh.getConfigForIncident = function(incidentName, withMetadata) {
      return window.cordova.plugins.BEMUserCache.getLocalStorage(incidentName, withMetadata);
    };

    csdh.setEnabled = function(incidentName, configData, enableState) {
      if (enableState == true) {
        return window.cordova.plugins.BEMShakeNotification.enableEventListener(incidentName, configData);
      } else {
        return window.cordova.plugins.BEMShakeNotification.disableEventListener(incidentName, configData);
      }
    };

    return csdh;
});
