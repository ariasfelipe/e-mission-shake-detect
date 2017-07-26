//
//  BEMShakeNotifier.m
//  emission
//
//  Created by Felipe Arias
//
//
#import <CoreMotion/CoreMotion.h>
#import <math.h>
#import <Foundation/Foundation.h>
#import <QuartzCore/QuartzCore.h>
#import "BEMShakeNotifier.h"
#import "TripDiaryStateMachine.h"
#import "APPLocalNotification.h"
#import "BEMBuiltinUserCache.h"
#import "DataUtils.h"
#import "LocalNotificationManager.h"
#import "SimpleLocation.h"
#import "PotentialIncident.h"
#import "Transition.h"
#import "MotionActivity.h"


#define TRIP_STARTED @"trip_started"
#define TRIP_ENDED @"trip_ended"
#define TRACKING_STARTED @"tracking_started"
#define TRACKING_STOPPED @"tracking_stopped"
#define POTENTIAL_INCIDENT @"potential_incident"

#define CONFIG_LIST_KEY @"config_list"
#define MUTED_LIST_KEY @"muted_list"

#define isNSNull(value) [value isKindOfClass:[NSNull class]]

@interface BEMShakeNotifier () {
    // Member variables go here.
}

@property (nonatomic,strong) NSMutableDictionary *observerMap;
- (void)pluginInitialize;
- (void)fireGenericTransitionFor:(NSString*) transition withUserInfo:(NSDictionary*) userInfo;
- (void)notifyEvent:(NSString *)eventName data:(NSDictionary*)data;
- (void)addEventListener:(CDVInvokedUrlCommand*)command;
- (void)removeEventListener:(CDVInvokedUrlCommand*)command;
- (void)enableSensor;
- (void)disableSensor;

@end

@implementation BEMShakeNotifier


- (void)pluginInitialize
{
    // TODO: We should consider adding a create statement to the init, similar
    // to android - then it doesn't matter if the pre-populated database is not
    // copied over.
    NSLog(@"BEMShakeNotifier:pluginInitialize singleton -> initialize statemachine and delegate");
    __typeof(self) __weak weakSelf = self;
    [[NSNotificationCenter defaultCenter] addObserverForName:CFCTransitionNotificationName object:nil queue:nil
                                                  usingBlock:^(NSNotification* note) {
                                                      __typeof(self) __strong strongSelf = weakSelf;
                                                      [strongSelf fireGenericTransitionFor:(NSString*)note.object withUserInfo:note.userInfo];
                                                  }];
}

- (void)fireGenericTransitionFor:(NSString*) transition withUserInfo:(NSDictionary*) userInfo {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Received platform-specific notification %@", transition] showUI:FALSE];
    
    if ([TripDiaryStateMachine instance].currState == kWaitingForTripStartState &&
        ([transition isEqualToString:CFCTransitionExitedGeofence] ||
         [transition isEqualToString:CFCTransitionVisitEnded])) {
            
            //Start Detecting Shakes
            [self enableSensor];
            return;
        }
    
    // We want to generate the event on trip end detected because we transition from
    // trip end detected -> trip ended after the geofence creation is complete.
    // Even if geofence creation failed, the trip still ended.
    // and once the trip is ended we start pushing data right way
    // which means that we can't reliably read local data since it is being
    // actively pushed to the server.
    // This is still somewhat unreliable - if this is slow and the geofence creation is fast, we may still race. In that case, we will just skip the trip end notification for now. Also, need to check whether iOS notification processing is serial or parallel. If serial, we are saved.
    if ([transition isEqualToString:CFCTransitionTripEndDetected] || [transition isEqualToString:CFCTransitionTrackingStopped])
        //  || [transition isEqualToString:CFCTransitionTripEnded])
    {
        //Stop Detecting Shakes
        [self disableSensor];
        return;
    }
}

