//
//  ShakeDetection.h
//  emission
//
//  Created by Felipe Arias
//
//

#ifndef ShakeDetection_h
#define ShakeDetection_h

#import <CoreMotion/CoreMotion.h>
#import "LocalNotificationManager.h"

CMMotionManager *motionManager;
@interface ShakeDetection:NSObject

+(void)enableGyro;
+(void)disableGyro;

@end

#endif /* ShakeDetection_h */