- (void)notifyEvent:(NSString *)eventName data:(NSDictionary*)autogenData
{
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Generating all notifications for generic %@", eventName] showUI:FALSE];
    
    NSDictionary* notifyConfigWrapper = [[BuiltinUserCache database] getLocalStorage:eventName
                                                                        withMetadata:NO];
    if (notifyConfigWrapper == NULL) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"no configurations found for event %@, skipping notification", eventName] showUI:FALSE];
        return;
    }
    
    NSArray* notifyConfigs = notifyConfigWrapper[CONFIG_LIST_KEY];
    NSArray* mutedConfigs = notifyConfigWrapper[MUTED_LIST_KEY];
    
    for(int i=0; i < [notifyConfigs count]; i++) {
        NSMutableDictionary* currNotifyConfig = [notifyConfigs objectAtIndex:i];
        NSUInteger mutedIndex = [self findIndex:currNotifyConfig fromList:mutedConfigs];
        
        if (mutedIndex == NSNotFound) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"notification for event %@ and id %@ not muted, generate ", eventName, currNotifyConfig[@"id"]] showUI:FALSE];
            if (autogenData != NULL) { // we need to merge in the autogenerated data
                NSMutableDictionary *currData = currNotifyConfig[@"data"];
                if (currData == NULL) {
                    currData = [NSMutableDictionary new];
                    currNotifyConfig[@"data"] = currData;
                }
                [currData addEntriesFromDictionary:autogenData];
            }
            NSString* currNotifyString = [DataUtils saveToJSONString:currNotifyConfig];
            NSString *func = [NSString stringWithFormat:@"window.cordova.plugins.BEMShakeNotification.dispatchIOSLocalNotification(%@);", currNotifyString];
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"generating notification for event %@ and id %@", eventName, currNotifyConfig[@"id"]] showUI:FALSE];
            
            [self.commandDelegate evalJs:func];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"notification for event %@ and id %@ muted, skip ", eventName, currNotifyConfig[@"id"]] showUI:FALSE];
        }
    }
}
-(NSUInteger)findIndex:(NSDictionary*)localNotifyConfig fromList:(NSArray*)currList
{
    // This handles the muted list case. muted list could be null if the event had never been muted
    if (currList == NULL) {
        return NSNotFound;
    }
    NSUInteger existingIndex = [currList indexOfObjectPassingTest:^BOOL(id  _Nonnull obj, NSUInteger idx, BOOL * _Nonnull stop) {
        // Note that the id is a long so == works. If we assume that it is a string, we would need to use isEqualToString
        return obj[@"id"] == localNotifyConfig[@"id"];
    }];
    return existingIndex;
}

-(void)addOrReplaceEntry:(NSDictionary*)localNotifyConfig
                forEvent:(NSString*)eventName
               withLabel:(NSString*)listName
{
    NSMutableDictionary* configWrapper = [[BuiltinUserCache database] getLocalStorage:eventName
                                                                         withMetadata:NO];
    
    NSMutableArray* currList;
    
    if (configWrapper == NULL) {
        configWrapper = [NSMutableDictionary new];
        currList = [NSMutableArray new];
        configWrapper[listName] = currList;
    } else {
        currList = configWrapper[listName];
        if (currList == NULL) {
            currList = [NSMutableArray new];
            configWrapper[listName] = currList;
        }
    }
    
    // Checking for the invariant
    assert(configWrapper != NULL && currList != NULL && configWrapper[listName] == currList);
    
    NSUInteger existingIndex = [self findIndex:localNotifyConfig fromList:currList];
    
    BOOL modified = YES;
    if (existingIndex == NSNotFound) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"new configuration, adding object with id %@", localNotifyConfig[@"id"]]];
        [currList addObject:localNotifyConfig];
    } else {
        if ([localNotifyConfig isEqualToDictionary:currList[existingIndex]]) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"configuration unchanged, skipping list modify"]];
            modified = NO;
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"configuration changed, changing object at index %lu", existingIndex]];
            [currList setObject:localNotifyConfig atIndexedSubscript:existingIndex];
        }
    }
    
    if (modified) {
        [[BuiltinUserCache database] putLocalStorage:eventName jsonValue:configWrapper];
    }
}

- (void)removeEntry:(NSDictionary*)localNotifyConfig
           forEvent:(NSString*)eventName
          withLabel:(NSString*)listName
{
    NSMutableDictionary* configWrapper = [[BuiltinUserCache database] getLocalStorage:eventName
                                                                         withMetadata:NO];
    
    if (configWrapper != NULL) { // There is an existing entry for this event
        NSMutableArray* currList = configWrapper[listName];
        NSUInteger existingIndex = [self findIndex:localNotifyConfig fromList:currList];
        if (existingIndex != -1) { // There is an existing entry for this ID
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"removed obsolete notification at %lu", existingIndex]];
            [currList removeObjectAtIndex:existingIndex];
            if ([currList count] == 0) { // list size is now zero
                // Let us think about what we want to happen here. One thought might be that we want to
                // remove the entry iff both lists are empty. But then you could run into situations in which
                // there was a notification, it was muted, and then it was removed. Because the muted list still
                // had an entry, we would keep the (zombie) entry around.
                // So it seems like we should actually look at the list and treat the config list and the muted list
                // differently. Alternatively, we could document that you always need to unmute a list before deleting it
                // but that places additional (unnecessary) burden on the user.
                // So let's treat them separately for now and fix later if it is a problem
                if ([listName isEqualToString:CONFIG_LIST_KEY]) {
                    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"config list size is now 0, removing entry for event %@", eventName]];
                    [[BuiltinUserCache database] removeLocalStorage:eventName];
                } else {
                    assert([listName isEqualToString:MUTED_LIST_KEY]);
                    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"muted list size is now 0, removing list %@ for event %@", listName, eventName]];
                    [configWrapper removeObjectForKey:listName];
                    [[BuiltinUserCache database] putLocalStorage:eventName jsonValue:configWrapper];
                }
            } else {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"saving list with size %lu", [currList count]]];
                [[BuiltinUserCache database] putLocalStorage:eventName jsonValue:configWrapper];
            }
        }
    }
}


- (void)addEventListener:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult;
    
    __block NSString* eventName = command.arguments[0];
    
    if (isNSNull(eventName)|| [eventName length] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"eventName is null or empty"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    __block NSDictionary* localNotifyConfig = command.arguments[1];
    
    if (isNSNull(localNotifyConfig) || [localNotifyConfig count] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"localNotifyConfig is null or empty"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    [self addOrReplaceEntry:localNotifyConfig
                   forEvent:eventName
                  withLabel:CONFIG_LIST_KEY];
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}



- (void)removeEventListener:(CDVInvokedUrlCommand*)command
{
    
    CDVPluginResult* pluginResult;
    
    __block NSString* eventName = command.arguments[0];
    
    if (isNSNull(eventName) || [eventName length] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"eventName is null or empty"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    __block NSDictionary* localNotifyConfig = command.arguments[1];
    
    if (isNSNull(localNotifyConfig) || [localNotifyConfig count] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"localNotifyConfig is null or empty"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    [self removeEntry:localNotifyConfig forEvent:eventName withLabel:CONFIG_LIST_KEY];
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    
}

- (void)enableEventListener:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult;
    
    __block NSString* eventName = command.arguments[0];
    
    if (isNSNull(eventName) || [eventName length] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"eventName is null or empty"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    __block NSDictionary* localNotifyConfig = command.arguments[1];
    
    if (isNSNull(localNotifyConfig) || [localNotifyConfig count] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"localNotifyConfig is null or empty"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    // enabling the listener means removing an entry from the muted list
    [self removeEntry:localNotifyConfig
             forEvent:eventName
            withLabel:MUTED_LIST_KEY];
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)disableEventListener:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult;
    
    __block NSString* eventName = command.arguments[0];
    
    if (isNSNull(eventName) || eventName == nil || [eventName length] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"eventName is null"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    __block NSDictionary* localNotifyConfig = command.arguments[1];
    
    if (isNSNull(localNotifyConfig) || localNotifyConfig == nil || [localNotifyConfig count] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"localNotifyConfig is null or empty"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    // disabling the listener means adding an entry to the muted list
    [self addOrReplaceEntry:localNotifyConfig
                   forEvent:eventName
                  withLabel:MUTED_LIST_KEY];
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

-(void)enableSensor{
    motionManager = [[CMMotionManager alloc] init];
    if([motionManager isAccelerometerAvailable]){
        if([motionManager isAccelerometerActive] == NO){
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"DetectedShake"]
                                                                    showUI:FALSE];
            motionManager.accelerometerUpdateInterval = 0.1f;
            __block double currentAcceleration = 0.0;
            __block double acceleration = 0.0;
            __block double lastUpdated = 0.0;
            
            double yAccelerationConst = 0.5;
            [motionManager startAccelerometerUpdatesToQueue:[NSOperationQueue mainQueue] withHandler:^(CMAccelerometerData *data, NSError *error){
                double currTime = CACurrentMediaTime();
                if((currTime - lastUpdated) >= 1.0){
                    double lastAcceleration = currentAcceleration;
                    currentAcceleration = sqrtf(data.acceleration.z*data.acceleration.z + data.acceleration.y*data.acceleration.y*yAccelerationConst*yAccelerationConst);
                
                    acceleration = acceleration*0.9f + (currentAcceleration - lastAcceleration);
                
                    if((acceleration > 5.0) && (fabs(data.acceleration.x) < 5.0)){
                        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                                   @"DetectedIncident"]
                                                           showUI:FALSE];
                        
                        
                        lastUpdated = currTime;
                        [self notifyEvent:POTENTIAL_INCIDENT data:NULL];
                        NSArray* lastLocation = [[BuiltinUserCache database] getLastSensorData:@"key.usercache.filtered_location" nEntries:1 wrapperClass:[SimpleLocation class]];
                        
                        if ([lastLocation count] == 0){
                            return;
                        }
                        
                        NSArray* lastActivity = [[BuiltinUserCache database] getLastSensorData:@"key.usercache.activity" nEntries:1 wrapperClass:[MotionActivity class]];
                        
                        if ([lastActivity count] == 0){
        
                            SimpleLocation *lastLoc = ((SimpleLocation*)lastLocation.firstObject);
                        
                            PotentialIncident* potIncident = [[PotentialIncident alloc]initWithLocationAccel: lastLoc xVal:data.acceleration.x yVal:data.acceleration.y zVal:data.acceleration.z];
                        
                            [[BuiltinUserCache database] putSensorData:@"key.usercache.location" value:potIncident];
                        }
                        else{
                            SimpleLocation *lastAc = ((SimpleLocation*)lastActivity.firstObject);
                            
                            SimpleLocation *lastLoc = ((SimpleLocation*)lastLocation.firstObject);
                            
                            //Also add activity to potIncident
                            PotentialIncident* potIncident = [[PotentialIncident alloc]initWithLocationAccel: lastLoc xVal:data.acceleration.x yVal:data.acceleration.y zVal:data.acceleration.z];
                            
                            [[BuiltinUserCache database] putSensorData:@"key.usercache.location" value:potIncident];
                            
                        }

                    }
                }
            }];
            
        }
    }
    //Gyroscope implementation
    /*else if([motionManager isGyroAvailable]){
        if([motionManager isGyroActive] == NO){
            [motionManager setGyroUpdateInterval:1.0f / 10.0f];
            
            [motionManager startGyroUpdatesToQueue:[NSOperationQueue mainQueue] withHandler:^(CMGyroData *gyroData, NSError *error){
                float rate = gyroData.rotationRate.x;
                if(fabs(rate) > 0.6f){
                    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                               @"DetectedShake rotX: %f",
                                                               rate]
                                                       showUI:FALSE];
                    [self notifyEvent:POTENTIAL_INCIDENT data:NULL];
                }
            }];
        }
    }*/
}

-(void)disableSensor{
    //[motionManager stopGyroUpdates];
    [motionManager stopAccelerometerUpdates];
}

@end
